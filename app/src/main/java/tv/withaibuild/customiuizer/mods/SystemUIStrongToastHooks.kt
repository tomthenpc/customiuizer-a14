package tv.withaibuild.customiuizer.mods

import android.graphics.Color
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
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
import tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandMotionProfile
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * HyperOS 1 StrongToast presentation (the top black capsule used by charging and system modes).
 *
 * MATCH_STATUS_BAR_HEIGHT sets the StrongToast Window and the actual message capsule to the
 * current status-bar inset height. The ROM's original content width is preserved, child content is
 * centered vertically, and the forehead bottom sibling is hidden so the visible capsule stays inside
 * the Window surface. A valid status-bar inset of 0 falls back to the ROM window height.
 * Hiding stops the request at MIUIStrongToastControl before a View or animation is created. No
 * Activity, View, controller or listener is retained.
 * Dynamic Island mode reuses that same event-scoped ROM View and cleanup path. The ROM's full-width
 * forehead bottom is removed and a module-owned FrameLayout shell is inserted around the ROM
 * message container. The shell owns the black rounded capsule background and the entrance/exit
 * transforms; the ROM content stays the authoritative content owner. No overlay service, listener
 * or polling is introduced.
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
    private const val CAPSULE_TOP_MARGIN_DP = 6f
    private const val CAPSULE_BOTTOM_SAFETY_MARGIN_DP = 16f
    private const val BOTTOM_EDGE_GAP_MIN_DP = 2f
    private const val BOTTOM_EDGE_GAP_MAX_DP = 6f
    private const val SWIPE_DISMISS_THRESHOLD_MAX_DP = 28f
    private const val SWIPE_TOUCH_EXPANSION_HORIZONTAL_DP = 24f
    private const val SWIPE_TOUCH_EXPANSION_VERTICAL_DP = 16f
    private const val SWIPE_STATE_FIELD = "customiuizer_strong_toast_swipe"
    private const val BOTTOM_BASE_PADDING_FIELD = "customiuizer_strong_toast_bottom_base_padding"
    private const val DISMISS_RUNNING_FIELD = "customiuizer_strong_toast_dismiss_running"
    private const val SHELL_STATE_FIELD = "customiuizer_dynamic_island_shell_state"
    private const val TOUCH_REGION_LISTENER_FIELD =
        "customiuizer_strong_toast_touch_region_listener"
    private const val TOUCHABLE_INSETS_REGION = 3
    private const val STATUS_BAR_CONTENTS_ID = "status_bar_contents"
    private const val STATUS_BAR_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"
    private const val RUNTIME_SNAPSHOT_FIELD = "customiuizer_strong_toast_runtime_snapshot"
    private const val MATCH_BASELINE_FIELD = "customiuizer_match_mode_baseline"
    // Mechanism reference: Ajaxy/telegram-tt, tag air_v2.11.5, commit d915b1b9,
    // src/styles/_variables.scss and src/util/animation.ts (GPL-3.0). Only its bounded
    // cubic-bezier curve and latest-animation-wins ownership are translated to Android here;
    // no Telegram source or framework is copied. Avoiding geometric overshoot is mandatory
    // because SurfaceFlinger clips StrongToast outside the ROM-owned Window surface.
    private val boundedDynamicIslandInterpolator = PathInterpolator(0.25f, 1f, 0.5f, 1f)
    private val dynamicIslandExitInterpolator = boundedDynamicIslandInterpolator
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
        val bottomViewVisibility: Int
    )

    /**
     * Per-MIUIStrongToast Dynamic Island ownership state.
     *
     * A module-owned [FrameLayout] shell is inserted around the ROM
     * [mDarkToastContent] / [cl_strong_toast_msg]. The shell owns the black
     * rounded capsule background and all shell-level transforms; the ROM content
     * stays untouched and is reparented as a whole.
     */
    internal data class DynamicIslandShellState(
        val shell: FrameLayout,
        val content: View,
        val originalParent: ViewGroup,
        val originalIndex: Int,
        val originalContentLayoutParams: ViewGroup.LayoutParams?,
        val originalContentBackground: Drawable?
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
                                val statusBarInsetPx = currentStatusBarInsetPx(strongToast)
                                val visualHeightPx = strongToastVisualHeightPx(strongToast)
                                val originalWindowHeightPx = layoutParams.height
                                val targetHeightPx = resolveMatchedStatusBarHeightPx(
                                    statusBarInsetPx,
                                    originalWindowHeightPx
                                )
                                val targetContentHeightPx = resolveMatchContainerHeightPx(
                                    statusBarInsetPx,
                                    visualHeightPx
                                )
                                val prepared = applyMatchStatusBarHeight(
                                    strongToast,
                                    targetContentHeightPx
                                )
                                if (prepared) {
                                    layoutParams.height = targetHeightPx
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
                                        resolveBottomTopSafetyPx(strongToast, visualHeightPx),
                                        bottomPaddingPx
                                    )
                                } else {
                                    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                    layoutParams.height = resolveDynamicIslandWindowHeightPx(
                                        currentStatusBarInsetPx(strongToast),
                                        visualHeightPx,
                                        dpToPx(strongToast, CAPSULE_TOP_MARGIN_DP),
                                        dpToPx(strongToast, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
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

    private fun startDynamicIslandEntrance(
        view: View,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ) {
        try {
            val shell = prepareDynamicIslandCapsule(view, position, bottomOffsetDp) ?: return
            if (position == StrongToastPosition.TOP) hideStatusBarContents(view)
            XposedHelpers.setAdditionalInstanceField(view, SWIPE_STATE_FIELD, SwipeGestureState(shell))
            installSwipeToDismiss(view, shell, position)
            view.animate().cancel()
            resetDynamicIslandHostTransform(view)
            shell.animate().cancel()
            resetDynamicIslandTransform(shell)
            // onAttachedToWindow precedes the first layout pass. Defer exactly once so the
            // shell has real width/height and the profile can set a safe pivot/translation.
            shell.post {
                ModuleHelper.guarded {
                    if (!shell.isAttachedToWindow) {
                        resetDynamicIslandHostTransform(view)
                        resetDynamicIslandTransform(shell)
                        return@guarded
                    }
                    runDynamicIslandEntrance(view, shell, position)
                }
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            restoreStatusBarContents(view)
            resetDynamicIslandHostTransform(view)
            val state = XposedHelpers.getAdditionalInstanceField(view, SHELL_STATE_FIELD) as? DynamicIslandShellState
            state?.shell?.let { resetDynamicIslandTransform(it) }
            restoreDynamicIslandShell(state)
            XposedHelpers.removeAdditionalInstanceField(view, SHELL_STATE_FIELD)
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

            // The shell is laid out at its resting position. Set the pivot to the
            // screen edge so vertical scaling grows from that edge, not the center.
            shell.pivotX = shell.width / 2f
            shell.pivotY = profile.pivotY
            shell.scaleX = 1f
            shell.scaleY = profile.entranceScaleY
            shell.translationY = profile.entranceTranslationY
            shell.alpha = 1f

            shell.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(profile.restingTranslationY)
                .setDuration(profile.entranceDurationMs)
                .setInterpolator(boundedDynamicIslandInterpolator)
                .start()

            // The ROM content children are animated by the ROM itself. Module does
            // not own their alpha to avoid double ownership with ROM Folme.
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

                val travel = profile.entranceTravelPx.toFloat().coerceAtLeast(1f)
                val progress = (kotlin.math.abs(directionalDelta) / travel).coerceIn(0f, 1f)

                capsule.pivotY = profile.pivotY
                capsule.scaleY = 1f - (1f - profile.entranceScaleY) * progress
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

        shell.pivotY = profile.pivotY
        shell.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(profile.exitScaleY)
            .translationY(profile.exitTranslationY)
            .setDuration(profile.exitDurationMs)
            .setInterpolator(dynamicIslandExitInterpolator)
            .withEndAction(complete)
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

        val shell = FrameLayout(root.context).apply {
            clipChildren = false
            clipToPadding = false
            clipToOutline = false
        }

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

        parent.removeView(content)
        parent.addView(shell, originalIndex)
        content.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        content.background = null
        content.clipToOutline = false
        shell.addView(content)

        XposedHelpers.setAdditionalInstanceField(
            root,
            SHELL_STATE_FIELD,
            DynamicIslandShellState(
                shell = shell,
                content = content,
                originalParent = parent,
                originalIndex = originalIndex,
                originalContentLayoutParams = originalLp,
                originalContentBackground = originalBackground
            )
        )
        return shell
    }

    /**
     * Reverses [bindDynamicIslandShell]. The ROM content is returned to its
     * original parent, the shell is removed and the original background / layout
     * params are restored. Safe to call on an already-restored or partially
     * torn-down state.
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
            shell.removeView(content)
        }
        content.background = state.originalContentBackground
        content.layoutParams = state.originalContentLayoutParams

        when {
            shell.parent === parent -> {
                parent.removeView(shell)
                val index = state.originalIndex.coerceIn(0, parent.childCount)
                parent.addView(content, index)
            }
            shell.parent is ViewGroup -> {
                (shell.parent as ViewGroup).removeView(shell)
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
    }

    internal fun prepareDynamicIslandCapsule(
        root: View,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ): View? {
        val content = findDynamicIslandCapsule(root) ?: return null
        val shell = bindDynamicIslandShell(root, content, position, bottomOffsetDp)
            ?: return null

        val visualWidthPx = strongToastDimensionPx(root, "strong_toast_width")
        val visualHeightPx = strongToastVisualHeightPx(root)
        if (visualWidthPx <= 0 || visualHeightPx <= 0) return null

        val horizontalMarginPx = dpToPx(root, 16f)
        val availableWidthPx = root.resources.displayMetrics.widthPixels - horizontalMarginPx * 2
        val layoutParams = shell.layoutParams ?: return null
        layoutParams.width = minOf(visualWidthPx, availableWidthPx)
        layoutParams.height = visualHeightPx
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            if (position == StrongToastPosition.BOTTOM) {
                layoutParams.topMargin = resolveBottomTopSafetyPx(root, visualHeightPx)
                // Bottom spacing is owned by the LinearLayout parent padding.
                layoutParams.bottomMargin = 0
            } else {
                layoutParams.topMargin = dpToPx(root, CAPSULE_TOP_MARGIN_DP)
                layoutParams.bottomMargin = dpToPx(root, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
            }
        }
        if (layoutParams is LinearLayout.LayoutParams) {
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL
        }
        shell.layoutParams = layoutParams
        shell.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            cornerRadius = visualHeightPx / 2f
        }
        // The ROM content lives inside the shell and must not clip to its own
        // outline; the shell background provides the rounded pill shape.
        shell.clipToOutline = false

        (shell.parent as? LinearLayout)?.apply {
            gravity = if (position == StrongToastPosition.BOTTOM) {
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            } else {
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            val bottomPadding = if (position == StrongToastPosition.BOTTOM) {
                resolveBottomPaddingForCapsule(root, visualHeightPx, bottomOffsetDp)
            } else {
                0
            }
            setPadding(paddingLeft, 0, paddingRight, bottomPadding)
        }
        disableClippingThroughAncestors(shell, root)
        findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)?.visibility = View.GONE
        return shell
    }
    private fun disableClippingThroughAncestors(capsule: View, root: View) {
        var ancestor = capsule.parent
        while (ancestor is ViewGroup) {
            ancestor.clipChildren = false
            ancestor.clipToPadding = false
            if (ancestor === root) return
            ancestor = ancestor.parent
        }
    }

    private fun restoreDynamicIslandAfterDrag(
        view: View,
        profile: DynamicIslandMotionProfile
    ) {
        view.pivotY = profile.pivotY
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(boundedDynamicIslandInterpolator)
            .start()
    }

    private fun resolveDynamicIslandMotionProfile(
        root: View,
        capsule: View,
        position: StrongToastPosition
    ): DynamicIslandMotionProfile? {
        val visualHeightPx = strongToastVisualHeightPx(root)
        if (visualHeightPx <= 0) return null
        return when (position) {
            StrongToastPosition.TOP -> {
                val topMarginPx = dpToPx(capsule, CAPSULE_TOP_MARGIN_DP)
                val bottomSafetyPx = dpToPx(capsule, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
                val statusBarInsetPx = currentStatusBarInsetPx(root)
                DynamicIslandMotionProfile.forTop(
                    visualHeightPx,
                    topMarginPx,
                    bottomSafetyPx,
                    statusBarInsetPx
                )
            }
            StrongToastPosition.BOTTOM -> {
                val topSafetyPx = resolveBottomTopSafetyPx(root, visualHeightPx)
                val bottomPaddingPx = resolveBottomPaddingForCapsule(
                    root,
                    visualHeightPx,
                    resolveSnapshot(root)?.bottomOffsetDp ?: 0
                )
                DynamicIslandMotionProfile.forBottom(
                    visualHeightPx,
                    topSafetyPx,
                    bottomPaddingPx
                )
            }
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

    private fun resolveBottomTopSafetyPx(view: View?, visualHeightPx: Int): Int {
        val minimum = dpToPx(view, 4f)
        val maximum = dpToPx(view, CAPSULE_BOTTOM_SAFETY_MARGIN_DP).coerceAtLeast(minimum)
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
            bottomViewVisibility = bottomView?.visibility ?: View.VISIBLE
        )
    }

    private fun applyMatchModeMutations(
        root: View,
        capsule: View,
        parent: ViewGroup?,
        bottomView: View?,
        targetContentHeightPx: Int
    ) {
        resetDynamicIslandHostTransform(root)
        resetDynamicIslandTransform(capsule)
        resetDynamicIslandContent(capsule)
        setSwipeListenerRecursively(capsule, null)
        (capsule.parent as? View)?.setOnTouchListener(null)
        removeExpandedWindowTouchRegion(root)
        bottomView?.visibility = View.GONE

        val visualWidthPx = strongToastDimensionPx(root, "strong_toast_width")
        val lp = capsule.layoutParams
            ?: LinearLayout.LayoutParams(
                if (visualWidthPx > 0) visualWidthPx else ViewGroup.LayoutParams.WRAP_CONTENT,
                targetContentHeightPx
            )
        lp.height = targetContentHeightPx
        if (visualWidthPx > 0) lp.width = visualWidthPx
        if (lp is ViewGroup.MarginLayoutParams) {
            lp.topMargin = 0
            lp.bottomMargin = 0
        }
        if (lp is LinearLayout.LayoutParams) {
            lp.gravity = Gravity.CENTER
        }
        capsule.layoutParams = lp
        (capsule as? LinearLayout)?.gravity = Gravity.CENTER_VERTICAL

        if (parent === root) {
            parent.setPadding(0, 0, 0, 0)
            (parent as? LinearLayout)?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    internal fun applyMatchModeBaselineToViews(
        root: View,
        capsule: View,
        parent: ViewGroup?,
        bottomView: View?,
        targetContentHeightPx: Int
    ): Boolean {
        val existing = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        if (existing == null) {
            val baseline = captureMatchModeBaseline(capsule, parent, bottomView) ?: return false
            XposedHelpers.setAdditionalInstanceField(root, MATCH_BASELINE_FIELD, baseline)
        }

        applyMatchModeMutations(root, capsule, parent, bottomView, targetContentHeightPx)
        return true
    }

    internal fun applyMatchStatusBarHeight(root: View, targetContentHeightPx: Int): Boolean {
        val capsule = findViewBySystemUiId(root, MESSAGE_CONTAINER_ID) ?: return false
        val parent = capsule.parent as? ViewGroup
        val bottomView = findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)
        return applyMatchModeBaselineToViews(
            root,
            capsule,
            parent,
            bottomView,
            targetContentHeightPx
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

        if (parent === root) {
            parent.setPadding(
                baseline.parentPaddingLeft,
                baseline.parentPaddingTop,
                baseline.parentPaddingRight,
                baseline.parentPaddingBottom
            )
            (parent as? LinearLayout)?.gravity = baseline.parentGravity
        }

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

    @JvmStatic
    internal fun resolveMatchedStatusBarHeightPx(
        statusBarInsetPx: Int,
        originalWindowHeightPx: Int
    ): Int = if (statusBarInsetPx > 0) statusBarInsetPx else originalWindowHeightPx

    @JvmStatic
    internal fun resolveMatchContainerHeightPx(
        targetHeightPx: Int,
        romVisualHeightPx: Int
    ): Int = if (targetHeightPx > 0) targetHeightPx else romVisualHeightPx

    @JvmStatic
    internal fun resolveDynamicIslandWindowHeightPx(
        statusBarInsetPx: Int,
        visualHeightPx: Int,
        topMarginPx: Int,
        bottomSafetyMarginPx: Int
    ): Int {
        if (visualHeightPx <= 0) return statusBarInsetPx
        return maxOf(
            statusBarInsetPx,
            visualHeightPx + topMarginPx.coerceAtLeast(0) +
                bottomSafetyMarginPx.coerceAtLeast(0)
        )
    }

    @JvmStatic
    internal fun resolveBottomDynamicIslandWindowHeightPx(
        visualHeightPx: Int,
        topSafetyMarginPx: Int,
        bottomPaddingPx: Int
    ): Int {
        if (visualHeightPx <= 0) return bottomPaddingPx.coerceAtLeast(0)
        return visualHeightPx + topSafetyMarginPx.coerceAtLeast(0) +
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
