package tv.withaibuild.customiuizer.mods

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.ArrayMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import org.json.JSONObject
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.HookUtils
import tv.withaibuild.customiuizer.utils.PrefPair
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Collection
import java.util.Collections
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

/**
 * Keyguard, unlock and app-lock hooks.
 * Covers PIN scrambling, trusted-unlock conditions, the no-screen-lock path, app
 * lock timeouts, and the lock screen's own clock, alarm, hint and wallpaper.
 */
object SystemLockScreenHooks {

    @JvmStatic
    fun ScramblePINHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardPINView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mViews = XposedHelpers.getObjectField(thisObject, "mViews") as Array<Array<View?>>?
                    val mRandomViews = ArrayList<View>()
                    if (mViews != null) {
                        for (row in 1..3) {
                            for (col in 0..2) {
                                mViews[row][col]?.let { mRandomViews.add(it) }
                            }
                        }
                        mViews[4][1]?.let { mRandomViews.add(it) }
                        Collections.shuffle(mRandomViews)

                        val pinview = thisObject as View
                        val row1 = pinview.findViewById<ViewGroup>(HookUtils.getResId(pinview.resources, "row1", "id", "com.android.systemui"))
                        val row2 = pinview.findViewById<ViewGroup>(HookUtils.getResId(pinview.resources, "row2", "id", "com.android.systemui"))
                        val row3 = pinview.findViewById<ViewGroup>(HookUtils.getResId(pinview.resources, "row3", "id", "com.android.systemui"))
                        val row4 = pinview.findViewById<ViewGroup>(HookUtils.getResId(pinview.resources, "row4", "id", "com.android.systemui"))

                        row1.removeAllViews()
                        row2.removeAllViews()
                        row3.removeAllViews()
                        row4.removeViewAt(1)

                        mViews[1] = arrayOf(mRandomViews[0], mRandomViews[1], mRandomViews[2])
                        row1.addView(mRandomViews[0])
                        row1.addView(mRandomViews[1])
                        row1.addView(mRandomViews[2])

                        mViews[2] = arrayOf(mRandomViews[3], mRandomViews[4], mRandomViews[5])
                        row2.addView(mRandomViews[3])
                        row2.addView(mRandomViews[4])
                        row2.addView(mRandomViews[5])

                        mViews[3] = arrayOf(mRandomViews[6], mRandomViews[7], mRandomViews[8])
                        row3.addView(mRandomViews[6])
                        row3.addView(mRandomViews[7])
                        row3.addView(mRandomViews[8])

                        mViews[4] = arrayOf(null, mRandomViews[9], mViews[4][2])
                        row4.addView(mRandomViews[9], 1)

                        XposedHelpers.setObjectField(thisObject, "mViews", mViews)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NoPasswordHook(lpparam: PackageReadyParam) {
        val isAllowed = "isBiometricAllowedForUser"
        ModuleHelper.findAndHookMethod("com.android.internal.widget.LockPatternUtils\$StrongAuthTracker", lpparam.classLoader, isAllowed, Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("com.android.internal.widget.LockPatternUtils", lpparam.classLoader, isAllowed, Int::class.javaPrimitiveType!!, HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun EnhancedSecurityHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "interceptPowerKeyDown", KeyEvent::class.java, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mPWMContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val kgMgr = mPWMContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (kgMgr.isKeyguardLocked && kgMgr.isKeyguardSecure) {
                        val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler?
                        if (mHandler != null) {
                            val mEndCallLongPress = XposedHelpers.getObjectField(thisObject, "mEndCallLongPress") as Runnable?
                            if (mEndCallLongPress != null) mHandler.removeCallbacks(mEndCallLongPress)
                        }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        val preventPowerHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val mPWMContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val kgMgr = mPWMContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (kgMgr.isKeyguardLocked && kgMgr.isKeyguardSecure) { skipped = true; result = null; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "powerLongPress", Long::class.javaPrimitiveType!!, preventPowerHook)
        ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "showGlobalActions", preventPowerHook)
        ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "showGlobalActionsInternal", preventPowerHook)
    }

    private fun isAuthOnce(): Boolean {
        val req = MainModule.mPrefs.getStringAsInt("system_noscreenlock_req", 1)
        if (req <= 1) return true
        if (req == 2 && !isUnlockedWithFingerprint && !isUnlockedWithStrong) return false
        if (req == 3 && !isUnlockedWithStrong) return false
        return true
    }

    private fun isTrusted(mContext: Context, classLoader: ClassLoader): Boolean {
        return isTrustedWiFi(mContext) || isTrustedBt(classLoader)
    }

    private fun isTrustedWiFi(mContext: Context): Boolean {
        val wifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        if (wifiManager == null || !wifiManager.isWifiEnabled) return false
        val trustedNetworks = MainModule.mPrefs.getStringSet("system_noscreenlock_wifi")
        val bssid = wifiManager.connectionInfo.bssid ?: ""
        return PrefPair.containsFirst(trustedNetworks, bssid)
    }

    @SuppressLint("MissingPermission")
    private fun isTrustedBt(classLoader: ClassLoader): Boolean {
        try {
            val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) return false
            val trustedDevices = MainModule.mPrefs.getStringSet("system_noscreenlock_bt")
            val mController = ModuleHelper.getDepInstance(classLoader, "com.android.systemui.statusbar.policy.BluetoothController")
            val cachedDevices = XposedHelpers.callMethod(mController, "getDevices") as Collection<*>?
            if (cachedDevices != null) {
                for (device in cachedDevices) {
                    val mDevice = XposedHelpers.getObjectField(device, "mDevice") as BluetoothDevice?
                    if (mDevice == null) continue
                    if (mDevice.bondState == BluetoothDevice.BOND_BONDED &&
                        XposedHelpers.callMethod(device, "isConnected") as Boolean &&
                        PrefPair.containsFirst(trustedDevices, mDevice.address ?: "")
                    ) return true
                }
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
        return false
    }

    private fun isUnlocked(mContext: Context, classLoader: ClassLoader): Boolean {
        if (!isAuthOnce()) return false
        var opt = MainModule.mPrefs.getStringAsInt("system_noscreenlock", 1)
        if (forcedOption == 1) opt = 2
        if (opt == 2) return true
        return if (opt == 3) isTrusted(mContext, classLoader) else false
    }

    private var isUnlockedInnerCall = false

    private var isUnlockedWithFingerprint = false

    private var isUnlockedWithStrong = false

    private var isChargingInfoHooked = false

    private var forcedOption = -1

    @JvmStatic
    fun NoScreenLockHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.keyguard.KeyguardViewMediator", lpparam.classLoader, "handleKeyguardDone", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    if (isUnlockedInnerCall) {
                        isUnlockedInnerCall = false
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    isUnlockedWithStrong = true

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardUpdateMonitor", lpparam.classLoader, "onFingerprintAuthenticated", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    isUnlockedWithFingerprint = true

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardSecurityContainerController", lpparam.classLoader, "onInit", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mContext = XposedHelpers.callMethod(thisObject, "getContext") as Context
                    val oldunlockStrongAuthReceiver = XposedHelpers.getAdditionalInstanceField(thisObject, "unlockStrongAuthReceiver")
                    if (oldunlockStrongAuthReceiver is BroadcastReceiver) {
                        try { mContext.unregisterReceiver(oldunlockStrongAuthReceiver) } catch (ignore: Throwable) {}
                    }
                    val unlockStrongAuthReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            try {
                                val mCallback = XposedHelpers.getObjectField(thisObject, "mKeyguardSecurityCallback")
                                XposedHelpers.callMethod(mCallback, "reportUnlockAttempt", 0, 0, 0, true)
                            } catch (t: Throwable) {
                                XposedHelpers.log(t)
                            }
                        }
                    }
                    XposedHelpers.setAdditionalInstanceField(thisObject, "unlockStrongAuthReceiver", unlockStrongAuthReceiver)
                    ModuleHelper.registerModuleReceiver(
                        mContext,
                        "unlockStrongAuthReceiver",
                        unlockStrongAuthReceiver,
                        IntentFilter(GlobalActions.ACTION_PREFIX + "UnlockStrongAuth"),
                        Context.RECEIVER_NOT_EXPORTED
                    )

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.keyguard.KeyguardViewMediator", lpparam.classLoader, "doKeyguardLocked", Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    if (forcedOption == 0) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    if (!isUnlocked(mContext, lpparam.classLoader)) { return XposedHelpers.proceedOrThrow(chain, throwable) }

                    val skip = MainModule.mPrefs.getBoolean("system_noscreenlock_skip")
                    if (skip) {
                        XposedHelpers.callMethod(thisObject, "keyguardDone")
                        skipped = true; result = null; throwable = null
                    }
                    isUnlockedInnerCall = true
                    val unlockIntent = Intent(GlobalActions.ACTION_PREFIX + "UnlockStrongAuth")
                    unlockIntent.setPackage("com.android.systemui")
                    mContext.sendBroadcast(unlockIntent)

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.keyguard.KeyguardViewMediator", lpparam.classLoader, "setupLocked", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val filter = IntentFilter()
                    filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    filter.addAction(GlobalActions.ACTION_PREFIX + "UnlockSetForced")
                    filter.addAction(GlobalActions.ACTION_PREFIX + "BTConnectionChanged")
                    val oldnoScreenLockReceiver = XposedHelpers.getAdditionalInstanceField(thisObject, "noScreenLockReceiver")
                    if (oldnoScreenLockReceiver is BroadcastReceiver) {
                        try { mContext.unregisterReceiver(oldnoScreenLockReceiver) } catch (ignore: Throwable) {}
                    }
                    val noScreenLockReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val action = intent.action ?: return
                            val fromPackage = getSentFromPackage()
                            when (action) {
                                GlobalActions.ACTION_PREFIX + "UnlockSetForced" -> if (fromPackage != Helpers.modulePkg) return
                                GlobalActions.ACTION_PREFIX + "BTConnectionChanged" -> if (fromPackage != "com.android.systemui") return
                                WifiManager.NETWORK_STATE_CHANGED_ACTION -> { }
                                else -> return
                            }

                            if (action == GlobalActions.ACTION_PREFIX + "UnlockSetForced")
                                forcedOption = intent.getIntExtra("system_noscreenlock_force", -1)

                            val isShowing = XposedHelpers.getBooleanField(thisObject, "mShowing")
                            if (!isShowing) return
                            if (!isAuthOnce()) return

                            var isTrusted = false
                            if (forcedOption == 1) isTrusted = true
                            else if (forcedOption != 0 && MainModule.mPrefs.getStringAsInt("system_noscreenlock", 1) == 3) {
                                if (action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                                    val netInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                                    if (netInfo == null) return
                                    if (netInfo.state != NetworkInfo.State.CONNECTED && netInfo.state != NetworkInfo.State.DISCONNECTED)
                                        return
                                    if (netInfo.isConnected) isTrusted = isTrustedWiFi(mContext)
                                } else if (action == GlobalActions.ACTION_PREFIX + "BTConnectionChanged") {
                                    isTrusted = isTrustedBt(lpparam.classLoader)
                                }
                            }

                            if (isTrusted) {
                                val skip = MainModule.mPrefs.getBoolean("system_noscreenlock_skip")
                                if (skip)
                                    XposedHelpers.callMethod(thisObject, "keyguardDone")
                                else
                                    XposedHelpers.callMethod(thisObject, "resetStateLocked", false)
                                isUnlockedInnerCall = true
                                val unlockIntent = Intent(GlobalActions.ACTION_PREFIX + "UnlockStrongAuth")
                                unlockIntent.setPackage("com.android.systemui")
                                mContext.sendBroadcast(unlockIntent)
                            } else try {
                                XposedHelpers.callMethod(thisObject, "resetStateLocked", true)
                            } catch (t: Throwable) {
                                XposedHelpers.log(t)
                            }
                        }
                    }
                    XposedHelpers.setAdditionalInstanceField(thisObject, "noScreenLockReceiver", noScreenLockReceiver)
                    ModuleHelper.registerModuleReceiver(mContext, "noScreenLockReceiver", noScreenLockReceiver, filter, Context.RECEIVER_EXPORTED)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardSecurityModel", lpparam.classLoader, "getSecurityMode", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    if (forcedOption == 0) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val skip = MainModule.mPrefs.getBoolean("system_noscreenlock_skip")
                    if (skip) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mKeyguardUpdateMonitor = XposedHelpers.getObjectField(thisObject, "mKeyguardUpdateMonitor")
                    val mContext = XposedHelpers.getObjectField(mKeyguardUpdateMonitor, "mContext") as Context
                    if (!isUnlocked(mContext, lpparam.classLoader)) { return XposedHelpers.throwOrReturn(throwable, result) }

                    val securityModeEnum = XposedHelpers.findClass("com.android.keyguard.KeyguardSecurityModel\$SecurityMode", lpparam.classLoader)
                    val securityModeNone = XposedHelpers.getStaticObjectField(securityModeEnum, "None")
                    val securityModePassword = XposedHelpers.getStaticObjectField(securityModeEnum, "Password")
                    val securityModePattern = XposedHelpers.getStaticObjectField(securityModeEnum, "Pattern")
                    val securityModePin = XposedHelpers.getStaticObjectField(securityModeEnum, "PIN")

                    val secModeResult = result
                    if (securityModePassword == secModeResult ||
                        securityModePattern == secModeResult ||
                        securityModePin == secModeResult
                    ) { result = securityModeNone; throwable = null }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.BluetoothControllerImpl", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mContext = chain.getArg(0) as Context
                    val fetchCachedDevicesReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                            if (getSentFromPackage() != Helpers.modulePkg) return@guarded
                            val deviceList = ArrayList<BluetoothDevice>()
                            val updateIntent = Intent(GlobalActions.EVENT_PREFIX + "CACHEDDEVICESUPDATE")
                            val cachedDevices = XposedHelpers.callMethod(thisObject, "getDevices") as Collection<*>?
                            if (cachedDevices != null) {
                                for (device in cachedDevices) {
                                    val mDevice = XposedHelpers.getObjectField(device, "mDevice") as BluetoothDevice?
                                    if (mDevice != null) deviceList.add(mDevice)
                                }
                            }
                            updateIntent.putParcelableArrayListExtra("device_list", deviceList)
                            updateIntent.setPackage(Helpers.modulePkg)
                            ModuleHelper.sendBroadcastWithIdentity(mContext, updateIntent)
                        }
                    }
                    ModuleHelper.registerModuleReceiver(
                        mContext,
                        "fetchCachedDevicesReceiver",
                        fetchCachedDevicesReceiver,
                        IntentFilter(GlobalActions.ACTION_PREFIX + "FetchCachedDevices"),
                        Context.RECEIVER_EXPORTED,
                        GlobalActions.BROADCAST_PERMISSION
                    )

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BluetoothControllerImpl", lpparam.classLoader, "updateConnected", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context?
                    if (mContext != null) {
                        ModuleHelper.sendBroadcastWithIdentity(mContext, Intent(GlobalActions.ACTION_PREFIX + "BTConnectionChanged"))
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun DoubleTapToSleepHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.shade.NotificationsQuickSettingsContainer", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val view = thisObject as View
                    ModuleHelper.setViewInfo(view, "currentTouchTime", 0L)
                    ModuleHelper.setViewInfo(view, "currentTouchX", 0F)
                    ModuleHelper.setViewInfo(view, "currentTouchY", 0F)

                    view.setOnTouchListener { v, event ->
                        ModuleHelper.guarded {
                            if (event.action != MotionEvent.ACTION_DOWN) return@guarded

                            val lastTouchTime = ModuleHelper.getViewInfo(view, "currentTouchTime") as Long
                            val lastTouchX = ModuleHelper.getViewInfo(view, "currentTouchX") as Float
                            val lastTouchY = ModuleHelper.getViewInfo(view, "currentTouchY") as Float

                            var currentTouchTime = java.lang.System.currentTimeMillis()
                            val currentTouchX = event.x
                            val currentTouchY = event.y

                            if (currentTouchTime - lastTouchTime < 250L && Math.abs(currentTouchX - lastTouchX) < 100F && Math.abs(currentTouchY - lastTouchY) < 100F) {
                                val keyguardMgr = v.context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                                if (keyguardMgr.isKeyguardLocked) GlobalActions.commonSendAction(v.context, "GoToSleep")
                                currentTouchTime = 0L
                            }

                            ModuleHelper.setViewInfo(view, "currentTouchTime", currentTouchTime)
                            ModuleHelper.setViewInfo(view, "currentTouchX", currentTouchX)
                            ModuleHelper.setViewInfo(view, "currentTouchY", currentTouchY)
                        }
                        false
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ShowNotificationsAfterUnlockHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.interruption.KeyguardNotificationVisibilityProviderImpl", lpparam.classLoader, "shouldHideNotification", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val notification = XposedHelpers.getObjectField(chain.getArg(0), "mSbn")
                    XposedHelpers.setObjectField(notification, "mHasShownAfterUnlock", false)

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun AppLockHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.miui.server.SecurityManagerService", lpparam.classLoader, "removeAccessControlPassLocked", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.thisObject
                try {

                    if (args[1] != "*") { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val mode = XposedHelpers.callMethod(thisObject, "getAccessControlLockMode", args[0]) as Int
                    if (mode != 1) { skipped = true; result = null; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    private fun saveLastCheck(thisObject: Any, pkgName: String?, userId: Int) {
        var enabled = false
        if (pkgName != null && pkgName != "com.miui.home") enabled = XposedHelpers.callMethod(thisObject, "getApplicationAccessControlEnabledAsUser", pkgName, userId) as Boolean
        val userState = XposedHelpers.callMethod(thisObject, "getUserStateLocked", userId)
        XposedHelpers.setAdditionalInstanceField(userState, "mAccessControlLastCheckSaved",
            if (enabled) ArrayMap<String, Long>(XposedHelpers.getObjectField(userState, "mAccessControlLastCheck") as ArrayMap<String, Long>) else null
        )
    }

    private fun checkLastCheck(thisObject: Any, userId: Int) {
        val userState = XposedHelpers.callMethod(thisObject, "getUserStateLocked", userId)
        val mAccessControlLastCheckSaved = XposedHelpers.getAdditionalInstanceField(userState, "mAccessControlLastCheckSaved") as ArrayMap<String, Long>?
        if (mAccessControlLastCheckSaved == null) return
        val mAccessControlLastCheck = XposedHelpers.getObjectField(userState, "mAccessControlLastCheck") as ArrayMap<String, Long>
        if (mAccessControlLastCheck.size == 0) return
        val timeout = MainModule.mPrefs.getInt("system_applock_timeout", 1) * 60L * 1000L
        for (pair in mAccessControlLastCheck) {
            val pkg = pair.key
            val time = pair.value
            if (mAccessControlLastCheckSaved.containsKey(pkg)) {
                val oldTime = mAccessControlLastCheckSaved[pkg]
                if (time != oldTime) {
                    mAccessControlLastCheck.put(pkg, time + (timeout - 60000L))
                    XposedHelpers.setObjectField(userState, "mAccessControlLastCheck", mAccessControlLastCheck)
                }
            } else {
                mAccessControlLastCheck.put(pkg, time + (timeout - 60000L))
                XposedHelpers.setObjectField(userState, "mAccessControlLastCheck", mAccessControlLastCheck)
            }
        }
    }

    @JvmStatic
    fun AppLockTimeoutHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService", lpparam.classLoader, "addAccessControlPassForUser", String::class.java, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.thisObject
                try {

                    saveLastCheck(thisObject, args[0] as String?, args[1] as Int)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    checkLastCheck(thisObject, args[1] as Int)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService", lpparam.classLoader, "checkAccessControlPassLocked", String::class.java, Intent::class.java, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.thisObject
                try {

                    saveLastCheck(thisObject, args[0] as String?, args[2] as Int)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    checkLastCheck(thisObject, args[2] as Int)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService", lpparam.classLoader, "activityResume", Intent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val intent = chain.getArg(0) as Intent
                    if (intent.component != null)
                        saveLastCheck(thisObject, intent.component?.packageName, 0)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val intent = chain.getArg(0) as Intent
                    if (intent.component != null)
                        checkLastCheck(thisObject, 0)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun HideLockScreenClockHook(lpparam: PackageReadyParam) {
        val mToAod = booleanArrayOf(false)
        val hideClockHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val visibility = chain.getArg(0) as Int
                    if (visibility == View.VISIBLE && !mToAod[0]) {
                        skipped = true; result = null; throwable = null
                    }
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.findAndHookMethod("com.android.keyguard.clock.KeyguardClockContainer", lpparam.classLoader, "setVisibility", Int::class.javaPrimitiveType!!, hideClockHook)
        ModuleHelper.findAndHookMethod("com.android.keyguard.clock.KeyguardClockContainer", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject
                    XposedHelpers.callMethod(thisObject, "setVisibility", View.GONE)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.keyguard.clock.KeyguardClockContainer", lpparam.classLoader, "doAnimationToAod", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {
                    mToAod[0] = chain.getArg(0) as Boolean
                    if (mToAod[0]) {
                        XposedHelpers.callMethod(thisObject, "setVisibility", View.VISIBLE)
                    }
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val mToAodLocal = chain.getArg(0) as Boolean
                    if (!mToAodLocal) {
                        XposedHelpers.callMethod(thisObject, "setVisibility", View.GONE)
                    }
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun AllowAllKeyguardHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.ExpandedNotification", lpparam.classLoader, "isEnableKeyguard", HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.NotificationSettingsManager", lpparam.classLoader, "canShowOnKeyguard", Context::class.java, String::class.java, String::class.java, HookerClassHelper.returnConstant(true))
    }

    private fun hookUpdateTime(alarmTime: TextView) {
        try {
            val mContext = alarmTime.context
            var timestamp = ModuleHelper.getNextMIUIAlarmTime(mContext)
            if (timestamp == 0L && MainModule.mPrefs.getBoolean("system_lsalarm_all"))
                timestamp = HookUtils.getNextStockAlarmTime(mContext)
            if (timestamp == 0L) {
                alarmTime.text = ""
                return
            }

            val alarmStr = StringBuilder()
            alarmStr.append(ModuleHelper.getModuleRes(mContext).getString(R.string.system_statusbaricons_alarm_title)).append(": ")
            val format = MainModule.mPrefs.getStringAsInt("system_lsalarm_format", 1)
            if (format == 1 || format == 3) {
                val dateFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(mContext)) "EHmm" else "EHmma"), Locale.getDefault())
                dateFormat.timeZone = TimeZone.getDefault()
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = timestamp
                alarmStr.append(dateFormat.format(calendar.time))
            }
            if (format == 2 || format == 3) {
                val timeStr = StringBuilder(DateUtils.getRelativeTimeSpanString(timestamp, java.lang.System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE))
                timeStr[0] = timeStr[0].lowercaseChar()
                alarmStr.append(if (format == 3) " ($timeStr)" else timeStr)
            }
            alarmTime.text = alarmStr
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    @JvmStatic
    fun LockScreenAlarmHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.KeyguardIndicationController", lpparam.classLoader, "setIndicationArea", ViewGroup::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mTopIndicationView = XposedHelpers.getObjectField(thisObject, "mTopIndicationView") as TextView
                    mTopIndicationView.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    mTopIndicationView.visibility = View.VISIBLE
                    val MiuiGxzwUtils = XposedHelpers.findClassIfExists("com.miui.keyguard.biometrics.fod.MiuiGxzwUtils", lpparam.classLoader)
                    var hasUdfs = true
                    if (MiuiGxzwUtils != null) {
                        val isGxzwLowPosition = XposedHelpers.callStaticMethod(MiuiGxzwUtils, "isGxzwLowPosition") as Boolean
                        hasUdfs = isGxzwLowPosition
                    }
                    val layoutParams = mTopIndicationView.layoutParams as LinearLayout.LayoutParams
                    layoutParams.bottomMargin = HookUtils.dp2px((if (hasUdfs) 80 else 20).toFloat()).toInt()
                    mTopIndicationView.layoutParams = layoutParams
                    val mInitialTextColorState = XposedHelpers.getObjectField(thisObject, "mInitialTextColorState") as ColorStateList
                    mTopIndicationView.setTextColor(mInitialTextColorState)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.KeyguardIndicationController", lpparam.classLoader, "updateDeviceEntryIndication", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mTopIndicationView = XposedHelpers.getObjectField(thisObject, "mTopIndicationView") as TextView
                    hookUpdateTime(mTopIndicationView)
                    val mInitialTextColorState = XposedHelpers.getObjectField(thisObject, "mInitialTextColorState") as ColorStateList
                    mTopIndicationView.setTextColor(mInitialTextColorState)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "handleBottomButtonClickedAnimation", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mTopIndicationView = ModuleHelper.getObjectFieldByPath(thisObject, "mKeyguardIndicationInjector.mKeyguardIndicationController.mTopIndicationView") as TextView
                    val showTips = chain.getArg(0) as Boolean
                    if (showTips) {
                        mTopIndicationView.visibility = View.GONE
                    } else {
                        mTopIndicationView.visibility = View.VISIBLE
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun TapToUnlockHook(lpparam: PackageReadyParam) {
        val NotificationPanelController = XposedHelpers.findClassIfExists("com.android.systemui.shade.NotificationPanelViewController", lpparam.classLoader)
        if (NotificationPanelController == null) {
            XposedHelpers.log("NotificationPanelController not found")
            return
        }

        val mTouchHandlerField = XposedHelpers.findField(NotificationPanelController, "mTouchHandler")
        XposedHelpers.findAndHookMethod(mTouchHandlerField.type, "handleMiuiTouch", MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val event = chain.getArg(0) as MotionEvent
                    if (event.pointerCount > 1) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val action = event.actionMasked
                    if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val thisObect = XposedHelpers.getSurroundingThis(thisObject)
                    val isOnKeyguard = XposedHelpers.callMethod(thisObect, "isOnKeyguard") as Boolean
                    val mQsController = XposedHelpers.getObjectField(thisObect, "mQsController")
                    val mExpanded = XposedHelpers.getBooleanField(mQsController, "mExpanded")
                    if (isOnKeyguard && !mExpanded) {
                        if (action == MotionEvent.ACTION_UP) {
                            val mKeyguardPanelViewInjector = XposedHelpers.getObjectField(thisObect, "mKeyguardPanelViewInjector")
                            val mKeyguardMoveHelper = XposedHelpers.getObjectField(mKeyguardPanelViewInjector, "mKeyguardMoveHelper")
                            val mCurrentScreen = XposedHelpers.getIntField(mKeyguardMoveHelper, "mCurrentScreen")
                            if (mCurrentScreen == 0) { return XposedHelpers.throwOrReturn(throwable, result) }
                            val keyguardBottomAreaInjector = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.keyguard.injector.KeyguardBottomAreaInjector")
                            if (!XposedHelpers.getBooleanField(keyguardBottomAreaInjector, "mTouchAtKeyguardBottomArea")) { return XposedHelpers.throwOrReturn(throwable, result) }
                            val mContext = XposedHelpers.getObjectField(keyguardBottomAreaInjector, "mContext") as Context
                            val mTouchDownX = XposedHelpers.getFloatField(keyguardBottomAreaInjector, "mTouchDownX")
                            val mTouchDownY = XposedHelpers.getFloatField(keyguardBottomAreaInjector, "mTouchDownY")
                            val slop = ViewConfiguration.get(mContext).scaledTouchSlop
                            if (Math.abs(event.x - mTouchDownX) > slop || Math.abs(event.y - mTouchDownY) > slop)
                                { return XposedHelpers.throwOrReturn(throwable, result) }
                            val statusBarKeyguardViewManager = XposedHelpers.getObjectField(thisObect, "statusBarKeyguardViewManager")
                            XposedHelpers.callMethod(statusBarKeyguardViewManager, "showBouncer", true)
                            result = true; throwable = null
                        }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ScrambleAppLockPINHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors("com.miui.applicationlock.widget.MiuiNumericInputView", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val keys = thisObject as LinearLayout
                    val mRandomViews = ArrayList<View>()
                    var bottom0: View? = null; var bottom2: View? = null
                    for (row in 0..3) {
                        val cols = keys.getChildAt(row) as ViewGroup
                        for (col in 0..2) {
                            if (row == 3)
                                if (col == 0) {
                                    bottom0 = cols.getChildAt(col)
                                    continue
                                } else if (col == 2) {
                                    bottom2 = cols.getChildAt(col)
                                    continue
                                }
                            mRandomViews.add(cols.getChildAt(col))
                        }
                        cols.removeAllViews()
                    }

                    Collections.shuffle(mRandomViews)

                    var cnt = 0
                    for (row in 0..3)
                        for (col in 0..2) {
                            val cols = keys.getChildAt(row) as ViewGroup
                            if (row == 3)
                                if (col == 0) {
                                    bottom0?.let { cols.addView(it) }
                                    continue
                                } else if (col == 2) {
                                    bottom2?.let { cols.addView(it) }
                                    continue
                                }
                            cols.addView(mRandomViews[cnt])
                            cnt++
                        }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ChargingInfoHook(lpparam: PackageReadyParam) {
        if (isChargingInfoHooked) return
        isChargingInfoHooked = true
        ModuleHelper.findAndHookMethod("com.miui.charge.ChargeUtils", lpparam.classLoader, "getChargingHintText", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Context::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val charge = chain.getArg(0) as Int
                    val hint = result as String?

                    if (charge <= 100 && hint != null && isKeyguardIndicationCaller()) {
                        val showCurr = MainModule.mPrefs.getBoolean("system_charginginfo_current")
                        val showVolt = MainModule.mPrefs.getBoolean("system_charginginfo_voltage")
                        val showWatt = MainModule.mPrefs.getBoolean("system_charginginfo_wattage")
                        val showTemp = MainModule.mPrefs.getBoolean("system_charginginfo_temp")

                        val values = ArrayList<String>()
                        var props: Properties? = null
                        var fis: FileInputStream? = null
                        try {
                            fis = FileInputStream("/sys/class/power_supply/battery/uevent")
                            props = Properties()
                            props.load(fis)
                        } catch (ign: Throwable) {
                        } finally {
                            try {
                                fis?.close()
                            } catch (ign: Throwable) {
                            }
                        }
                        if (props != null) {
                            val currVal = Math.abs(Integer.parseInt(props.getProperty("POWER_SUPPLY_CURRENT_NOW") ?: "0")) / 1000f / 1000f
                            if (showCurr) values.add(String.format(Locale.US, "%.2f", currVal) + " A")
                            val voltVal = Integer.parseInt(props.getProperty("POWER_SUPPLY_VOLTAGE_NOW") ?: "0") / 1000f / 1000f
                            if (showVolt)
                                values.add(String.format(Locale.US, "%.1f", voltVal) + " V")
                            if (showWatt)
                                values.add(String.format(Locale.US, "%.1f", voltVal * currVal) + " W")
                            if (showTemp) {
                                val tempVal = Integer.parseInt(props.getProperty("POWER_SUPPLY_TEMP") ?: "0")
                                values.add(Math.round(tempVal / 10f).toString() + " ℃")
                            }
                        }
                        if (values.size == 0) { return XposedHelpers.throwOrReturn(throwable, result) }
                        val info = TextUtils.join(" · ", values)

                        if (hint.contains(info)) { return XposedHelpers.throwOrReturn(throwable, result) }

                        val opt = MainModule.mPrefs.getStringAsInt("system_charginginfo_view", 1)
                        if (opt == 1)
                            { result = hint + "\n" + info; throwable = null }
                        else if (opt == 2)
                            { result = hint + " · " + info; throwable = null }
                        else if (opt == 3)
                            { result = info + " · " + hint; throwable = null }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.KeyguardIndicationTextView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val opt = MainModule.mPrefs.getStringAsInt("system_charginginfo_view", 1)
                    if (opt != 1) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val indicator = thisObject as TextView
                    indicator.isSingleLine = false

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    private fun isKeyguardIndicationCaller(): Boolean {
        try {
            for (e in Thread.currentThread().stackTrace) {
                val className = e.className
                if (className.contains("KeyguardIndication")) return true
                if (className.contains("MiuiCharge") || className.contains("miui.charge")) return false
            }
        } catch (ignore: Throwable) {}
        return false
    }

    @JvmStatic
    fun NoSOSHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.keyguard.EmergencyButtonController", lpparam.classLoader, "updateEmergencyCallButton", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val mSOS = XposedHelpers.getObjectField(thisObject, "mView") as Button
                    if (mSOS.visibility == View.VISIBLE) {
                        mSOS.visibility = View.INVISIBLE
                    }
                    skipped = true; result = null; throwable = null

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun SkipAppLockHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.miui.server.AccessController", lpparam.classLoader, "skipActivity", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val intent = chain.getArg(0) as Intent?
                    if (intent == null || intent.component == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val pkgName = intent.component!!.packageName
                    val actName = intent.component!!.className
                    val key = "system_applock_skip_activities"
                    val itemStr = MainModule.mPrefs.getString(key, "")
                    if (itemStr == null || itemStr.isEmpty()) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val itemArr = itemStr.trim().split(PrefPair.DELIMITER)
                    for (uuid in itemArr) {
                        val pkgAct = MainModule.mPrefs.getString(key + "_" + uuid + "_activity", "")
                        if (pkgAct == pkgName + "|" + actName) { result = true; throwable = null }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun HideLockScreenHintHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.KeyguardIndicationController", lpparam.classLoader, "updateDeviceEntryIndication", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    XposedHelpers.setObjectField(thisObject, "mPersistentUnlockMessage", "")

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun HideLockScreenStatusBarHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mKeyguardStatusBar = thisObject as View
                    mKeyguardStatusBar.visibility = View.GONE
                    mKeyguardStatusBar.translationY = -499f

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun SetLockscreenWallpaperHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.wallpaper.WallpaperManagerService", lpparam.classLoader, "setWallpaper", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject
                    val args = chain.args

                    if (throwable != null || result == null || args[5] as Int == 1 || "com.android.thememanager" == args[1]) { return XposedHelpers.throwOrReturn(throwable, result) }

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    if (mContext == null) { return XposedHelpers.throwOrReturn(throwable, result) }

                    var handleIncomingUser = 0
                    try {
                        handleIncomingUser = XposedHelpers.callStaticMethod(ActivityManager::class.java, "handleIncomingUser", Binder.getCallingPid(), Binder.getCallingUid(), args[7], false, true, "changing wallpaper", null) as Int
                    } catch (ignore: Throwable) {}
                    val wallpaperData = XposedHelpers.callMethod(thisObject, "getWallpaperSafeLocked", handleIncomingUser, args[5])
                    val wallpaper = XposedHelpers.getObjectField(wallpaperData, "wallpaperFile") as File

                    Handler(mContext.mainLooper).postDelayed({
                        try {
                            if (!wallpaper.exists()) return@postDelayed

                            val lockWallpaperPath = "/data/system/theme/thirdparty_lock_wallpaper"
                            HookUtils.copyFile(wallpaper.absolutePath, lockWallpaperPath)
                            val ThemeUtils = XposedHelpers.findClass("miui.content.res.ThemeNativeUtils", lpparam.classLoader)
                            XposedHelpers.callStaticMethod(ThemeUtils, "updateFilePermissionWithThemeContext", lockWallpaperPath)
                            val data = JSONObject()
                            val ex = JSONObject()
                            try {
                                val lockWallpaper = File(lockWallpaperPath)
                                ex
                                    .put("link_type", "0")
                                    .put("title_size", "26")
                                    .put("item_id", "wallpaper1")
                                    .put("title_color", "#ffffffff")
                                    .put("index_in_album", "1")
                                    .put("tag_list", "CustoMIUIzer,mod")
                                    .put("content_color", "#ffffffff")
                                    .put("total_of_album", "1")
                                    .put("img_level", "0")
                                    .put("album_id", "1")
                                    .put("title_customized", "0")
                                    .put("lks_entry_text", "Some wallpaper")

                                data
                                    .put("authority", "tv.withaibuild.customiuizer.mods.set_lockscreen_wallpaper")
                                    .put("content", "Wallpaper set by some app")
                                    .put("contentColorValue", 0)
                                    .put("cp", "CustoMIUIzer")
                                    .put("cpColorValue", 0)
                                    .put("definition", -1)
                                    .put("ex", ex.toString())
                                    .put("fromColorValue", 0)
                                    .put("hasAcc", false)
                                    .put("indexInAlbum", -1)
                                    .put("isAd", false)
                                    .put("isCustom", false)
                                    .put("isFd", false)
                                    .put("isFrontCover", false)
                                    .put("key", "wallpaper1")
                                    .put("like", false)
                                    .put("linkType", 0)
                                    .put("noApply", false)
                                    .put("noDislike", false)
                                    .put("noSave", false)
                                    .put("noShare", false)
                                    .put("pos", 0)
                                    .put("supportLike", true)
                                    .put("title", "Some wallpaper")
                                    .put("titleColorValue", 0)
                                    .put("titleTextSize", -1)
                                    .put("totalOfAlbum", -1)
                                    .put("wallpaperUri", lockWallpaper.toURI())
                            } catch (t: Throwable) {
                                XposedHelpers.log(t)
                            }

                            val setIntent = Intent("com.miui.miwallpaper.UPDATE_LOCKSCREEN_WALLPAPER")
                            setIntent.putExtra("wallpaperInfo", data.toString())
                            setIntent.putExtra("apply", true)
                            mContext.sendBroadcast(setIntent)
                        } catch (t: Throwable) {
                            XposedHelpers.log(t)
                        }
                    }, 1800)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun Disable72hStrongAuthHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth", lpparam.classLoader, "rescheduleStrongAuthTimeoutAlarm", Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, HookerClassHelper.DO_NOTHING)
    }

}
