package tv.withaibuild.customiuizer.mods

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import miui.app.MiuiFreeFormManager
import miui.process.ForegroundInfo
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.hasConfiguredActionCode
import tv.withaibuild.customiuizer.mods.utils.hasConfiguredToggle

object GlobalActionSystemServerHooks {

    private const val XIAOMI_UPDATER_PACKAGE = "com.android.updater"

    @JvmStatic
    fun setupAnimationScaleBridge(lpparam: XposedModuleInterface.SystemServerStartingParam) {
        ModuleHelper.hookAllMethods(
            "com.android.server.policy.BaseMiuiPhoneWindowManager",
            lpparam.classLoader,
            "initInternal",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val windowManager = callback.getThisObject() ?: return
                    val context = XposedHelpers.getObjectField(windowManager, "mContext") as? Context
                        ?: return
                    val filter = IntentFilter(GlobalActions.SET_ANIMATION_SCALE_ACTION)
                    ModuleHelper.registerModuleReceiver(
                        context,
                        "animationScaleBridgeReceiver",
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                var handled = false
                                ModuleHelper.guarded {
                                    if (intent.action != GlobalActions.SET_ANIMATION_SCALE_ACTION) {
                                        return@guarded
                                    }
                                    if (!ModuleHelper.isTrustedBroadcast(
                                            this,
                                            Helpers.modulePkg,
                                            rejectionResultCode = GlobalActions.ACTION_FAILED
                                        )
                                    ) {
                                        return@guarded
                                    }
                                    val type = intent.getIntExtra(
                                        GlobalActions.EXTRA_ANIMATION_SCALE_TYPE,
                                        -1
                                    )
                                    val value = intent.getFloatExtra(
                                        GlobalActions.EXTRA_ANIMATION_SCALE_VALUE,
                                        Float.NaN
                                    )
                                    val key = resolveAnimationScaleKey(type)
                                    if (key != null && value.isFinite() && value in 0f..10f) {
                                        handled = Settings.Global.putFloat(
                                            context.contentResolver,
                                            key,
                                            value
                                        )
                                    }
                                }
                                if (isOrderedBroadcast) {
                                    ModuleHelper.guarded {
                                        resultCode = if (handled) {
                                            GlobalActions.ACTION_HANDLED
                                        } else {
                                            GlobalActions.ACTION_FAILED
                                        }
                                    }
                                }
                            }
                        },
                        filter,
                        Context.RECEIVER_EXPORTED,
                        GlobalActions.BROADCAST_PERMISSION
                    )
                }
            }
        )
    }

    @JvmStatic
    fun setupUpdaterServicesBridge(lpparam: XposedModuleInterface.SystemServerStartingParam) {
        ModuleHelper.hookAllMethods(
            "com.android.server.policy.BaseMiuiPhoneWindowManager",
            lpparam.classLoader,
            "initInternal",
            object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val owner = callback.getThisObject() ?: return
                    val context = XposedHelpers.getObjectField(owner, "mContext") as? Context ?: return
                    ModuleHelper.registerModuleReceiver(
                        context,
                        "updaterServicesBridgeReceiver",
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                var handled = false
                                ModuleHelper.guarded {
                                    if (intent.action != GlobalActions.SET_UPDATER_SERVICES_ACTION) return@guarded
                                    if (!ModuleHelper.isTrustedBroadcast(
                                            this,
                                            Helpers.modulePkg,
                                            rejectionResultCode = GlobalActions.ACTION_FAILED
                                        )
                                    ) return@guarded
                                    val names = intent.getStringArrayExtra(
                                        GlobalActions.EXTRA_UPDATER_SERVICE_NAMES
                                    ) ?: return@guarded
                                    val states = intent.getIntArrayExtra(
                                        GlobalActions.EXTRA_UPDATER_SERVICE_STATES
                                    ) ?: return@guarded
                                    handled = applyUpdaterServiceStates(context, names, states)
                                }
                                if (isOrderedBroadcast) {
                                    ModuleHelper.guarded {
                                        resultCode = if (handled) {
                                            GlobalActions.ACTION_HANDLED
                                        } else {
                                            GlobalActions.ACTION_FAILED
                                        }
                                    }
                                }
                            }
                        },
                        IntentFilter(GlobalActions.SET_UPDATER_SERVICES_ACTION),
                        Context.RECEIVER_EXPORTED,
                        GlobalActions.BROADCAST_PERMISSION
                    )
                }
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun applyUpdaterServiceStates(
        context: Context,
        names: Array<String>,
        states: IntArray
    ): Boolean {
        if (names.isEmpty() || names.size > 32 || names.size != states.size) return false
        if (names.toSet().size != names.size || states.any { !isAllowedComponentState(it) }) return false
        val packageManager = context.packageManager
        val declared = try {
            packageManager.getPackageInfo(
                XIAOMI_UPDATER_PACKAGE,
                PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
            ).services?.mapTo(HashSet()) { it.name } ?: emptySet()
        } catch (t: Throwable) {
            tv.withaibuild.customiuizer.mods.utils.FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            return false
        }
        if (names.any { it !in declared }) return false

        val components = names.map { ComponentName(XIAOMI_UPDATER_PACKAGE, it) }
        val original = IntArray(components.size) { index ->
            packageManager.getComponentEnabledSetting(components[index])
        }
        var changed = 0
        return try {
            for (index in components.indices) {
                packageManager.setComponentEnabledSetting(
                    components[index],
                    states[index],
                    PackageManager.DONT_KILL_APP
                )
                changed++
            }
            true
        } catch (t: Throwable) {
            tv.withaibuild.customiuizer.mods.utils.FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            for (index in 0 until changed) {
                try {
                    packageManager.setComponentEnabledSetting(
                        components[index],
                        original[index],
                        PackageManager.DONT_KILL_APP
                    )
                } catch (rollback: Throwable) {
                    tv.withaibuild.customiuizer.mods.utils.FatalErrors.unwrapAndRethrowIfFatal(rollback)
                    XposedHelpers.log(rollback)
                }
            }
            false
        }
    }

    @JvmStatic
    internal fun isAllowedComponentState(state: Int): Boolean =
        state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

    @JvmStatic
    internal fun resolveAnimationScaleKey(type: Int): String? = when (type) {
        0 -> Settings.Global.WINDOW_ANIMATION_SCALE
        1 -> Settings.Global.TRANSITION_ANIMATION_SCALE
        2 -> Settings.Global.ANIMATOR_DURATION_SCALE
        else -> null
    }

    @JvmStatic
    fun setupGlobalActions(lpparam: XposedModuleInterface.SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, "initInternal", object : MethodHook() {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            override fun after(callback: AfterHookCallback) {
                val thisObject = callback.getThisObject()!!
                val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                val intentfilter = IntentFilter()
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "SimulateMenu")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ForceClose")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleColorInversion")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "SwitchToPrevApp")
                ModuleHelper.registerModuleReceiver(mContext, "phoneWindowManagerActionReceiver", object : BroadcastReceiver() {
                    @SuppressLint("MissingPermission")
                    override fun onReceive(context: Context, intent: Intent) {
                        var completed = false
                        ModuleHelper.guarded {
                            val action = intent.action ?: return@guarded
                            if (!ModuleHelper.isTrustedBroadcast(
                                    this,
                                    Helpers.modulePkg,
                                    "android",
                                    "com.android.systemui",
                                    "com.miui.home",
                                    rejectionResultCode = GlobalActions.ACTION_FAILED
                                )
                            ) {
                                return@guarded
                            }

                            when (action) {
                                GlobalActions.ACTION_PREFIX + "SimulateMenu" -> {
                                    try {
                                        val fRequestShowMenu = XposedHelpers.findField(thisObject.javaClass.superclass, "mRequestShowMenu")
                                        fRequestShowMenu.setAccessible(true)
                                        fRequestShowMenu.set(thisObject, true)
                                        val markShortcutTriggered = thisObject.javaClass.superclass!!.getDeclaredMethod("markShortcutTriggered")
                                        markShortcutTriggered.setAccessible(true)
                                        markShortcutTriggered.invoke(thisObject)
                                        val injectEvent = thisObject.javaClass.superclass!!.getDeclaredMethod("injectEvent", Int::class.javaPrimitiveType!!)
                                        injectEvent.setAccessible(true)
                                        injectEvent.invoke(thisObject, 82)
                                        completed = true
                                    } catch (t1: Throwable) {
                                        try {
                                            val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler
                                            completed = mHandler.sendMessageDelayed(mHandler.obtainMessage(1, "show_menu"), android.view.ViewConfiguration.getLongPressTimeout().toLong())
                                        } catch (t2: Throwable) {
                                            XposedHelpers.log(t2)
                                        }
                                    }
                                }
                                GlobalActions.ACTION_PREFIX + "ForceClose" -> {
                                    try {
                                        val closeApp = thisObject.javaClass.superclass!!.getDeclaredMethod("closeApp")
                                        closeApp.setAccessible(true)
                                        closeApp.invoke(thisObject)
                                        completed = true
                                    } catch (t: Throwable) {
                                        XposedHelpers.log(t)
                                    }
                                }
                                GlobalActions.ACTION_PREFIX + "ToggleColorInversion" -> {
                                    try {
                                        val originalValue = Settings.Secure.getInt(context.contentResolver, "accessibility_display_inversion_enabled")
                                        val conflictProp = ModuleHelper.proxySystemProperties("getInt", "ro.df.effect.conflict", 0, null) as Int
                                        val conflictProp2 = ModuleHelper.proxySystemProperties("getInt", "ro.vendor.df.effect.conflict", 0, null) as Int
                                        val hasConflict = conflictProp == 1 || conflictProp2 == 1
                                        val dfMgr = XposedHelpers.callStaticMethod(XposedHelpers.findClass("miui.hardware.display.DisplayFeatureManager", null), "getInstance")

                                        // Enabling: pre-apply the conflict workaround, then put the setting.
                                        // If putInt fails, try to restore the previous screen effect.
                                        // Disabling: put the setting first, then disable the conflict workaround.
                                        // If the workaround fails, restore the original setting so the next action does
                                        // not run the opposite direction just because the Secure setting changed.
                                        val enabling = originalValue == 0
                                        var stateChangeCompleted = false

                                        if (hasConflict && enabling) {
                                            XposedHelpers.callMethod(dfMgr, "setScreenEffect", 15, 1)
                                        }

                                        val putOk = try {
                                            Settings.Secure.putInt(context.contentResolver, "accessibility_display_inversion_enabled", if (enabling) 1 else 0)
                                        } catch (t: Throwable) {
                                            XposedHelpers.log(t)
                                            false
                                        }

                                        if (putOk) {
                                            stateChangeCompleted = true
                                            if (hasConflict && !enabling) {
                                                try {
                                                    XposedHelpers.callMethod(dfMgr, "setScreenEffect", 15, 0)
                                                } catch (t: Throwable) {
                                                    // The setting changed but the workaround did not; the action is not
                                                    // fully complete, so do not claim it as handled. Try to roll the
                                                    // setting back so the next trigger does not run the opposite action.
                                                    XposedHelpers.log(t)
                                                    stateChangeCompleted = false
                                                    try {
                                                        Settings.Secure.putInt(context.contentResolver, "accessibility_display_inversion_enabled", originalValue)
                                                    } catch (rollback: Throwable) {
                                                        XposedHelpers.log(rollback)
                                                    }
                                                }
                                            }
                                        } else if (hasConflict && enabling) {
                                            // putInt failed after we enabled the workaround; try to roll it back.
                                            try {
                                                XposedHelpers.callMethod(dfMgr, "setScreenEffect", 15, 0)
                                            } catch (t: Throwable) {
                                                XposedHelpers.log(t)
                                            }
                                        }

                                        completed = stateChangeCompleted
                                    } catch (e: Settings.SettingNotFoundException) {
                                        XposedHelpers.log(e)
                                    }
                                }
                                GlobalActions.ACTION_PREFIX + "SwitchToPrevApp" -> {
                                    val pm = context.packageManager
                                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                    val rti = am.getRecentTasks(15, 0)

                                    val topAct = am.getRunningTasks(1)[0]
                                    var moved = false
                                    for (rtitem in rti) {
                                        if (topAct.topActivity == rtitem.topActivity) continue

                                        var isLauncher = false
                                        val recentIntent = Intent(rtitem.baseIntent)
                                        if (rtitem.origActivity != null) recentIntent.setComponent(rtitem.origActivity)
                                        val resolvedAct = recentIntent.resolveActivity(pm)
                                        if (resolvedAct != null && "com.miui.home" == resolvedAct.packageName) {
                                            isLauncher = true
                                        }

                                        if (!isLauncher) {
                                            try {
                                                if (rtitem.taskId >= 0) am.moveTaskToFront(rtitem.taskId, 0) else context.startActivity(recentIntent)
                                                moved = true
                                                break
                                            } catch (e: Throwable) {
                                                XposedHelpers.log(e)
                                            }
                                        }
                                    }
                                    completed = moved
                                }
                            }
                        }

                        if (isOrderedBroadcast) {
                            ModuleHelper.guarded {
                                setResultCode(
                                    if (completed) {
                                        GlobalActions.ACTION_HANDLED
                                    } else {
                                        GlobalActions.ACTION_FAILED
                                    }
                                )
                            }
                        }
                    }
            }, intentfilter, Context.RECEIVER_EXPORTED)
            }
        })
    }

    @JvmStatic
    fun setupStatusBar(lpparam: PackageReadyParam) {
        val statusBarClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.CentralSurfacesImpl", lpparam.classLoader)
        if (statusBarClass == null) return
        ModuleHelper.findAndHookMethod(statusBarClass, "start", object : MethodHook() {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            override fun after(param: AfterHookCallback) {
                val thisObject = param.getThisObject()
                val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                val intentfilter = IntentFilter()

                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ExpandNotifications")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ExpandSettings")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "OpenRecents")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "OpenVolumeDialog")

                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleGPS")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleHotspot")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleZenMode")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleFlashlight")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleNightMode")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleWiFi")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleBluetooth")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleNFC")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleSoundProfile")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleAutoRotation")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleMobileData")

                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ClearMemory")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ClearNotifications")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "RestartSystemUI")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "RestartLauncher")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "RestartSecurityCenter")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "FloatingWindow")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "SwitchOneHanded")

                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ScrollToTop")

                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "WakeUp")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "GoToSleep")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "LockDevice")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "TakeScreenshot")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "OpenPowerMenu")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "VolumeUp")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "VolumeDown")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "GoBack")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "LaunchIntent")
                intentfilter.addAction(GlobalActions.ACTION_PREFIX + "SaveLastMusicPausedTime")

                ModuleHelper.registerModuleReceiver(mContext, "statusBarActionReceiver", GlobalActions.mSBReceiver, intentfilter, Context.RECEIVER_EXPORTED)
            }
        })

        if (hasConfiguredActionCode(28)) {
            ModuleHelper.findAndHookMethod("com.android.wm.shell.miuifreeform.MiuiFreeformModeController", lpparam.classLoader, "onInit", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val thisObject = param.getThisObject()
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val intentfilter = IntentFilter()
                    intentfilter.addAction(GlobalActions.ACTION_PREFIX + "PinningWindow")
                    val mFreeFormReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                            if (intent.action == null) return@guarded
                            val action = intent.action
                            if (action == GlobalActions.ACTION_PREFIX + "PinningWindow") {
                                val foregroundInfo = ProcessManager.getForegroundInfo()
                                if (foregroundInfo != null) {
                                    val topPackage = foregroundInfo.mForegroundPackageName
                                    if ("com.miui.home" == topPackage) return@guarded
                                } else return@guarded

                                val activityTaskManagerCls = XposedHelpers.findClassIfExists("android.app.ActivityTaskManager", context.classLoader)
                                val activityTaskManager = XposedHelpers.callStaticMethod(activityTaskManagerCls, "getService")
                                val freeFormStackInfoList = MiuiFreeFormManager.getAllFreeFormStackInfosOnDisplay(0)
                                var freeFormCount = 0
                                if (freeFormStackInfoList != null) freeFormCount = freeFormStackInfoList.size
                                if (freeFormCount == 2) return@guarded
                                val rootTaskInfos = XposedHelpers.callMethod(activityTaskManager, "getAllRootTaskInfosOnDisplay", 0) as List<*>
                                val freeformController = thisObject
                                for (rootTaskInfo in rootTaskInfos) {
                                    val conf = XposedHelpers.getObjectField(rootTaskInfo, "configuration")
                                    val windowConfiguration = XposedHelpers.getObjectField(conf, "windowConfiguration")
                                    val wmode = XposedHelpers.getIntField(windowConfiguration, "mWindowingMode")
                                    val mActivityType = XposedHelpers.getIntField(windowConfiguration, "mActivityType")
                                    if (wmode < 2 && mActivityType < 2) {
                                        val taskId = XposedHelpers.getIntField(rootTaskInfo, "taskId")
                                        XposedHelpers.callMethod(freeformController, "freeformFullscreenTask", taskId)
                                        val myhandler = Handler(Looper.myLooper()!!)
                                        val removeBg = object : Runnable {
                                            override fun run() = ModuleHelper.guarded {
                                                myhandler.removeCallbacks(this)
                                                XposedHelpers.callMethod(freeformController, "pinAllFreeForm")
                                            }
                                        }
                                        myhandler.postDelayed(removeBg, 200)
                                        return@guarded
                                    }
                                }
                            }
                        }
                    }
                    ModuleHelper.registerModuleReceiver(mContext, "freeformModeReceiver", mFreeFormReceiver, intentfilter, Context.RECEIVER_EXPORTED)
                }
            })
        }
        if (hasConfiguredActionCode(29)) {
            ModuleHelper.findAndHookMethod("com.android.wm.shell.sosc.SoScSplitScreenController", lpparam.classLoader, "onInit", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val thisObject = param.getThisObject()
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val intentfilter = IntentFilter()
                    intentfilter.addAction(GlobalActions.ACTION_PREFIX + "SplitScreen")
                    val mFreeFormReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                            if (intent.action == null) return@guarded
                            val action = intent.action
                            if (action == GlobalActions.ACTION_PREFIX + "SplitScreen") {
                                val foregroundInfo = ProcessManager.getForegroundInfo()
                                if (foregroundInfo != null) {
                                    val topPackage = foregroundInfo.mForegroundPackageName
                                    if ("com.miui.home" == topPackage) return@guarded
                                } else return@guarded

                                val activityTaskManagerCls = XposedHelpers.findClassIfExists("android.app.ActivityTaskManager", context.classLoader)
                                val activityTaskManager = XposedHelpers.callStaticMethod(activityTaskManagerCls, "getService")
                                val rootTaskInfos = XposedHelpers.callMethod(activityTaskManager, "getAllRootTaskInfosOnDisplay", 0) as List<*>
                                val freeformController = thisObject
                                for (rootTaskInfo in rootTaskInfos) {
                                    val conf = XposedHelpers.getObjectField(rootTaskInfo, "configuration")
                                    val windowConfiguration = XposedHelpers.getObjectField(conf, "windowConfiguration")
                                    val wmode = XposedHelpers.getIntField(windowConfiguration, "mWindowingMode")
                                    val mActivityType = XposedHelpers.getIntField(windowConfiguration, "mActivityType")
                                    if (wmode < 2 && mActivityType < 2) {
                                        val taskId = XposedHelpers.getIntField(rootTaskInfo, "taskId")
                                        XposedHelpers.callMethod(freeformController, "startTask", taskId, 0, null)
                                        return@guarded
                                    }
                                }
                            }
                        }
                    }
                    ModuleHelper.registerModuleReceiver(mContext, "soScSplitScreenReceiver", mFreeFormReceiver, intentfilter, Context.RECEIVER_EXPORTED)
                }
            })
        }
        if (hasConfiguredToggle(6)) {
            ModuleHelper.hookAllConstructors("com.android.systemui.controlcenter.policy.AutoBrightnessController", lpparam.classLoader, object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    val thisObject = callback.getThisObject()!!
                    val mContext = XposedHelpers.getObjectField(thisObject, "context") as Context
                    val intentfilter = IntentFilter()
                    intentfilter.addAction(GlobalActions.ACTION_PREFIX + "ToggleAutoBrightness")
                    val mFreeFormReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                            if (intent.action == null) return@guarded
                            val action = intent.action
                            if (action == GlobalActions.ACTION_PREFIX + "ToggleAutoBrightness") {
                                val modRes = ModuleHelper.getModuleRes(mContext)
                                val enabled = XposedHelpers.getBooleanField(thisObject, "enabled")
                                XposedHelpers.callMethod(thisObject, "toggleAutoBrightness")
                                if (enabled) {
                                    Toast.makeText(context, modRes.getString(R.string.toggle_autobright_off), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, modRes.getString(R.string.toggle_autobright_on), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    ModuleHelper.registerModuleReceiver(mContext, "autoBrightnessReceiver", mFreeFormReceiver, intentfilter, Context.RECEIVER_EXPORTED)
                }
            })
        }
    }
}
