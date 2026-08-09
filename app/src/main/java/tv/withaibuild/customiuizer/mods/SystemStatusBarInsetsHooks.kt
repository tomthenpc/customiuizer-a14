package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.graphics.Rect
import android.os.Handler
import android.util.DisplayMetrics
import android.view.WindowInsets
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

/**
 * system_server Insets boundary for status bar height.
 *
 * Two responsibilities:
 * 1. Adjust the `statusBars` [InsetsSource] frame so apps and SystemUI see the configured
 *    status bar height as an Insets top value.
 * 2. Drive the status bar [WindowState] to the same height without modifying the display
 *    frame, parent frame, relative frame or compatibility frame.
 *
 * WindowManager responsibilities:
 * - In `DisplayPolicy.layoutWindowLw` the status bar `WindowState.mAttrs.height` is set to
 *   the configured pixel height before the original method runs. The original WMS then calls
 *   `WindowLayout.computeFrames` and `WindowState.setFrames(ClientWindowFrames, ...)` once.
 * - `WindowState.setFrames` is hooked only as a narrow fallback: if the ROM's layout did not
 *   honor `mAttrs.height`, the `ClientWindowFrames.frame.bottom` is rewritten to
 *   `frame.top + configuredPx` before the original `setFrames` is called. No other frame is
 *   modified and `chain.proceed()` is called exactly once.
 * - `WindowState.computeFrame` is not hooked.
 *
 * Preference changes do not call `performSurfacePlacement` directly. They publish a new
 * configuration and request a single `WindowSurfacePlacer.requestTraversal()` on the WMS
 * animation handler, which coalesces duplicate requests.
 *
 * Diagnostics are generation-keyed and bounded: `preference-change:<gen>`,
 * `layout:<displayId>:<gen>`, `frame:<displayId>:<gen>`, `insets:<sourceId>:<gen>`,
 * `refresh:<gen>`.
 */
object SystemStatusBarInsetsHooks {

    private const val INSETS_SOURCE_CLASS = "android.view.InsetsSource"
    private const val INSETS_STATE_CLASS = "android.view.InsetsState"
    private const val SET_FRAME_METHOD = "setFrame"
    private const val GET_TYPE_METHOD = "getType"
    private const val GET_FRAME_METHOD = "getFrame"
    private const val GET_ID_METHOD = "getId"

    internal const val MAX_CRITICAL_KEYS = 16
    internal const val MAX_REJECTION_KEYS = 16

    private const val WINDOW_STATE_CLASS = "com.android.server.wm.WindowState"
    private const val DISPLAY_POLICY_CLASS = "com.android.server.wm.DisplayPolicy"
    private const val DECOR_INSETS_INFO_CLASS = "com.android.server.wm.DisplayPolicy\$DecorInsets\$Info"
    private const val DECOR_INSETS_UPDATE_METHOD = "update"
    private const val LAYOUT_WINDOW_LW_METHOD = "layoutWindowLw"
    private const val SET_FRAMES_METHOD = "setFrames"
    private const val STATUS_BAR_WINDOW_TAG = "StatusBar"
    private const val STATUS_BAR_HEIGHT_LIVE_TAG = "[StatusBarHeightLive]"
    private const val ORIGINAL_STATUS_BAR_HEIGHT_KEY = "customiuizer_originalStatusBarHeight"

    private var typeInfo: InsetsTypeInfo? = null
    private var hookInstalled: Boolean = false

    /** Weak reference to the most recently laid-out status bar WindowState for refresh. */
    private var statusBarWindowRef: WeakReference<Any>? = null

    /** Last generation for which a refresh was requested, to coalesce duplicates. */
    private val lastRefreshGeneration = AtomicLong(-1L)

    /** Resolved `ClientWindowFrames` class and fields; null if the ABI could not be resolved. */
    private var clientWindowFramesClass: Class<*>? = null
    private var clientWindowFramesFrameField: Field? = null
    private var clientWindowFramesDisplayFrameField: Field? = null
    private var clientWindowFramesParentFrameField: Field? = null

    /** A14 DisplayPolicy.DecorInsets.Info fields used on the cold configuration path. */
    private var decorInfoNonDecorInsetsField: Field? = null
    private var decorInfoNonDecorFrameField: Field? = null

    /** Cached `WindowState.getFrame()`/`getDisplayMetrics()`/`getDisplayId()` methods. */
    private var windowStateGetFrameMethod: Method? = null
    private var windowStateGetDisplayMetricsMethod: Method? = null
    private var windowStateGetDisplayIdMethod: Method? = null

    /** Bounded set of critical diagnostic keys whose first hit has already been logged. */
    private val loggedCritical = LinkedHashSet<String>()

    /** Bounded set of aggregated rejection keys whose first hit has already been logged. */
    private val loggedRejection = LinkedHashSet<String>()

    /** Bounded log keys for [STATUS_BAR_HEIGHT_LIVE_TAG] lifecycle events. */
    private val loggedLiveKeys = LinkedHashSet<String>()

    @JvmStatic
    internal fun resetDiagnosticsForTest() {
        synchronized(loggedCritical) { loggedCritical.clear() }
        synchronized(loggedRejection) { loggedRejection.clear() }
        synchronized(loggedLiveKeys) { loggedLiveKeys.clear() }
    }

