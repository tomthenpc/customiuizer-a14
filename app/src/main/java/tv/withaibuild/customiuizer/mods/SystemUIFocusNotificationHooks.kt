package tv.withaibuild.customiuizer.mods

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarFocusNotificationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * HyperOS 1/2 status-bar focus-notification presentation.
 *
 * The height mode only changes the prompt root from WRAP_CONTENT to MATCH_PARENT, so the ROM keeps
 * owning its content, animation and actual status-bar height. The hide mode runs after the ROM's
 * visibility decision because HyperOS posts that decision to the main thread with a short delay.
 */
object SystemUIFocusNotificationHooks {
    private const val FOCUSED_PROMPT_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptView"
    private const val COLLAPSED_STATUS_BAR_FRAGMENT_CLASS =
        "com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment"

    @JvmStatic
    fun install(
        lpparam: PackageReadyParam,
        mode: StatusBarFocusNotificationMode
    ) {
        if (mode == StatusBarFocusNotificationMode.SYSTEM_DEFAULT) return

        var focusedPromptViewId = 0
        ModuleHelper.findAndHookMethod(
            FOCUSED_PROMPT_VIEW_CLASS,
            lpparam.classLoader,
            "onFinishInflate",
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    try {
                        val view = param.getThisObject() as? View ?: return
                        if (view.id > 0) focusedPromptViewId = view.id
                        when (mode) {
                            StatusBarFocusNotificationMode.MATCH_STATUS_BAR_HEIGHT -> {
                                val layoutParams = view.layoutParams ?: return
                                if (layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                                    view.layoutParams = layoutParams
                                }
                            }
                            StatusBarFocusNotificationMode.HIDE -> view.visibility = View.GONE
                            StatusBarFocusNotificationMode.SYSTEM_DEFAULT -> Unit
                        }
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("StatusBarFocusNotification", t)
                    }
                }
            }
        )

        if (mode != StatusBarFocusNotificationMode.HIDE) return

        ModuleHelper.findAndHookMethod(
            COLLAPSED_STATUS_BAR_FRAGMENT_CLASS,
            lpparam.classLoader,
            "updateStatusBarVisibilities",
            Boolean::class.javaPrimitiveType!!,
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    try {
                        val statusBar = XposedHelpers.getObjectField(
                            param.getThisObject(),
                            "mStatusBar"
                        ) as? ViewGroup ?: return
                        if (focusedPromptViewId <= 0) {
                            focusedPromptViewId = statusBar.resources.getIdentifier(
                                "focused_notif_view",
                                "id",
                                "com.android.systemui"
                            )
                        }
                        if (focusedPromptViewId > 0) {
                            statusBar.findViewById<View>(focusedPromptViewId)?.visibility = View.GONE
                        }
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("StatusBarFocusNotification", t)
                    }
                }
            }
        )
    }
}
