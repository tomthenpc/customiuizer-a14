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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

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
 * Hot path contract (`InsetsSource.setFrame`, `DisplayPolicy.layoutWindowLw`,
 * `WindowState.setFrames` are global system_server methods):
 * - The `enabled` flag is read before any reflection.
 * - `InsetsSource` type is read once, through a cached `mType` field when available.
 * - WindowStates that were already proven to be status bars are matched by identity.
 * - `dp -> px` is recomputed only when the display density actually changed.
 * - Nothing is allocated unless a frame really changes or a first-hit log is emitted.
 *
 * Diagnostics are generation-keyed and bounded: `preference-change:<gen>`,
 * `layout:<displayId>:<gen>`, `frame:<displayId>:<gen>`, `insets:<sourceId>:<gen>`,
 * `refresh:<gen>`. Generation stamps gate the whole diagnostic prelude so a suppressed log
 * costs one atomic read instead of a key string.
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

    /** Upper bound for the identity fast path and for the per-display diagnostic stamps. */
    internal const val MAX_TRACKED_DISPLAYS = 4

    /** Upper bound for the expensive packageName/toString status bar probe. */
    internal const val MAX_FALLBACK_PROBES = 4096

    private var typeInfo: InsetsTypeInfo? = null
    private var hookInstalled: Boolean = false

    /** Weak reference to the most recently laid-out status bar WindowState for refresh. */
    @Volatile
    private var statusBarWindowRef: WeakReference<Any>? = null

    /**
     * Identity fast path: WindowStates already proven to be status bars. The hot path only
     * compares references against this snapshot; the array is rebuilt on the rare discovery
     * path so readers never allocate and never lock.
     */
    @Volatile
    private var statusBarWindows: Array<WeakReference<Any>> = emptyArray()

    /** True once `mAttrs.type == TYPE_STATUS_BAR` has identified a status bar on this ROM. */
    @Volatile
    private var typeMatchObserved = false

    /** Remaining budget for the packageName/toString fallback probe. */
    private val fallbackProbeBudget = AtomicInteger(MAX_FALLBACK_PROBES)

    /** Resolved `WindowState.mAttrs` / `WindowManager.LayoutParams.type` for the WMS hot path. */
    @Volatile
    private var windowStateAttrsField: Field? = null

    @Volatile
    private var layoutParamsTypeField: Field? = null

    /** Resolved `com.android.server.wm.WindowState` class, used for an allocation-free type test. */
    @Volatile
    private var windowStateClass: Class<*>? = null

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

    /** True once [loggedRejection] is full, so the hot path can skip key construction entirely. */
    @Volatile
    private var rejectionLoggingExhausted = false

    /**
     * Per-display "already logged for this generation" stamps. They let the hot path skip the
     * whole diagnostic prelude (string keys, Rect copies, extra reflection) without a lock.
     */
    private val layoutLogStamps = AtomicLongArray(MAX_TRACKED_DISPLAYS)
    private val windowFrameLogStamps = AtomicLongArray(MAX_TRACKED_DISPLAYS)
    private val clientFrameLogStamps = AtomicLongArray(MAX_TRACKED_DISPLAYS)

    /**
     * Returns true at most once per (display, generation) pair. The stamp is `generation + 1`
     * so the zero-initialised array never suppresses generation 0.
     */
    private fun claimLiveLogStamp(stamps: AtomicLongArray, displayId: Int): Boolean {
        val slot = if (displayId in 0 until MAX_TRACKED_DISPLAYS) displayId else MAX_TRACKED_DISPLAYS - 1
        val stamp = StatusBarHeightConfig.generation.get() + 1
        return stamps.getAndSet(slot, stamp) != stamp
    }

    private fun clearLiveLogStamps(stamps: AtomicLongArray) {
        for (i in 0 until stamps.length()) stamps.set(i, 0L)
    }

    @JvmStatic
    internal fun resetDiagnosticsForTest() {
        synchronized(loggedCritical) { loggedCritical.clear() }
        synchronized(loggedRejection) {
            loggedRejection.clear()
            rejectionLoggingExhausted = false
        }
        synchronized(loggedLiveKeys) { loggedLiveKeys.clear() }
        clearLiveLogStamps(layoutLogStamps)
        clearLiveLogStamps(windowFrameLogStamps)
        clearLiveLogStamps(clientFrameLogStamps)
        statusSourceLogStamp.set(0L)
        reflectionFailureLogStamp.set(0L)
        invalidShapeLogStamp.set(0L)
    }

    @JvmStatic
    internal fun resetForTest() {
        resetDiagnosticsForTest()
        statusBarWindowRef = null
        statusBarWindows = emptyArray()
        typeMatchObserved = false
        fallbackProbeBudget.set(MAX_FALLBACK_PROBES)
        lastRefreshGeneration.set(-1L)
        windowStateClass = null
        windowStateAttrsField = null
        layoutParamsTypeField = null
        clientWindowFramesClass = null
        clientWindowFramesFrameField = null
        clientWindowFramesDisplayFrameField = null
        clientWindowFramesParentFrameField = null
        windowStateGetFrameMethod = null
        windowStateGetDisplayMetricsMethod = null
        windowStateGetDisplayIdMethod = null
        decorInfoNonDecorInsetsField = null
        decorInfoNonDecorFrameField = null
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

        val callback = SetFrameCallback(resolvedTypeInfo, hasGetId, resolveIntField(insetsSourceClass, "mType"))
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
        this.windowStateClass = windowStateClass
        windowStateAttrsField = resolveDeclaredField(windowStateClass, "mAttrs")

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
        if (!isWindowState(win)) return chain.proceed()

        if (!StatusBarHeightConfig.enabled) {
            // Disabled path must not perform status-bar discovery on unknown WindowStates.
            // Only a previously identified status bar gets its original height restored.
            if (isKnownStatusBarWindow(win)) {
                if (statusBarWindowRef?.get() !== win) statusBarWindowRef = WeakReference(win)
                restoreStatusBarWindowHeight(win)
            }
            return chain.proceed()
        }

        if (!isStatusBarWindow(win)) return chain.proceed()

        if (statusBarWindowRef?.get() !== win) statusBarWindowRef = WeakReference(win)

        val metrics = tryGetWindowDisplayMetrics(win) ?: return chain.proceed()
        val displayId = getDisplayId(win)

        val configuredPx = if (displayId == 0) {
            // Only a real density change may re-enter the synchronized recompute.
            if (metrics.densityDpi != StatusBarHeightConfig.densityDpi) {
                StatusBarHeightConfig.recomputePx(metrics)
            }
            StatusBarHeightConfig.configuredPx
        } else {
            StatusBarHeightConfig.configuredPxFor(StatusBarHeightConfig.configuredDp, metrics)
        }

        if (configuredPx <= 0) return chain.proceed()

        if (claimLiveLogStamp(layoutLogStamps, displayId)) {
            val densityForLog = if (displayId == 0) StatusBarHeightConfig.density else metrics.density
            val densityDpiForLog = if (displayId == 0) StatusBarHeightConfig.densityDpi else metrics.densityDpi
            logLive(
                "layout displayId=$displayId " +
                    "rawDp=${StatusBarHeightConfig.rawPreferenceDp} " +
                    "resolvedDp=${StatusBarHeightConfig.configuredDp} " +
                    "density=$densityForLog " +
                    "densityDpi=$densityDpiForLog " +
                    "configuredPx=$configuredPx",
                "layout:$displayId"
            )
        }

        applyStatusBarWindowHeight(win, configuredPx)

        val result = chain.proceed()

        if (claimLiveLogStamp(windowFrameLogStamps, displayId)) {
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
        }

        return result
    }

    @JvmStatic
    internal fun onSetFrames(chain: XposedInterface.Chain): Any? {
        if (!StatusBarHeightConfig.enabled) return chain.proceed()

        val win = chain.thisObject ?: return chain.proceed()
        // setFrames is a global WindowState method. Discovery already happened in
        // layoutWindowLw, so the hot path only accepts known status bars by identity.
        if (!isKnownStatusBarWindow(win)) return chain.proceed()

        val clientFrames = chain.getArg(0) ?: return chain.proceed()
        if (clientFramesClassMismatch(clientFrames)) return chain.proceed()

        val displayId = getDisplayId(win)

        val configuredPx = if (displayId == 0) {
            StatusBarHeightConfig.configuredPx
        } else {
            val metrics = tryGetWindowDisplayMetrics(win) ?: return chain.proceed()
            StatusBarHeightConfig.configuredPxFor(StatusBarHeightConfig.configuredDp, metrics)
        }
        if (configuredPx <= 0) return chain.proceed()

        val frame = readClientWindowFrame(clientFrames) ?: return chain.proceed()

        val oldTop = frame.top
        val oldBottom = frame.bottom
        val newBottom = oldTop + configuredPx

        if (newBottom != oldBottom) {
            frame.bottom = newBottom
            if (claimLiveLogStamp(clientFrameLogStamps, displayId)) {
                logLive(
                    "frame displayId=$displayId " +
                        "left=${frame.left} top=$oldTop right=${frame.right} " +
                        "oldBottom=$oldBottom newBottom=$newBottom " +
                        "configuredPx=$configuredPx",
                    "frame:$displayId"
                )
            }
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

    /** Allocation-free `WindowState` test; falls back to the class name before the ABI is resolved. */
    private fun isWindowState(win: Any): Boolean {
        val resolved = windowStateClass
        return if (resolved != null) resolved.isInstance(win) else win.javaClass.name == WINDOW_STATE_CLASS
    }

    private fun isKnownStatusBarWindow(win: Any): Boolean {
        val known = statusBarWindows
        for (i in known.indices) {
            if (known[i].get() === win) return true
        }
        return false
    }

    private fun rememberStatusBarWindow(win: Any) {
        synchronized(this) {
            if (isKnownStatusBarWindow(win)) return
            val known = statusBarWindows
            val live = ArrayList<WeakReference<Any>>(known.size + 1)
            for (ref in known) if (ref.get() != null) live.add(ref)
            while (live.size >= MAX_TRACKED_DISPLAYS) live.removeAt(0)
            live.add(WeakReference(win))
            statusBarWindows = live.toTypedArray()
        }
    }

    /**
     * Identity fast path first: a WindowState that has already been proven to be a status bar
     * costs one reference comparison. Only unknown windows pay the `mAttrs.type` read, and the
     * expensive `packageName`/`toString()` fallback runs only while the type check has never
     * succeeded on this ROM and only for a bounded number of probes.
     */
    @JvmStatic
    internal fun isStatusBarWindow(win: Any): Boolean {
        if (isKnownStatusBarWindow(win)) return true
        return try {
            val attrs = readWindowAttrs(win) ?: return false
            if (readAttrsType(attrs) == TYPE_STATUS_BAR) {
                typeMatchObserved = true
                rememberStatusBarWindow(win)
                return true
            }

            if (typeMatchObserved) return false
            if (fallbackProbeBudget.decrementAndGet() < 0) return false

            val packageName = XposedHelpers.getObjectField(attrs, "packageName") as? String
            if (packageName?.contains("com.android.systemui") != true) return false
            if (!win.toString().contains(STATUS_BAR_WINDOW_TAG)) return false

            rememberStatusBarWindow(win)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    /** Reads `WindowState.mAttrs` through a cached [Field]; the lookup self-installs on first use. */
    private fun readWindowAttrs(win: Any): Any? {
        var field = windowStateAttrsField
        if (field == null || !field.declaringClass.isInstance(win)) {
            field = resolveDeclaredField(win.javaClass, "mAttrs")
                ?: return XposedHelpers.getObjectField(win, "mAttrs")
            windowStateAttrsField = field
        }
        return field.get(win)
    }

    /** Reads `WindowManager.LayoutParams.type` without boxing; returns -1 when unavailable. */
    private fun readAttrsType(attrs: Any): Int {
        var field = layoutParamsTypeField
        if (field == null || !field.declaringClass.isInstance(attrs)) {
            field = resolveDeclaredField(attrs.javaClass, "type")
                ?: return XposedHelpers.getIntField(attrs, "type")
            layoutParamsTypeField = field
        }
        return field.getInt(attrs)
    }

    private fun resolveDeclaredField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).also { it.isAccessible = true }
            } catch (t: NoSuchFieldException) {
                current = current.superclass
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }
        }
        return null
    }

    /** Resolves a declared `int` field, used to read `InsetsSource.mType` without reflection boxing. */
    private fun resolveIntField(clazz: Class<*>, name: String): Field? {
        val field = resolveDeclaredField(clazz, name) ?: return null
        return field.takeIf { it.type == Int::class.javaPrimitiveType }
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
            val attrs = readWindowAttrs(win) ?: return
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
            val attrs = readWindowAttrs(win) ?: return
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

    /** Sentinel returned when `InsetsSource` type resolution failed. */
    internal const val TYPE_UNRESOLVED = Int.MIN_VALUE

    private const val OVERLOAD_RECT = "setFrame(Rect)"
    private const val OVERLOAD_INTS = "setFrame(int,int,int,int)"

    private const val REASON_REFLECTION_FAILED = "preprocessing-reflection-failed"
    private const val REASON_INVALID_SHAPE = "invalid-argument-shape"

    /**
     * Generation stamps that gate the `setFrame` diagnostics. They are the only thing the hot
     * path touches before deciding not to log, so no key string, no `Rect` copy and no extra
     * reflection are produced once a generation has been reported.
     */
    private val statusSourceLogStamp = AtomicLong(0L)
    private val reflectionFailureLogStamp = AtomicLong(0L)
    private val invalidShapeLogStamp = AtomicLong(0L)

    private fun claimGenerationStamp(stamp: AtomicLong): Boolean {
        val value = StatusBarHeightConfig.generation.get() + 1
        return stamp.getAndSet(value) != value
    }

    /**
     * `InsetsSource.setFrame` boundary.
     *
     * `setFrame` is a global framework method: every source of every window passes through it,
     * so the release path is deliberately stupid. A volatile boolean, two primitive compares
     * and one `mType` read reject everything that is not the status bar source, and the single
     * `Rect` allocation only happens when the frame really changes. There is no decision
     * object, no `Pair`, no diagnostic frame copy and no log key construction unless a
     * first-hit log is actually going to be emitted.
     */
    internal class SetFrameCallback(
        private val typeInfo: InsetsTypeInfo,
        private val hasGetId: Boolean,
        private val typeField: Field?,
    ) : MethodHook() {

        private val statusBarType: Int =
            if (typeInfo.encoding == InsetsTypeEncoding.UNSUPPORTED) TYPE_UNRESOLVED else typeInfo.statusBarType

        override fun intercept(chain: XposedInterface.Chain): Any? {
            if (!StatusBarHeightConfig.enabled) return chain.proceed()
            if (statusBarType == TYPE_UNRESOLVED) return chain.proceed()
            val configuredPx = StatusBarHeightConfig.configuredPx
            if (configuredPx <= 0) return chain.proceed()

            val source = chain.thisObject ?: return chain.proceed()
            val type = readSourceType(source)
            if (type == TYPE_UNRESOLVED) {
                logRejection(REASON_REFLECTION_FAILED, reflectionFailureLogStamp)
                return chain.proceed()
            }
            if (type != statusBarType) return chain.proceed()

            // Status bar source only: rare compared to the global call rate.
            val adjusted = try {
                adjustArgs(chain, source, type, configuredPx)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                logRejection(REASON_REFLECTION_FAILED, reflectionFailureLogStamp)
                null
            }

            // Exactly one proceed. Exceptions from the original method propagate.
            return if (adjusted == null) chain.proceed() else chain.proceed(adjusted)
        }

        /** Returns the rewritten argument array, or null when the original arguments must be used. */
        private fun adjustArgs(
            chain: XposedInterface.Chain,
            source: Any,
            type: Int,
            configuredPx: Int,
        ): Array<Any>? {
            when (val firstArg = chain.getArg(0)) {
                is Rect -> {
                    val newBottom = computeStatusBarFrameBottom(firstArg.top, firstArg.bottom, configuredPx, true)
                    logStatusSource(source, type, OVERLOAD_RECT, firstArg.top, firstArg.bottom, newBottom)
                    if (newBottom == firstArg.bottom) return null
                    val adjusted = copyRect(firstArg)
                    adjusted.bottom = newBottom
                    return arrayOf(adjusted)
                }
                is Int -> {
                    val top = chain.getArg(1) as? Int
                    val right = chain.getArg(2) as? Int
                    val bottom = chain.getArg(3) as? Int
                    if (top == null || right == null || bottom == null) {
                        logRejection(REASON_INVALID_SHAPE, invalidShapeLogStamp)
                        return null
                    }
                    val newBottom = computeStatusBarFrameBottom(top, bottom, configuredPx, true)
                    logStatusSource(source, type, OVERLOAD_INTS, top, bottom, newBottom)
                    if (newBottom == bottom) return null
                    return arrayOf(firstArg, top, right, newBottom)
                }
                else -> {
                    logRejection(REASON_INVALID_SHAPE, invalidShapeLogStamp)
                    return null
                }
            }
        }

        /** Reads `InsetsSource.mType` through the cached field, or falls back to `getType()`. */
        private fun readSourceType(source: Any): Int {
            val field = typeField
            return try {
                if (field != null && field.declaringClass.isInstance(source)) {
                    field.getInt(source)
                } else {
                    XposedHelpers.callMethod(source, GET_TYPE_METHOD) as? Int ?: TYPE_UNRESOLVED
                }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                TYPE_UNRESOLVED
            }
        }

        /** First hit of the status bar source for the current configuration generation. */
        private fun logStatusSource(
            source: Any,
            type: Int,
            overload: String,
            oldTop: Int,
            oldBottom: Int,
            newBottom: Int,
        ) {
            if (!claimGenerationStamp(statusSourceLogStamp)) return

            val generation = StatusBarHeightConfig.generation.get()
            val sourceId = if (hasGetId) readSourceId(source) else null
            val key = "insets:${sourceId ?: "n/a"}:$generation"
            synchronized(loggedCritical) {
                if (loggedCritical.size >= MAX_CRITICAL_KEYS) return
                if (!loggedCritical.add(key)) return
            }

            XposedHelpers.log(
                "[StatusBarInsets] insets " +
                    "encoding=${typeInfo.encoding} " +
                    "sourceId=${sourceId ?: "n/a"} " +
                    "type=$type " +
                    "overload=$overload " +
                    "oldTop=$oldTop oldBottom=$oldBottom " +
                    "newBottom=$newBottom " +
                    "rawDp=${StatusBarHeightConfig.rawPreferenceDp} " +
                    "resolvedDp=${StatusBarHeightConfig.configuredDp} " +
                    "configuredPx=${StatusBarHeightConfig.configuredPx} " +
                    "changed=${newBottom != oldBottom} " +
                    "gen=$generation"
            )
        }

        private fun readSourceId(source: Any): Int? {
            return try {
                XposedHelpers.callMethod(source, GET_ID_METHOD) as? Int
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                null
            }
        }

        /** Bounded, generation-gated anomaly log. Never runs for ordinary non-status sources. */
        private fun logRejection(reason: String, stamp: AtomicLong) {
            if (rejectionLoggingExhausted) return
            if (!claimGenerationStamp(stamp)) return

            val generation = StatusBarHeightConfig.generation.get()
            val key = "insets-reject:$reason:$generation"
            synchronized(loggedRejection) {
                if (loggedRejection.size >= MAX_REJECTION_KEYS) {
                    rejectionLoggingExhausted = true
                    return
                }
                if (!loggedRejection.add(key)) return
                if (loggedRejection.size >= MAX_REJECTION_KEYS) rejectionLoggingExhausted = true
            }

            XposedHelpers.log(
                "[StatusBarInsets] insets-reject " +
                    "encoding=${typeInfo.encoding} " +
                    "reason=$reason " +
                    "gen=$generation"
            )
        }
    }
}
