package tv.withaibuild.customiuizer.mods

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

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
    private const val MESSAGE_CONTAINER_ID = "cl_strong_toast_msg"
    private const val FOREHEAD_BOTTOM_ID = "strong_toast_bottom_view"
    private const val CAPSULE_TOP_MARGIN_DP = 6f
    private const val CAPSULE_BOTTOM_SAFETY_MARGIN_DP = 12f
    private val dynamicIslandInterpolator = OvershootInterpolator(0.72f)

    @JvmStatic
    fun install(
        lpparam: PackageReadyParam,
        mode: StrongToastPresentationMode
    ) {
        when (mode) {
            StrongToastPresentationMode.SYSTEM_DEFAULT -> Unit
            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT -> installHeightMatch(lpparam)
            StrongToastPresentationMode.HIDE -> installHide(lpparam)
            StrongToastPresentationMode.DYNAMIC_ISLAND -> {
                installHeightMatch(lpparam, dynamicIsland = true)
                installDynamicIslandMotion(lpparam)
            }
        }
    }

    private fun installHeightMatch(
        lpparam: PackageReadyParam,
        dynamicIsland: Boolean = false
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
                            resolveDynamicIslandWindowHeightPx(
                                statusBarInsetPx,
                                visualHeightPx,
                                dpToPx(strongToast, CAPSULE_TOP_MARGIN_DP),
                                dpToPx(strongToast, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
                            )
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

    private fun currentTopCutoutWidthPx(view: View): Int {
        val windowManager = view.context.getSystemService(WindowManager::class.java) ?: return 0
        return windowManager.currentWindowMetrics.windowInsets.displayCutout
            ?.boundingRectTop
            ?.width()
            ?: 0
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

    private fun installDynamicIslandMotion(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "onAttachedToWindow",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val strongToast = callback.getThisObject() as? View ?: return
                    startDynamicIslandEntrance(strongToast)
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
                    capsule.animate().cancel()
                    resetDynamicIslandTransform(capsule)
                }
            }
        )
    }

    private fun startDynamicIslandEntrance(view: View) {
        try {
            val capsule = prepareDynamicIslandCapsule(view) ?: return
            capsule.animate().cancel()
            // onAttachedToWindow precedes the first layout pass. Hide the not-yet-laid-out
            // capsule and defer exactly once so its real width and centered pivot are stable.
            // Starting here made HyperOS draw a clipped left half on the first event while
            // subsequent events reused the already measured View and appeared correct.
            capsule.alpha = 0f
            capsule.scaleX = 1f
            capsule.scaleY = 1f
            capsule.translationY = 0f
            capsule.post {
                if (!capsule.isAttachedToWindow) {
                    resetDynamicIslandTransform(capsule)
                    return@post
                }
                runDynamicIslandEntrance(view, capsule)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            resetDynamicIslandTransform(findDynamicIslandCapsule(view) ?: view)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
    }

    private fun runDynamicIslandEntrance(view: View, capsule: View) {
        try {
            val visualWidthPx = capsule.width
                .takeIf { it > 0 }
                ?: capsule.layoutParams?.width?.takeIf { it > 0 }
                ?: strongToastDimensionPx(view, "strong_toast_width")
            val visualHeightPx = capsule.height
                .takeIf { it > 0 }
                ?: capsule.layoutParams?.height?.takeIf { it > 0 }
                ?: strongToastVisualHeightPx(view)
            capsule.pivotX = visualWidthPx / 2f
            // Anchor the capsule to its top edge. Overshoot may then expand only into the
            // explicitly reserved bottom safety area instead of crossing the window's top edge.
            capsule.pivotY = 0f
            capsule.alpha = 0f
            capsule.scaleX = resolveDynamicIslandStartScaleX(
                currentTopCutoutWidthPx(view),
                visualWidthPx
            )
            capsule.scaleY = resolveDynamicIslandStartScaleY(
                currentStatusBarInsetPx(view),
                visualHeightPx
            )
            capsule.translationY = 0f
            capsule.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(460L)
                .setInterpolator(dynamicIslandInterpolator)
                .start()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            resetDynamicIslandTransform(capsule)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
    }

    private fun prepareDynamicIslandCapsule(root: View): View? {
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
            layoutParams.topMargin = dpToPx(root, CAPSULE_TOP_MARGIN_DP)
            layoutParams.bottomMargin = dpToPx(root, CAPSULE_BOTTOM_SAFETY_MARGIN_DP)
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
        capsule.clipToOutline = true

        val rootLayout = root as? LinearLayout
        rootLayout?.apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)?.visibility = View.GONE
        return capsule
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
    internal fun resolveDynamicIslandStartScaleX(
        cutoutWidthPx: Int,
        visualWidthPx: Int
    ): Float {
        if (cutoutWidthPx <= 0 || visualWidthPx <= 0) return 0.68f
        return (cutoutWidthPx.toFloat() / visualWidthPx).coerceIn(0.56f, 0.82f)
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
