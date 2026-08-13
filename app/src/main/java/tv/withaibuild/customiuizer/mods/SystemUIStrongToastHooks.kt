package tv.withaibuild.customiuizer.mods

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field

/**
 * HyperOS 1 StrongToast presentation (the top black capsule used by charging and system modes).
 *
 * Height matching changes only the outer overlay window. The ROM's content height, width and
 * corner radius resources remain untouched. The window never becomes shorter than the ROM's
 * visual capsule height, so a short status bar cannot clip its lower corners.
 * Hiding stops the request at MIUIStrongToastControl before a View or animation is created. No
 * Activity, View, controller or listener is retained.
 * Dynamic Island mode reuses that same event-scoped ROM View and cleanup path. The ROM's full-width
 * forehead bottom is removed and its message container becomes a centered rounded capsule. Only
 * that capsule receives a device-scaled entrance transform while attached; no overlay service,
 * listener or polling is introduced.
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
    private const val CAPSULE_BOTTOM_SAFETY_MARGIN_DP = 12f
    private const val BOTTOM_CAPSULE_EDGE_GAP_DP = 18f
    private const val BOTTOM_ENTRANCE_TRAVEL_DP = 12f
    private const val SWIPE_DISMISS_THRESHOLD_DP = 28f
    private const val SWIPE_DISMISS_DURATION_MS = 160L
    private const val CENTER_POP_START_SCALE_X = 0.52f
    private const val CENTER_POP_START_ALPHA = 0.58f
    private const val CENTER_POP_DURATION_MS = 520L
    private const val ATTACHED_STATE_FIELD = "customiuizer_strong_toast_attached"
    private const val SWIPE_STATE_FIELD = "customiuizer_strong_toast_swipe"
    private const val BOTTOM_LAYOUT_OFFSET_FIELD = "customiuizer_strong_toast_bottom_offset"
    private const val BOTTOM_BASE_PADDING_FIELD = "customiuizer_strong_toast_bottom_base_padding"
    private const val BOTTOM_BASE_TOP_PADDING_FIELD =
        "customiuizer_strong_toast_bottom_base_top_padding"
    private const val BOTTOM_LAYOUT_ANIMATOR_FIELD = "customiuizer_strong_toast_bottom_animator"
    private val dynamicIslandInterpolator = OvershootInterpolator(0.72f)
    private val dynamicIslandExitInterpolator = AccelerateInterpolator(1.35f)

    private class SwipeGestureState {
        var downRawY = 0f
        var active = false
        var moved = false
        var startLayoutOffset = 0
    }

    @JvmStatic
    fun install(
        lpparam: PackageReadyParam,
        mode: StrongToastPresentationMode,
        position: StrongToastPosition = StrongToastPosition.TOP,
        bottomOffsetDp: Int = 0
    ) {
        val boundedBottomOffsetDp = bottomOffsetDp.coerceIn(0, MAX_BOTTOM_OFFSET_DP)
        when (mode) {
            StrongToastPresentationMode.SYSTEM_DEFAULT -> Unit
            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT -> installHeightMatch(lpparam)
            StrongToastPresentationMode.HIDE -> installHide(lpparam)
            StrongToastPresentationMode.DYNAMIC_ISLAND -> {
                installHeightMatch(
                    lpparam,
                    dynamicIsland = true,
                    position = position,
                    bottomOffsetDp = boundedBottomOffsetDp
                )
                installDynamicIslandMotion(
                    lpparam,
                    centerPop = false,
                    position = position,
                    bottomOffsetDp = boundedBottomOffsetDp
                )
                installLockscreenSupport(lpparam)
            }
            StrongToastPresentationMode.DYNAMIC_ISLAND_CENTER_POP -> {
                installHeightMatch(
                    lpparam,
                    dynamicIsland = true,
                    position = position,
                    bottomOffsetDp = boundedBottomOffsetDp
                )
                installDynamicIslandMotion(
                    lpparam,
                    centerPop = true,
                    position = position,
                    bottomOffsetDp = boundedBottomOffsetDp
                )
                installLockscreenSupport(lpparam)
            }
        }
    }

    private fun installHeightMatch(
        lpparam: PackageReadyParam,
        dynamicIsland: Boolean = false,
        position: StrongToastPosition = StrongToastPosition.TOP,
        bottomOffsetDp: Int = 0
    ) {
        ModuleHelper.findAndHookMethod(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "getWindowParam",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    try {
                        val layoutParams = callback.getResult() as? WindowManager.LayoutParams ?: return
                        val strongToast = callback.getThisObject() as? View
                        val statusBarInsetPx = currentStatusBarInsetPx(strongToast)
                        val visualHeightPx = strongToastVisualHeightPx(strongToast)
                        layoutParams.height = if (dynamicIsland) {
                            // HyperOS normally makes this Window exactly as wide as
                            // strong_toast_width. A centered overshoot is then clipped by the
                            // Window surface before ViewGroup clip flags can help. Keep the
                            // capsule at ROM width, but give it a full-screen transparent host.
                            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                            if (position == StrongToastPosition.BOTTOM) {
                                layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                // This ROM window animation is authored for the top edge and
                                // moves the complete surface downward. At the bottom it reverses
                                // the intended direction and clips early frames at the surface
                                // boundary, so the bounded capsule animation owns motion here.
                                layoutParams.windowAnimations = 0
                                // The bottom window owns its navigation/gesture-safe padding.
                                // Leaving the ROM's NAVIGATION_BARS fit type enabled makes WMS
                                // subtract the visible navigation inset a second time and clips
                                // the capsule by that exact amount on gesture-navigation devices.
                                layoutParams.setFitInsetsTypes(0)
                                resolveBottomDynamicIslandWindowHeightPx(
                                    currentBottomSafeInsetPx(strongToast),
                                    visualHeightPx,
                                    dpToPx(strongToast, CAPSULE_BOTTOM_SAFETY_MARGIN_DP),
                                    dpToPx(strongToast, BOTTOM_CAPSULE_EDGE_GAP_DP) +
                                        dpToPx(strongToast, bottomOffsetDp.toFloat())
                                )
                            } else {
                                layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                resolveDynamicIslandWindowHeightPx(
                                    statusBarInsetPx,
                                    visualHeightPx,
                                    dpToPx(strongToast, CAPSULE_TOP_MARGIN_DP),
                                    dpToPx(strongToast, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
                                )
                            }
                        } else {
                            resolveWindowHeightPx(
                                statusBarInsetPx,
                                visualHeightPx,
                                layoutParams.height
                            )
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

    private fun installHide(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CONTROL_CLASS,
            lpparam.classLoader,
            "showCustomStrongToast",
            object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    callback.returnAndSkip(null)
                }
            }
        )
    }

    private fun installDynamicIslandMotion(
        lpparam: PackageReadyParam,
        centerPop: Boolean,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ) {
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "onAttachedToWindow",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val strongToast = callback.getThisObject() as? View ?: return
                    XposedHelpers.setAdditionalInstanceField(
                        strongToast,
                        ATTACHED_STATE_FIELD,
                        false
                    )
                    startDynamicIslandEntrance(
                        strongToast,
                        centerPop,
                        position,
                        bottomOffsetDp
                    )
                }
            }
        )
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "updateStrongToast",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val strongToast = callback.getThisObject() as? View ?: return
                    if (XposedHelpers.getAdditionalInstanceField(
                            strongToast,
                            ATTACHED_STATE_FIELD
                        ) == true
                    ) {
                        startDynamicIslandRefresh(strongToast, position)
                    }
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
                    val capsule = findDynamicIslandCapsule(strongToast) ?: strongToast
                    val motionView = dynamicIslandMotionView(capsule, position)
                    setSwipeListenerRecursively(capsule, null)
                    XposedHelpers.removeAdditionalInstanceField(strongToast, ATTACHED_STATE_FIELD)
                    XposedHelpers.removeAdditionalInstanceField(strongToast, SWIPE_STATE_FIELD)
                    strongToast.animate().cancel()
                    resetDynamicIslandHostTransform(strongToast)
                    if (position == StrongToastPosition.BOTTOM) {
                        cancelBottomLayoutAnimation(motionView)
                        setBottomLayoutOffset(motionView, 0)
                    } else {
                        motionView.animate().cancel()
                        resetDynamicIslandTransform(motionView)
                    }
                    capsule.animate().cancel()
                    resetDynamicIslandTransform(capsule)
                }
            }
        )
    }

    private fun startDynamicIslandEntrance(
        view: View,
        centerPop: Boolean,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ) {
        try {
            val capsule = prepareDynamicIslandCapsule(view, position, bottomOffsetDp) ?: return
            val motionView = dynamicIslandMotionView(capsule, position)
            XposedHelpers.setAdditionalInstanceField(view, SWIPE_STATE_FIELD, SwipeGestureState())
            installSwipeToDismiss(view, capsule, position)
            view.animate().cancel()
            resetDynamicIslandHostTransform(view)
            if (position == StrongToastPosition.BOTTOM) {
                cancelBottomLayoutAnimation(motionView)
                setBottomLayoutOffset(motionView, 0)
            } else {
                motionView.animate().cancel()
                resetDynamicIslandTransform(motionView)
            }
            capsule.animate().cancel()
            // onAttachedToWindow precedes the first layout pass. Hide the not-yet-laid-out
            // capsule and defer exactly once so its real width and centered pivot are stable.
            // Starting here made HyperOS draw a clipped left half on the first event while
            // subsequent events reused the already measured View and appeared correct.
            capsule.alpha = if (position == StrongToastPosition.BOTTOM) {
                0f
            } else if (motionView === capsule) {
                0f
            } else {
                1f
            }
            capsule.scaleX = 1f
            capsule.scaleY = 1f
            capsule.translationY = 0f
            if (motionView !== capsule) {
                // Bottom entrance is a complete opaque capsule travelling upward. Fading the
                // outer container over launcher content made its top edge look cropped during
                // the first third even though the rounded outline was geometrically complete.
                motionView.alpha = if (position == StrongToastPosition.BOTTOM) 1f else 0f
                motionView.scaleX = 1f
                motionView.scaleY = 1f
                motionView.translationY = 0f
            }
            val startEntrance = Runnable {
                ModuleHelper.guarded {
                    if (!capsule.isAttachedToWindow) {
                        resetDynamicIslandHostTransform(view)
                        if (position == StrongToastPosition.BOTTOM) {
                            cancelBottomLayoutAnimation(motionView)
                            setBottomLayoutOffset(motionView, 0)
                        } else {
                            resetDynamicIslandTransform(motionView)
                        }
                        resetDynamicIslandTransform(capsule)
                        return@guarded
                    }
                    runDynamicIslandEntrance(view, capsule, motionView, centerPop, position)
                    XposedHelpers.setAdditionalInstanceField(view, ATTACHED_STATE_FIELD, true)
                }
            }
            capsule.post(startEntrance)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            resetDynamicIslandHostTransform(view)
            resetDynamicIslandTransform(findDynamicIslandCapsule(view) ?: view)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
    }

    private fun runDynamicIslandEntrance(
        view: View,
        capsule: View,
        motionView: View,
        centerPop: Boolean,
        position: StrongToastPosition
    ) {
        try {
            val visualHeightPx = capsule.height
                .takeIf { it > 0 }
                ?: capsule.layoutParams?.height?.takeIf { it > 0 }
                ?: strongToastVisualHeightPx(view)
            motionView.alpha = if (position == StrongToastPosition.BOTTOM) {
                1f
            } else if (centerPop) {
                CENTER_POP_START_ALPHA
            } else {
                0f
            }
            if (centerPop) {
                // HyperOS clips horizontal transforms applied directly to its message
                // container against a stale outline. Scale the already full-width transparent
                // host instead: the capsule expands symmetrically around the screen center
                // while its own rounded outline and child layout remain untouched.
                view.pivotX = view.width / 2f
                view.scaleX = CENTER_POP_START_SCALE_X
                motionView.pivotY = if (position == StrongToastPosition.BOTTOM) {
                    motionView.height.toFloat()
                } else {
                    motionView.height / 2f
                }
                motionView.scaleX = 1f
            } else {
                // Keep both horizontal halves present from the first visible frame. HyperOS
                // reuses the attached StrongToast for consecutive events, so a first-attach
                // one-sided scaleX expansion looked like clipping on the first event only.
                motionView.pivotY = if (position == StrongToastPosition.BOTTOM) {
                    motionView.height.toFloat()
                } else {
                    0f
                }
                motionView.scaleX = 1f
            }
            motionView.scaleY = if (position == StrongToastPosition.BOTTOM) {
                // Keep the complete capsule and its contents present from the first visible
                // bottom frame. Scaling this custom ConstraintLayout made its outline crop
                // text and corners during roughly the first third of the entrance.
                1f
            } else {
                resolveDynamicIslandStartScaleY(
                    currentStatusBarInsetPx(view),
                    visualHeightPx
                )
            }
            motionView.translationY = 0f
            if (centerPop) {
                view.animate()
                    .scaleX(1f)
                    .setDuration(CENTER_POP_DURATION_MS)
                    .setInterpolator(dynamicIslandInterpolator)
                    .start()
            }
            if (position == StrongToastPosition.BOTTOM) {
                setBottomLayoutOffset(
                    motionView,
                    dpToPx(view, BOTTOM_ENTRANCE_TRAVEL_DP)
                )
                capsule.alpha = 1f
                animateBottomLayoutOffset(
                    motionView,
                    0,
                    460L,
                    dynamicIslandInterpolator
                )
            } else {
                motionView.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(460L)
                    .setInterpolator(dynamicIslandInterpolator)
                    .start()
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            view.animate().cancel()
            resetDynamicIslandHostTransform(view)
            if (position == StrongToastPosition.BOTTOM) {
                cancelBottomLayoutAnimation(motionView)
                setBottomLayoutOffset(motionView, 0)
            } else {
                motionView.animate().cancel()
                resetDynamicIslandTransform(motionView)
            }
            resetDynamicIslandTransform(capsule)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
    }

    private fun startDynamicIslandRefresh(root: View, position: StrongToastPosition) {
        try {
            val capsule = findDynamicIslandCapsule(root) ?: return
            val motionView = dynamicIslandMotionView(capsule, position)
            root.animate().cancel()
            resetDynamicIslandHostTransform(root)
            motionView.animate().cancel()
            motionView.pivotX = motionView.width / 2f
            motionView.pivotY = motionView.height / 2f
            motionView.alpha = 0.78f
            motionView.scaleX = if (position == StrongToastPosition.BOTTOM) 1f else 0.96f
            motionView.scaleY = if (position == StrongToastPosition.BOTTOM) 1f else 0.90f
            if (position == StrongToastPosition.BOTTOM) {
                setBottomLayoutOffset(motionView, dpToPx(root, 3f))
                animateBottomLayoutOffset(motionView, 0, 280L, dynamicIslandInterpolator)
                motionView.animate()
                    .alpha(1f)
                    .setDuration(280L)
                    .setInterpolator(dynamicIslandInterpolator)
                    .start()
            } else {
                motionView.translationY = 0f
                motionView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(280L)
                    .setInterpolator(dynamicIslandInterpolator)
                    .start()
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            resetDynamicIslandHostTransform(root)
            val capsule = findDynamicIslandCapsule(root) ?: root
            val motionView = dynamicIslandMotionView(capsule, position)
            if (position == StrongToastPosition.BOTTOM) {
                cancelBottomLayoutAnimation(motionView)
                setBottomLayoutOffset(motionView, 0)
            } else {
                resetDynamicIslandTransform(motionView)
            }
            resetDynamicIslandTransform(capsule)
            XposedHelpers.log("StrongToastDynamicIslandRefresh", t)
        }
    }

    private fun handleDynamicIslandTouch(
        strongToast: View,
        event: MotionEvent,
        position: StrongToastPosition
    ): Boolean {
        val capsule = findDynamicIslandCapsule(strongToast) ?: return false
        val motionView = dynamicIslandMotionView(capsule, position)
        val state = XposedHelpers.getAdditionalInstanceField(
            strongToast,
            SWIPE_STATE_FIELD
        ) as? SwipeGestureState ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.active = true
                state.downRawY = event.rawY
                state.moved = false
                strongToast.animate().cancel()
                resetDynamicIslandHostTransform(strongToast)
                if (position == StrongToastPosition.BOTTOM) {
                    cancelBottomLayoutAnimation(motionView)
                    state.startLayoutOffset = currentBottomLayoutOffset(motionView)
                } else {
                    motionView.animate().cancel()
                }
                motionView.scaleX = 1f
                motionView.scaleY = 1f
            }
            MotionEvent.ACTION_MOVE -> {
                if (!state.active) return false
                val deltaY = event.rawY - state.downRawY
                val directionalDelta = if (position == StrongToastPosition.BOTTOM) {
                    deltaY.coerceAtLeast(0f)
                } else {
                    deltaY.coerceAtMost(0f)
                }
                state.moved = state.moved ||
                    kotlin.math.abs(deltaY) >= dpToPx(capsule, 4f)
                if (position == StrongToastPosition.BOTTOM) {
                    setBottomLayoutOffset(
                        motionView,
                        state.startLayoutOffset + directionalDelta.toInt()
                    )
                } else {
                    motionView.translationY = directionalDelta
                }
                val fadeDistance = (capsule.height * 1.5f).coerceAtLeast(1f)
                motionView.alpha = (1f - kotlin.math.abs(directionalDelta) / fadeDistance)
                    .coerceIn(0.55f, 1f)
            }
            MotionEvent.ACTION_UP -> {
                if (!state.active) return false
                state.active = false
                val deltaY = event.rawY - state.downRawY
                if (shouldDismissDynamicIsland(
                        deltaY,
                        position,
                        dpToPx(capsule, SWIPE_DISMISS_THRESHOLD_DP).toFloat()
                    )
                ) {
                    animateDynamicIslandDismiss(strongToast, capsule, motionView, position)
                } else {
                    restoreDynamicIslandAfterDrag(motionView, position)
                    if (!state.moved) capsule.performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!state.active) return false
                state.active = false
                restoreDynamicIslandAfterDrag(motionView, position)
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
        setSwipeListenerRecursively(capsule, listener)
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
            XposedHelpers.callMethod(strongToast, "hideStrongToast")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandDismiss", t)
        }
    }

    private fun animateDynamicIslandDismiss(
        strongToast: View,
        capsule: View,
        motionView: View,
        position: StrongToastPosition
    ) {
        val distance = capsule.height.coerceAtLeast(strongToastVisualHeightPx(strongToast))
        if (position == StrongToastPosition.BOTTOM) {
            val targetOffset = currentBottomLayoutOffset(motionView) + distance
            motionView.animate()
                .alpha(0f)
                .setDuration(SWIPE_DISMISS_DURATION_MS)
                .setInterpolator(dynamicIslandExitInterpolator)
                .start()
            animateBottomLayoutOffset(
                motionView,
                targetOffset,
                SWIPE_DISMISS_DURATION_MS,
                dynamicIslandExitInterpolator
            ) {
                ModuleHelper.guarded { dismissStrongToast(strongToast) }
            }
            return
        }
        motionView.animate()
            .alpha(0f)
            .translationY(-distance.toFloat())
            .setDuration(SWIPE_DISMISS_DURATION_MS)
            .setInterpolator(dynamicIslandExitInterpolator)
            .withEndAction {
                ModuleHelper.guarded { dismissStrongToast(strongToast) }
            }
            .start()
    }

    /**
     * HyperOS 1 rejects StrongToast while KeyguardStateControllerImpl.mShowing is true. Keep the
     * ROM method and every other eligibility gate intact, but expose that single state as false
     * only for the synchronous native call. Nested charging callbacks restore the outer value in
     * strict LIFO order and every chain proceeds exactly once.
     */
    private fun installLockscreenSupport(lpparam: PackageReadyParam) {
        try {
            val controlClass = XposedHelpers.findClassIfExists(
                STRONG_TOAST_CONTROL_CLASS,
                lpparam.classLoader
            ) ?: return
            val keyguardClass = XposedHelpers.findClassIfExists(
                KEYGUARD_STATE_CLASS,
                lpparam.classLoader
            ) ?: return
            val controllerField = XposedHelpers.findFieldIfExists(
                controlClass,
                "mKeyguardStateController"
            ) ?: return
            val showingField = XposedHelpers.findFieldIfExists(keyguardClass, "mShowing") ?: return

            ModuleHelper.hookAllMethods(
                controlClass,
                "showCustomStrongToast",
                lockscreenGateHook(controllerField, showingField, null)
            )

            val batteryCallbackClass = XposedHelpers.findClassIfExists(
                BATTERY_CALLBACK_CLASS,
                lpparam.classLoader
            ) ?: return
            val outerControlField = XposedHelpers.findFieldIfExists(
                batteryCallbackClass,
                "this\$0"
            ) ?: return
            ModuleHelper.hookAllMethods(
                batteryCallbackClass,
                "onRefreshBatteryInfo",
                lockscreenGateHook(controllerField, showingField, outerControlField)
            )
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastLockscreen", t)
        }
    }

    private fun lockscreenGateHook(
        controllerField: Field,
        showingField: Field,
        outerControlField: Field?
    ): MethodHook = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val keyguardState: Any
            val wasShowing: Boolean
            try {
                val receiver = chain.thisObject
                val control = if (outerControlField != null) {
                    outerControlField.get(receiver)
                } else {
                    receiver
                }
                keyguardState = controllerField.get(control) ?: return chain.proceed()
                wasShowing = showingField.getBoolean(keyguardState)
                if (!wasShowing) return chain.proceed()
                showingField.setBoolean(keyguardState, false)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("StrongToastLockscreenRead", t)
                return chain.proceed()
            }

            try {
                return chain.proceed()
            } finally {
                try {
                    showingField.setBoolean(keyguardState, true)
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log("StrongToastLockscreenRestore", t)
                }
            }
        }
    }

    private fun prepareDynamicIslandCapsule(
        root: View,
        position: StrongToastPosition,
        bottomOffsetDp: Int
    ): View? {
        val capsule = findDynamicIslandCapsule(root) ?: return null
        val visualWidthPx = strongToastDimensionPx(root, "strong_toast_width")
        val visualHeightPx = strongToastVisualHeightPx(root)
        if (visualWidthPx <= 0 || visualHeightPx <= 0) return null

        val horizontalMarginPx = dpToPx(root, 16f)
        val availableWidthPx = root.resources.displayMetrics.widthPixels - horizontalMarginPx * 2
        val layoutParams = capsule.layoutParams ?: return null
        layoutParams.width = minOf(visualWidthPx, availableWidthPx)
        layoutParams.height = visualHeightPx
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            if (position == StrongToastPosition.BOTTOM) {
                layoutParams.topMargin = dpToPx(root, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
                // Bottom spacing is owned by the LinearLayout parent's padding. Animating this
                // child margin is absorbed by HyperOS relayouts and leaves the capsule stationary.
                layoutParams.bottomMargin = 0
            } else {
                layoutParams.topMargin = dpToPx(root, CAPSULE_TOP_MARGIN_DP)
                layoutParams.bottomMargin = dpToPx(root, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
            }
        }
        if (layoutParams is LinearLayout.LayoutParams) {
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL
        }
        capsule.layoutParams = layoutParams
        capsule.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            cornerRadius = visualHeightPx / 2f
        }
        // The ROM's STConstraintLayout does not need outline clipping for a rounded
        // GradientDrawable background. Its first-layout outline can lag the transform and
        // cut the top of the capsule, so animate the outer container and keep this disabled.
        capsule.clipToOutline = false

        (capsule.parent as? LinearLayout)?.apply {
            gravity = if (position == StrongToastPosition.BOTTOM) {
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            } else {
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            if (position == StrongToastPosition.BOTTOM) {
                val bottomPadding = currentBottomSafeInsetPx(root) +
                    dpToPx(root, BOTTOM_CAPSULE_EDGE_GAP_DP) +
                    dpToPx(root, bottomOffsetDp.toFloat())
                setPadding(paddingLeft, paddingTop, paddingRight, bottomPadding)
                XposedHelpers.setAdditionalInstanceField(
                    this,
                    BOTTOM_BASE_PADDING_FIELD,
                    bottomPadding
                )
                XposedHelpers.setAdditionalInstanceField(
                    this,
                    BOTTOM_BASE_TOP_PADDING_FIELD,
                    paddingTop
                )
                XposedHelpers.setAdditionalInstanceField(this, BOTTOM_LAYOUT_OFFSET_FIELD, 0)
            }
        }
        disableClippingThroughAncestors(capsule, root)
        findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)?.visibility = View.GONE
        return capsule
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

    private fun dynamicIslandMotionView(capsule: View, position: StrongToastPosition): View {
        return if (position == StrongToastPosition.BOTTOM) {
            capsule.parent as? View ?: capsule
        } else {
            capsule
        }
    }

    private fun restoreDynamicIslandAfterDrag(view: View, position: StrongToastPosition) {
        if (position == StrongToastPosition.BOTTOM) {
            animateBottomLayoutOffset(view, 0, 180L, dynamicIslandInterpolator)
            view.animate()
                .alpha(1f)
                .setDuration(180L)
                .setInterpolator(dynamicIslandInterpolator)
                .start()
        } else {
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(dynamicIslandInterpolator)
                .start()
        }
    }

    private fun currentBottomLayoutOffset(view: View): Int {
        return XposedHelpers.getAdditionalInstanceField(
            view,
            BOTTOM_LAYOUT_OFFSET_FIELD
        ) as? Int ?: 0
    }

    private fun setBottomLayoutOffset(view: View, offset: Int) {
        val current = currentBottomLayoutOffset(view)
        if (current == offset) return
        val basePadding = XposedHelpers.getAdditionalInstanceField(
            view,
            BOTTOM_BASE_PADDING_FIELD
        ) as? Int ?: (view.paddingBottom + current).also { resolved ->
            XposedHelpers.setAdditionalInstanceField(view, BOTTOM_BASE_PADDING_FIELD, resolved)
        }
        val baseTopPadding = XposedHelpers.getAdditionalInstanceField(
            view,
            BOTTOM_BASE_TOP_PADDING_FIELD
        ) as? Int ?: (view.paddingTop - current).coerceAtLeast(0).also { resolved ->
            XposedHelpers.setAdditionalInstanceField(
                view,
                BOTTOM_BASE_TOP_PADDING_FIELD,
                resolved
            )
        }
        // Transfer space inside the fixed-height LinearLayout: positive offset adds the same
        // amount above the capsule that it removes below. The parent remains exactly the window
        // height while the complete child moves down; animating back to zero moves it upward.
        view.setPadding(
            view.paddingLeft,
            baseTopPadding + offset,
            view.paddingRight,
            (basePadding - offset).coerceAtLeast(0)
        )
        XposedHelpers.setAdditionalInstanceField(view, BOTTOM_LAYOUT_OFFSET_FIELD, offset)
    }

    private fun cancelBottomLayoutAnimation(view: View) {
        (XposedHelpers.getAdditionalInstanceField(
            view,
            BOTTOM_LAYOUT_ANIMATOR_FIELD
        ) as? ValueAnimator)?.cancel()
        XposedHelpers.removeAdditionalInstanceField(view, BOTTOM_LAYOUT_ANIMATOR_FIELD)
    }

    private fun animateBottomLayoutOffset(
        view: View,
        targetOffset: Int,
        durationMs: Long,
        interpolator: android.animation.TimeInterpolator,
        endAction: (() -> Unit)? = null
    ) {
        cancelBottomLayoutAnimation(view)
        val startOffset = currentBottomLayoutOffset(view)
        if (startOffset == targetOffset) {
            endAction?.invoke()
            return
        }
        val animator = ValueAnimator.ofInt(startOffset, targetOffset).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { animation ->
                ModuleHelper.guarded {
                    setBottomLayoutOffset(view, animation.animatedValue as Int)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    ModuleHelper.guarded { cancelled = true }
                }

                override fun onAnimationEnd(animation: Animator) {
                    ModuleHelper.guarded {
                        XposedHelpers.removeAdditionalInstanceField(
                            view,
                            BOTTOM_LAYOUT_ANIMATOR_FIELD
                        )
                        if (!cancelled) endAction?.invoke()
                    }
                }
            })
        }
        XposedHelpers.setAdditionalInstanceField(view, BOTTOM_LAYOUT_ANIMATOR_FIELD, animator)
        animator.start()
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

    private fun resetDynamicIslandTransform(view: View) {
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationY = 0f
    }

    private fun resetDynamicIslandHostTransform(view: View) {
        view.scaleX = 1f
    }

    @JvmStatic
    internal fun resolveWindowHeightPx(
        statusBarInsetPx: Int,
        visualHeightPx: Int,
        originalWindowHeightPx: Int
    ): Int {
        if (statusBarInsetPx <= 0) return originalWindowHeightPx
        return if (visualHeightPx > 0) maxOf(statusBarInsetPx, visualHeightPx) else statusBarInsetPx
    }

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
        navigationBarInsetPx: Int,
        visualHeightPx: Int,
        topSafetyMarginPx: Int,
        bottomMarginPx: Int
    ): Int {
        if (visualHeightPx <= 0) return navigationBarInsetPx.coerceAtLeast(0)
        return visualHeightPx + navigationBarInsetPx.coerceAtLeast(0) +
            topSafetyMarginPx.coerceAtLeast(0) + bottomMarginPx.coerceAtLeast(0)
    }

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

    @JvmStatic
    internal fun resolveDynamicIslandStartScaleY(
        statusBarInsetPx: Int,
        visualHeightPx: Int
    ): Float {
        if (statusBarInsetPx <= 0 || visualHeightPx <= 0) return 0.72f
        return (statusBarInsetPx.toFloat() / visualHeightPx).coerceIn(0.62f, 0.90f)
    }
}