    @JvmStatic
    internal fun resetForTest() {
        resetDiagnosticsForTest()
        statusBarWindowRef = null
        lastRefreshGeneration.set(-1L)
    }

    @JvmStatic
    internal fun criticalKeyCountForTest(): Int {
        synchronized(loggedCritical) { return loggedCritical.size }
    }

    @JvmStatic
    internal fun rejectionKeyCountForTest(): Int {
        synchronized(loggedRejection) { return loggedRejection.size }
    }

    @JvmStatic
    internal fun liveKeyCountForTest(): Int {
        synchronized(loggedLiveKeys) { return loggedLiveKeys.size }
    }

    @JvmStatic
    fun StatusBarInsetsHeightHook(lpparam: SystemServerStartingParam) {
        if (hookInstalled) return

        val classLoader = lpparam.classLoader ?: run {
            logInstall("no classLoader")
            return
        }

        val insetsSourceClass = XposedHelpers.findClassIfExists(INSETS_SOURCE_CLASS, classLoader)
        if (insetsSourceClass == null) {
            logInstall("InsetsSource class not found")
            return
        }

        val setFrameMethods = try {
            insetsSourceClass.getDeclaredMethods().filter { it.name == SET_FRAME_METHOD }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            logInstall("setFrame methods not accessible: ${t.javaClass.simpleName}")
            return
        }

        val setFrameOneArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Rect::class.java)) }
        val setFrameFourArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.java, Int::class.java, Int::class.java)) }

        if (!setFrameOneArg && !setFrameFourArg) {
            logInstall("setFrame(Rect) and setFrame(int,int,int,int) both missing")
            return
        }

        val sourceAbi = resolveInsetsSourceAbi(insetsSourceClass, classLoader)
        val resolvedTypeInfo = selectTypeEncoding(sourceAbi)
        if (resolvedTypeInfo.encoding == InsetsTypeEncoding.UNSUPPORTED) {
            logInstall("status bar Insets type encoding not resolvable")
            return
        }
        typeInfo = resolvedTypeInfo

        val hasGetId = hasMethod(insetsSourceClass, GET_ID_METHOD)
        val hasGetFrame = hasMethod(insetsSourceClass, GET_FRAME_METHOD)

        val resources = try {
            Resources.getSystem()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            logInstall("Resources.getSystem() failed: ${t.javaClass.simpleName}")
            return
        }

        StatusBarHeightConfig.configure(MainModule.mPrefs, resources)

        val state = StatusBarHeightConfig.currentState()
        logInstall(
            "enabled=${state.enabled} " +
                "rawDp=${state.rawPreferenceDp} " +
                "resolvedDp=${state.configuredDp} " +
                "configuredPx=${state.configuredPx} " +
                "density=${state.density} " +
                "densityDpi=${state.densityDpi} " +
                "encoding=${resolvedTypeInfo.encoding} " +
                "statusType=${resolvedTypeInfo.statusBarType} " +
                "navType=${resolvedTypeInfo.navigationType} " +
                "cutoutType=${resolvedTypeInfo.displayCutoutType} " +
                "setFrame1=$setFrameOneArg setFrame4=$setFrameFourArg " +
                "getId=$hasGetId getFrame=$hasGetFrame " +
                "abi=$sourceAbi"
        )

        val callback = SetFrameCallback(resolvedTypeInfo, hasGetId, hasGetFrame)
        ModuleHelper.hookAllMethods(insetsSourceClass, SET_FRAME_METHOD, callback)

        resolveWindowManagerAbi(classLoader)
        installDisplayPolicyHook(classLoader)
        installDecorInsetsInfoHook(classLoader)
        installWindowStateHook(classLoader)

        ModuleHelper.observePreferenceChange(statusBarHeightObserver, StatusBarHeightConfig)

        hookInstalled = true
    }

    private fun logInstall(message: String) {
        XposedHelpers.log("[StatusBarInsets] install $message")
    }

    /** Public `WindowInsets.Type.statusBars()` bit. */
    private const val STATUS_BARS_TYPE = 1

    /** `WindowManager.LayoutParams.TYPE_STATUS_BAR` = 2000. */
    private const val TYPE_STATUS_BAR = 2000

    /** The display-aware preference observer installed in system_server. */
    private val statusBarHeightObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key != null && key != StatusBarHeightConfig.PREF_KEY) return
            try {
                val oldGen = StatusBarHeightConfig.generation.get()
                val change = StatusBarHeightConfig.reconfigure(MainModule.mPrefs)
                val newGen = StatusBarHeightConfig.generation.get()

                logLive(
                    "preference-change " +
                        "oldDp=${change.previous.configuredDp} " +
                        "newDp=${change.current.configuredDp} " +
                        "oldPx=${change.previous.configuredPx} " +
                        "newPx=${change.current.configuredPx} " +
                        "oldGen=$oldGen " +
                        "newGen=$newGen " +
                        "densityDpi=${change.current.densityDpi}",
                    "preference-change"
                )

                if (change.changed) {
                    requestStatusBarTraversal()
                }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("$STATUS_BAR_HEIGHT_LIVE_TAG preference-change failed: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun logLive(message: String, key: String) {
        val gen = StatusBarHeightConfig.generation.get()
        val fullKey = "$key:$gen"
        synchronized(loggedLiveKeys) {
            if (loggedLiveKeys.size >= MAX_CRITICAL_KEYS) return
            if (!loggedLiveKeys.add(fullKey)) return
        }
        XposedHelpers.log("$STATUS_BAR_HEIGHT_LIVE_TAG $message gen=$gen")
    }

    private fun installDisplayPolicyHook(classLoader: ClassLoader) {
        val displayPolicyClass = XposedHelpers.findClassIfExists(DISPLAY_POLICY_CLASS, classLoader)
        if (displayPolicyClass == null) {
            logInstall("DisplayPolicy class not found")
            return
        }

        ModuleHelper.hookAllMethods(displayPolicyClass, LAYOUT_WINDOW_LW_METHOD, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onLayoutWindowLw(chain)
            }
        })
    }

    private fun installWindowStateHook(classLoader: ClassLoader) {
        val windowStateClass = XposedHelpers.findClassIfExists(WINDOW_STATE_CLASS, classLoader)
        if (windowStateClass == null) {
            logInstall("WindowState class not found")
            return
        }

        resolveClientWindowFramesClass(windowStateClass)

        ModuleHelper.hookAllMethods(windowStateClass, SET_FRAMES_METHOD, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onSetFrames(chain)
            }
        })
    }

    /**
     * Android 14 intentionally excludes status bars from DisplayPolicy's DECOR_TYPES and
     * adds them only to mConfigInsets. On a cutout device that leaves app bounds at the
     * cutout safe top (104 px on fuxi) even after the statusBars source grows (129 px), so
     * third-party apps look as if only the icons moved. Expand the cached non-decor top on
     * the same cold configuration calculation; no Activity/View hook or per-frame work is
     * needed.
     */
    private fun installDecorInsetsInfoHook(classLoader: ClassLoader) {
        val infoClass = XposedHelpers.findClassIfExists(DECOR_INSETS_INFO_CLASS, classLoader)
        if (infoClass == null) {
            logInstall("DisplayPolicy.DecorInsets.Info class not found")
            return
        }

        val updateMethod = try {
            infoClass.declaredMethods.singleOrNull { method ->
                val parameterTypes = method.parameterTypes
                method.name == DECOR_INSETS_UPDATE_METHOD &&
                    parameterTypes.size == 4 &&
                    parameterTypes[0].name == "com.android.server.wm.DisplayContent" &&
                    parameterTypes[1] == Int::class.javaPrimitiveType &&
                    parameterTypes[2] == Int::class.javaPrimitiveType &&
                    parameterTypes[3] == Int::class.javaPrimitiveType
            }?.also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
        if (updateMethod == null) {
            logInstall("DisplayPolicy.DecorInsets.Info.update ABI unavailable")
            return
        }

        try {
            decorInfoNonDecorInsetsField = infoClass.getDeclaredField("mNonDecorInsets").also {
                it.isAccessible = true
            }
            decorInfoNonDecorFrameField = infoClass.getDeclaredField("mNonDecorFrame").also {
                it.isAccessible = true
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            decorInfoNonDecorInsetsField = null
            decorInfoNonDecorFrameField = null
            logInstall("DisplayPolicy.DecorInsets.Info fields unavailable: ${t.javaClass.simpleName}")
            return
        }

        ModuleHelper.hookMethod(updateMethod, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onDecorInsetsInfoUpdate(chain)
            }
        })
    }

    private fun resolveWindowManagerAbi(classLoader: ClassLoader) {
        val windowStateClass = XposedHelpers.findClassIfExists(WINDOW_STATE_CLASS, classLoader) ?: return

        windowStateGetFrameMethod = try {
            windowStateClass.getMethod("getFrame")?.also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
        windowStateGetDisplayMetricsMethod = try {
            windowStateClass.getMethod("getDisplayMetrics")?.also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
        windowStateGetDisplayIdMethod = try {
            windowStateClass.getMethod("getDisplayId")?.also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun resolveClientWindowFramesClass(windowStateClass: Class<*>) {
        val setFramesMethods = try {
            windowStateClass.getDeclaredMethods().filter { it.name == SET_FRAMES_METHOD }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            emptyList<Method>()
        }

        val matched = setFramesMethods.firstOrNull { method ->
            method.parameterTypes.isNotEmpty() && method.parameterTypes[0].simpleName == "ClientWindowFrames"
        } ?: return

        val clazz: Class<*> = matched.parameterTypes[0]
        clientWindowFramesClass = clazz
        try {
            clientWindowFramesFrameField = clazz.getField("frame").also { it.isAccessible = true }
            clientWindowFramesDisplayFrameField = clazz.getField("displayFrame").also { it.isAccessible = true }
            clientWindowFramesParentFrameField = clazz.getField("parentFrame").also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
    }

    @JvmStatic
    internal fun onLayoutWindowLw(chain: XposedInterface.Chain): Any? {
        val win = chain.getArg(0) ?: return chain.proceed()
        if (win.javaClass.name != WINDOW_STATE_CLASS) return chain.proceed()
        if (!isStatusBarWindow(win)) return chain.proceed()

        statusBarWindowRef = WeakReference(win)

        val state = StatusBarHeightConfig.currentState()
        if (!state.enabled) {
            restoreStatusBarWindowHeight(win)
            return chain.proceed()
        }

        val metrics = tryGetWindowDisplayMetrics(win) ?: return chain.proceed()
        val displayId = getDisplayId(win)

        val configuredPx = if (displayId == 0) {
            StatusBarHeightConfig.recomputePx(metrics)
            StatusBarHeightConfig.configuredPx
        } else {
            StatusBarHeightConfig.configuredPxFor(state.configuredDp, metrics)
        }

        if (configuredPx <= 0) return chain.proceed()

        val densityForLog = if (displayId == 0) StatusBarHeightConfig.density else metrics.density
        val densityDpiForLog = if (displayId == 0) StatusBarHeightConfig.densityDpi else metrics.densityDpi

        logLive(
            "layout displayId=$displayId " +
                "rawDp=${state.rawPreferenceDp} " +
                "resolvedDp=${state.configuredDp} " +
                "density=$densityForLog " +
                "densityDpi=$densityDpiForLog " +
                "configuredPx=$configuredPx",
            "layout:$displayId"
        )

        applyStatusBarWindowHeight(win, configuredPx)

        val result = chain.proceed()

        val frame = readWindowFrame(win)
        if (frame != null) {
            logLive(
                "window-frame displayId=$displayId " +
                    "left=${frame.left} " +
                    "top=${frame.top} " +
                    "right=${frame.right} " +
                    "bottom=${frame.bottom}",
                "frame:$displayId"
            )
        }

        return result
    }

    @JvmStatic
    internal fun onSetFrames(chain: XposedInterface.Chain): Any? {
        if (!StatusBarHeightConfig.enabled) return chain.proceed()

        val win = chain.thisObject ?: return chain.proceed()
        if (!isStatusBarWindow(win)) return chain.proceed()

        val clientFrames = chain.getArg(0) ?: return chain.proceed()
        if (clientFramesClassMismatch(clientFrames)) return chain.proceed()

        val metrics = tryGetWindowDisplayMetrics(win) ?: return chain.proceed()
        val displayId = getDisplayId(win)

        val configuredPx = if (displayId == 0) {
            StatusBarHeightConfig.configuredPx
        } else {
            StatusBarHeightConfig.configuredPxFor(StatusBarHeightConfig.configuredDp, metrics)
        }
        if (configuredPx <= 0) return chain.proceed()

        val frame = readClientWindowFrame(clientFrames) ?: return chain.proceed()

        val oldLeft = frame.left
        val oldTop = frame.top
        val oldRight = frame.right
        val oldBottom = frame.bottom
        val newBottom = oldTop + configuredPx

        if (newBottom != oldBottom) {
            frame.bottom = newBottom
            logLive(
                "frame displayId=$displayId " +
                    "left=$oldLeft top=$oldTop right=$oldRight " +
                    "oldBottom=$oldBottom newBottom=$newBottom " +
                    "configuredPx=$configuredPx",
                "frame:$displayId"
            )
        }

        return chain.proceed()
    }

    @JvmStatic
    internal fun onDecorInsetsInfoUpdate(chain: XposedInterface.Chain): Any? {
        // The original calculation must run exactly once and its exception must propagate.
        val result = chain.proceed()
        if (!StatusBarHeightConfig.enabled) return result

        try {
            val args = chain.args
            if (args.size != 4 || args[0] == null || args[1] !is Int) return result
            val info = chain.thisObject ?: return result
            val nonDecorInsets = decorInfoNonDecorInsetsField?.get(info) as? Rect ?: return result
            val nonDecorFrame = decorInfoNonDecorFrameField?.get(info) as? Rect ?: return result
            val originalInsetTop = nonDecorInsets.top
            val originalFrameTop = nonDecorFrame.top
            val configuredPx = configuredPxForDecorInfo(args[0])
            val newInsetTop = computeNonDecorTop(originalInsetTop, configuredPx, true)
            if (newInsetTop == originalInsetTop) return result

            nonDecorInsets.top = newInsetTop
            nonDecorFrame.top = computeNonDecorFrameTop(
                originalFrameTop,
                originalInsetTop,
                configuredPx,
                true,
            )

            val rotation = args[1] as Int
            logLive(
                "decor rotation=$rotation " +
                    "oldTop=$originalInsetTop newTop=$newInsetTop " +
                    "oldFrameTop=$originalFrameTop newFrameTop=${nonDecorFrame.top} " +
                    "configuredPx=$configuredPx",
                "decor:$rotation",
            )
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("$STATUS_BAR_HEIGHT_LIVE_TAG decor update failed: ${t.javaClass.simpleName}")
        }

        return result
    }

    private fun configuredPxForDecorInfo(displayContent: Any?): Int {
        val state = StatusBarHeightConfig.currentState()
        if (displayContent == null) return state.configuredPx
        return try {
            val metrics = XposedHelpers.callMethod(displayContent, "getDisplayMetrics") as? DisplayMetrics
            if (metrics == null) state.configuredPx
            else StatusBarHeightConfig.configuredPxFor(state.configuredDp, metrics)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            state.configuredPx
        }
    }

    private fun clientFramesClassMismatch(clientFrames: Any): Boolean {
        val resolvedClass = clientWindowFramesClass
        return if (resolvedClass != null) {
            !resolvedClass.isInstance(clientFrames)
        } else {
            clientFrames.javaClass.simpleName != "ClientWindowFrames"
        }
    }

    private fun readClientWindowFrame(clientFrames: Any): Rect? {
        return try {
            clientWindowFramesFrameField?.get(clientFrames) as? Rect
                ?: XposedHelpers.getObjectField(clientFrames, "frame") as? Rect
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    @JvmStatic
    internal fun isStatusBarWindow(win: Any): Boolean {
        return try {
            val attrs = XposedHelpers.getObjectField(win, "mAttrs") ?: return false
            val type = XposedHelpers.getIntField(attrs, "type")
            if (type == TYPE_STATUS_BAR) return true

            val packageName = XposedHelpers.getObjectField(attrs, "packageName") as? String
            if (packageName?.contains("com.android.systemui") == true) {
                win.toString().contains(STATUS_BAR_WINDOW_TAG)
            } else {
                false
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    @JvmStatic
    internal fun tryGetWindowDisplayMetrics(win: Any): DisplayMetrics? {
        return try {
            val direct = windowStateGetDisplayMetricsMethod?.invoke(win) as? DisplayMetrics
            if (direct != null) return direct

            val displayContent = XposedHelpers.getObjectField(win, "mDisplayContent") ?: return null
            XposedHelpers.callMethod(displayContent, "getDisplayMetrics") as? DisplayMetrics
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    @JvmStatic
    internal fun getDisplayId(win: Any): Int {
        return try {
            windowStateGetDisplayIdMethod?.invoke(win) as? Int
                ?: XposedHelpers.callMethod(win, "getDisplayId") as? Int
                ?: -1
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            -1
        }
    }

    @JvmStatic
    internal fun readWindowFrame(win: Any): Rect? {
        return try {
            windowStateGetFrameMethod?.invoke(win) as? Rect
                ?: XposedHelpers.callMethod(win, "getFrame") as? Rect
                ?: XposedHelpers.getObjectField(win, "mWindowFrames")?.let { frames ->
                    XposedHelpers.getObjectField(frames, "mFrame") as? Rect
                }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    @JvmStatic
    internal fun applyStatusBarWindowHeight(win: Any, configuredPx: Int) {
        if (configuredPx <= 0) return

        try {
            val attrs = XposedHelpers.getObjectField(win, "mAttrs") ?: return
            val currentHeight = XposedHelpers.getIntField(attrs, "height")
            if (XposedHelpers.getAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY) == null) {
                XposedHelpers.setAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY, currentHeight)
            }
            if (currentHeight != configuredPx) {
                XposedHelpers.setIntField(attrs, "height", configuredPx)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
    }

    @JvmStatic
    internal fun restoreStatusBarWindowHeight(win: Any) {
        try {
            val attrs = XposedHelpers.getObjectField(win, "mAttrs") ?: return
            val originalHeight = XposedHelpers.getAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY) as? Int ?: return
            val currentHeight = XposedHelpers.getIntField(attrs, "height")
            if (currentHeight != originalHeight) {
                XposedHelpers.setIntField(attrs, "height", originalHeight)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
    }

    @JvmStatic
    internal fun requestStatusBarTraversal() {
        val win = statusBarWindowRef?.get() ?: return
        try {
            invalidateDecorInsets(win)
            val wmService = XposedHelpers.getObjectField(win, "mWmService") ?: return
            val windowPlacer = XposedHelpers.getObjectField(wmService, "mWindowPlacerLocked") ?: return

            val newGen = StatusBarHeightConfig.generation.get()
            val lastGen = lastRefreshGeneration.getAndSet(newGen)
            if (lastGen == newGen) {
                logLive("refresh coalesced gen=$newGen", "refresh")
                return
            }

            try {
                XposedHelpers.callMethod(windowPlacer, "requestTraversal")
                logLive("refresh requestTraversal gen=$newGen", "refresh")
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                logLive("refresh requestTraversal-unavailable gen=$newGen", "refresh")
                XposedHelpers.log("$STATUS_BAR_HEIGHT_LIVE_TAG requestTraversal unavailable, waiting for natural layout: ${t.javaClass.simpleName}")
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("$STATUS_BAR_HEIGHT_LIVE_TAG refresh failed: ${t.javaClass.simpleName}")
        }
    }

    /** Invalidates the four rotation caches before a live height change is traversed. */
    private fun invalidateDecorInsets(win: Any) {
        try {
            val displayContent = XposedHelpers.getObjectField(win, "mDisplayContent") ?: return
            val displayPolicy = XposedHelpers.callMethod(displayContent, "getDisplayPolicy") ?: return
            val decorInsets = XposedHelpers.getObjectField(displayPolicy, "mDecorInsets") ?: return
            XposedHelpers.callMethod(decorInsets, "invalidate")
            logLive("decor invalidate", "decor-invalidate")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("$STATUS_BAR_HEIGHT_LIVE_TAG decor invalidate failed: ${t.javaClass.simpleName}")
        }
    }

    /**
     * Which type encoding the ROM uses for `InsetsSource.getType()`.
     *
     * - `MODERN_PUBLIC`: `getType()` returns public `WindowInsets.Type` masks.
     * - `LEGACY_INTERNAL`: `getType()` returns `InsetsState.ITYPE_*` indices.
     * - `UNSUPPORTED`: neither could be resolved.
     */
    internal enum class InsetsTypeEncoding {
        MODERN_PUBLIC,
        LEGACY_INTERNAL,
        UNSUPPORTED,
    }

    internal data class InsetsTypeInfo(
        val encoding: InsetsTypeEncoding,
        val statusBarType: Int,
        val navigationType: Int,
        val displayCutoutType: Int,
    )

    /**
     * Cold-path description of the `InsetsSource` ABI. All fields are filled by
     * reflection on the install ClassLoader; `selectTypeEncoding` uses only this
     * snapshot to freeze the unique type encoding.
     */
    internal data class InsetsSourceAbi(
        val hasOneIntConstructor: Boolean,
        val hasIdTypeConstructor: Boolean,
        val hasGetId: Boolean,
        val hasGetType: Boolean,
        val legacyStatusType: Int?,
        val legacyNavigationType: Int?,
        val publicStatusType: Int?,
        val publicNavigationType: Int?,
        val publicDisplayCutoutType: Int?,
    )

    /**
     * Select the unique type encoding from the observed ABI.
     *
     * Rules:
     * - MODERN_PUBLIC: modern `(int id, int type)` constructor, `getId()`,
     *   `getType()` and `WindowInsets.Type.statusBars()` are all resolvable.
     * - LEGACY_INTERNAL: legacy `(int type)` constructor, no modern constructor,
     *   `getType()` and both `ITYPE_STATUS_BAR` / `ITYPE_NAVIGATION_BAR` resolvable.
     * - UNSUPPORTED: anything else, including ambiguous ABI where both constructors
     *   exist but the modern contract is not fully satisfied.
     */
    private fun Int?.isResolvedType(): Boolean = this != null && this >= 0

    @JvmStatic
    internal fun selectTypeEncoding(abi: InsetsSourceAbi): InsetsTypeInfo {
        val isModern = abi.hasIdTypeConstructor &&
            abi.hasGetId &&
            abi.hasGetType &&
            abi.publicStatusType.isResolvedType()

        val isLegacy = abi.hasOneIntConstructor &&
            !abi.hasIdTypeConstructor &&
            abi.hasGetType &&
            abi.legacyStatusType.isResolvedType() &&
            abi.legacyNavigationType.isResolvedType()

        return when {
            isModern -> InsetsTypeInfo(
                InsetsTypeEncoding.MODERN_PUBLIC,
                abi.publicStatusType!!,
                abi.publicNavigationType.takeIf { it.isResolvedType() } ?: -1,
                abi.publicDisplayCutoutType.takeIf { it.isResolvedType() } ?: -1,
            )
            isLegacy -> InsetsTypeInfo(
                InsetsTypeEncoding.LEGACY_INTERNAL,
                abi.legacyStatusType!!,
                abi.legacyNavigationType!!,
                -1,
            )
            else -> InsetsTypeInfo(
                InsetsTypeEncoding.UNSUPPORTED,
                -1,
                -1,
                -1,
            )
        }
    }

    private fun resolveInsetsSourceAbi(insetsSourceClass: Class<*>, classLoader: ClassLoader?): InsetsSourceAbi {
        val constructors = try {
            insetsSourceClass.declaredConstructors
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            emptyArray()
        }

        val hasOneIntConstructor = constructors.any { it.parameterTypes.contentEquals(arrayOf(Int::class.java)) }
        val hasIdTypeConstructor = constructors.any { it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.java)) }

        val hasGetId = hasMethod(insetsSourceClass, GET_ID_METHOD)
        val hasGetType = hasMethod(insetsSourceClass, GET_TYPE_METHOD)

        val public = resolvePublicTypes()
        val legacy = resolveLegacyTypes(classLoader)

        return InsetsSourceAbi(
            hasOneIntConstructor = hasOneIntConstructor,
            hasIdTypeConstructor = hasIdTypeConstructor,
            hasGetId = hasGetId,
            hasGetType = hasGetType,
            legacyStatusType = legacy.statusBarType,
            legacyNavigationType = legacy.navigationType,
            publicStatusType = public.statusBarType,
            publicNavigationType = public.navigationType,
            publicDisplayCutoutType = public.displayCutoutType,
        )
    }

    private fun resolvePublicTypes(): RawTypeInfo {
        val status = safePublicType { WindowInsets.Type.statusBars() }
        val nav = safePublicType { WindowInsets.Type.navigationBars() }
        val cutout = safePublicType { WindowInsets.Type.displayCutout() }
        return RawTypeInfo(status, nav, cutout)
    }

    /**
     * Resolves a public `WindowInsets.Type` method. Returns `null` on failure so that
     * `-1` is never mistaken for a valid type mask.
     */
    private fun safePublicType(block: () -> Int): Int? {
        return try {
            normalizeResolvedType(block())
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    @JvmStatic
    internal fun normalizeResolvedType(value: Int): Int? = value.takeIf { it >= 0 }

    private fun resolveLegacyTypes(classLoader: ClassLoader?): RawTypeInfo {
        val insetsStateClass = XposedHelpers.findClassIfExists(INSETS_STATE_CLASS, classLoader)
            ?: return RawTypeInfo(null, null, null)
        return RawTypeInfo(
            getStaticInt(insetsStateClass, "ITYPE_STATUS_BAR"),
            getStaticInt(insetsStateClass, "ITYPE_NAVIGATION_BAR"),
            getStaticInt(insetsStateClass, "ITYPE_DISPLAY_CUTOUT"),
        )
    }

    /**
     * Resolves a static int field. Returns `null` when the field is missing or negative,
     * so that `-1` is never treated as a resolved legacy type.
     */
    private fun getStaticInt(clazz: Class<*>, fieldName: String): Int? {
        return try {
            normalizeResolvedType(XposedHelpers.getStaticIntField(clazz, fieldName))
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun hasMethod(clazz: Class<*>, methodName: String): Boolean {
        return try {
            clazz.getDeclaredMethods().any { it.name == methodName }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    /** Small helper used for both public and legacy type resolution. */
    internal data class RawTypeInfo(
        val statusBarType: Int?,
        val navigationType: Int?,
        val displayCutoutType: Int?,
    )

    internal fun isStatusBarType(type: Int, typeInfo: InsetsTypeInfo): Boolean {
        return type == typeInfo.statusBarType
    }

    /**
     * Pure geometry logic: given the original frame and the configured height in pixels,
     * return the new bottom.
     *
     * The configured value is a height, so the new bottom is `originalTop + configuredPx`.
     * This allows the user to shrink the status bar below the original bottom, which is
     * required on devices (e.g. fuxi) where the original bottom already includes the
     * display cutout safe top and is therefore too large a floor.
     *
     * A non-zero `originalTop` is respected (e.g. secondary displays or rotation).
     */
    @JvmStatic
    fun computeStatusBarFrameBottom(originalTop: Int, originalBottom: Int, configuredPx: Int, enabled: Boolean): Int {
        if (!enabled || configuredPx <= 0) return originalBottom
        return originalTop + configuredPx
    }

    /**
     * Convenience overload that reads [StatusBarHeightConfig.enabled] and [StatusBarHeightConfig.configuredPx].
     */
    @JvmStatic
    fun computeStatusBarFrameBottom(originalTop: Int, originalBottom: Int): Int {
        return computeStatusBarFrameBottom(originalTop, originalBottom, StatusBarHeightConfig.configuredPx, StatusBarHeightConfig.enabled)
    }

    /**
     * Keeps the framework/cutout safe inset as a floor and expands it only when the chosen
     * status bar is taller. Shrinking below a physical cutout would place app content under
     * the camera even though the status bar source itself is allowed to shrink.
     */
    @JvmStatic
    fun computeNonDecorTop(originalTop: Int, configuredPx: Int, enabled: Boolean): Int {
        if (!enabled || configuredPx <= 0) return originalTop
        return maxOf(originalTop, configuredPx)
    }

    /** Applies the same top-inset delta to the already-computed non-decor frame. */
    @JvmStatic
    fun computeNonDecorFrameTop(
        originalFrameTop: Int,
        originalInsetTop: Int,
        configuredPx: Int,
        enabled: Boolean,
    ): Int {
        val newInsetTop = computeNonDecorTop(originalInsetTop, configuredPx, enabled)
        return originalFrameTop + (newInsetTop - originalInsetTop)
    }

    private fun copyRect(source: Rect): Rect {
        return Rect().apply {
            left = source.left
            top = source.top
            right = source.right
            bottom = source.bottom
        }
    }

    /**
     * Decision produced by the framework-light prelude. The actual `chain.proceed()` call
     * happens exactly once, after this decision has been computed and only outside any
     * catch block that could retry.
     */
    internal sealed interface SetFrameDecision {
        val reason: String
    }

    internal data class ProceedOriginal(override val reason: String) : SetFrameDecision
    internal data class ProceedWithArgs(val args: Array<Any>, override val reason: String) : SetFrameDecision

    /**
     * Compute the decision for a single `InsetsSource.setFrame` interception.
     *
     * This function must never call [XposedInterface.Chain.proceed]. All reflection and
     * logging exceptions are either fatal (rethrown) or non-fatal (produce a safe
     * `ProceedOriginal` decision). The returned decision is then executed exactly once
     * by [SetFrameCallback.intercept].
     */
    internal fun makeSetFrameDecision(
        chain: XposedInterface.Chain,
        typeInfo: InsetsTypeInfo,
        configuredPx: Int,
        enabled: Boolean,
    ): SetFrameDecision {
        val source = chain.thisObject
            ?: return ProceedOriginal("source-null")

        val type = try {
            XposedHelpers.callMethod(source, GET_TYPE_METHOD) as? Int
                ?: return ProceedOriginal("preprocessing-reflection-failed")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            return ProceedOriginal("preprocessing-reflection-failed")
        }

        if (!isStatusBarType(type, typeInfo)) {
            return ProceedOriginal("non-status-type")
        }

        if (!enabled || configuredPx <= 0) {
            return ProceedOriginal("disabled")
        }

        return try {
            val firstArg = chain.getArg(0)
            when {
                firstArg is Rect -> {
                    val newBottom = computeStatusBarFrameBottom(firstArg.top, firstArg.bottom, configuredPx, enabled)
                    val changed = newBottom != firstArg.bottom
                    if (changed) {
                        val adjusted = copyRect(firstArg)
                        adjusted.bottom = newBottom
                        ProceedWithArgs(arrayOf<Any>(adjusted), "status-source-changed")
                    } else {
                        ProceedOriginal("status-source-no-change")
                    }
                }
                firstArg is Int -> {
                    val top = chain.getArg(1) as? Int
                    val right = chain.getArg(2) as? Int
                    val bottom = chain.getArg(3) as? Int
                    if (top == null || right == null || bottom == null) {
                        return ProceedOriginal("invalid-argument-shape")
                    }
                    val newBottom = computeStatusBarFrameBottom(top, bottom, configuredPx, enabled)
                    val changed = newBottom != bottom
                    if (changed) {
                        ProceedWithArgs(arrayOf<Any>(firstArg, top, right, newBottom), "status-source-changed")
                    } else {
                        ProceedOriginal("status-source-no-change")
                    }
                }
                else -> ProceedOriginal("invalid-argument-shape")
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            ProceedOriginal("preprocessing-reflection-failed")
        }
    }

    internal class SetFrameCallback(
        private val typeInfo: InsetsTypeInfo,
        private val hasGetId: Boolean,
        private val hasGetFrame: Boolean,
    ) : MethodHook() {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val decision = makeSetFrameDecision(
                chain,
                typeInfo,
                StatusBarHeightConfig.configuredPx,
                StatusBarHeightConfig.enabled,
            )

            // Log is best-effort and must never trigger a second proceed or swallow
            // the real decision. It uses a bounded set to avoid unbounded growth.
            try {
                maybeLogFirstHit(chain, decision, typeInfo)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }

            // Exactly one proceed. Exceptions from the original method propagate.
            return when (decision) {
                is ProceedOriginal -> chain.proceed()
                is ProceedWithArgs -> chain.proceed(decision.args)
            }
        }

        private fun maybeLogFirstHit(
            chain: XposedInterface.Chain,
            decision: SetFrameDecision,
            typeInfo: InsetsTypeInfo,
        ) {
            val source = chain.thisObject ?: return

            val type = try {
                XposedHelpers.callMethod(source, GET_TYPE_METHOD) as? Int ?: return
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return
            }

            val overload = when (chain.getArg(0)) {
                is Rect -> "setFrame(Rect)"
                is Int -> "setFrame(int,int,int,int)"
                else -> "unknown"
            }

            val (oldFrame, newBottom) = readIncomingFrame(chain, decision)

            val sourceId = if (hasGetId) {
                try {
                    XposedHelpers.callMethod(source, GET_ID_METHOD) as? Int
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    null
                }
            } else null

            val reason = decision.reason
            val isCritical = reason in CRITICAL_REASONS
            val generation = StatusBarHeightConfig.generation.get()

            if (isCritical) {
                val key = criticalKey(typeInfo.encoding, sourceId, type, overload, reason, generation)
                synchronized(loggedCritical) {
                    if (loggedCritical.size >= MAX_CRITICAL_KEYS) return
                    if (!loggedCritical.add(key)) return
                }
            } else {
                val key = rejectionKey(typeInfo.encoding, type, overload, reason, generation)
                synchronized(loggedRejection) {
                    if (loggedRejection.size >= MAX_REJECTION_KEYS) return
                    if (!loggedRejection.add(key)) return
                }
            }

            XposedHelpers.log(
                "[StatusBarInsets] insets " +
                    "encoding=${typeInfo.encoding} " +
                    "sourceId=${sourceId ?: "n/a"} " +
                    "type=$type " +
                    "overload=$overload " +
                    "oldFrame=${oldFrame?.toShortString()} " +
                    "newBottom=$newBottom " +
                    "rawDp=${StatusBarHeightConfig.rawPreferenceDp} " +
                    "resolvedDp=${StatusBarHeightConfig.configuredDp} " +
                    "configuredPx=${StatusBarHeightConfig.configuredPx} " +
                    "changed=${decision is ProceedWithArgs} " +
                    "reason=$reason " +
                    "gen=$generation"
            )
        }

        private fun readIncomingFrame(
            chain: XposedInterface.Chain,
            decision: SetFrameDecision,
        ): Pair<Rect?, Int> {
            val firstArg = chain.getArg(0)
            return when (firstArg) {
                is Rect -> {
                    val newBottom = if (decision is ProceedWithArgs) {
                        val adjusted = decision.args[0]
                        if (adjusted is Rect) adjusted.bottom else firstArg.bottom
                    } else {
                        firstArg.bottom
                    }
                    copyRect(firstArg) to newBottom
                }
                is Int -> {
                    val top = chain.getArg(1) as? Int ?: 0
                    val right = chain.getArg(2) as? Int ?: 0
                    val bottom = chain.getArg(3) as? Int ?: 0
                    val newBottom = if (decision is ProceedWithArgs) {
                        val args = decision.args
                        if (args.size >= 4) args[3] as? Int ?: bottom else bottom
                    } else {
                        bottom
                    }
                    val rect = Rect()
                    rect.left = firstArg
                    rect.top = top
                    rect.right = right
                    rect.bottom = bottom
                    rect to (newBottom ?: bottom)
                }
                else -> null to -1
            }
        }
    }

    private val CRITICAL_REASONS = setOf(
        "status-source-changed",
        "status-source-no-change",
        "preprocessing-reflection-failed",
        "invalid-argument-shape",
    )

    private fun criticalKey(
        encoding: InsetsTypeEncoding,
        sourceId: Int?,
        type: Int,
        overload: String,
        reason: String,
        generation: Long,
    ): String {
        return "insets:${sourceId ?: "n/a"}:$generation"
    }

    /** Rejection keys deliberately omit sourceId so many non-status sources cannot starve critical logs. */
    private fun rejectionKey(
        encoding: InsetsTypeEncoding,
        type: Int,
        overload: String,
        reason: String,
        generation: Long,
    ): String {
        return "insets-reject:$type:$overload:$reason:$generation"
    }
}
