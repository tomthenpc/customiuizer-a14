package tv.withaibuild.customiuizer.mods

import android.graphics.Region
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandCapsuleView
import tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandMotionProfile
import tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandWindowEnvelope
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * HyperOS 1 StrongToast presentation (the top black capsule used by charging and system modes).
 *
 * MATCH_STATUS_BAR_HEIGHT makes the *total visible forehead* - the resized ROM message row plus
 * the `strong_toast_bottom_view` chin - exactly as tall as the current status bar, and sizes the
 * Window to that same height. A status-bar inset of 0 leaves the ROM window untouched.
 * Hiding stops the request at MIUIStrongToastControl before a View or animation is created. No
 * Activity, View, controller or listener is retained.
 * Dynamic Island mode reuses that same event-scoped ROM View and cleanup path. It has exactly one
 * shape owner: a module-owned [DynamicIslandCapsuleView] is inserted around the ROM message
 * container and both paints the pill and clips its children to it. Every competing ROM shape - the
 * `cl_strong_toast_msg` background, the `round_rect` overlay and the forehead chin - is suppressed
 * for the duration of the event and restored exactly on teardown. No overlay service, listener or
 * polling is introduced.
 */
object SystemUIStrongToastHooks {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val STRONG_TOAST_CLASS = "com.android.systemui.toast.MIUIStrongToast"
    private const val STRONG_TOAST_CONTROL_CLASS =
        "com.android.systemui.toast.MIUIStrongToastControl"
    private const val BATTERY_CALLBACK_CLASS =
        "com.android.systemui.toast.MIUIStrongToastControl\$6"
    private const val KEYGUARD_STATE_CLASS =
        "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl"
    private const val MESSAGE_CONTAINER_ID = "cl_strong_toast_msg"
    private const val FOREHEAD_BOTTOM_ID = "strong_toast_bottom_view"
    // The ROM draws a second rounded shape here. It is laid out inside cl_strong_toast_msg but is
    // strong_toast_width_window (337dp) wide against a strong_toast_width (320dp) capsule, so
    // whenever the ROM makes it visible its corners fall outside the module capsule and read as a
    // clipped island. Dynamic Island suppresses it; see suppressRomRoundRect.
    private const val ROUND_RECT_ID = "round_rect"
    private const val CAPSULE_TOP_MARGIN_DP = 6f
    // Visible gap between the capsule and whatever the ROM lays out underneath it. This is a
    // spacing value, not a clipping workaround: the capsule shape is owned by the capsule View.
    private const val CAPSULE_BOTTOM_CLEARANCE_DP = 8f
    private const val BOTTOM_EDGE_GAP_MIN_DP = 2f
    private const val BOTTOM_EDGE_GAP_MAX_DP = 6f
    private const val SWIPE_DISMISS_THRESHOLD_MAX_DP = 28f
    private const val SWIPE_TOUCH_EXPANSION_HORIZONTAL_DP = 24f
    private const val SWIPE_TOUCH_EXPANSION_VERTICAL_DP = 16f
    private const val SWIPE_STATE_FIELD = "customiuizer_strong_toast_swipe"
    private const val DISMISS_RUNNING_FIELD = "customiuizer_strong_toast_dismiss_running"
    private const val SHELL_STATE_FIELD = "customiuizer_dynamic_island_shell_state"
    private const val PENDING_PREDRAW_LISTENER_FIELD =
        "customiuizer_dynamic_island_pending_predraw"
    private const val TOUCH_REGION_LISTENER_FIELD =
        "customiuizer_strong_toast_touch_region_listener"
    private const val TOUCHABLE_INSETS_REGION = 3
    private const val STATUS_BAR_CONTENTS_ID = "status_bar_contents"
    private const val STATUS_BAR_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"
    private const val RUNTIME_SNAPSHOT_FIELD = "customiuizer_strong_toast_runtime_snapshot"
    private const val MATCH_BASELINE_FIELD = "customiuizer_match_mode_baseline"
    // Entrance settles the capsule into place: fast start, long decelerating tail, no overshoot.
    // Geometric overshoot is not available here because SurfaceFlinger clips anything the module
    // draws outside the ROM-owned Window surface.
    private val dynamicIslandEntranceInterpolator = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)

    // Exit accelerates away from rest. The previous exit reused the entrance curve, which is
    // front-loaded: it finished almost all of its movement in the first third of the duration and
    // then held a static, fully opaque capsule until the Window was torn down. That stall followed
    // by an instant removal is what read as a stutter.
    private val dynamicIslandExitInterpolator = PathInterpolator(0.4f, 0f, 0.9f, 0.35f)

    private var statusBarContentsRef = WeakReference<View>(null)
    private var statusBarHiddenOwnerRef = WeakReference<View>(null)
    private var statusBarContentsOriginalAlpha = 1f
    @JvmField
    internal var snapshotRef: AtomicReference<StrongToastRuntimeSnapshot>? = null
    @JvmField
    internal var installed: Boolean = false

    internal fun currentSnapshot(): StrongToastRuntimeSnapshot? = snapshotRef?.get()

    internal fun storeSnapshot(view: Any, snapshot: StrongToastRuntimeSnapshot) {
        XposedHelpers.setAdditionalInstanceField(view, RUNTIME_SNAPSHOT_FIELD, snapshot)
    }

    internal fun resolveSnapshot(view: Any?): StrongToastRuntimeSnapshot? {
        if (view != null) {
            val stored = XposedHelpers.getAdditionalInstanceField(
                view,
                RUNTIME_SNAPSHOT_FIELD
            ) as? StrongToastRuntimeSnapshot
            if (stored != null) return stored
        }
        return currentSnapshot()
    }

    private class SwipeGestureState(
        val capsule: View
    ) {
        var downRawY = 0f
        var active = false
        var moved = false
        var startTranslationY = 0f
        val capsuleLocation = IntArray(2)
        var motionProfile: DynamicIslandMotionProfile? = null
    }

    /**
     * Captured pre-mutation baseline for a single MATCH_STATUS_BAR_HEIGHT event.
     * Stored on the StrongToast root via [MATCH_BASELINE_FIELD] and removed on detach.
     */
    internal data class MatchModeBaseline(
        val width: Int,
        val height: Int,
        val topMargin: Int,
        val bottomMargin: Int,
        val layoutGravity: Int,
        val capsuleGravity: Int,
        val parentPaddingLeft: Int,
        val parentPaddingTop: Int,
        val parentPaddingRight: Int,
        val parentPaddingBottom: Int,
        val parentGravity: Int,
        val parentWidth: Int,
        val parentHeight: Int,
        val parentTopMargin: Int,
        val parentBottomMargin: Int,
        val parentLayoutGravity: Int,
        val bottomViewVisibility: Int
    )

    /**
     * Per-MIUIStrongToast Dynamic Island ownership state.
     *
     * A module-owned [DynamicIslandCapsuleView] shell is inserted around the ROM
     * `cl_strong_toast_msg`. The shell is the only shape owner: it paints the pill
     * and clips its children to that same path, and it owns all shell-level
     * transforms. The ROM content stays untouched and is reparented as a whole.
     *
     * Every module-owned mutation is captured here so [restoreDynamicIslandShell]
     * can return the ROM hierarchy to its exact original state.
     */
    internal data class DynamicIslandShellState(
        val shell: FrameLayout,
        val content: View,
        val originalParent: ViewGroup,
        val originalIndex: Int,
        val originalContentLayoutParams: ViewGroup.LayoutParams?,
        val originalContentBackground: Drawable?,
        val originalContentClipToOutline: Boolean,
        val originalParentPaddingLeft: Int,
        val originalParentPaddingTop: Int,
        val originalParentPaddingRight: Int,
        val originalParentPaddingBottom: Int,
        val originalParentGravity: Int,
        val bottomView: View?,
        val bottomViewOriginalVisibility: Int,
        val romRoundRect: View?,
        val romRoundRectOriginalAlpha: Float,
        val romRoundRectOriginalVisibility: Int
    )

    @JvmStatic
    internal fun install(
        lpparam: PackageReadyParam,
        snapshotRef: AtomicReference<StrongToastRuntimeSnapshot>
    ) {
        this.snapshotRef = snapshotRef
        if (installed) return
        installed = true

        installHeightMatch(lpparam)
        installDynamicIslandMotion(lpparam)
        installStatusBarContentsCapture(lpparam)
        installControlClassHooks(lpparam)
    }

    private fun installHeightMatch(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "getWindowParam",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    try {
                        val layoutParams = callback.getResult() as? WindowManager.LayoutParams ?: return
                        val strongToast = callback.getThisObject() as? View ?: return
                        val snapshot = resolveSnapshot(strongToast) ?: return
                        storeSnapshot(strongToast, snapshot)
                        when (snapshot.mode) {
                            StrongToastPresentationMode.SYSTEM_DEFAULT,
                            StrongToastPresentationMode.HIDE -> return
                            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT -> {
                                // The visible forehead is the ROM message row plus the
                                // strong_toast_bottom_view chin, which draws the concave corners
                                // that blend the black area back into the screen. "Match the
                                // status bar" therefore constrains the *sum* of the two, not the
                                // message row alone.
                                val statusBarHeightPx = currentStatusBarInsetPx(strongToast)
                                if (statusBarHeightPx <= 0) return
                                val chinHeightPx = foreheadChinHeightPx(strongToast)
                                val prepared = applyMatchStatusBarHeight(
                                    strongToast,
                                    resolveMatchContentHeightPx(statusBarHeightPx, chinHeightPx),
                                    matchModeHidesChin(statusBarHeightPx, chinHeightPx)
                                )
                                if (prepared) {
                                    layoutParams.height =
                                        resolveMatchWindowHeightPx(statusBarHeightPx)
                                    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                }
                            }
                            StrongToastPresentationMode.DYNAMIC_ISLAND -> {
                                // HyperOS normally makes this Window exactly as wide as
                                // strong_toast_width. A centered overshoot is then clipped by the
                                // Window surface before ViewGroup clip flags can help. Keep the
                                // capsule at ROM width, but give it a full-screen transparent host.
                                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                                // The ROM window animation is authored for the status-bar edge.
                                // Dynamic-island modes own their complete transform so a second
                                // surface animation cannot add a direction or clip the first frames.
                                layoutParams.windowAnimations = 0
                                // Keep the transparent safety area inside the actual input frame.
                                // HyperOS otherwise fits this STATUS_BAR_SUB_PANEL window again and
                                // exposes only its legacy content height as the touchable region.
                                layoutParams.setFitInsetsTypes(0)
                                val visualHeightPx = strongToastVisualHeightPx(strongToast)
                                if (snapshot.position == StrongToastPosition.BOTTOM) {
                                    layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                    // The bottom window owns its navigation/gesture-safe padding.
                                    // Leaving the ROM's NAVIGATION_BARS fit type enabled makes WMS
                                    // subtract the visible navigation inset a second time and clips
                                    // the capsule by that exact amount on gesture-navigation devices.
                                    val bottomPaddingPx = resolveBottomPaddingPx(
                                        currentBottomSafeInsetPx(strongToast),
                                        resolveBottomEdgeGapPx(strongToast, visualHeightPx),
                                        dpToPx(strongToast, snapshot.bottomOffsetDp.toFloat())
                                    )
                                    layoutParams.height = resolveBottomDynamicIslandWindowHeightPx(
                                        visualHeightPx,
                                        resolveBottomTopClearancePx(strongToast, visualHeightPx),
                                        bottomPaddingPx
                                    )
                                } else {
                                    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                    layoutParams.height = resolveDynamicIslandWindowHeightPx(
                                        visualHeightPx,
                                        dpToPx(strongToast, CAPSULE_TOP_MARGIN_DP),
                                        dpToPx(strongToast, CAPSULE_BOTTOM_CLEARANCE_DP)
                                    )
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("StrongToastPresentation", t)
                    }
                }
            }
        )
    }

    private fun currentStatusBarInsetPx(view: View?): Int {
        val context = view?.context ?: return 0
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return 0
        return windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            .top
    }

    private fun currentBottomSafeInsetPx(view: View?): Int {
        val context = view?.context ?: return 0
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return 0
        val insets = windowManager.currentWindowMetrics.windowInsets
        val navigationBar = insets
            .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            .bottom
        val mandatoryGestures = insets
            .getInsetsIgnoringVisibility(WindowInsets.Type.mandatorySystemGestures())
            .bottom
        return maxOf(navigationBar, mandatoryGestures)
    }

    private fun strongToastVisualHeightPx(view: View?): Int {
        return strongToastDimensionPx(view, "strong_toast_height")
    }

    private fun strongToastDimensionPx(view: View?, name: String): Int {
        val resources = view?.resources ?: return 0
        val id = resources.getIdentifier(
            name,
            "dimen",
            SYSTEM_UI_PACKAGE
        )
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun installDynamicIslandMotion(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "onAttachedToWindow",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val strongToast = callback.getThisObject() as? View ?: return
                    val snapshot = resolveSnapshot(strongToast) ?: return
                    storeSnapshot(strongToast, snapshot)
                    if (!snapshot.isDynamicIsland) return
                    installExpandedWindowTouchRegion(strongToast)
                    startDynamicIslandEntrance(
                        strongToast,
                        snapshot.position,
                        snapshot.bottomOffsetDp
                    )
                }
            }
        )
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "onComplete",
            object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    val strongToast = callback.getThisObject() as? View ?: return
                    val snapshot = resolveSnapshot(strongToast) ?: return
                    if (snapshot.isDynamicIsland) restoreStatusBarContents(strongToast)
                }
            }
        )
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "realHideStrongToast",
            object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    val strongToast = callback.getThisObject() as? View ?: return
                    val snapshot = resolveSnapshot(strongToast) ?: return
                    removePendingDynamicIslandPreDrawListener(strongToast)
                    if (!snapshot.isDynamicIsland) return
                    if (XposedHelpers.getAdditionalInstanceField(
                            strongToast,
                            DISMISS_RUNNING_FIELD
                        ) == true
                    ) {
                        callback.returnAndSkip(null)
                        return
                    }
                    val swipeState = XposedHelpers.getAdditionalInstanceField(
                        strongToast,
                        SWIPE_STATE_FIELD
                    ) as? SwipeGestureState
                    val shell = swipeState?.capsule
                        ?: findDynamicIslandShell(strongToast)
                        ?: prepareDynamicIslandCapsule(
                            strongToast,
                            snapshot.position,
                            snapshot.bottomOffsetDp
                        )
                        ?: return
                    if (!strongToast.isAttachedToWindow) return
                    callback.returnAndSkip(null)
                    animateDynamicIslandDismiss(
                        strongToast,
                        shell,
                        snapshot.position
                    )
                }
            }
        )
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "onDetachedFromWindow",
            object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    val strongToast = callback.getThisObject() as? View ?: return
                    val snapshot = resolveSnapshot(strongToast) ?: return
                    try {
                        removePendingDynamicIslandPreDrawListener(strongToast)
                        if (snapshot.isDynamicIsland) {
                            val state = XposedHelpers.getAdditionalInstanceField(
                                strongToast,
                                SHELL_STATE_FIELD
                            ) as? DynamicIslandShellState
                            val shell = state?.shell
                                ?: findDynamicIslandShell(strongToast)
                                ?: findDynamicIslandCapsule(strongToast)
                                ?: strongToast
                            shell.animate().cancel()
                            state?.content?.animate()?.cancel()
                            resetDynamicIslandHostTransform(strongToast)
                            resetDynamicIslandTransform(shell)
                            state?.content?.let { resetDynamicIslandTransform(it) }
                            setSwipeListenerRecursively(shell, null)
                            (shell.parent as? View)?.setOnTouchListener(null)
                            removeExpandedWindowTouchRegion(strongToast)
                            restoreStatusBarContents(strongToast)
                            restoreDynamicIslandShell(state)
                            XposedHelpers.removeAdditionalInstanceField(strongToast, SHELL_STATE_FIELD)
                            XposedHelpers.removeAdditionalInstanceField(strongToast, SWIPE_STATE_FIELD)
                            XposedHelpers.removeAdditionalInstanceField(strongToast, DISMISS_RUNNING_FIELD)
                        } else {
                            resetMatchModeCapsule(strongToast)
                        }
                    } finally {
                        XposedHelpers.removeAdditionalInstanceField(strongToast, RUNTIME_SNAPSHOT_FIELD)
                    }
                }
            }
        )
    }

    private fun installExpandedWindowTouchRegion(view: View) {
        removeExpandedWindowTouchRegion(view)
        try {
            val listenerClass = Class.forName(
                "android.view.ViewTreeObserver\$OnComputeInternalInsetsListener"
            )
            val listener = Proxy.newProxyInstance(
                view.javaClass.classLoader,
                arrayOf(listenerClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onComputeInternalInsets" -> {
                        val info = args?.getOrNull(0) ?: return@newProxyInstance null
                        ModuleHelper.guarded {
                            XposedHelpers.callMethod(info, "setTouchableInsets", TOUCHABLE_INSETS_REGION)
                            val region = XposedHelpers.getObjectField(info, "touchableRegion") as? Region
                                ?: return@guarded
                            val shell = findDynamicIslandShell(view) ?: return@guarded
                            val horizontal = dpToPx(shell, SWIPE_TOUCH_EXPANSION_HORIZONTAL_DP)
                            val vertical = dpToPx(shell, SWIPE_TOUCH_EXPANSION_VERTICAL_DP)
                            region.set(
                                (shell.left - horizontal).coerceAtLeast(0),
                                (shell.top - vertical).coerceAtLeast(0),
                                (shell.right + horizontal).coerceAtMost(view.width),
                                (shell.bottom + vertical).coerceAtMost(view.height)
                            )
                        }
                        null
                    }

                    "equals" -> proxy === args?.getOrNull(0)
                    "hashCode" -> java.lang.System.identityHashCode(proxy)
                    "toString" -> "StrongToastTouchRegionListener"
                    else -> null
                }
            }
            XposedHelpers.callMethod(
                view.viewTreeObserver,
                "addOnComputeInternalInsetsListener",
                listener
            )
            XposedHelpers.setAdditionalInstanceField(view, TOUCH_REGION_LISTENER_FIELD, listener)
            view.requestLayout()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandTouchRegion", t)
        }
    }

    private fun removeExpandedWindowTouchRegion(view: View) {
        val listener = XposedHelpers.getAdditionalInstanceField(
            view,
            TOUCH_REGION_LISTENER_FIELD
        ) ?: return
        try {
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                XposedHelpers.callMethod(
                    observer,
                    "removeOnComputeInternalInsetsListener",
                    listener
                )
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandTouchRegionCleanup", t)
        } finally {
            XposedHelpers.removeAdditionalInstanceField(view, TOUCH_REGION_LISTENER_FIELD)
        }
    }

    /**
     * Removes a stale Dynamic Island pre-draw listener for [view].  The listener was added to the
     * shell's [ViewTreeObserver] in [startDynamicIslandEntrance], but once the View is attached the
     * shell and root share the same observer.  Calling this before the next entrance or during
     * detach prevents a stale callback from running after the shell has been replaced or removed.
     */
    private fun removePendingDynamicIslandPreDrawListener(view: View) {
        val listener = XposedHelpers.getAdditionalInstanceField(
            view,
            PENDING_PREDRAW_LISTENER_FIELD
        ) as? ViewTreeObserver.OnPreDrawListener ?: return
        try {
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnPreDrawListener(listener)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandPreDrawCleanup", t)
        } finally {
            XposedHelpers.removeAdditionalInstanceField(view, PENDING_PREDRAW_LISTENER_FIELD)
        }
    }

    private fun startDynamicIslandEntrance(
        view: View,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ) {
        try {
            removePendingDynamicIslandPreDrawListener(view)

            val shell = prepareDynamicIslandCapsule(view, position, bottomOffsetDp) ?: return
            if (position == StrongToastPosition.TOP) hideStatusBarContents(view)
            XposedHelpers.setAdditionalInstanceField(view, SWIPE_STATE_FIELD, SwipeGestureState(shell))
            installSwipeToDismiss(view, shell, position)
            view.animate().cancel()
            resetDynamicIslandHostTransform(view)
            shell.animate().cancel()
            resetDynamicIslandTransform(shell)

            // onAttachedToWindow precedes the first layout pass. Defer exactly once until the
            // shell is laid out so the profile can set a safe pivot/translation from real
            // width/height and the first animation frame does not run before drawing.
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val observer = shell.viewTreeObserver
                    if (observer.isAlive) {
                        observer.removeOnPreDrawListener(this)
                    }
                    val current = XposedHelpers.getAdditionalInstanceField(
                        view,
                        PENDING_PREDRAW_LISTENER_FIELD
                    )
                    if (current !== this) return true
                    XposedHelpers.removeAdditionalInstanceField(view, PENDING_PREDRAW_LISTENER_FIELD)

                    if (!shell.isAttachedToWindow || shell.width <= 0 || shell.height <= 0) {
                        resetDynamicIslandHostTransform(view)
                        resetDynamicIslandTransform(shell)
                        return true
                    }
                    ModuleHelper.guarded {
                        runDynamicIslandEntrance(view, shell, position)
                    }
                    return true
                }
            }

            XposedHelpers.setAdditionalInstanceField(view, PENDING_PREDRAW_LISTENER_FIELD, listener)
            val vto = shell.viewTreeObserver
            if (vto.isAlive) {
                vto.addOnPreDrawListener(listener)
            } else {
                XposedHelpers.removeAdditionalInstanceField(view, PENDING_PREDRAW_LISTENER_FIELD)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            restoreStatusBarContents(view)
            resetDynamicIslandHostTransform(view)
            val state = XposedHelpers.getAdditionalInstanceField(view, SHELL_STATE_FIELD) as? DynamicIslandShellState
            state?.shell?.let { resetDynamicIslandTransform(it) }
            restoreDynamicIslandShell(state)
            XposedHelpers.removeAdditionalInstanceField(view, SHELL_STATE_FIELD)
            XposedHelpers.removeAdditionalInstanceField(view, SWIPE_STATE_FIELD)
            XposedHelpers.removeAdditionalInstanceField(view, PENDING_PREDRAW_LISTENER_FIELD)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
    }
    private fun runDynamicIslandEntrance(
        view: View,
        shell: View,
        position: StrongToastPosition
    ) {
        try {
            val profile = resolveDynamicIslandMotionProfile(view, shell, position)
                ?: return

            // Bind the prepared geometry to the event-owned swipe state so every
            // MotionEvent and the dismiss animation can reuse it without window/inset
            // lookups or new allocations.
            val state = XposedHelpers.getAdditionalInstanceField(
                view,
                SWIPE_STATE_FIELD
            ) as? SwipeGestureState
            state?.motionProfile = profile

            // The capsule is laid out at its resting position. The pivot sits on the near screen
            // edge so the uniform scale collapses toward that edge instead of the capsule centre.
            shell.pivotX = shell.width / 2f
            shell.pivotY = profile.pivotY
            shell.scaleX = profile.entranceStartScale
            shell.scaleY = profile.entranceStartScale
            shell.translationY = profile.entranceStartTranslationY
            shell.alpha = 0f

            shell.animate()
                .alpha(1f)
                .scaleX(profile.restingScale)
                .scaleY(profile.restingScale)
                .translationY(profile.restingTranslationY)
                .setDuration(profile.entranceDurationMs)
                .setInterpolator(dynamicIslandEntranceInterpolator)
                .withLayer()
                .start()

            // The ROM content children are animated by the ROM itself. The module owns the
            // capsule alpha only, and `withLayer()` composites the whole capsule as one surface
            // so ROM child alpha and capsule alpha cannot multiply into banding.
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            restoreStatusBarContents(view)
            view.animate().cancel()
            resetDynamicIslandHostTransform(view)
            shell.animate().cancel()
            resetDynamicIslandTransform(shell)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
    }
    private fun handleDynamicIslandTouch(
        strongToast: View,
        event: MotionEvent,
        position: StrongToastPosition
    ): Boolean {
        val state = XposedHelpers.getAdditionalInstanceField(
            strongToast,
            SWIPE_STATE_FIELD
        ) as? SwipeGestureState ?: return false
        val capsule = state.capsule

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isWithinExpandedCapsuleTouchBounds(event, capsule, state)) return false
                state.active = true
                state.downRawY = event.rawY
                state.moved = false
                state.startTranslationY = capsule.translationY
                strongToast.animate().cancel()
                resetDynamicIslandHostTransform(strongToast)
                capsule.animate().cancel()
                capsule.scaleX = 1f
                capsule.scaleY = 1f
            }
            MotionEvent.ACTION_MOVE -> {
                if (!state.active) return false
                val profile = state.motionProfile ?: return false
                val deltaY = event.rawY - state.downRawY
                val directionalDelta = if (position == StrongToastPosition.BOTTOM) {
                    deltaY.coerceAtLeast(0f)
                } else {
                    deltaY.coerceAtMost(0f)
                }
                state.moved = state.moved ||
                    kotlin.math.abs(deltaY) >= dpToPx(capsule, 4f)

                val travel = profile.edgeTravelPx.toFloat().coerceAtLeast(1f)
                val progress = (kotlin.math.abs(directionalDelta) / travel).coerceIn(0f, 1f)

                capsule.pivotX = capsule.width / 2f
                capsule.pivotY = profile.pivotY
                val dragScale = profile.scaleForProgress(progress)
                capsule.scaleX = dragScale
                capsule.scaleY = dragScale
                capsule.translationY = when (position) {
                    StrongToastPosition.BOTTOM ->
                        (state.startTranslationY + directionalDelta).coerceIn(0f, travel)
                    StrongToastPosition.TOP ->
                        (state.startTranslationY + directionalDelta).coerceIn(-travel, 0f)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!state.active) return false
                state.active = false
                val deltaY = event.rawY - state.downRawY
                if (shouldDismissDynamicIsland(
                        deltaY,
                        position,
                        resolveSwipeDismissThresholdPx(capsule)
                    )
                ) {
                    dismissStrongToast(strongToast)
                } else {
                    val profile = state.motionProfile
                    if (profile != null) {
                        restoreDynamicIslandAfterDrag(capsule, profile)
                    }
                    if (!state.moved) capsule.performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!state.active) return false
                state.active = false
                val profile = state.motionProfile
                if (profile != null) restoreDynamicIslandAfterDrag(capsule, profile)
            }
            else -> return state.active
        }
        return true
    }
    private fun installSwipeToDismiss(
        strongToast: View,
        capsule: View,
        position: StrongToastPosition
    ) {
        val listener = View.OnTouchListener { _, event ->
            ModuleHelper.guarded(false) {
                handleDynamicIslandTouch(strongToast, event, position)
            }
        }
        // The capsule's parent is the native full StrongToast row. Receiving ACTION_DOWN
        // there makes the expanded hit rectangle real; attaching only to the visual capsule
        // cannot receive touches that start just outside its narrow rounded bounds.
        (capsule.parent as? View)?.setOnTouchListener(listener)
        setSwipeListenerRecursively(capsule, listener)
    }
    private fun isWithinExpandedCapsuleTouchBounds(
        event: MotionEvent,
        capsule: View,
        state: SwipeGestureState
    ): Boolean {
        capsule.getLocationOnScreen(state.capsuleLocation)
        val horizontal = dpToPx(capsule, SWIPE_TOUCH_EXPANSION_HORIZONTAL_DP)
        val vertical = dpToPx(capsule, SWIPE_TOUCH_EXPANSION_VERTICAL_DP)
        val left = state.capsuleLocation[0] - horizontal
        val top = state.capsuleLocation[1] - vertical
        val right = state.capsuleLocation[0] + capsule.width + horizontal
        val bottom = state.capsuleLocation[1] + capsule.height + vertical
        return event.rawX >= left && event.rawX <= right &&
            event.rawY >= top && event.rawY <= bottom
    }

    private fun setSwipeListenerRecursively(
        view: View,
        listener: View.OnTouchListener?
    ) {
        view.setOnTouchListener(listener)
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            setSwipeListenerRecursively(group.getChildAt(index), listener)
        }
    }

    private fun dismissStrongToast(strongToast: View) {
        try {
            XposedHelpers.callMethod(strongToast, "realHideStrongToast")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandDismiss", t)
        }
    }

    /**
     * Builds the dismiss completion runnable that runs only after the shell exit
     * animation has fully finished. The ROM cleanup order is intentionally:
     *
     *     1. Visual shell exit completed.
     *     2. clearAll() once.
     *     3. onComplete() once.
     *
     * This prevents clearAll() from mutating content and requesting layout in the
     * middle of the shell transform, which was the root cause of the choppy exit.
     */
    internal fun buildDynamicIslandDismissComplete(strongToast: View): Runnable {
        return Runnable {
            ModuleHelper.guarded {
                restoreStatusBarContents(strongToast)
                try {
                    XposedHelpers.callMethod(strongToast, "clearAll")
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log("StrongToastDynamicIslandClear", t)
                }
                XposedHelpers.callMethod(strongToast, "onComplete")
            }
        }
    }

    private fun animateDynamicIslandDismiss(
        strongToast: View,
        shell: View,
        position: StrongToastPosition
    ) {
        XposedHelpers.setAdditionalInstanceField(strongToast, DISMISS_RUNNING_FIELD, true)
        setSwipeListenerRecursively(shell, null)
        (shell.parent as? View)?.setOnTouchListener(null)
        val complete = buildDynamicIslandDismissComplete(strongToast)
        try {
            XposedHelpers.setBooleanField(strongToast, "mCheckInOutStrongToasting", true)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandDismissState", t)
        }

        val swipeState = XposedHelpers.getAdditionalInstanceField(
            strongToast,
            SWIPE_STATE_FIELD
        ) as? SwipeGestureState
        val profile = swipeState?.motionProfile
            ?: resolveDynamicIslandMotionProfile(strongToast, shell, position)
                .also { fallback -> swipeState?.motionProfile = fallback }
        if (profile == null) {
            complete.run()
            return
        }

        shell.pivotX = shell.width / 2f
        shell.pivotY = profile.pivotY
        // The island retracts, it does not dissolve: the capsule stays fully opaque and the
        // uniform scale carries it to zero at the pivot on the near screen edge. Because there is
        // no geometry left when the animator ends, the ROM Window teardown cannot cut away a
        // still-visible capsule. A single ViewPropertyAnimator drives all three properties.
        shell.animate().setUpdateListener(null)
        shell.alpha = 1f
        shell.animate()
            .scaleX(profile.exitEndScale)
            .scaleY(profile.exitEndScale)
            .translationY(profile.exitEndTranslationY)
            .setDuration(profile.exitDurationMs)
            .setInterpolator(dynamicIslandExitInterpolator)
            .withEndAction(complete)
            .withLayer()
            .start()
    }
    private fun installControlClassHooks(lpparam: PackageReadyParam) {
        try {
            val controlClass = XposedHelpers.findClassIfExists(
                STRONG_TOAST_CONTROL_CLASS,
                lpparam.classLoader
            )
            val keyguardClass = XposedHelpers.findClassIfExists(
                KEYGUARD_STATE_CLASS,
                lpparam.classLoader
            )
            val controllerField = if (controlClass != null && keyguardClass != null) {
                XposedHelpers.findFieldIfExists(controlClass, "mKeyguardStateController")
            } else null
            val showingField = if (keyguardClass != null) {
                XposedHelpers.findFieldIfExists(keyguardClass, "mShowing")
            } else null

            val showHook = StrongToastControlHook(
                controllerField = controllerField,
                showingField = showingField,
                outerControlField = null,
                allowHide = true
            )
            ModuleHelper.hookAllMethods(
                STRONG_TOAST_CONTROL_CLASS,
                lpparam.classLoader,
                "showCustomStrongToast",
                showHook
            )

            val batteryCallbackClass = XposedHelpers.findClassIfExists(
                BATTERY_CALLBACK_CLASS,
                lpparam.classLoader
            )
            val outerControlField = if (batteryCallbackClass != null) {
                XposedHelpers.findFieldIfExists(batteryCallbackClass, "this\$0")
            } else null
            if (batteryCallbackClass != null && outerControlField != null) {
                val batteryHook = StrongToastControlHook(
                    controllerField = controllerField,
                    showingField = showingField,
                    outerControlField = outerControlField,
                    allowHide = false
                )
                ModuleHelper.hookAllMethods(
                    batteryCallbackClass,
                    "onRefreshBatteryInfo",
                    batteryHook
                )
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastControlClassHooks", t)
        }
    }

    private data class LockscreenGateToken(
        val keyguardState: Any,
        val wasShowing: Boolean
    )

    internal class StrongToastControlHook(
        private val controllerField: Field?,
        private val showingField: Field?,
        private val outerControlField: Field?,
        private val allowHide: Boolean
    ) : MethodHook() {
        @Throws(Throwable::class)
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val snapshot = currentSnapshot() ?: return chain.proceed()
            if (snapshot.mode == StrongToastPresentationMode.HIDE && allowHide) {
                return null
            }
            if (!snapshot.isDynamicIsland) {
                return chain.proceed()
            }

            val before = tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback(chain)
            val receiver = before.getThisObject() ?: return chain.proceed()
            val control = try {
                if (outerControlField != null) outerControlField.get(receiver) else receiver
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("StrongToastControlResolve", t)
                return chain.proceed()
            }

            val token = openLockscreenGate(control, controllerField, showingField)
            return try {
                chain.proceed()
            } finally {
                if (token != null) closeLockscreenGate(token, showingField)
            }
        }
    }

    private fun openLockscreenGate(
        control: Any?,
        controllerField: Field?,
        showingField: Field?
    ): LockscreenGateToken? {
        if (control == null || controllerField == null || showingField == null) return null
        val keyguardState = try {
            controllerField.get(control)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastLockscreenRead", t)
            null
        } ?: return null
        val wasShowing = try {
            showingField.getBoolean(keyguardState)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastLockscreenRead", t)
            return null
        }
        if (!wasShowing) return null
        try {
            showingField.setBoolean(keyguardState, false)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastLockscreenSet", t)
            return null
        }
        return LockscreenGateToken(keyguardState, wasShowing)
    }

    private fun closeLockscreenGate(
        token: LockscreenGateToken,
        showingField: Field?
    ) {
        if (showingField == null) return
        try {
            showingField.setBoolean(token.keyguardState, token.wasShowing)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastLockscreenRestore", t)
        }
    }

    /**
     * Locates the module-owned Dynamic Island shell for a live MIUIStrongToast.
     *
     * If the shell state has been removed or the shell is no longer attached,
     * returns null so the caller can re-bind or let the ROM handle cleanup.
     */
    private fun findDynamicIslandShell(root: View): View? {
        val state = XposedHelpers.getAdditionalInstanceField(
            root,
            SHELL_STATE_FIELD
        ) as? DynamicIslandShellState ?: return null
        if (!state.shell.isAttachedToWindow) return null
        if (state.content.parent !== state.shell) return null
        if (state.shell.parent !== state.originalParent) return null
        return state.shell
    }

    /**
     * Wraps the ROM message container in a module-owned FrameLayout shell.
     *
     * The ROM content is reparented as a whole into the shell, the shell takes
     * the original container's outer layout params, and the content fills the
     * shell. This is idempotent: calling it again for the same content returns
     * the existing shell.
     *
     * All mutations are wrapped in a single transaction: if any non-fatal step
     * fails, the original parent/index/background is rolled back and no state
     * is published.
     */
    internal fun bindDynamicIslandShell(
        root: View,
        content: View,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ): FrameLayout? {
        val existing = XposedHelpers.getAdditionalInstanceField(
            root,
            SHELL_STATE_FIELD
        ) as? DynamicIslandShellState
        if (existing != null) {
            if (existing.content === content &&
                existing.shell.isAttachedToWindow &&
                existing.content.parent === existing.shell &&
                existing.shell.parent === existing.originalParent
            ) {
                return existing.shell
            }
            // A state exists but is stale or belongs to a different content
            // instance. Tear it down before creating a new one.
            restoreDynamicIslandShell(existing)
            XposedHelpers.removeAdditionalInstanceField(root, SHELL_STATE_FIELD)
        }

        val parent = content.parent as? ViewGroup ?: return null
        val originalIndex = parent.indexOfChild(content)
        if (originalIndex < 0) return null
        val originalLp = content.layoutParams
        val originalBackground = content.background
        val originalContentClipToOutline = content.clipToOutline
        val originalParentPaddingLeft = parent.paddingLeft
        val originalParentPaddingTop = parent.paddingTop
        val originalParentPaddingRight = parent.paddingRight
        val originalParentPaddingBottom = parent.paddingBottom
        val originalParentGravity = (parent as? LinearLayout)?.gravity ?: 0

        val shell = DynamicIslandCapsuleView(root.context)

        val shellLp = when (originalLp) {
            is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(
                ViewGroup.MarginLayoutParams(originalLp)
            ).apply {
                gravity = originalLp.gravity
                weight = originalLp.weight
            }
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(originalLp)
            else -> ViewGroup.LayoutParams(
                originalLp?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                originalLp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        shell.layoutParams = shellLp

        try {
            parent.removeView(content)
            parent.addView(shell, originalIndex)
            content.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            content.background = null
            content.clipToOutline = false
            shell.addView(content)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            try {
                if (content.parent === shell) {
                    shell.removeView(content)
                }
                if (content.parent == null) {
                    content.layoutParams = originalLp
                    content.background = originalBackground
                    content.clipToOutline = originalContentClipToOutline
                    parent.addView(content, originalIndex)
                }
                if (shell.parent === parent) {
                    parent.removeView(shell)
                }
            } catch (rollback: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(rollback)
                XposedHelpers.log("StrongToastDynamicIslandBindRollback", rollback)
            }
            return null
        }

        XposedHelpers.setAdditionalInstanceField(
            root,
            SHELL_STATE_FIELD,
            DynamicIslandShellState(
                shell = shell,
                content = content,
                originalParent = parent,
                originalIndex = originalIndex,
                originalContentLayoutParams = originalLp,
                originalContentBackground = originalBackground,
                originalContentClipToOutline = originalContentClipToOutline,
                originalParentPaddingLeft = originalParentPaddingLeft,
                originalParentPaddingTop = originalParentPaddingTop,
                originalParentPaddingRight = originalParentPaddingRight,
                originalParentPaddingBottom = originalParentPaddingBottom,
                originalParentGravity = originalParentGravity,
                bottomView = null,
                bottomViewOriginalVisibility = View.VISIBLE,
                romRoundRect = null,
                romRoundRectOriginalAlpha = 1f,
                romRoundRectOriginalVisibility = View.VISIBLE
            )
        )
        return shell
    }

    /**
     * Reverses [bindDynamicIslandShell]. The ROM content is returned to its
     * original parent, the shell is removed and the original background / layout
     * params / parent padding / gravity / ancestor clip flags are restored.
     * Safe to call on an already-restored or partially torn-down state.
     */
    internal fun restoreDynamicIslandShell(state: DynamicIslandShellState?) {
        if (state == null) return
        val shell = state.shell
        val content = state.content
        val parent = state.originalParent

        try {
            shell.animate().cancel()
            resetDynamicIslandTransform(shell)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandShellTeardown", t)
        }
        try {
            content.animate().cancel()
            resetDynamicIslandTransform(content)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandContentTeardown", t)
        }

        if (content.parent === shell) {
            try { shell.removeView(content) } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
        }
        try {
            content.background = state.originalContentBackground
            content.layoutParams = state.originalContentLayoutParams
            content.clipToOutline = state.originalContentClipToOutline
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandContentRestore", t)
        }

        when {
            shell.parent === parent -> {
                try { parent.removeView(shell) } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                }
                val index = state.originalIndex.coerceIn(0, parent.childCount)
                parent.addView(content, index)
            }
            shell.parent is ViewGroup -> {
                try { (shell.parent as ViewGroup).removeView(shell) } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                }
                if (content.parent == null) {
                    val index = state.originalIndex.coerceIn(0, parent.childCount)
                    parent.addView(content, index)
                }
            }
            content.parent == null -> {
                val index = state.originalIndex.coerceIn(0, parent.childCount)
                parent.addView(content, index)
            }
        }

        try {
            parent.setPadding(
                state.originalParentPaddingLeft,
                state.originalParentPaddingTop,
                state.originalParentPaddingRight,
                state.originalParentPaddingBottom
            )
            (parent as? LinearLayout)?.gravity = state.originalParentGravity
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandParentRestore", t)
        }

        try {
            state.bottomView?.let { bottom ->
                if (bottom.visibility != state.bottomViewOriginalVisibility) {
                    bottom.visibility = state.bottomViewOriginalVisibility
                }
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandBottomRestore", t)
        }

        restoreRomRoundRect(state)
    }

    /**
     * Hides the ROM's second rounded shape for the duration of a Dynamic Island event.
     *
     * `round_rect` is `strong_toast_width_window` (337dp) wide inside a `strong_toast_width`
     * (320dp) capsule, so as soon as the ROM makes it visible its own corners land outside the
     * module capsule and the island reads as clipped. Only its alpha is taken: the View keeps its
     * layout footprint, its ConstraintLayout relationships and its ROM-driven state, so
     * `setRectProgress` and any later `setVisibility(VISIBLE)` still behave exactly as the ROM
     * expects - they simply do not paint a second pill. Its parent `fl_pad_toast_bg` is left
     * alone because it also hosts real content (`iv_pad_toast_bg`, the VideoView).
     */
    private fun suppressRomRoundRect(roundRect: View?) {
        roundRect?.alpha = 0f
    }

    private fun restoreRomRoundRect(state: DynamicIslandShellState) {
        val roundRect = state.romRoundRect ?: return
        try {
            roundRect.alpha = state.romRoundRectOriginalAlpha
            roundRect.visibility = state.romRoundRectOriginalVisibility
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandRoundRectRestore", t)
        }
    }

    internal fun prepareDynamicIslandCapsule(
        root: View,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ): View? {
        val content = findDynamicIslandCapsule(root) ?: return null

        val visualWidthPx = strongToastDimensionPx(root, "strong_toast_width")
        val visualHeightPx = strongToastVisualHeightPx(root)
        if (visualWidthPx <= 0 || visualHeightPx <= 0) {
            return null
        }

        val shell = bindDynamicIslandShell(root, content, position, bottomOffsetDp)
            ?: return null

        val state = XposedHelpers.getAdditionalInstanceField(
            root,
            SHELL_STATE_FIELD
        ) as? DynamicIslandShellState ?: return null

        return try {
            val envelope = resolveDynamicIslandWindowEnvelope(root, position, bottomOffsetDp)
                ?: return null

            val horizontalMarginPx = dpToPx(root, 16f)
            val availableWidthPx = root.resources.displayMetrics.widthPixels - horizontalMarginPx * 2
            val layoutParams = shell.layoutParams
                ?: throw IllegalStateException("Shell has no layout params")
            layoutParams.width = minOf(visualWidthPx, availableWidthPx)
            layoutParams.height = visualHeightPx
            if (layoutParams is ViewGroup.MarginLayoutParams) {
                layoutParams.topMargin = envelope.shellTopMarginPx
                layoutParams.bottomMargin = envelope.shellBottomMarginPx
            }
            if (layoutParams is LinearLayout.LayoutParams) {
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL
            }
            shell.layoutParams = layoutParams
            // The capsule paints its own pill and clips its children to that same path (see
            // DynamicIslandCapsuleView), so no ancestor clip flags are touched: everything the
            // island draws is inside the capsule bounds by construction.

            // Capture all remaining baselines before any mutation. The updated
            // state is published immediately so the catch/restore path can roll
            // back every module-owned change.
            val bottomView = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)
            val bottomViewOriginalVisibility = bottomView?.visibility ?: View.VISIBLE
            val roundRect = findViewBySystemUiId(root, ROUND_RECT_ID)

            val updatedState = state.copy(
                bottomView = bottomView,
                bottomViewOriginalVisibility = bottomViewOriginalVisibility,
                romRoundRect = roundRect,
                romRoundRectOriginalAlpha = roundRect?.alpha ?: 1f,
                romRoundRectOriginalVisibility = roundRect?.visibility ?: View.VISIBLE
            )
            XposedHelpers.setAdditionalInstanceField(root, SHELL_STATE_FIELD, updatedState)

            val parent = state.originalParent
            (parent as? LinearLayout)?.apply {
                gravity = envelope.parentGravity
                setPadding(
                    state.originalParentPaddingLeft,
                    envelope.parentPaddingTopPx,
                    state.originalParentPaddingRight,
                    envelope.parentPaddingBottomPx
                )
            }

            suppressRomRoundRect(roundRect)
            bottomView?.visibility = View.GONE

            shell
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastPrepareShell", t)
            val publishedState = XposedHelpers.getAdditionalInstanceField(
                root,
                SHELL_STATE_FIELD
            ) as? DynamicIslandShellState
            restoreDynamicIslandShell(publishedState ?: state)
            XposedHelpers.removeAdditionalInstanceField(root, SHELL_STATE_FIELD)
            null
        }
    }

    private fun restoreDynamicIslandAfterDrag(
        view: View,
        profile: DynamicIslandMotionProfile
    ) {
        view.animate().setUpdateListener(null)
        view.pivotX = view.width / 2f
        view.pivotY = profile.pivotY
        view.animate()
            .alpha(1f)
            .scaleX(profile.restingScale)
            .scaleY(profile.restingScale)
            .translationY(profile.restingTranslationY)
            .setDuration(DynamicIslandMotionProfile.DRAG_RELEASE_DURATION_MS)
            .setInterpolator(dynamicIslandEntranceInterpolator)
            .withLayer()
            .start()
    }

    private fun resolveDynamicIslandWindowEnvelope(
        root: View,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ): DynamicIslandWindowEnvelope? {
        val visualHeightPx = strongToastVisualHeightPx(root)
        if (visualHeightPx <= 0) return null
        val maxTravelPx = dpToPx(root, DynamicIslandWindowEnvelope.MAX_EDGE_TRAVEL_DP)
        return when (position) {
            StrongToastPosition.TOP -> DynamicIslandWindowEnvelope.forTop(
                visualHeightPx,
                dpToPx(root, CAPSULE_TOP_MARGIN_DP),
                dpToPx(root, CAPSULE_BOTTOM_CLEARANCE_DP),
                maxTravelPx
            )
            StrongToastPosition.BOTTOM -> DynamicIslandWindowEnvelope.forBottom(
                visualHeightPx,
                resolveBottomTopClearancePx(root, visualHeightPx),
                resolveBottomPaddingForCapsule(
                    root,
                    visualHeightPx,
                    bottomOffsetDp
                ),
                maxTravelPx
            )
        }
    }

    private fun resolveDynamicIslandMotionProfile(
        root: View,
        capsule: View,
        position: StrongToastPosition
    ): DynamicIslandMotionProfile? {
        val visualHeightPx = strongToastVisualHeightPx(root)
        if (visualHeightPx <= 0) return null
        val maxTravelPx = dpToPx(capsule, DynamicIslandWindowEnvelope.MAX_EDGE_TRAVEL_DP)
        return when (position) {
            StrongToastPosition.TOP -> DynamicIslandMotionProfile.forTop(
                visualHeightPx,
                dpToPx(capsule, CAPSULE_TOP_MARGIN_DP),
                dpToPx(capsule, CAPSULE_BOTTOM_CLEARANCE_DP),
                maxTravelPx
            )
            StrongToastPosition.BOTTOM -> DynamicIslandMotionProfile.forBottom(
                visualHeightPx,
                resolveBottomTopClearancePx(root, visualHeightPx),
                resolveBottomPaddingForCapsule(
                    root,
                    visualHeightPx,
                    resolveSnapshot(root)?.bottomOffsetDp ?: 0
                ),
                maxTravelPx
            )
        }
    }
    /**
     * Resets any module-imposed content alpha/animation state. Only used in
     * MATCH_STATUS_BAR_HEIGHT cleanup; the Dynamic Island path no longer owns
     * ROM content child alpha because the module shell is the transform target.
     */
    private fun resetDynamicIslandContent(capsule: View) {
        val group = capsule as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            child.animate().cancel()
            child.alpha = 1f
        }
    }

    private fun findDynamicIslandCapsule(root: View): View? {
        return findViewBySystemUiId(root, MESSAGE_CONTAINER_ID)
    }

    private fun findViewBySystemUiId(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", SYSTEM_UI_PACKAGE)
        return if (id != 0) root.findViewById(id) else null
    }

    private fun dpToPx(view: View?, dp: Float): Int {
        val density = view?.resources?.displayMetrics?.density ?: return 0
        return (dp * density + 0.5f).toInt()
    }

    private fun resolveBottomEdgeGapPx(view: View?, visualHeightPx: Int): Int {
        val minPx = dpToPx(view, BOTTOM_EDGE_GAP_MIN_DP)
        val maxPx = dpToPx(view, BOTTOM_EDGE_GAP_MAX_DP).coerceAtLeast(minPx)
        return (visualHeightPx / 16).coerceIn(minPx, maxPx)
    }

    private fun resolveBottomTopClearancePx(view: View?, visualHeightPx: Int): Int {
        val minimum = dpToPx(view, 4f)
        val maximum = dpToPx(view, CAPSULE_BOTTOM_CLEARANCE_DP).coerceAtLeast(minimum)
        return (visualHeightPx / 4).coerceIn(minimum, maximum)
    }

    private fun resolveBottomPaddingForCapsule(
        root: View,
        visualHeightPx: Int,
        bottomOffsetDp: Int
    ): Int = resolveBottomPaddingPx(
        currentBottomSafeInsetPx(root),
        resolveBottomEdgeGapPx(root, visualHeightPx),
        dpToPx(root, bottomOffsetDp.toFloat())
    )

    private fun resolveSwipeDismissThresholdPx(view: View): Float {
        val capsuleThreshold = view.height.coerceAtLeast(strongToastVisualHeightPx(view)) / 5
        val touchThreshold = ViewConfiguration.get(view.context).scaledTouchSlop * 2
        return maxOf(capsuleThreshold, touchThreshold)
            .coerceAtMost(dpToPx(view, SWIPE_DISMISS_THRESHOLD_MAX_DP))
            .coerceAtLeast(1)
            .toFloat()
    }

    private fun resetDynamicIslandTransform(view: View) {
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationY = 0f
    }

    private fun resetDynamicIslandHostTransform(view: View) {
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationY = 0f
    }

    internal fun captureMatchModeBaseline(
        capsule: View,
        parent: ViewGroup?,
        bottomView: View?
    ): MatchModeBaseline? {
        val lp = capsule.layoutParams ?: return null
        val parentLp = parent?.layoutParams
        return MatchModeBaseline(
            width = lp.width,
            height = lp.height,
            topMargin = if (lp is ViewGroup.MarginLayoutParams) lp.topMargin else 0,
            bottomMargin = if (lp is ViewGroup.MarginLayoutParams) lp.bottomMargin else 0,
            layoutGravity = if (lp is LinearLayout.LayoutParams) lp.gravity else 0,
            capsuleGravity = (capsule as? LinearLayout)?.gravity ?: 0,
            parentPaddingLeft = parent?.paddingLeft ?: 0,
            parentPaddingTop = parent?.paddingTop ?: 0,
            parentPaddingRight = parent?.paddingRight ?: 0,
            parentPaddingBottom = parent?.paddingBottom ?: 0,
            parentGravity = (parent as? LinearLayout)?.gravity ?: 0,
            parentWidth = parentLp?.width ?: 0,
            parentHeight = parentLp?.height ?: 0,
            parentTopMargin = if (parentLp is ViewGroup.MarginLayoutParams) parentLp.topMargin else 0,
            parentBottomMargin = if (parentLp is ViewGroup.MarginLayoutParams) parentLp.bottomMargin else 0,
            parentLayoutGravity = if (parentLp is LinearLayout.LayoutParams) parentLp.gravity else 0,
            bottomViewVisibility = bottomView?.visibility ?: View.VISIBLE
        )
    }

    private fun applyMatchModeMutations(
        root: View,
        capsule: View,
        parent: ViewGroup?,
        bottomView: View?,
        targetContentHeightPx: Int,
        hideChin: Boolean
    ) {
        resetDynamicIslandHostTransform(root)
        resetDynamicIslandTransform(capsule)
        resetDynamicIslandContent(capsule)
        setSwipeListenerRecursively(capsule, null)
        (capsule.parent as? View)?.setOnTouchListener(null)
        removeExpandedWindowTouchRegion(root)

        // Resize the message row itself. The ROM declares it as MATCH_PARENT inside a
        // WRAP_CONTENT column, so pinning the column height instead pushed the
        // strong_toast_bottom_view chin - the piece that draws the concave corners - out of the
        // Window and left the forehead ending in a hard square edge.
        val capsuleLp = capsule.layoutParams
        if (capsuleLp != null) {
            capsuleLp.height = targetContentHeightPx
            capsule.layoutParams = capsuleLp
        }

        // The column keeps wrapping so the chin stays laid out directly under the message row and
        // the two together are exactly one status bar tall. A chin that cannot fit is dropped
        // rather than squeezing the message row down to nothing.
        bottomView?.visibility = if (hideChin) View.GONE else View.VISIBLE
        parent?.setPadding(0, 0, 0, 0)
        (parent as? LinearLayout)?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    internal fun applyMatchModeBaselineToViews(
        root: View,
        capsule: View,
        parent: ViewGroup?,
        bottomView: View?,
        targetContentHeightPx: Int,
        hideChin: Boolean
    ): Boolean {
        val existing = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        if (existing == null) {
            val baseline = captureMatchModeBaseline(capsule, parent, bottomView) ?: return false
            XposedHelpers.setAdditionalInstanceField(root, MATCH_BASELINE_FIELD, baseline)
        }

        applyMatchModeMutations(
            root,
            capsule,
            parent,
            bottomView,
            targetContentHeightPx,
            hideChin
        )
        return true
    }

    internal fun applyMatchStatusBarHeight(
        root: View,
        targetContentHeightPx: Int,
        hideChin: Boolean
    ): Boolean {
        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID) ?: return false
        val parent = capsule.parent as? ViewGroup
        val bottomView = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)
        return applyMatchModeBaselineToViews(
            root,
            capsule,
            parent,
            bottomView,
            targetContentHeightPx,
            hideChin
        )
    }

    internal fun restoreMatchModeBaseline(
        root: View,
        capsule: View,
        parent: ViewGroup?,
        bottomView: View?,
        baseline: MatchModeBaseline
    ) {
        resetDynamicIslandContent(capsule)
        resetDynamicIslandTransform(capsule)

        val lp = capsule.layoutParams
        if (lp != null) {
            lp.height = baseline.height
            lp.width = baseline.width
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.topMargin = baseline.topMargin
                lp.bottomMargin = baseline.bottomMargin
            }
            if (lp is LinearLayout.LayoutParams) {
                lp.gravity = baseline.layoutGravity
            }
            capsule.layoutParams = lp
        }
        (capsule as? LinearLayout)?.gravity = baseline.capsuleGravity

        val parentLp = parent?.layoutParams
        if (parentLp != null) {
            parentLp.height = baseline.parentHeight
            parentLp.width = baseline.parentWidth
            if (parentLp is ViewGroup.MarginLayoutParams) {
                parentLp.topMargin = baseline.parentTopMargin
                parentLp.bottomMargin = baseline.parentBottomMargin
            }
            if (parentLp is LinearLayout.LayoutParams) {
                parentLp.gravity = baseline.parentLayoutGravity
            }
            parent.layoutParams = parentLp
        }

        parent?.setPadding(
            baseline.parentPaddingLeft,
            baseline.parentPaddingTop,
            baseline.parentPaddingRight,
            baseline.parentPaddingBottom
        )
        (parent as? LinearLayout)?.gravity = baseline.parentGravity

        bottomView?.visibility = baseline.bottomViewVisibility

        setSwipeListenerRecursively(capsule, null)
        (capsule.parent as? View)?.setOnTouchListener(null)
        removeExpandedWindowTouchRegion(root)
        resetDynamicIslandHostTransform(root)
    }

    internal fun resetMatchModeBaselineToViews(
        root: View,
        capsule: View?,
        parent: ViewGroup?,
        bottomView: View?,
        baseline: MatchModeBaseline
    ) {
        try {
            if (capsule != null) {
                restoreMatchModeBaseline(root, capsule, parent, bottomView, baseline)
            }
        } finally {
            XposedHelpers.removeAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        }
    }

    internal fun resetMatchModeCapsule(root: View) {
        val baseline = XposedHelpers.getAdditionalInstanceField(
            root,
            MATCH_BASELINE_FIELD
        ) as? MatchModeBaseline ?: return

        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID)
            ?: findDynamicIslandCapsule(root)

        val parent = capsule?.parent as? ViewGroup
        val bottomView = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)
        resetMatchModeBaselineToViews(root, capsule, parent, bottomView, baseline)
    }

    private fun installStatusBarContentsCapture(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods(
            STATUS_BAR_VIEW_CLASS,
            lpparam.classLoader,
            "onAttachedToWindow",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val statusBar = callback.getThisObject() as? View ?: return
                    val id = statusBar.resources.getIdentifier(
                        STATUS_BAR_CONTENTS_ID,
                        "id",
                        SYSTEM_UI_PACKAGE
                    )
                    if (id != 0) {
                        statusBar.findViewById<View>(id)?.let { contents ->
                            statusBarContentsRef = WeakReference(contents)
                        }
                    }
                }
            }
        )
        ModuleHelper.hookAllMethods(
            STATUS_BAR_VIEW_CLASS,
            lpparam.classLoader,
            "onDetachedFromWindow",
            object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    val statusBar = callback.getThisObject() as? View ?: return
                    val contents = statusBarContentsRef.get() ?: return
                    if (contents.parent === statusBar || !contents.isAttachedToWindow) {
                        statusBarContentsRef.clear()
                    }
                }
            }
        )
    }

    private fun hideStatusBarContents(owner: View) {
        val contents = statusBarContentsRef.get() ?: return
        if (!contents.isAttachedToWindow) return
        val currentOwner = statusBarHiddenOwnerRef.get()
        if (currentOwner === owner) return
        if (currentOwner != null && currentOwner.isAttachedToWindow) return
        statusBarContentsOriginalAlpha = contents.alpha
        statusBarHiddenOwnerRef = WeakReference(owner)
        contents.alpha = 0f
    }

    private fun restoreStatusBarContents(owner: View) {
        if (statusBarHiddenOwnerRef.get() !== owner) return
        val contents = statusBarContentsRef.get()
        if (contents != null && contents.alpha == 0f) {
            contents.alpha = statusBarContentsOriginalAlpha
        }
        statusBarHiddenOwnerRef.clear()
    }

    /**
     * Message-row height for [StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT].
     *
     * The visible forehead is the message row plus the ROM chin, so matching the status bar means
     * `content + chin == statusBar`. When the chin is absent or too tall to leave a usable row the
     * chin is dropped instead (see [matchModeHidesChin]) and the row takes the full matched
     * height; a zero or negative row is never produced.
     */
    @JvmStatic
    internal fun resolveMatchContentHeightPx(
        statusBarHeightPx: Int,
        chinHeightPx: Int
    ): Int {
        val statusBar = statusBarHeightPx.coerceAtLeast(0)
        if (chinHeightPx <= 0 || matchModeHidesChin(statusBar, chinHeightPx)) return statusBar
        return statusBar - chinHeightPx
    }

    /**
     * True when the ROM chin cannot share the matched height with a usable message row.
     *
     * `strong_toast_down` is 38dp tall while a phone status bar is around 44dp, so on most devices
     * keeping the chin would leave the ROM content a few pixels. The chin is kept only while it
     * takes at most half of the matched height; otherwise it is hidden and the module owns the
     * whole matched forehead.
     */
    @JvmStatic
    internal fun matchModeHidesChin(
        statusBarHeightPx: Int,
        chinHeightPx: Int
    ): Boolean {
        val statusBar = statusBarHeightPx.coerceAtLeast(0)
        if (statusBar <= 0) return false
        return chinHeightPx > statusBar / 2
    }

    /**
     * Window height for [StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT].
     *
     * The Window is exactly the status bar: the total visible forehead is what the user asked to
     * match, so the Window must not add the chin on top of it.
     */
    @JvmStatic
    internal fun resolveMatchWindowHeightPx(
        statusBarHeightPx: Int
    ): Int = statusBarHeightPx.coerceAtLeast(0)

    /**
     * Natural height of the ROM's `strong_toast_bottom_view` chin.
     *
     * `getWindowParam` runs before the first measure pass, so the drawable's intrinsic height is
     * the authoritative value; a measured height is preferred when one is already available.
     */
    private fun foreheadChinHeightPx(root: View): Int {
        val bottomView = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID) ?: return 0
        if (bottomView.visibility == View.GONE) return 0
        val measured = bottomView.measuredHeight
        if (measured > 0) return measured
        return (bottomView.background?.intrinsicHeight ?: 0).coerceAtLeast(0)
    }

    /**
     * Window height for a top Dynamic Island: margin, capsule, clearance. The corner radius lives
     * inside the capsule rectangle, so it adds nothing here.
     */
    @JvmStatic
    internal fun resolveDynamicIslandWindowHeightPx(
        visualHeightPx: Int,
        topMarginPx: Int,
        bottomClearancePx: Int
    ): Int {
        val top = topMarginPx.coerceAtLeast(0)
        val bottom = bottomClearancePx.coerceAtLeast(0)
        if (visualHeightPx <= 0) return top + bottom
        return visualHeightPx + top + bottom
    }

    @JvmStatic
    internal fun resolveBottomDynamicIslandWindowHeightPx(
        visualHeightPx: Int,
        topClearancePx: Int,
        bottomPaddingPx: Int
    ): Int {
        if (visualHeightPx <= 0) return bottomPaddingPx.coerceAtLeast(0)
        return visualHeightPx + topClearancePx.coerceAtLeast(0) +
            bottomPaddingPx.coerceAtLeast(0)
    }

    @JvmStatic
    internal fun resolveBottomPaddingPx(
        safeInsetPx: Int,
        adaptiveEdgeGapPx: Int,
        userOffsetPx: Int
    ): Int = (safeInsetPx.coerceAtLeast(0) + adaptiveEdgeGapPx.coerceAtLeast(0) +
        userOffsetPx).coerceAtLeast(0)

    internal const val MIN_BOTTOM_OFFSET_DP = -40
    internal const val MAX_BOTTOM_OFFSET_DP = 80

    @JvmStatic
    internal fun shouldDismissDynamicIsland(
        deltaY: Float,
        position: StrongToastPosition,
        thresholdPx: Float
    ): Boolean {
        if (thresholdPx <= 0f) return false
        return if (position == StrongToastPosition.BOTTOM) {
            deltaY >= thresholdPx
        } else {
            deltaY <= -thresholdPx
        }
    }

}
