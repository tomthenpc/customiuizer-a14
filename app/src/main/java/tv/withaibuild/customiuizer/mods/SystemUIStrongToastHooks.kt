package tv.withaibuild.customiuizer.mods

import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
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
 * corner radius resources remain untouched, so a taller status bar cannot stretch the capsule.
 * Hiding stops the request at MIUIStrongToastControl before a View or animation is created. No
 * Activity, View, controller or listener is retained.
 */
object SystemUIStrongToastHooks {
    private const val STRONG_TOAST_CLASS = "com.android.systemui.toast.MIUIStrongToast"
    private const val STRONG_TOAST_CONTROL_CLASS =
        "com.android.systemui.toast.MIUIStrongToastControl"

    @JvmStatic
    fun install(
        lpparam: PackageReadyParam,
        mode: StrongToastPresentationMode
    ) {
        when (mode) {
            StrongToastPresentationMode.SYSTEM_DEFAULT -> Unit
            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT -> installHeightMatch(lpparam)
            StrongToastPresentationMode.HIDE -> installHide(lpparam)
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
                        val statusBarInsetPx = currentStatusBarInsetPx(callback.getThisObject() as? View)
                        layoutParams.height = resolveWindowHeightPx(
                            statusBarInsetPx,
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

    @JvmStatic
    internal fun resolveWindowHeightPx(statusBarInsetPx: Int, originalWindowHeightPx: Int): Int =
        statusBarInsetPx.takeIf { it > 0 } ?: originalWindowHeightPx
}
