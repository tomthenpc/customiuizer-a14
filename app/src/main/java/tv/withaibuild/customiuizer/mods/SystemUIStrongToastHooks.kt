package tv.withaibuild.customiuizer.mods

import android.graphics.Color
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
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
    private const val CAPSULE_BOTTOM_SAFETY_MARGIN_DP = 16f
    private const val BOTTOM_EDGE_GAP_MIN_DP = 2f
    private const val BOTTOM_EDGE_GAP_MAX_DP = 6f
    private const val SWIPE_DISMISS_THRESHOLD_MAX_DP = 28f
    private const val SWIPE_TOUCH_EXPANSION_HORIZONTAL_DP = 24f
    private const val SWIPE_TOUCH_EXPANSION_VERTICAL_DP = 16f
    private const val DYNAMIC_ISLAND_EXIT_DURATION_MS = 300L
    private const val ISLAND_CONTENT_DURATION_MS = 180L
    private const val ISLAND_CONTENT_DELAY_MS = 60L
    private const val SWIPE_STATE_FIELD = "customiuizer_strong_toast_swipe"
    private const val BOTTOM_BASE_PADDING_FIELD = "customiuizer_strong_toast_bottom_base_padding"
    private const val DISMISS_RUNNING_FIELD = "customiuizer_strong_toast_dismiss_running"
    private const val TOUCH_REGION_LISTENER_FIELD =
        "customiuizer_strong_toast_touch_region_listener"
    private const val TOUCHABLE_INSETS_REGION = 3
    private const val STATUS_BAR_CONTENTS_ID = "status_bar_contents"
    private const val STATUS_BAR_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"
    private const val RUNTIME_SNAPSHOT_FIELD = "customiuizer_strong_toast_runtime_snapshot"
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
                                layoutParams.height = resolveWindowHeightPx(
                                    statusBarInsetPx,
                                    visualHeightPx,
                                    layoutParams.height
                                )
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
                    val capsule = swipeState?.capsule ?: findDynamicIslandCapsule(strongToast) ?: return
                    if (!strongToast.isAttachedToWindow) return
                    callback.returnAndSkip(null)
                    animateDynamicIslandDismiss(
                        strongToast,
                        capsule,
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
                            val swipeState = XposedHelpers.getAdditionalInstanceField(
                                strongToast,
                                SWIPE_STATE_FIELD
                            ) as? SwipeGestureState
                            val capsule = swipeState?.capsule
                                ?: findDynamicIslandCapsule(strongToast)
                                ?: strongToast
                            setSwipeListenerRecursively(capsule, null)
                            (capsule.parent as? View)?.setOnTouchListener(null)
                            removeExpandedWindowTouchRegion(strongToast)
                            resetDynamicIslandContent(capsule)
                            XposedHelpers.removeAdditionalInstanceField(strongToast, SWIPE_STATE_FIELD)
                            XposedHelpers.removeAdditionalInstanceField(strongToast, DISMISS_RUNNING_FIELD)
                            restoreStatusBarContents(strongToast)
                            strongToast.animate().cancel()
                            resetDynamicIslandHostTransform(strongToast)
                            capsule.animate().cancel()
                            resetDynamicIslandTransform(capsule)
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
                            val capsule = findDynamicIslandCapsule(view) ?: return@guarded
                            val horizontal = dpToPx(capsule, SWIPE_TOUCH_EXPANSION_HORIZONTAL_DP)
                            val vertical = dpToPx(capsule, SWIPE_TOUCH_EXPANSION_VERTICAL_DP)
                            region.set(
                                (capsule.left - horizontal).coerceAtLeast(0),
                                (capsule.top - vertical).coerceAtLeast(0),
                                (capsule.right + horizontal).coerceAtMost(view.width),
                                (capsule.bottom + vertical).coerceAtMost(view.height)
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
            val capsule = prepareDynamicIslandCapsule(view, position, bottomOffsetDp) ?: return
            if (position == StrongToastPosition.TOP) hideStatusBarContents(view)
            XposedHelpers.setAdditionalInstanceField(view, SWIPE_STATE_FIELD, SwipeGestureState(capsule))
            installSwipeToDismiss(view, capsule, position)
            view.animate().cancel()
            resetDynamicIslandHostTransform(view)
            capsule.animate().cancel()
            resetDynamicIslandTransform(capsule)
            prepareDynamicIslandContent(capsule)
            // onAttachedToWindow precedes the first layout pass. Defer exactly once so the
            // capsule has real width/height and the profile can set a safe pivot/translation.
            capsule.post {
                ModuleHelper.guarded {
                    if (!capsule.isAttachedToWindow) {
                        resetDynamicIslandHostTransform(view)
                        resetDynamicIslandTransform(capsule)
                        resetDynamicIslandContent(capsule)
                        return@guarded
                    }
                    runDynamicIslandEntrance(view, capsule, position)
                }
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            restoreStatusBarContents(view)
            resetDynamicIslandHostTransform(view)
            val capsule = findDynamicIslandCapsule(view) ?: view
            resetDynamicIslandTransform(capsule)
            resetDynamicIslandContent(capsule)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
    }
    private fun runDynamicIslandEntrance(
        view: View,
        capsule: View,
        position: StrongToastPosition
    ) {
        try {
            val profile = resolveDynamicIslandMotionProfile(view, capsule, position)
                ?: return

            // Bind the prepared geometry to the event-owned swipe state so every
            // MotionEvent and the dismiss animation can reuse it without window/inset
            // lookups or new allocations.
            val state = XposedHelpers.getAdditionalInstanceField(
                view,
                SWIPE_STATE_FIELD
            ) as? SwipeGestureState
            state?.motionProfile = profile

            // The capsule is laid out at its resting position. Set the pivot to the
            // screen edge so vertical scaling grows from that edge, not the center.
            capsule.pivotX = capsule.width / 2f
            capsule.pivotY = profile.pivotY
            capsule.scaleX = 1f
            capsule.scaleY = profile.entranceScaleY
            capsule.translationY = profile.entranceTranslationY
            capsule.alpha = 1f

            capsule.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(profile.restingTranslationY)
                .setDuration(profile.entranceDurationMs)
                .setInterpolator(boundedDynamicIslandInterpolator)
                .start()

            animateDynamicIslandContent(capsule)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            restoreStatusBarContents(view)
            view.animate().cancel()
            resetDynamicIslandHostTransform(view)
            capsule.animate().cancel()
            resetDynamicIslandTransform(capsule)
            resetDynamicIslandContent(capsule)
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

    private fun animateDynamicIslandDismiss(
        strongToast: View,
        capsule: View,
        position: StrongToastPosition
    ) {
        XposedHelpers.setAdditionalInstanceField(strongToast, DISMISS_RUNNING_FIELD, true)
        setSwipeListenerRecursively(capsule, null)
        animateDynamicIslandContentOut(capsule)
        try {
            XposedHelpers.setBooleanField(strongToast, "mCheckInOutStrongToasting", true)
            XposedHelpers.callMethod(strongToast, "clearAll")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("StrongToastDynamicIslandClear", t)
        }
        val complete = Runnable {
            ModuleHelper.guarded {
                restoreStatusBarContents(strongToast)
                XposedHelpers.callMethod(strongToast, "onComplete")
            }
        }

        val swipeState = XposedHelpers.getAdditionalInstanceField(
            strongToast,
            SWIPE_STATE_FIELD
        ) as? SwipeGestureState
        val profile = swipeState?.motionProfile
            ?: resolveDynamicIslandMotionProfile(strongToast, capsule, position)
                .also { fallback -> swipeState?.motionProfile = fallback }
        if (profile == null) {
            complete.run()
            return
        }

        capsule.pivotY = profile.pivotY
        capsule.animate()
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
            val bottomPadding = if (position == StrongToastPosition.BOTTOM) {
                resolveBottomPaddingForCapsule(root, visualHeightPx, bottomOffsetDp)
            } else {
                0
            }
            setPadding(paddingLeft, 0, paddingRight, bottomPadding)
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
    private fun prepareDynamicIslandContent(capsule: View) {
        val group = capsule as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            child.animate().cancel()
            child.alpha = 0f
        }
    }

    private fun animateDynamicIslandContent(capsule: View) {
        val group = capsule as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            group.getChildAt(index).animate()
                .alpha(1f)
                .setStartDelay(ISLAND_CONTENT_DELAY_MS)
                .setDuration(ISLAND_CONTENT_DURATION_MS)
                .setInterpolator(boundedDynamicIslandInterpolator)
                .start()
        }
    }

    private fun animateDynamicIslandContentOut(capsule: View) {
        val group = capsule as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            group.getChildAt(index).animate()
                .alpha(0f)
                .setStartDelay(0L)
                .setDuration(ISLAND_CONTENT_DURATION_MS / 2)
                .setInterpolator(dynamicIslandExitInterpolator)
                .start()
        }
    }

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
