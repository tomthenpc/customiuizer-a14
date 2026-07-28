package tv.withaibuild.customiuizer.mods

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import java.util.ArrayList

/**
 * SystemUI hooks that belong to no larger surface.
 *
 * The status bar, control centre, lock screen, notification shade and screenshot
 * hooks live in their own `SystemUI*Hooks` objects. What is left here is the power
 * menu, the MIUI strong toast and the charge animation — three unrelated surfaces,
 * none of which shares state with anything else.
 */
object SystemUI {

    @JvmStatic
    fun ExtendedPowerMenuHook(lpparam: PackageReadyParam) {
        val fastbootTitleId = MainModule.resHooks.addFakeResource("epm_fastboot_title", R.string.system_epm_action_fastboot_title, "string")
        val recoveryTitleId = MainModule.resHooks.addFakeResource("epm_recovery_title", R.string.system_epm_action_recovery_title, "string")

        var actionId = -1
        val DialogClass = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite", lpparam.classLoader)
        ModuleHelper.findAndHookConstructor("com.android.systemui.globalactions.GlobalActionsDialogLite\$SinglePressAction", lpparam.classLoader, DialogClass, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (actionId == 1) {
                    param.getArgs()[2] = fastbootTitleId
                } else if (actionId == 2) {
                    param.getArgs()[2] = recoveryTitleId
                }
            }
            override fun after(param: AfterHookCallback) {
                actionId = -1
            }
        })
        val PowerActionClass = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite\$PowerOptionsAction", lpparam.classLoader)
        ModuleHelper.findAndHookMethod("com.android.systemui.globalactions.GlobalActionsDialogLite", lpparam.classLoader, "createActionItems", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mItems = XposedHelpers.getObjectField(param.getThisObject(), "mItems") as ArrayList<Any>
                actionId = 1
                val fastbootAction = XposedHelpers.newInstance(PowerActionClass, param.getThisObject())
                actionId = 2
                val recoveryAction = XposedHelpers.newInstance(PowerActionClass, param.getThisObject())
                mItems.add(fastbootAction)
                mItems.add(recoveryAction)
            }
        })

        ModuleHelper.findAndHookMethod(PowerActionClass, "onPress", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mMessageResId = XposedHelpers.getIntField(param.getThisObject(), "mMessageResId")
                if (mMessageResId == fastbootTitleId || mMessageResId == recoveryTitleId) {
                    val actionsDialog = XposedHelpers.getSurroundingThis(param.getThisObject())
                    val mContext = XposedHelpers.getObjectField(actionsDialog, "mContext") as Context
                    val modRes = ModuleHelper.getModuleRes(mContext)
                    val SystemUIDialogClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.SystemUIDialog", lpparam.classLoader)
                    val confirmDlg = XposedHelpers.newInstance(SystemUIDialogClass, mContext) as AlertDialog
                    confirmDlg.setTitle(
                        modRes.getString(
                            if (mMessageResId == recoveryTitleId) R.string.system_epm_action_recovery_confirm_title else R.string.system_epm_action_fastboot_confirm_title
                        )
                    )
                    confirmDlg.setButton(-1, Resources.getSystem().getString(android.R.string.ok), DialogInterface.OnClickListener { _, _ ->
                        val pm = mContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                        val mService = XposedHelpers.getObjectField(pm, "mService")
                        if (mMessageResId == recoveryTitleId) {
                            XposedHelpers.callMethod(mService, "reboot", false, "recovery", false)
                        } else {
                            XposedHelpers.callMethod(mService, "reboot", false, "bootloader", false)
                        }
                    })
                    confirmDlg.setButton(-2, Resources.getSystem().getString(android.R.string.cancel), DialogInterface.OnClickListener { _, _ -> })
                    confirmDlg.show()
                    param.returnAndSkip(null)
                }
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.plugins.PluginEnablerImpl", lpparam.classLoader, "isEnabled", ComponentName::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val componentName = param.getArg(0) as ComponentName
                if (componentName.className.contains("GlobalActions")) {
                    param.returnAndSkip(false)
                }
            }
        })
    }

    @JvmStatic
    fun DisableStrongToastHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.toast.MIUIStrongToastControl", lpparam.classLoader, "showCustomStrongToast", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                var blockToast = MainModule.mPrefs.getBoolean("system_notif_disable_strong_toast_always", true)
                if (!blockToast) {
                    val dnd = MainModule.mPrefs.getBoolean("system_notif_disable_strong_toast_dnd", false)
                    if (dnd) {
                        val zenModeController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.policy.ZenModeController")
                        blockToast = XposedHelpers.callMethod(zenModeController, "isZenModeOn") as Boolean
                    }
                }
                if (blockToast) {
                    param.returnAndSkip(null)
                }
            }
        })
    }

    @JvmStatic
    fun TweakStrongToastHook(lpparam: PackageReadyParam) {
        val toastWidth = MainModule.mPrefs.getInt("system_notif_strong_toast_width", 100)
        if (toastWidth < 100) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "strong_toast_width_window", Math.ceil(3.37 * toastWidth))
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "strong_toast_width", Math.ceil(3.2 * toastWidth))
            ModuleHelper.hookAllMethods("com.android.systemui.toast.MIUIStrongToast", lpparam.classLoader, "showCustomStrongToast", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mStrongToastBottomView = XposedHelpers.getObjectField(param.getThisObject(), "mStrongToastBottomView") as View
                    mStrongToastBottomView.visibility = View.GONE
                    val mRLLeft = XposedHelpers.getObjectField(param.getThisObject(), "mRLLeft") as RelativeLayout
                    val layoutParams = mRLLeft.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.leftMargin = 0
                    mRLLeft.layoutParams = layoutParams
                }
            })
            ModuleHelper.findAndHookMethod("com.android.systemui.toast.MIUIStrongToast", lpparam.classLoader, "getWindowParam", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val lp = param.getResult() as WindowManager.LayoutParams
                    lp.width = Helpers.dp2px(3.2f * toastWidth).toInt()
                    param.setResult(lp)
                }
            })
        }
    }

    @JvmStatic
    fun NoLightUpOnChargeHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.charge.MiuiChargeController", lpparam.classLoader, "shouldShowChargeAnim", HookerClassHelper.returnConstant(false))
    }

}
