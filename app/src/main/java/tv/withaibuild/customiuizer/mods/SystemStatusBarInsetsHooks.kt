package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.graphics.Rect
import android.os.Handler
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsSourceCapability
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsTypeEncoding
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightAbi
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightEffect
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightRuntime
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightResolver
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method
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

    private const val SET_FRAME_METHOD = "setFrame"

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

    /** Process-scoped frozen ABI for the status bar height feature. */
    @Volatile
    private var statusBarHeightAbi: StatusBarHeightAbi? = null

    /** Hot-path effect holding frozen Class/Field/Method references. */
    @Volatile
    private var statusBarHeightEffect: StatusBarHeightEffect? = null

    private var hookInstalled: Boolean = false

    /** Process-scoped runtime state: identity, weak owner refs, fallback budget and refresh. */
    private val statusBarHeightRuntime = StatusBarHeightRuntime()

    /** Resolved `com.android.server.wm.WindowState` class, used for an allocation-free type test. */
    @Volatile
    private var windowStateClass: Class<*>? = null

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
    private val layoutLogStamps = AtomicLongArray(StatusBarHeightRuntime.MAX_TRACKED)
    private val windowFrameLogStamps = AtomicLongArray(StatusBarHeightRuntime.MAX_TRACKED)
    private val clientFrameLogStamps = AtomicLongArray(StatusBarHeightRuntime.MAX_TRACKED)

    /**
     * Returns true at most once per (display, generation) pair. The stamp is `generation + 1`
     * so the zero-initialised array never suppresses generation 0.
     */
    private fun claimLiveLogStamp(stamps: AtomicLongArray, displayId: Int): Boolean {
        val slot = if (displayId in 0 until StatusBarHeightRuntime.MAX_TRACKED) displayId else StatusBarHeightRuntime.MAX_TRACKED - 1
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
        statusBarHeightAbi = null
        statusBarHeightEffect = null
        hookInstalled = false
        statusBarHeightRuntime.resetKnownStatusBars()
        windowStateClass = null
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

        // Resolve the full cold ABI once through the Architecture C resolver.
        val abi = StatusBarHeightResolver.resolveCore(classLoader)
        val insets = abi.insets
        if (!insets.coreSupported) {
            logInstall("status bar Insets core capability not supported")
            return
        }

        val insetsSourceClass = insets.sourceClass ?: run {
            logInstall("InsetsSource class not found")
            return
        }

        val resources = try {
            Resources.getSystem()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            logInstall("Resources.getSystem() failed: ${t.javaClass.simpleName}")
            return
        }

        StatusBarHeightConfig.configure(MainModule.mPrefs, resources)

        // Publish the frozen ABI and install the H1 callback.  This must not happen unless both
        // ABI resolution and configuration succeeded.
        statusBarHeightAbi = abi
        statusBarHeightEffect = StatusBarHeightEffect(abi)
        publishH3LegacyAbi(abi.windowManager)

        val state = StatusBarHeightConfig.currentState()
        logInstall(
            "enabled=${state.enabled} " +
                "rawDp=${state.rawPreferenceDp} " +
                "resolvedDp=${state.configuredDp} " +
                "configuredPx=${state.configuredPx} " +
                "density=${state.density} " +
                "densityDpi=${state.densityDpi} " +
                "encoding=${insets.typeInfo.encoding} " +
                "statusType=${insets.typeInfo.statusBarType} " +
                "navType=${insets.typeInfo.navigationType} " +
                "cutoutType=${insets.typeInfo.displayCutoutType} " +
                "setFrame1=${insets.setFrameOneArg} " +
                "setFrame4=${insets.setFrameFourArg} " +
                "typeReader=${if (insets.typeField != null) "FIELD" else "METHOD"} " +
                "getId=${insets.getIdMethod != null} " +
                "getFrame=${insets.getFrameMethod != null}"
        )

        val callback = SetFrameCallback(insets)
        ModuleHelper.hookAllMethods(insetsSourceClass, SET_FRAME_METHOD, callback)

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
        val abi = statusBarHeightAbi ?: run {
            logInstall("ABI not resolved before DisplayPolicy hook install")
            return
        }
        val effect = statusBarHeightEffect ?: run {
            logInstall("Effect not ready before DisplayPolicy hook install")
            return
        }

        val displayPolicyClass = abi.windowManager.displayPolicyClass
        if (displayPolicyClass == null) {
            logInstall("DisplayPolicy class not found")
            return
        }

        if (!isH2Capable(abi, effect)) {
            logInstall("H2 capability incomplete, skipping layout hot path")
            return
        }

        ModuleHelper.hookAllMethods(displayPolicyClass, LAYOUT_WINDOW_LW_METHOD, LayoutWindowCallback(effect))
    }

    private class LayoutWindowCallback(
        private val effect: StatusBarHeightEffect,
    ) : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            return onLayoutWindowLw(chain, effect)
        }
    }

    private fun isH2Capable(abi: StatusBarHeightAbi, effect: StatusBarHeightEffect): Boolean {
        val wm = abi.windowManager
        val decor = abi.decorInsets

        val hasCore = wm.windowStateClass != null
            && wm.windowStateAttrsField != null
            && wm.layoutParamsHeightField != null
            && wm.displayPolicyClass != null
            && wm.windowStateGetDisplayIdMethod != null

        val hasMetrics = wm.windowStateGetDisplayMetricsMethod != null ||
            (wm.windowStateDisplayContentField != null && decor.displayContentGetDisplayMetricsMethod != null)

        val hasIdentification = wm.layoutParamsTypeField != null || wm.layoutParamsPackageNameField != null

        return hasCore && hasMetrics && hasIdentification
    }

    private fun installWindowStateHook(classLoader: ClassLoader) {
        val abi = statusBarHeightAbi ?: run {
            logInstall("ABI not resolved before WindowState hook install")
            return
        }
        val windowStateClass = abi.windowManager.windowStateClass
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

    private fun publishH3LegacyAbi(wm: tv.withaibuild.customiuizer.mods.statusbarheight.WindowManagerCapability) {
        windowStateClass = wm.windowStateClass
        windowStateGetFrameMethod = wm.windowStateGetFrameMethod
        windowStateGetDisplayMetricsMethod = wm.windowStateGetDisplayMetricsMethod
        windowStateGetDisplayIdMethod = wm.windowStateGetDisplayIdMethod
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
        val effect = statusBarHeightEffect ?: return chain.proceed()
        return onLayoutWindowLw(chain, effect)
    }

    private fun onLayoutWindowLw(chain: XposedInterface.Chain, effect: StatusBarHeightEffect): Any? {
        val win = chain.getArg(0) ?: return chain.proceed()
        if (!effect.isWindowState(win)) return chain.proceed()

        var config = StatusBarHeightConfig.currentState()

        if (!config.enabled) {
            // Disabled path must not perform status-bar discovery on unknown WindowStates.
            // Only a previously identified status bar gets its original height restored.
            if (markLatestIfKnownStatusBar(win)) {
                restoreStatusBarWindowHeight(win, effect)
            }
            return chain.proceed()
        }

        // Single-pass recognition: known owners reuse the retained WeakReference and update
        // latest; unknown WindowStates fall through to type/fallback discovery.
        if (!isStatusBarWindow(win)) return chain.proceed()

        val metrics = effect.readWindowDisplayMetrics(win) ?: return chain.proceed()
        val displayId = effect.readDisplayId(win)

        val configuredPx = if (displayId == 0) {
            // Only a real density change may re-enter the synchronized recompute.
            // The new snapshot is taken directly from the recompute result so there
            // is only one currentState read per callback.
            if (metrics.densityDpi != config.densityDpi) {
                config = StatusBarHeightConfig.recomputePx(metrics).current
            }
            config.configuredPx
        } else {
            StatusBarHeightConfig.configuredPxFor(config.configuredDp, metrics)
        }

        if (configuredPx <= 0) return chain.proceed()

        if (claimLiveLogStamp(layoutLogStamps, displayId)) {
            val densityForLog = if (displayId == 0) config.density else metrics.density
            val densityDpiForLog = if (displayId == 0) config.densityDpi else metrics.densityDpi
            logLive(
                "layout displayId=$displayId " +
                    "rawDp=${config.rawPreferenceDp} " +
                    "resolvedDp=${config.configuredDp} " +
                    "density=$densityForLog " +
                    "densityDpi=$densityDpiForLog " +
                    "configuredPx=$configuredPx",
                "layout:$displayId"
            )
        }

        applyStatusBarWindowHeight(win, configuredPx, effect)

        val result = chain.proceed()

        if (claimLiveLogStamp(windowFrameLogStamps, displayId)) {
            val frame = effect.readWindowFrame(win)
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

    private fun isKnownStatusBarWindow(win: Any): Boolean =
        statusBarHeightRuntime.isKnownStatusBar(win)

    private fun markLatestIfKnownStatusBar(win: Any): Boolean =
        statusBarHeightRuntime.markLatestIfKnown(win)

    private fun rememberStatusBarWindow(win: Any) {
        statusBarHeightRuntime.rememberStatusBar(win)
    }

    /**
     * Identity fast path first: a WindowState that has already been proven to be a status bar
     * costs one reference comparison. Only unknown windows pay the `mAttrs.type` read, and the
     * expensive `packageName`/`toString()` fallback runs only while the type check has never
     * succeeded on this ROM and only for a bounded number of probes.
     */
    @JvmStatic
    internal fun isStatusBarWindow(win: Any): Boolean {
        val effect = statusBarHeightEffect ?: return false

        // Known owner: a single bounded scan also updates the latest reference, reusing the
        // retained WeakReference.  Unknown windows fall through to type/fallback discovery.
        if (markLatestIfKnownStatusBar(win)) return true
        return try {
            val attrs = effect.readWindowAttrs(win) ?: return false
            if (effect.readAttrsType(attrs) == TYPE_STATUS_BAR) {
                statusBarHeightRuntime.typeMatchObserved = true
                rememberStatusBarWindow(win)
                return true
            }

            if (statusBarHeightRuntime.typeMatchObserved) return false
            if (statusBarHeightRuntime.fallbackProbeBudget.decrementAndGet() < 0) return false

            val packageName = effect.readPackageName(attrs)
            if (packageName?.contains("com.android.systemui") != true) return false
            if (!win.toString().contains(STATUS_BAR_WINDOW_TAG)) return false

            rememberStatusBarWindow(win)
            true
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

    private fun applyStatusBarWindowHeight(win: Any, configuredPx: Int, effect: StatusBarHeightEffect) {
        if (configuredPx <= 0) return

        try {
            val attrs = effect.readWindowAttrs(win) ?: return
            val currentHeight = effect.readStatusBarHeight(attrs) ?: return
            if (XposedHelpers.getAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY) == null) {
                XposedHelpers.setAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY, currentHeight)
            }
            if (currentHeight != configuredPx) {
                effect.setHeight(attrs, configuredPx)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
    }

    private fun restoreStatusBarWindowHeight(win: Any, effect: StatusBarHeightEffect) {
        try {
            val attrs = effect.readWindowAttrs(win) ?: return
            val originalHeight = XposedHelpers.getAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY) as? Int ?: return
            val currentHeight = effect.readStatusBarHeight(attrs) ?: return
            if (currentHeight != originalHeight) {
                effect.setHeight(attrs, originalHeight)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
    }

    @JvmStatic
    internal fun requestStatusBarTraversal() {
        val win = statusBarHeightRuntime.latestKnownStatusBar?.get() ?: return
        try {
            invalidateDecorInsets(win)
            val wmService = XposedHelpers.getObjectField(win, "mWmService") ?: return
            val windowPlacer = XposedHelpers.getObjectField(wmService, "mWindowPlacerLocked") ?: return

            val newGen = StatusBarHeightConfig.generation.get()
            val lastGen = statusBarHeightRuntime.lastRefreshGeneration.getAndSet(newGen)
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
        private val insets: InsetsSourceCapability,
    ) : MethodHook() {

        private val statusBarType: Int =
            if (insets.typeInfo.encoding == InsetsTypeEncoding.UNSUPPORTED) TYPE_UNRESOLVED else insets.typeInfo.statusBarType

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val config = StatusBarHeightConfig.currentState()
            if (!config.enabled) return chain.proceed()
            if (statusBarType == TYPE_UNRESOLVED) return chain.proceed()
            val configuredPx = config.configuredPx
            if (configuredPx <= 0) return chain.proceed()

            val source = chain.thisObject ?: return chain.proceed()
            val type = readSourceType(source)
            if (type == TYPE_UNRESOLVED) {
                logRejection(REASON_REFLECTION_FAILED, reflectionFailureLogStamp, config)
                return chain.proceed()
            }
            if (type != statusBarType) return chain.proceed()

            // Status bar source only: rare compared to the global call rate.
            val adjusted = try {
                adjustArgs(chain, source, type, configuredPx, config)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                logRejection(REASON_REFLECTION_FAILED, reflectionFailureLogStamp, config)
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
            config: StatusBarHeightConfig.State,
        ): Array<Any>? {
            when (val firstArg = chain.getArg(0)) {
                is Rect -> {
                    val newBottom = computeStatusBarFrameBottom(firstArg.top, firstArg.bottom, configuredPx, true)
                    logStatusSource(source, type, OVERLOAD_RECT, firstArg.top, firstArg.bottom, newBottom, config)
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
                        logRejection(REASON_INVALID_SHAPE, invalidShapeLogStamp, config)
                        return null
                    }
                    val newBottom = computeStatusBarFrameBottom(top, bottom, configuredPx, true)
                    logStatusSource(source, type, OVERLOAD_INTS, top, bottom, newBottom, config)
                    if (newBottom == bottom) return null
                    return arrayOf(firstArg, top, right, newBottom)
                }
                else -> {
                    logRejection(REASON_INVALID_SHAPE, invalidShapeLogStamp, config)
                    return null
                }
            }
        }

        /** Reads `InsetsSource.mType` through the frozen field, or falls back to the frozen `getType()` method. */
        private fun readSourceType(source: Any): Int {
            val field = insets.typeField
            if (field != null) {
                return try {
                    if (!field.declaringClass.isInstance(source)) {
                        TYPE_UNRESOLVED
                    } else {
                        field.getInt(source)
                    }
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    TYPE_UNRESOLVED
                }
            }

            val method = insets.getTypeMethod ?: return TYPE_UNRESOLVED

            return try {
                if (!method.declaringClass.isInstance(source)) {
                    TYPE_UNRESOLVED
                } else {
                    method.invoke(source) as? Int ?: TYPE_UNRESOLVED
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
            config: StatusBarHeightConfig.State,
        ) {
            if (!claimGenerationStamp(statusSourceLogStamp)) return

            val generation = StatusBarHeightConfig.generation.get()
            val sourceId = if (insets.getIdMethod != null) readSourceId(source) else null
            val key = "insets:${sourceId ?: "n/a"}:$generation"
            synchronized(loggedCritical) {
                if (loggedCritical.size >= MAX_CRITICAL_KEYS) return
                if (!loggedCritical.add(key)) return
            }

            XposedHelpers.log(
                "[StatusBarInsets] insets " +
                    "encoding=${insets.typeInfo.encoding} " +
                    "sourceId=${sourceId ?: "n/a"} " +
                    "type=$type " +
                    "overload=$overload " +
                    "oldTop=$oldTop oldBottom=$oldBottom " +
                    "newBottom=$newBottom " +
                    "rawDp=${config.rawPreferenceDp} " +
                    "resolvedDp=${config.configuredDp} " +
                    "configuredPx=${config.configuredPx} " +
                    "changed=${newBottom != oldBottom} " +
                    "gen=$generation"
            )
        }

        private fun readSourceId(source: Any): Int? {
            val method = insets.getIdMethod ?: return null
            return try {
                method.invoke(source) as? Int
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                null
            }
        }

        /** Bounded, generation-gated anomaly log. Never runs for ordinary non-status sources. */
        private fun logRejection(reason: String, stamp: AtomicLong, config: StatusBarHeightConfig.State) {
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
                    "encoding=${insets.typeInfo.encoding} " +
                    "reason=$reason " +
                    "gen=$generation"
            )
        }
    }
}
