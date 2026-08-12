package tv.withaibuild.customiuizer.mods

import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
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
 * Dynamic Island mode reuses that same event-scoped ROM View and cleanup path. It only adds a
 * device-scaled entrance transform while the View is attached; no overlay service or polling is
 * introduced.
 */
object SystemUIStrongToastHooks {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val STRONG_TOAST_CLASS = "com.android.systemui.toast.MIUIStrongToast"
    private const val STRONG_TOAST_CONTROL_CLASS =
        "com.android.systemui.toast.MIUIStrongToastControl"
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
                installHeightMatch(lpparam)
                installDynamicIslandMotion(lpparam)
            }
        }
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
                        val strongToast = callback.getThisObject() as? View
                        val statusBarInsetPx = currentStatusBarInsetPx(strongToast)
                        layoutParams.height = resolveWindowHeightPx(
                            statusBarInsetPx,
                            strongToastVisualHeightPx(strongToast),
                            layoutParams.height
                        )
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
                    strongToast.animate().cancel()
                    resetDynamicIslandTransform(strongToast)
                }
            }
        )
    }

    private fun startDynamicIslandEntrance(view: View) {
        try {
            view.animate().cancel()
            val visualWidthPx = strongToastDimensionPx(view, "strong_toast_width")
            val visualHeightPx = strongToastVisualHeightPx(view)
            view.pivotX = visualWidthPx / 2f
            view.pivotY = 0f
            view.alpha = 0f
            view.scaleX = resolveDynamicIslandStartScaleX(
                currentTopCutoutWidthPx(view),
                visualWidthPx
            )
            view.scaleY = resolveDynamicIslandStartScaleY(
                currentStatusBarInsetPx(view),
                visualHeightPx
            )
            view.translationY = -minOf(
                view.resources.displayMetrics.density * 8f,
                visualHeightPx * 0.08f
            )
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(460L)
                .setInterpolator(dynamicIslandInterpolator)
                .start()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            resetDynamicIslandTransform(view)
            XposedHelpers.log("StrongToastDynamicIsland", t)
        }
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
