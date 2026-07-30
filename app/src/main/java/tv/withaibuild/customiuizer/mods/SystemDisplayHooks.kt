package tv.withaibuild.customiuizer.mods

import android.animation.ObjectAnimator
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.os.Handler
import android.view.View
import io.github.libxposed.api.XposedInterface
import miui.os.Build
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Screen, brightness and wallpaper hooks.
 * Screen-off animation timing, the auto-brightness range and its reset on screen
 * off, dim timeout, drawer blur, forced dark mode and wallpaper scale.
 */
object SystemDisplayHooks {

    @JvmStatic
    fun ScreenAnimHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "initialize", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    try {
                        XposedHelpers.setObjectField(thisObject, "mColorFadeEnabled", true)
                        XposedHelpers.setObjectField(thisObject, "mColorFadeFadesConfig", true)
                    } catch (ignore: Throwable) {}

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

                    val mColorFadeOffAnimator = XposedHelpers.getObjectField(thisObject, "mColorFadeOffAnimator") as ObjectAnimator?
                    if (mColorFadeOffAnimator != null) {
                        var value = MainModule.mPrefs.getInt("system_screenanim_duration", 0)
                        if (value == 0) value = 250
                        mColorFadeOffAnimator.duration = value.toLong()
                    }
                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) {
                            if (key?.contains("system_screenanim_duration") == true) {
                                if (mColorFadeOffAnimator == null) return
                                var value2 = MainModule.mPrefs.getInt("system_screenanim_duration", 0)
                                if (value2 == 0) value2 = 250
                                mColorFadeOffAnimator.duration = value2.toLong()
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NoLightUpOnChargeHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.power.PowerManagerService", lpparam.classLoader, "wakePowerGroupLocked", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val reason = chain.getArg(3) as String?
                    if (reason == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (
                        reason.startsWith("android.server.power:PLUGGED")
                        || reason == "com.android.systemui:RAPID_CHARGE"
                        || reason == "com.android.systemui:WIRELESS_CHARGE"
                        || reason == "com.android.systemui:WIRELESS_RAPID_CHARGE"
                    ) { skipped = true; result = null; throwable = null }

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
    fun DrawerBlurRatioHook(lpparam: PackageReadyParam) {
        val mCustomBlurModifier = intArrayOf(0)
        ModuleHelper.hookAllConstructors("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, object : MethodHook() {
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
                    mCustomBlurModifier[0] = MainModule.mPrefs.getInt("system_drawer_blur", 100)
                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) {
                            if (key?.contains("system_drawer_blur") == true) {
                                mCustomBlurModifier[0] = MainModule.mPrefs.getInt("system_drawer_blur", 100)
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.NotificationShadeDepthController\$updateBlurCallback\$1", lpparam.classLoader, "doFrame", Long::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val parentCtrl = XposedHelpers.getSurroundingThis(thisObject)
                    val mBlurUtils = XposedHelpers.getObjectField(parentCtrl, "blurUtilsExt")
                    XposedHelpers.setAdditionalInstanceField(mBlurUtils, "mCustomBlurModifier", mCustomBlurModifier[0])

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

                    val parentCtrl = XposedHelpers.getSurroundingThis(thisObject)
                    val mBlurUtils = XposedHelpers.getObjectField(parentCtrl, "blurUtilsExt")
                    XposedHelpers.removeAdditionalInstanceField(mBlurUtils, "mCustomBlurModifier")

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BlurUtilsExt", lpparam.classLoader, "applyBlur", View::class.java, Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                val thisObject = chain.thisObject
                try {

                    val multiplier = XposedHelpers.getAdditionalInstanceField(thisObject, "mCustomBlurModifier")
                    if (multiplier != null) {
                        val ratio = args[1] as Float
                        val newRatio = ratio * (multiplier as Int) / 100f
                        args[1] = newRatio
                    }

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.controlcenter.phone.ControlPanelWindowManager", lpparam.classLoader, "setBlurRatio", Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    args[0] = (args[0] as Float) * mCustomBlurModifier[0] / 100f

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ChargeAnimationHook(lpparam: PackageReadyParam) {
        val timeout = MainModule.mPrefs.getInt("system_chargeanimtime", 20) * 1000
        ModuleHelper.findAndHookMethod("com.miui.charge.container.MiuiChargeAnimationView", lpparam.classLoader, "getAnimationDuration", HookerClassHelper.returnConstant(timeout))
    }

    private var mMaximumBacklight = 0f

    private var mMinimumBacklight = 0f

    private var backlightMaxLevel = 0

    private fun constrainValue(value: Float): Float {
        var newVal = value
        if (newVal < 0) newVal = 0f
        if (newVal > 1) newVal = 1f

        val limitmin = MainModule.mPrefs.getBoolean("system_autobrightness_limitmin")
        val limitmax = MainModule.mPrefs.getBoolean("system_autobrightness_limitmax")
        val min_pct = MainModule.mPrefs.getInt("system_autobrightness_min", 25)
        val max_pct = MainModule.mPrefs.getInt("system_autobrightness_max", 75)

        val min = HookUtils.convertGammaToLinearFloat(min_pct / 100f * backlightMaxLevel, backlightMaxLevel, mMinimumBacklight, mMaximumBacklight)
        val max = HookUtils.convertGammaToLinearFloat(max_pct / 100f * backlightMaxLevel, backlightMaxLevel, mMinimumBacklight, mMaximumBacklight)

        if (limitmin && newVal < min) newVal = min
        if (limitmax && newVal > max) newVal = max
        return newVal
    }

    @JvmStatic
    fun AutoBrightnessRangeHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.display.AutomaticBrightnessController", lpparam.classLoader, "clampScreenBrightness", Float::class.javaPrimitiveType!!, object : MethodHook() {
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

                    val value = result as Float
                    if (value >= 0) {
                        val res = constrainValue(value)
                        result = res; throwable = null
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllConstructors("com.android.server.display.AutomaticBrightnessController", lpparam.classLoader, object : MethodHook() {
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

                    XposedHelpers.setLongField(thisObject, "mBrighteningLightDebounceConfig", 1000L)
                    XposedHelpers.setLongField(thisObject, "mDarkeningLightDebounceConfig", 1200L)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "clampScreenBrightness", Float::class.javaPrimitiveType!!, object : MethodHook() {
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

                    val value = result as Float
                    if (value >= 0) {
                        val res = constrainValue(value)
                        result = res; throwable = null
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllConstructors("com.android.server.display.DisplayPowerController", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val res = Resources.getSystem()
                    val minBrightnessLevel = res.getInteger(HookUtils.getResId(res, "config_screenBrightnessSettingMinimum", "integer", "android"))
                    val maxBrightnessLevel = res.getInteger(HookUtils.getResId(res, "config_screenBrightnessSettingMaximum", "integer", "android"))
                    val backlightBit = res.getInteger(HookUtils.getResId(res, "config_backlightBit", "integer", "android.miui"))
                    backlightMaxLevel = (1 shl backlightBit) - 1
                    mMinimumBacklight = (minBrightnessLevel - 1) * 1.0f / (backlightMaxLevel - 1)
                    mMaximumBacklight = (maxBrightnessLevel - 1) * 1.0f / (backlightMaxLevel - 1)

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
    fun AutoBrightnessAfterScreenOffHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "setScreenState", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            var stateChanged = false
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.thisObject
                try {

                    val state = args[0] as Int
                    val reportOnly = args[1] as Boolean
                    val mUseAutoBrightness = XposedHelpers.getBooleanField(thisObject, "mUseAutoBrightness")
                    if (state == 1 && mUseAutoBrightness && !reportOnly) {
                        val mPowerState = XposedHelpers.getObjectField(thisObject, "mPowerState")
                        val mScreenState = XposedHelpers.getIntField(mPowerState, "mScreenState")
                        stateChanged = state != mScreenState
                    } else {
                        stateChanged = false
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

                    if (stateChanged) {
                        val readyToUpdateDisplayState = XposedHelpers.callMethod(thisObject, "readyToUpdateDisplayState") as Boolean
                        if (readyToUpdateDisplayState) {
                            val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler
                            val msg = mHandler.obtainMessage(255)
                            mHandler.sendMessage(msg)
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
    fun ScreenDimTimeHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.power.PowerManagerService", lpparam.classLoader, "readConfigurationLocked", object : MethodHook() {
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

                    val opt = MainModule.mPrefs.getInt("system_dimtime", 0) / 100f
                    XposedHelpers.setIntField(thisObject, "mMaximumScreenDimDurationConfig", 600000)
                    XposedHelpers.setFloatField(thisObject, "mMaximumScreenDimRatioConfig", opt)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ForceDarkAllAppsHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.ForceDarkAppListProvider", lpparam.classLoader, "fillDarkModeAppSettingsInfo", object : MethodHook() {
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

                    XposedHelpers.callMethod(chain.getArg(0), "setShowInSettings", true)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        if (!Build.IS_INTERNATIONAL_BUILD) {
            ModuleHelper.findAndHookMethod("com.android.server.ForceDarkAppListManager", lpparam.classLoader, "getDarkModeAppList", Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {

                        XposedHelpers.setStaticBooleanField(Build::class.java, "IS_INTERNATIONAL_BUILD", true)

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

                        XposedHelpers.setStaticBooleanField(Build::class.java, "IS_INTERNATIONAL_BUILD", false)

                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        }
        ModuleHelper.findAndHookMethod("com.android.server.ForceDarkAppListManager", lpparam.classLoader, "shouldShowInSettings", ApplicationInfo::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    if (chain.getArg(0) == null) {
                        return XposedHelpers.throwOrReturn(null, false)
                    }
                    val applicationInfo = chain.getArg(0) as ApplicationInfo
                    val flags = applicationInfo.flags
                    val systemApp = (flags and 1) != 0 || (flags and 128) != 0 || applicationInfo.uid < 10000
                    skipped = true; result = !systemApp; throwable = null

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
    fun WallpaperScaleLevelHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllConstructors("com.android.server.wm.WallpaperController", lpparam.classLoader, object : MethodHook() {
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

                    val scale = MainModule.mPrefs.getInt("system_other_wallpaper_scale", 6) / 10.0f
                    XposedHelpers.setObjectField(thisObject, "mMaxWallpaperScale", scale)
                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) {
                            if (key?.contains("system_other_wallpaper_scale") == true) {
                                val value = MainModule.mPrefs.getInt("system_other_wallpaper_scale", 6)
                                XposedHelpers.setObjectField(thisObject, "mMaxWallpaperScale", value / 10.0f)
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

}
