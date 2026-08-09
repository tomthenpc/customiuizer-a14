package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.view.View
import android.view.WindowManager
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * HyperOS 1 StrongToast presentation (the top black capsule used by charging and system modes).
 *
 * Height matching changes both the ROM's 48dp content resource and the independent overlay window
 * returned by MIUIStrongToast. Hiding stops the request at MIUIStrongToastControl before a View or
 * animation is created. No Activity, View, controller or listener is retained.
 */
object SystemUIStrongToastHooks {
    private const val STRONG_TOAST_CLASS = "com.android.systemui.toast.MIUIStrongToast"
    private const val STRONG_TOAST_CONTROL_CLASS =
        "com.android.systemui.toast.MIUIStrongToastControl"

    @JvmStatic
    fun install(
        lpparam: PackageReadyParam,
        mode: StrongToastPresentationMode,
        statusBarHeightDp: Int
    ) {
        when (mode) {
            StrongToastPresentationMode.SYSTEM_DEFAULT -> Unit
            StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT ->
                installHeightMatch(lpparam, statusBarHeightDp)
            StrongToastPresentationMode.HIDE -> installHide(lpparam)
        }
    }

    private fun installHeightMatch(lpparam: PackageReadyParam, statusBarHeightDp: Int) {
        MainModule.resHooks.setThemeValueReplacement(
            "com.android.systemui",
            "dimen",
            "strong_toast_height",
            statusBarHeightDp
        )

        ModuleHelper.findAndHookMethod(
            STRONG_TOAST_CLASS,
            lpparam.classLoader,
            "getWindowParam",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    try {
                        val layoutParams = callback.getResult() as? WindowManager.LayoutParams ?: return
                        val densityDpi = (callback.getThisObject() as? View)
                            ?.resources
                            ?.displayMetrics
                            ?.densityDpi
                            ?: Resources.getSystem().displayMetrics.densityDpi
                        layoutParams.height = targetHeightPx(statusBarHeightDp, densityDpi)
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("StrongToastPresentation", t)
                    }
                }
            }
        )
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
    internal fun targetHeightPx(statusBarHeightDp: Int, densityDpi: Int): Int =
        StatusBarHeightConfig.dpToPx(statusBarHeightDp, densityDpi)
}
