package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Pair
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import miui.os.Build
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.mods.utils.StepCounterController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.HookUtils
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.net.NetworkInterface
import java.util.ArrayList
import java.util.HashSet

/**
 * Status bar content hooks.
 * Owns the custom text-icon registry that DeviceInfoMonitor writes battery and
 * temperature readings into, plus the signal, network speed, clock position and
 * icon visibility hooks that share it.
 */
internal fun resolveNetSpeedLineSpacing(
    fontSize: Int,
    adjustmentPercent: Int
): Float {
    val baseSpacing =
        if (fontSize > 17) 0.85f
        else 0.90f

    val adjustment = adjustmentPercent.coerceIn(70, 130)

    return baseSpacing * adjustment / 100f
}

internal fun resolveNetSpeedTypefaceStyle(baseStyle: Int, bold: Boolean): Int =
    if (bold) baseStyle or Typeface.BOLD else baseStyle and Typeface.BOLD.inv()

internal fun formatNetSpeedValue(value: Float): String {
    if (value >= 100.0f) return Math.round(value).toString()

    val tenths = Math.round(value * 10.0f)
    return "${tenths / 10}.${tenths % 10}"
}

object SystemUIStatusBarHooks {

    private val StatusBarCls = "com.android.systemui.statusbar.phone.CentralSurfacesImpl"

    private var statusbarTextIconLayoutResId = 0

    val textIconTagId = ResourceHooks.getFakeResId("text_icon_tag")

    private val viewInitedTag = ResourceHooks.getFakeResId("view_inited_tag")

    private val netspeedNumberViewTag = ResourceHooks.getFakeResId("netspeed_number_view")

    private val netspeedUnitViewTag = ResourceHooks.getFakeResId("netspeed_unit_view")

    private val netspeedTypefaceStateTag = ResourceHooks.getFakeResId("netspeed_typeface_state")

    @JvmStatic
    fun setupStatusBar(mContext: Context) {
        statusbarTextIconLayoutResId = MainModule.resHooks.addFakeResource("statusbar_text_icon", R.layout.statusbar_text_icon, "layout")
        if (MainModule.mPrefs.getBoolean("system_statusbar_topmargin")) {
            val topMargin = MainModule.mPrefs.getInt("system_statusbar_topmargin_val", 1)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_padding_top", topMargin)
        }
        if (MainModule.mPrefs.getBoolean("system_statusbar_horizmargin")) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_padding_start", 0)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_padding_end", 0)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_enable_style_switch")) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "integer", "force_use_control_panel", 0)
        }
        if (MainModule.mPrefs.getBoolean("system_volumetimer")) {
            val module_volume_timer_segments = intArrayOf(0, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 28800, 36000, 43200)
            MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "integer-array", "miui_volume_timer_segments", module_volume_timer_segments)
        }
        val iconSize = MainModule.mPrefs.getInt("system_statusbar_iconsize", 6)
        if (iconSize > 6) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_size", iconSize)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_clock_size", iconSize + 0.4f)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_drawing_size", iconSize)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_drawing_size_dark", iconSize)
            val notifyPadding = 2.5f * iconSize / 13
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_notification_icon_padding", notifyPadding)
            val iconHeight = 20.5f * iconSize / 13
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_icon_height", iconHeight)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_show_stepcount")) {
            StepCounterController.initContext(mContext)
        }
        if (!MainModule.mPrefs.getBoolean("system_drawer_hidedate")) {
            val drawerDateSize = MainModule.mPrefs.getInt("system_drawer_date_fontsize", 12)
            if (drawerDateSize > 12) {
                MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "qs_control_header_date_size", drawerDateSize)
            }
        }
        if (MainModule.mPrefs.getBoolean("system_taptounlock")) {
            MainModule.resHooks.setResReplacement("com.android.systemui", "string", "default_lockscreen_unlock_hint_text", R.string.system_taptounlock_title)
        }
        val userActivityTimeout = MainModule.mPrefs.getInt("system_lstimeout", 3)
        if (userActivityTimeout > 3) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "integer", "config_lockScreenDisplayTimeout", userActivityTimeout * 1000)
        }
        Settings.System.putLong(mContext.contentResolver, "systemui_restart_time", java.lang.System.currentTimeMillis())
    }

    @JvmStatic
    fun getSlotNameByType(mIconType: Int): String {
        var slotName = ""
        if (mIconType == 91) {
            slotName = "battery_info"
        } else if (mIconType == 92) {
            slotName = "device_temp"
        }
        return slotName
    }

    @JvmStatic
    fun MonitorDeviceInfoHook(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        SystemUIMonitorAndTileHooks.MonitorDeviceInfoHook(lpparam, mPrefs)
    }

    private fun getIconTextView(iconView: View): TextView {
        return XposedHelpers.getObjectField(iconView, "mNetworkSpeedNumberText") as TextView
    }

    @JvmStatic
    fun initStatusbarTextIcon(mContext: Context, iconType: Int, iconView: View, fromController: Boolean) {
        if (!fromController) {
            XposedHelpers.callMethod(iconView, "setBlocked", false)
        }
        val iconTextView = getIconTextView(iconView)
        val res = mContext.resources
        val styleId = res.getIdentifier("TextAppearance.StatusBar.Clock", "style", "com.android.systemui")
        iconTextView.setTextAppearance(styleId)
        var subKey = ""
        if (iconType == 91) {
            subKey = "batterytempandcurrent"
        } else if (iconType == 92) {
            subKey = "showdevicetemperature"
        }
        val fontSize = MainModule.mPrefs.getInt("system_statusbar_${subKey}_fontsize", 16) * 0.5f
        val opt = MainModule.mPrefs.getStringAsInt("system_statusbar_${subKey}_content", 1)
        if ((opt == 1 || opt == 4 || opt == 5) && !MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_singlerow")) {
            iconTextView.maxLines = 2
            iconTextView.setLineSpacing(0f, if (fontSize > 8.5f) 0.85f else 0.9f)
        }
        iconTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize)
        if (MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_bold")) {
            iconTextView.typeface = Typeface.DEFAULT_BOLD
        }
        var leftMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_leftmargin", 8)
        leftMargin = HookUtils.dp2px(leftMargin * 0.5f).toInt()
        var rightMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_rightmargin", 8)
        rightMargin = HookUtils.dp2px(rightMargin * 0.5f).toInt()
        var topMargin = 0
        val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_${subKey}_verticaloffset", 8)
        if (verticalOffset != 8) {
            topMargin = HookUtils.dp2px((verticalOffset - 8) * 0.5f).toInt()
        }
        iconTextView.setPaddingRelative(leftMargin, topMargin, rightMargin, 0)
        val fixedWidth = MainModule.mPrefs.getInt("system_statusbar_${subKey}_fixedcontent_width", 10)
        if (fixedWidth > 10) {
            val lp = iconTextView.layoutParams as LinearLayout.LayoutParams
            lp.width = HookUtils.dp2px(fixedWidth.toFloat()).toInt()
            iconTextView.layoutParams = lp
        }

        val align = MainModule.mPrefs.getStringAsInt("system_statusbar_${subKey}_align", 1)
        if (align == 2) {
            iconTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        } else if (align == 3) {
            iconTextView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        } else if (align == 4) {
            iconTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }
    }

    @JvmStatic
    fun createStatusbarTextIcon(mContext: Context, lp: LinearLayout.LayoutParams, iconType: Int, fromController: Boolean): View {
        val iconView = LayoutInflater.from(mContext).inflate(statusbarTextIconLayoutResId, null)
        iconView.setTag(textIconTagId, iconType)
        iconView.layoutParams = lp
        val mNumber = iconView.findViewWithTag<View>("network_speed_number")
        XposedHelpers.setObjectField(iconView, "mNetworkSpeedNumberText", mNumber)
        val mUnit = iconView.findViewWithTag<View>("network_speed_unit")
        XposedHelpers.setObjectField(iconView, "mNetworkSpeedUnitText", mUnit)
        initStatusbarTextIcon(mContext, iconType, iconView, fromController)
        return iconView
    }

    /**
     * Battery detail / device temperature text icons created by this module inside SystemUI.
     *
     * SystemUI re-inflates the status bar on theme, density, display and fold changes, so a strong
     * static list keeps every dead View (and its Context) alive for the whole process lifetime and
     * makes the 2 s monitor tick walk detached views. References are weak and dead entries are
     * dropped on every register/update. All access happens on the SystemUI main thread.
     */
    private val statusbarTextIcons = ArrayList<WeakReference<View>>(4)

    @JvmStatic
    fun registerStatusbarTextIcon(iconView: View) {
        for (i in statusbarTextIcons.indices.reversed()) {
            val existing = statusbarTextIcons[i].get()
            if (existing == null || existing === iconView) statusbarTextIcons.removeAt(i)
        }
        statusbarTextIcons.add(WeakReference(iconView))
    }

    @JvmStatic
    fun updateStatusbarTextIcons(iconType: Int, show: Boolean, text: String) {
        for (i in statusbarTextIcons.indices.reversed()) {
            val iconView = statusbarTextIcons[i].get()
            if (iconView == null) {
                statusbarTextIcons.removeAt(i)
                continue
            }
            if (iconView.getTag(textIconTagId) != iconType) continue
            XposedHelpers.callMethod(iconView, "setVisibilityByController", show)
            if (show) XposedHelpers.callMethod(iconView, "setNetworkSpeed", text, "")
        }
    }

    @JvmStatic
    fun DualRowsStatusbarHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                var firstRowLeftPadding = 0
                var firstRowRightPadding = 0
                if (MainModule.mPrefs.getBoolean("system_statusbar_dualrows_firstrow_horizmargin")) {
                    firstRowLeftPadding = MainModule.mPrefs.getInt("system_statusbar_dualrows_firstrow_horizmargin_left", 0)
                    firstRowRightPadding = MainModule.mPrefs.getInt("system_statusbar_dualrows_firstrow_horizmargin_right", 0)
                }
                val clock2Rows = MainModule.mPrefs.getBoolean("system_statusbar_dualrows_clock_span2rows")
                val sbView = param.getThisObject() as FrameLayout
                val mContext = sbView.context
                val leftContainer = XposedHelpers.getObjectField(sbView, "mStatusBarLeftContainer") as LinearLayout
                leftContainer.setTag("mStatusBarLeftContainer")
                val statusBarcontents = leftContainer.parent as LinearLayout
                val leftLayout = LinearLayout(mContext)
                val rightLayout = LinearLayout(mContext)
                statusBarcontents.addView(leftLayout, 0)
                statusBarcontents.addView(rightLayout)
                val leftGroup: LinearLayout

                if (clock2Rows) {
                    val mMiuiClock = XposedHelpers.getObjectField(sbView, "mClock") as TextView
                    leftContainer.removeView(mMiuiClock)
                    leftGroup = LinearLayout(mContext)
                    leftLayout.addView(mMiuiClock)
                    leftLayout.addView(leftGroup)
                    leftLayout.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    val groupLp = LinearLayout.LayoutParams(0, -1, 1f)
                    leftGroup.layoutParams = groupLp
                } else {
                    leftGroup = leftLayout
                    if (firstRowLeftPadding > 0) {
                        leftContainer.setPaddingRelative(firstRowLeftPadding, 0, 0, 0)
                    }
                }
                statusBarcontents.removeView(leftContainer)
                leftGroup.addView(leftContainer)
                val secondLeft = LinearLayout(mContext)
                leftGroup.addView(secondLeft)
                leftLayout.id = leftContainer.id
                leftContainer.id = View.NO_ID
                XposedHelpers.setObjectField(sbView, "mStatusBarLeftContainer", leftLayout)

                val rightContainer = XposedHelpers.getObjectField(param.getThisObject(), "mSystemIconArea") as ViewGroup
                val mFullscreenStatusBarNotificationIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mFullscreenStatusBarNotificationIconArea") as View
                rightContainer.removeView(mFullscreenStatusBarNotificationIconArea)
                secondLeft.addView(mFullscreenStatusBarNotificationIconArea)
                val mDripStatusBarNotificationIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mDripStatusBarNotificationIconArea") as View
                leftContainer.removeView(mDripStatusBarNotificationIconArea)
                secondLeft.addView(mDripStatusBarNotificationIconArea)
                secondLeft.orientation = LinearLayout.VERTICAL
                val leftLp = LinearLayout.LayoutParams(-1, 0, 1f)
                leftContainer.layoutParams = leftLp
                secondLeft.layoutParams = leftLp
                secondLeft.gravity = Gravity.START or Gravity.CENTER_VERTICAL

                XposedHelpers.setObjectField(param.getThisObject(), "mSystemIconArea", rightLayout)
                val firstRight = LinearLayout(mContext)
                rightLayout.addView(firstRight)
                firstRight.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                if (firstRowRightPadding > 0) {
                    firstRight.setPaddingRelative(0, 0, firstRowRightPadding, 0)
                }
                val secondRight = LinearLayout(mContext)
                rightLayout.addView(secondRight)
                secondRight.gravity = Gravity.END or Gravity.CENTER_VERTICAL

                rightLayout.orientation = LinearLayout.VERTICAL
                val rightLp = LinearLayout.LayoutParams(-1, 0, 1f)
                firstRight.layoutParams = rightLp
                secondRight.layoutParams = rightLp

                val rightChildCount = rightContainer.childCount
                for (i in rightChildCount - 1 downTo 0) {
                    val child = rightContainer.getChildAt(i)
                    rightContainer.removeView(child)
                    firstRight.addView(child, 0)
                }

                val resSystemIconsId = sbView.resources.getIdentifier("system_icons", "id", lpparam.packageName)
                rightLayout.id = resSystemIconsId

                val showBatteryDetail = MainModule.mPrefs.getBoolean("system_statusbar_batterytempandcurrent")
                val showDeviceTemp = MainModule.mPrefs.getBoolean("system_statusbar_showdevicetemperature")
                val batteryAtRight = showBatteryDetail && MainModule.mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
                val tempAtRight = showDeviceTemp && MainModule.mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")
                val customIconTypes = ArrayList<Int>()
                if (batteryAtRight) {
                    customIconTypes.add(91)
                }
                if (tempAtRight) {
                    customIconTypes.add(92)
                }
                if (!customIconTypes.isEmpty()) {
                    val DarkIconDispatcher = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.plugins.DarkIconDispatcher")
                    for (iconType in customIconTypes) {
                        val iconView = createStatusbarTextIcon(mContext, LinearLayout.LayoutParams(-2, -2), iconType, false)
                        secondRight.addView(iconView, 0)
                        registerStatusbarTextIcon(iconView)
                        XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", iconView)
                    }
                }

                statusBarcontents.removeView(rightContainer)

                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "leftLayout", leftLayout)
                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "rightLayout", rightLayout)

                if (MainModule.mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")) {
                    ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setNetworkSpeedIcon", object : MethodHook() {
                        private var networkSpeedView: View? = null
                        override fun after(param: AfterHookCallback) {
                            val networkSpeedState = param.getArgs()[0]
                            if (networkSpeedView == null) {
                                val ctx = secondRight.context
                                val layoutResId = ctx.resources.getIdentifier("network_speed", "layout", "com.android.systemui")
                                networkSpeedView = LayoutInflater.from(ctx).inflate(layoutResId, null)
                                secondRight.addView(networkSpeedView, 0, LinearLayout.LayoutParams(-2, -2))
                                val DarkIconDispatcher = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.plugins.DarkIconDispatcher")
                                XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", networkSpeedView)
                            }
                            if (networkSpeedView != null) {
                                XposedHelpers.callMethod(networkSpeedView, "setBlocked", false)
                                XposedHelpers.callMethod(networkSpeedView, "setNetworkSpeed",
                                    XposedHelpers.getObjectField(networkSpeedState, "networkSpeedNumber"),
                                    XposedHelpers.getObjectField(networkSpeedState, "networkSpeedUnit")
                                )
                                XposedHelpers.callMethod(networkSpeedView, "setVisibilityByController",
                                    XposedHelpers.getObjectField(networkSpeedState, "visible")
                                )
                            }
                        }
                    })
                }
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "updateCutoutLocation", object : MethodHook(-1000) {
            override fun after(param: AfterHookCallback) {
                val mCurrentStatusBarType = XposedHelpers.getObjectField(param.getThisObject(), "mCurrentStatusBarType") as Int
                val leftLayout = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "leftLayout") as LinearLayout?
                val rightLayout = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "rightLayout") as LinearLayout?

                if (leftLayout != null && rightLayout != null) {
                    if (mCurrentStatusBarType == 0) {
                        val leftWidth = MainModule.mPrefs.getInt("system_statusbar_dualrows_left_ratio", 4)
                        val leftLayoutLp = LinearLayout.LayoutParams(0, -1, leftWidth.toFloat())
                        leftLayout.layoutParams = leftLayoutLp
                        val rightLayoutLp = LinearLayout.LayoutParams(0, -1, (10 - leftWidth).toFloat())
                        rightLayout.layoutParams = rightLayoutLp
                    } else {
                        val leftLayoutLp = LinearLayout.LayoutParams(0, -1, 1f)
                        leftLayout.layoutParams = leftLayoutLp
                        val rightLayoutLp = LinearLayout.LayoutParams(0, -1, 1f)
                        rightLayout.layoutParams = rightLayoutLp
                    }
                }
            }
        })
    }

    private fun initDigitalSignalView(mContext: Context, digitalTextView: TextView) {
        val res = mContext.resources
        val styleId = res.getIdentifier("TextAppearance.StatusBar.Clock", "style", "com.android.systemui")
        digitalTextView.setTextAppearance(styleId)
        val subKey = "mobile_digital_signal"
        val fontSize = MainModule.mPrefs.getInt("system_statusbar_${subKey}_fontsize", 26) * 0.5f
        if (MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_in2rows")) {
            digitalTextView.maxLines = 2
            digitalTextView.setLineSpacing(0f, if (fontSize > 8.5f) 0.85f else 0.9f)
        }
        digitalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize)
        if (MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_bold")) {
            digitalTextView.typeface = Typeface.DEFAULT_BOLD
        }
        var leftMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_leftmargin", 8)
        leftMargin = HookUtils.dp2px(leftMargin * 0.5f).toInt()
        var rightMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_rightmargin", 8)
        rightMargin = HookUtils.dp2px(rightMargin * 0.5f).toInt()
        var topMargin = 0
        val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_${subKey}_verticaloffset", 8)
        if (verticalOffset != 8) {
            topMargin = HookUtils.dp2px((verticalOffset - 8) * 0.5f).toInt()
        }
        digitalTextView.setPaddingRelative(leftMargin, topMargin, rightMargin, 0)
        val align = MainModule.mPrefs.getStringAsInt("system_statusbar_${subKey}_align", 1)
        if (align == 2) {
            digitalTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        } else if (align == 3) {
            digitalTextView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        } else if (align == 4) {
            digitalTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }
    }

    @JvmStatic
    fun StatusBarDigitalSignalHook(lpparam: PackageReadyParam) {
        val signalLevelMap = SparseIntArray()
        val MobileStatusTrackerClass = XposedHelpers.findClass("com.android.systemui.statusbar.mobile.MobileStatusTracker", lpparam.classLoader)
        val mCallback = XposedHelpers.findField(MobileStatusTrackerClass, "mCallback")
        ModuleHelper.findAndHookMethod(mCallback.type, "onMobileStatusChanged", Boolean::class.javaPrimitiveType!!, "com.android.systemui.statusbar.mobile.MobileStatusTracker\$MobileStatus", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mobileStatus = param.getArg(1)
                val mobileSignalController = XposedHelpers.getSurroundingThis(param.getThisObject())
                val subscriptionInfo = XposedHelpers.getObjectField(mobileSignalController, "mSubscriptionInfo") as SubscriptionInfo
                val sid = subscriptionInfo.subscriptionId
                val signalStrength = XposedHelpers.getObjectField(mobileStatus, "signalStrength")
                if (signalStrength != null) {
                    val dbm = XposedHelpers.callMethod(signalStrength, "getDbm") as Int
                    signalLevelMap.put(sid, dbm)
                }
            }
        })
        val stateUpdateHook = object : MethodHook() {
            private var initAction = false
            override fun before(param: BeforeHookCallback) {
                if (param.getMember().name == "updateState") {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }

            override fun after(param: AfterHookCallback) {
                val updateStateMethod = param.getMember().name == "updateState"
                val mMobile = XposedHelpers.getObjectField(param.getThisObject(), "mMobile") as View
                val signalImageContainer = mMobile.parent as FrameLayout
                if (initAction) {
                    val digitalView = TextView(signalImageContainer.context)
                    initDigitalSignalView(signalImageContainer.context, digitalView)
                    signalImageContainer.addView(digitalView)
                    digitalView.setTag("digitalSignalView")
                    mMobile.visibility = View.GONE
                }
                if (updateStateMethod || initAction) {
                    val mobileIconState = param.getArgs()[0]
                    val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                    if (!visible) return
                    val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                    if (airplane) return
                    val dualRows = MainModule.mPrefs.getBoolean("system_statusbar_mobile_digital_signal_in2rows")
                    val subId = XposedHelpers.getObjectField(mobileIconState, "subId") as Int
                    val digitalView = signalImageContainer.findViewWithTag<TextView>("digitalSignalView")
                    val hideUnit = MainModule.mPrefs.getBoolean("system_statusbar_mobile_digital_signal_hideunit")
                    if (dualRows) {
                        val slotId = SubscriptionManager.getSlotIndex(subId)
                        if (slotId == 0) {
                            val subSubId = SubscriptionManager.getSubscriptionId(1)
                            digitalView?.text = signalLevelMap.get(subId).toString() + (if (hideUnit) "" else "dBm") +
                                "\n" + signalLevelMap.get(subSubId).toString() + (if (hideUnit) "" else "dBm")
                        }
                    } else {
                        digitalView?.text = signalLevelMap.get(subId).toString() + (if (hideUnit) "" else "dBm")
                    }
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateUpdateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateUpdateHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyDarknessInternal", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                val digitalView = (param.getThisObject() as LinearLayout).findViewWithTag<TextView>("digitalSignalView")
                if (digitalView != null) {
                    digitalView.setTextColor(mMobileTypeSingle.currentTextColor)
                }
            }
        })
        val dualRows = MainModule.mPrefs.getBoolean("system_statusbar_mobile_digital_signal_in2rows")
        if (dualRows) {
            ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setMobileIcons", object : MethodHook() {
                private var isHooked = false
                override fun before(param: BeforeHookCallback) {
                    if (!isHooked) {
                        isHooked = true
                    }
                    val iconStates = param.getArgs()[1] as List<*>
                    if (iconStates.size == 2) {
                        val iconState0 = iconStates[0]
                        val iconState1 = iconStates[1]
                        val mainIconState: Any
                        val subIconState: Any
                        val subId = XposedHelpers.getObjectField(iconState0, "subId") as Int
                        val slotId = SubscriptionManager.getSlotIndex(subId)
                        if (slotId == 0) {
                            mainIconState = iconState0!!
                            subIconState = iconState1!!
                        } else {
                            mainIconState = iconState1!!
                            subIconState = iconState0!!
                        }
                        XposedHelpers.setObjectField(subIconState, "visible", false)
                        val subDataConnected = XposedHelpers.getObjectField(subIconState, "dataConnected") as Boolean
                        if (subDataConnected) {
                            for (field in MOBILE_STATE_SYNC_FIELDS) {
                                XposedHelpers.setObjectField(mainIconState, field, XposedHelpers.getObjectField(subIconState, field))
                            }
                        }
                        param.getArgs()[1] = iconStates
                    }
                }
            })
        }
    }

    private fun getSignalLevel(res: Resources, resId: Int, cache: SparseIntArray): Int {
        if (resId == 0) return 6
        val idx = cache.indexOfKey(resId)
        if (idx >= 0) return cache.valueAt(idx)
        var level = 6
        try {
            val name = res.getResourceName(resId)
            if (name != null && name.contains("signal")) {
                if (name.contains("null")) {
                    level = 6
                } else {
                    val i = name.lastIndexOf("signal_")
                    if (i != -1) {
                        var start = i + "signal_".length
                        var end = start
                        while (end < name.length && Character.isDigit(name[end])) end++
                        if (end > start) {
                            try {
                                level = name.substring(start, end).toInt()
                                if (level < 0 || level > 5) level = 6
                            } catch (ignore: NumberFormatException) {
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
        }
        cache.put(resId, level)
        return level
    }

    private val DUAL_SIGNAL_WHITE_TINT = ColorStateList.valueOf(Color.WHITE)

    private val DUAL_SIGNAL_BLACK_TINT = ColorStateList.valueOf(Color.BLACK)

    private val MOBILE_STATE_SYNC_FIELDS = arrayOf("showName", "activityIn", "activityOut", "dataConnected")

    private fun applyDualSignalDrawables(mobileView: Any?, mobileIconState: Any?, subLevel: Int, systemUIRes: Resources?, signalResToLevelMap: SparseIntArray, dualSignalResIds: Array<Array<IntArray>>, selectedIconStyle: String): Boolean {
        if (systemUIRes == null) return false
        val mainSignalResId = XposedHelpers.getIntField(mobileIconState, "strengthId")
        var mainLevel = getSignalLevel(systemUIRes, mainSignalResId, signalResToLevelMap)
        if (mainLevel == 6) mainLevel = 0
        var subLevelVar = subLevel
        if (subLevelVar == 6) subLevelVar = 0
        val mLight = XposedHelpers.getBooleanField(mobileView, "mLight")
        val mUseTint = XposedHelpers.getBooleanField(mobileView, "mUseTint")
        val mSmallRoaming = XposedHelpers.getObjectField(mobileView, "mSmallRoaming")
        val mMobile = XposedHelpers.getObjectField(mobileView, "mMobile")
        if (mMobile == null || mSmallRoaming == null) return false
        val colorModeIndex = if (mUseTint && selectedIconStyle != "theme") {
            2
        } else if (!mLight) {
            1
        } else {
            0
        }
        val sim1ResId = dualSignalResIds[0][mainLevel][colorModeIndex]
        val sim2ResId = dualSignalResIds[1][subLevelVar][colorModeIndex]
        if (sim1ResId == 0 || sim2ResId == 0) return false
        XposedHelpers.callMethod(mMobile, "setImageResource", sim1ResId)
        XposedHelpers.callMethod(mSmallRoaming, "setImageResource", sim2ResId)
        var tintList: ColorStateList? = null
        val mMobileRoaming = XposedHelpers.getObjectField(mobileView, "mMobileRoaming")
        if (mMobileRoaming != null) {
            tintList = XposedHelpers.callMethod(mMobileRoaming, "getImageTintList") as ColorStateList?
        }
        if (tintList == null) {
            tintList = if (mLight) DUAL_SIGNAL_WHITE_TINT else DUAL_SIGNAL_BLACK_TINT
        }
        XposedHelpers.callMethod(mMobile, "setImageTintList", tintList)
        XposedHelpers.callMethod(mSmallRoaming, "setImageTintList", tintList)
        return true
    }

    @JvmStatic
    fun DualRowSignalHook(lpparam: PackageReadyParam) {
        val mobileTypeSingle = MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single")
        if (!mobileTypeSingle) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_mobile_type_half_to_top_distance", 3)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_mobile_left_inout_over_strength", 0)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_mobile_type_middle_to_strength_start", -0.4f)
        }

        val colorModeList = arrayOf("", "dark", "tint")
        val dualSignalResIds = Array(2) { Array(6) { IntArray(colorModeList.size) } }
        val selectedIconStyle = MainModule.mPrefs.getString("system_statusbar_dualsimin2rows_style", "")

        ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIApplication", lpparam.classLoader, "onCreate", object : MethodHook() {
            private var isHooked = false
            override fun after(param: AfterHookCallback) {
                if (!isHooked) {
                    isHooked = true
                    val mContext = XposedHelpers.callMethod(param.getThisObject(), "getApplicationContext") as Context
                    val modRes = ModuleHelper.getModuleRes(mContext)
                    for (slotIndex in 0..1) {
                        val slot = slotIndex + 1
                        for (lvl in 0..5) {
                            for ((colorModeIndex, colorMode) in colorModeList.withIndex()) {
                                if (selectedIconStyle != "theme" || colorMode != "tint") {
                                    val dualIconResName = "statusbar_signal_${slot}_${lvl}" + (if (colorMode.isNotEmpty()) "_$colorMode" else "") + (if (selectedIconStyle.isNotEmpty()) "_$selectedIconStyle" else "")
                                    val iconResId = modRes.getIdentifier(dualIconResName, "drawable", Helpers.modulePkg)
                                    dualSignalResIds[slotIndex][lvl][colorModeIndex] =
                                        MainModule.resHooks.addFakeResource(dualIconResName, iconResId, "drawable")
                                }
                            }
                        }
                    }
                }
            }
        })

        val systemUIRes = arrayOfNulls<Resources>(1)
        val signalResToLevelMap = SparseIntArray()
        val signalStates = intArrayOf(-1, -1) // main-subId, sub-level
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setMobileIcons", object : MethodHook() {
            private var isHooked = false
            override fun before(param: BeforeHookCallback) {
                if (!isHooked) {
                    isHooked = true
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val res = mContext.resources
                    systemUIRes[0] = res
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_0", "drawable", lpparam.packageName), 0)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_1", "drawable", lpparam.packageName), 1)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_2", "drawable", lpparam.packageName), 2)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_3", "drawable", lpparam.packageName), 3)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_4", "drawable", lpparam.packageName), 4)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_5", "drawable", lpparam.packageName), 5)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_null", "drawable", lpparam.packageName), 6)
                }
                val iconStates = param.getArgs()[1] as List<*>
                if (iconStates.size == 2) {
                    val mainIconState = iconStates[0]
                    val subIconState = iconStates[1]
                    XposedHelpers.setObjectField(subIconState, "visible", false)
                    val subSignalResId = XposedHelpers.getIntField(subIconState, "strengthId")
                    signalStates[0] = XposedHelpers.getIntField(mainIconState, "subId")
                    signalStates[1] = getSignalLevel(systemUIRes[0]!!, subSignalResId, signalResToLevelMap)
                    val subDataConnected = XposedHelpers.getObjectField(subIconState, "dataConnected") as Boolean
                    if (subDataConnected) {
                        for (field in MOBILE_STATE_SYNC_FIELDS) {
                            XposedHelpers.setObjectField(mainIconState, field, XposedHelpers.getObjectField(subIconState, field))
                        }
                    }
                    param.getArgs()[1] = iconStates
                }
            }
        })

        val stateUpdateHook = object : MethodHook() {
            private var initAction = false
            override fun before(param: BeforeHookCallback) {
                if (param.getMember().name == "updateState") {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }
            override fun after(param: AfterHookCallback) {
                val updateStateMethod = param.getMember().name == "updateState"
                if (updateStateMethod || initAction) {
                    val mobileIconState = param.getArgs()[0]
                    val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                    if (!visible) return
                    val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                    if (airplane) return
                    val subId = XposedHelpers.getIntField(mobileIconState, "subId")
                    if (signalStates[0] == -1 || subId != signalStates[0]) return
                    val mSmallHd = XposedHelpers.getObjectField(param.getThisObject(), "mSmallHd")
                    XposedHelpers.callMethod(mSmallHd, "setVisibility", View.GONE)
                    val mSmallRoaming = XposedHelpers.getObjectField(param.getThisObject(), "mSmallRoaming")
                    XposedHelpers.callMethod(mSmallRoaming, "setVisibility", View.VISIBLE)
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateUpdateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateUpdateHook)

        val resetImageDrawable = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mobileIconState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                if (mobileIconState == null) return
                val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                val subId = XposedHelpers.getIntField(mobileIconState, "subId")
                if (!visible || airplane || subId != signalStates[0]) return
                applyDualSignalDrawables(param.getThisObject(), mobileIconState, signalStates[1], systemUIRes[0], signalResToLevelMap, dualSignalResIds, selectedIconStyle)
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyDarknessInternal", resetImageDrawable)

        val onDarkChangedSetter = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mobileIconState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                if (mobileIconState == null) return
                val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                val subId = XposedHelpers.getIntField(mobileIconState, "subId")
                if (!visible || airplane || subId != signalStates[0]) return
                applyDualSignalDrawables(param.getThisObject(), mobileIconState, signalStates[1], systemUIRes[0], signalResToLevelMap, dualSignalResIds, selectedIconStyle)
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "onDarkChanged", onDarkChangedSetter)

        val rightMargin = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_rightmargin", 0)
        val leftMargin = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_leftmargin", 0)
        val iconScale = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_scale", 10)
        val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_verticaloffset", 8)
        if (rightMargin > 0 || leftMargin > 0 || iconScale != 10 || verticalOffset != 8) {
            val initHook = object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mobileView = param.getThisObject() as LinearLayout
                    val inited = mobileView.getTag(viewInitedTag)
                    if (inited == null) {
                        mobileView.setTag(viewInitedTag, true)
                    } else {
                        return
                    }
                    val rightSpacing = HookUtils.dp2px(rightMargin * 0.5f).toInt()
                    val leftSpacing = HookUtils.dp2px(leftMargin * 0.5f).toInt()
                    mobileView.setPadding(leftSpacing, 0, rightSpacing, 0)
                    val mMobile = XposedHelpers.getObjectField(param.getThisObject(), "mMobile") as View
                    if (verticalOffset != 8) {
                        val marginTop = HookUtils.dp2px((verticalOffset - 8) * 0.5f)
                        val mobileIcon = mMobile.parent as FrameLayout
                        mobileIcon.translationY = marginTop
                    }
                    if (iconScale != 10) {
                        val mSmallRoaming = XposedHelpers.getObjectField(param.getThisObject(), "mSmallRoaming") as View
                        val layoutParams = mMobile.layoutParams as FrameLayout.LayoutParams?
                            ?: FrameLayout.LayoutParams(-2, HookUtils.dp2px(2.0f * iconScale).toInt())
                        layoutParams.height = HookUtils.dp2px(2.0f * iconScale).toInt()
                        layoutParams.gravity = Gravity.CENTER
                        mMobile.layoutParams = layoutParams
                        mSmallRoaming.layoutParams = layoutParams
                    }
                }
            }
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "setDripEnd", Boolean::class.javaPrimitiveType!!, initHook)
        }
    }

    @JvmStatic
    fun StatusBarIconsPositionAdjustHook(lpparam: PackageReadyParam, moveLeft: Boolean) {
        val mPrefs = MainModule.mPrefs
        val dualRows = mPrefs.getBoolean("system_statusbar_dualrows")
        val swapWifiSignal = mPrefs.getBoolean("system_statusbaricons_swap_wifi_mobile")
        val moveSignalLeft = mPrefs.getBoolean("system_statusbaricons_wifi_mobile_atleft")
        val netspeedAtRow2 = dualRows && mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")
        val showBatteryDetail = mPrefs.getBoolean("system_statusbar_batterytempandcurrent")
        val showDeviceTemp = mPrefs.getBoolean("system_statusbar_showdevicetemperature")
        val batteryAtRight = showBatteryDetail && !dualRows && mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtRight = showDeviceTemp && !dualRows && mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")
        val batteryAtLeft = showBatteryDetail && !mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtLeft = showDeviceTemp && !mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")

        val leftIcons = HashSet<String>()
        if (!netspeedAtRow2 && mPrefs.getBoolean("system_statusbar_netspeed_atleft")) {
            leftIcons.add("network_speed")
        }
        if (mPrefs.getBoolean("system_statusbar_gps_atleft")) {
            leftIcons.add("location")
        }
        if (mPrefs.getBoolean("system_statusbar_alarm_atleft")) {
            leftIcons.add("alarm_clock")
        }
        if (mPrefs.getBoolean("system_statusbar_sound_atleft")) {
            leftIcons.add("volume")
        }
        if (mPrefs.getBoolean("system_statusbar_dnd_atleft")) {
            leftIcons.add("zen")
        }
        if (batteryAtLeft) {
            leftIcons.add("battery_info")
        }
        if (tempAtLeft) {
            leftIcons.add("device_temp")
        }

        val signalRelatedIcons: List<String>
        signalRelatedIcons = if (!swapWifiSignal) {
            listOf("no_sim", "hd", "mobile", "demo_mobile", "airplane", "hotspot", "wifi", "demo_wifi")
        } else {
            listOf("hotspot", "wifi", "demo_wifi", "no_sim", "hd", "mobile", "demo_mobile", "airplane")
        }

        val leftBlockList = ArrayList<String>()
        val keyguardRightBlockList = ArrayList<String>()

        ModuleHelper.findAndHookConstructor("com.android.systemui.statusbar.phone.StatusBarIconList", lpparam.classLoader, Array<String>::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val allStatusIcons = ArrayList((param.getArgs()[0] as Array<String>).toList())
                val MiuiIconManagerUtils = XposedHelpers.findClass("com.android.systemui.statusbar.phone.MiuiIconManagerUtils", lpparam.classLoader)
                val rightBlockList = ModuleHelper.getStaticObjectFieldSilently(MiuiIconManagerUtils, "RIGHT_BLOCK_LIST") as? ArrayList<String> ?: ArrayList()
                val customIcons = ArrayList<String>()
                if (batteryAtLeft || batteryAtRight) {
                    customIcons.add("battery_info")
                }
                if (tempAtLeft || tempAtRight) {
                    customIcons.add("device_temp")
                }
                if (!customIcons.isEmpty()) {
                    val netspeedIndex = allStatusIcons.indexOf("network_speed") + 1
                    allStatusIcons.addAll(netspeedIndex, customIcons)
                }
                if (netspeedAtRow2) {
                    rightBlockList.add("network_speed")
                }
                if (mPrefs.getBoolean("system_statusbar_alarm_atright")) {
                    rightBlockList.remove("alarm_clock")
                }
                if (mPrefs.getBoolean("system_statusbar_btbattery_atright")) {
                    rightBlockList.remove("bluetooth_handsfree_battery")
                }
                if (mPrefs.getBoolean("system_statusbar_nfc_atright")) {
                    rightBlockList.remove("nfc")
                }
                if (mPrefs.getBoolean("system_statusbar_headset_atright")) {
                    rightBlockList.remove("headset")
                }
                if (mPrefs.getBoolean("system_statusbar_vpn_atright")) {
                    rightBlockList.remove("vpn")
                }
                if (moveLeft) {
                    keyguardRightBlockList.addAll(rightBlockList)
                    for (slotName in allStatusIcons) {
                        if (leftIcons.contains(slotName)) {
                            rightBlockList.add(slotName)
                        } else {
                            leftBlockList.add(slotName)
                        }
                    }
                }
                XposedHelpers.setStaticObjectField(MiuiIconManagerUtils, "RIGHT_BLOCK_LIST", rightBlockList)
                if (swapWifiSignal) {
                    val realSignalIcons = ArrayList<String>()
                    for (slotName in signalRelatedIcons) {
                        if (allStatusIcons.contains(slotName)) {
                            realSignalIcons.add(slotName)
                        }
                    }
                    allStatusIcons.removeAll(signalRelatedIcons)
                    allStatusIcons.addAll(realSignalIcons)
                }
                if (!customIcons.isEmpty() || swapWifiSignal) {
                    param.getArgs()[0] = allStatusIcons.toTypedArray()
                }
            }
        })

        if (moveLeft) {
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onAttachedToWindow", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mStatusBar = param.getThisObject() as FrameLayout
                    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
                    val iconContainer = XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
                    iconContainer.layoutDirection = View.LAYOUT_DIRECTION_RTL
                    iconContainer.setTag("leftIconsContainer")
                    val leftContainer: LinearLayout
                    if (dualRows) {
                        leftContainer = mStatusBar.findViewWithTag<View>("mStatusBarLeftContainer") as LinearLayout
                        leftContainer.addView(iconContainer)
                    } else {
                        val leftNotifyContainer = XposedHelpers.getObjectField(mStatusBar, "mDripStatusBarNotificationIconArea") as View
                        leftContainer = leftNotifyContainer.parent as LinearLayout
                        leftContainer.addView(iconContainer, leftContainer.indexOfChild(leftNotifyContainer))
                    }
                    val miuiIconManagerFactory = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.MiuiIconManagerFactory")

                    val DarkIconManager = XposedHelpers.findClass("com.android.systemui.statusbar.phone.StatusBarIconController\$DarkIconManager", lpparam.classLoader)
                    val mDarkIconManager = XposedHelpers.newInstance(DarkIconManager,
                        iconContainer,
                        XposedHelpers.getObjectField(miuiIconManagerFactory, "mStatusBarPipelineFlags"),
                        XposedHelpers.getObjectField(miuiIconManagerFactory, "mMobileContextProvider"),
                        XposedHelpers.getObjectField(miuiIconManagerFactory, "mDarkIconDispatcher")
                    )

                    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController")
                    XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
                    XposedHelpers.callMethod(iconContainer, "setIgnoredSlots", leftBlockList)
                }
            })

            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment", lpparam.classLoader, "updateStatusBarVisibilities", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
                private var lastShowLeftIcons = -1
                override fun after(param: AfterHookCallback) {
                    val mLastIsFocusedNotifPromptViewShowing = XposedHelpers.getBooleanField(param.getThisObject(), "mLastIsFocusedNotifPromptViewShowing")
                    val mIsShowNotifPromptView = XposedHelpers.getBooleanField(param.getThisObject(), "mIsShowNotifPromptView")
                    val mLastModifiedVisibility = XposedHelpers.getObjectField(param.getThisObject(), "mLastModifiedVisibility")
                    val showSystemInfo = XposedHelpers.getBooleanField(mLastModifiedVisibility, "showSystemInfo")
                    val showLeftIcons = showSystemInfo && (!mIsShowNotifPromptView || !mLastIsFocusedNotifPromptViewShowing)
                    val showFlag = if (showLeftIcons) 1 else 0
                    if (showFlag == lastShowLeftIcons) return
                    lastShowLeftIcons = showFlag
                    val mStatusBar = XposedHelpers.getObjectField(param.getThisObject(), "mStatusBar") as FrameLayout
                    val leftIconContainer = mStatusBar.findViewWithTag<View>("leftIconsContainer")
                    if (leftIconContainer != null) {
                        leftIconContainer.visibility = if (showLeftIcons) View.VISIBLE else View.INVISIBLE
                    }
                }
            })

            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "miuiOnAttachedToWindow", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mTintedIconManager = XposedHelpers.getObjectField(param.getThisObject(), "mTintedIconManager")
                    val mBlockList = XposedHelpers.getObjectField(mTintedIconManager, "mBlockList") as ArrayList<Any>
                    mBlockList.clear()
                    mBlockList.addAll(keyguardRightBlockList)
                    val statusBarIconController = XposedHelpers.getObjectField(mTintedIconManager, "mController")
                    XposedHelpers.callMethod(statusBarIconController, "refreshIconGroup", mTintedIconManager)
                }
            })
        }
    }

    @JvmStatic
    fun StatusBarClockPositionHook(lpparam: PackageReadyParam) {
        val pos = MainModule.mPrefs.getStringAsInt("system_statusbar_clock_position", 1)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val sbView = param.getThisObject() as FrameLayout
                val mContext = sbView.context
                val mClockView = XposedHelpers.getObjectField(param.getThisObject(), "mClock") as TextView
                val leftIconsContainer = mClockView.parent as LinearLayout
                leftIconsContainer.removeView(mClockView)
                val spaceView = XposedHelpers.getObjectField(param.getThisObject(), "mCutoutSpace") as View
                val mContentsContainer = spaceView.parent as LinearLayout
                val spaceIndex = mContentsContainer.indexOfChild(spaceView)
                val rightContainer = LinearLayout(mContext)
                val rightLp = LinearLayout.LayoutParams(0, -1, 1.0f)
                val mSystemIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mSystemIconArea") as View
                mContentsContainer.removeView(mSystemIconArea)
                mContentsContainer.addView(rightContainer, spaceIndex + 1, rightLp)
                rightContainer.addView(mSystemIconArea)

                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
                if (pos == 2) {
                    lp.gravity = Gravity.CENTER
                    mContentsContainer.addView(mClockView, spaceIndex, lp)
                } else {
                    rightContainer.addView(mClockView, lp)
                }
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "updateLayoutForCutout", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mCurrentStatusBarType = XposedHelpers.getObjectField(param.getThisObject(), "mCurrentStatusBarType") as Int
                val mSystemIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mSystemIconArea") as View
                val mStatusBarLeftContainer = XposedHelpers.getObjectField(param.getThisObject(), "mStatusBarLeftContainer") as View
                if (mCurrentStatusBarType == 0) {
                    val mSystemIconAreaLp = mSystemIconArea.layoutParams as LinearLayout.LayoutParams
                    mSystemIconAreaLp.width = 0
                    mSystemIconAreaLp.weight = 1.0f
                    if (pos == 2) {
                        val rightContainer = mSystemIconArea.parent as LinearLayout
                        val mDripStatusBarNotificationIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mDripStatusBarNotificationIconArea") as View
                        mDripStatusBarNotificationIconArea.visibility = View.VISIBLE
                        val mStatusBarLeftContainerLp = mStatusBarLeftContainer.layoutParams as LinearLayout.LayoutParams
                        mStatusBarLeftContainerLp.width = 0
                        mStatusBarLeftContainerLp.weight = 1.0f
                        val sbView = param.getThisObject() as FrameLayout
                        val leftPadding = sbView.paddingStart
                        val rightPadding = sbView.paddingEnd
                        if (Math.abs(leftPadding - rightPadding) > 12) {
                            val topPadding = sbView.paddingTop
                            val bottomPadding = sbView.paddingBottom
                            mStatusBarLeftContainer.setPadding(leftPadding, 0, 0, 0)
                            rightContainer.setPadding(0, 0, rightPadding, 0)
                            sbView.setPadding(0, topPadding, 0, bottomPadding)
                            var focusedNotifView = sbView.findViewWithTag<View>("focused_notif_view")
                            if (focusedNotifView == null) {
                                val focusedNotifViewResId = sbView.resources.getIdentifier("focused_notif_view", "id", "com.android.systemui")
                                if (focusedNotifViewResId > 0) {
                                    focusedNotifView = sbView.findViewById<View>(focusedNotifViewResId)
                                    focusedNotifView.setTag("focused_notif_view")
                                }
                            }
                            focusedNotifView?.let {
                                it.setPaddingRelative(leftPadding, it.paddingTop, 0, 0)
                            }
                        }
                    }
                } else {
                    if (pos == 2) {
                        val mCutoutSpace = XposedHelpers.getObjectField(param.getThisObject(), "mCutoutSpace") as View
                        mCutoutSpace.visibility = View.GONE
                        mStatusBarLeftContainer.setPadding(0, 0, 0, 0)
                        val rightContainer = mSystemIconArea.parent as LinearLayout
                        rightContainer.setPadding(0, 0, 0, 0)
                    }
                }
            }
        })
        if (pos == 2) {
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "updateNotificationIconAreaInnnerParent", object : MethodHook() {
                private var originType = 0
                override fun before(param: BeforeHookCallback) {
                    val mCurrentStatusBarType = XposedHelpers.getIntField(param.getThisObject(), "mCurrentStatusBarType")
                    if (mCurrentStatusBarType == 0) {
                        XposedHelpers.setObjectField(param.getThisObject(), "mCurrentStatusBarType", 1)
                    }
                    originType = mCurrentStatusBarType
                }
                override fun after(param: AfterHookCallback) {
                    XposedHelpers.setObjectField(param.getThisObject(), "mCurrentStatusBarType", originType)
                }
            })
        }
    }

    private var measureTime = 0L

    private var txBytesTotal = 0L

    private var rxBytesTotal = 0L

    private var txSpeed = 0L

    private var rxSpeed = 0L

    private var sampledTxBytes = -1L

    private var sampledRxBytes = -1L

    private fun sampleTrafficBytes() {
        var tx = -1L
        var rx = -1L

        try {
            val list = NetworkInterface.getNetworkInterfaces()
            while (list != null && list.hasMoreElements()) {
                val iface = list.nextElement()
                if (iface.isUp && !iface.isVirtual && !iface.isLoopback && !iface.isPointToPoint && "" != iface.name) {
                    tx += TrafficStats.getTxBytes(iface.name)
                    rx += TrafficStats.getRxBytes(iface.name)
                }
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            tx = TrafficStats.getTotalTxBytes()
            rx = TrafficStats.getTotalRxBytes()
        }

        sampledTxBytes = tx
        sampledRxBytes = rx
    }

    private fun humanReadableByteCount(ctx: Context, bytes: Long): String {
        try {
            val modRes = ModuleHelper.getModuleRes(ctx)
            val hideSecUnit = MainModule.mPrefs.getBoolean("system_detailednetspeed_secunit")
            val unitSuffix = if (hideSecUnit) "" else modRes.getString(R.string.Bs)
            var f = bytes / 1024.0f
            var expIndex = 0
            if (f > 999.0f) {
                expIndex = 1
                f /= 1024.0f
            }
            val pre = modRes.getString(R.string.speedunits)[expIndex]
            val number = formatNetSpeedValue(f)
            return "$number$pre$unitSuffix"
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            return ""
        }
    }

    @JvmStatic
    fun DetailedNetSpeedHook(lpparam: PackageReadyParam) {
        val NetworkSpeedController = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.policy.NetworkSpeedController", lpparam.classLoader)
        if (NetworkSpeedController == null) {
            XposedHelpers.log("DetailedNetSpeedHook", "No NetworkSpeed view or controller")
            return
        }

        val mBgHandlerField = XposedHelpers.findField(NetworkSpeedController, "mBgHandler")
        ModuleHelper.findAndHookMethod(mBgHandlerField.type, "handleMessage", Message::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val message = param.getArg(0) as Message
                if (message.what == 200001) {
                    val thisObect = XposedHelpers.getSurroundingThis(param.getThisObject())
                    var isConnected = false
                    val mContext = XposedHelpers.getObjectField(thisObect, "mContext") as Context
                    val mConnectivityManager = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val nw = mConnectivityManager.activeNetwork
                    if (nw != null) {
                        val capabilities = mConnectivityManager.getNetworkCapabilities(nw)
                        if (capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))) {
                            isConnected = true
                        }
                    }
                    if (isConnected) {
                        val nanoTime = java.lang.System.nanoTime()
                        var newTime = nanoTime - measureTime
                        measureTime = nanoTime
                        if (newTime > 12_000_000_000L || newTime == 0L) newTime = 4_000_000_000L
                        sampleTrafficBytes()
                        val newTxBytes = sampledTxBytes
                        val newRxBytes = sampledRxBytes
                        var newTxBytesFixed = newTxBytes - txBytesTotal
                        var newRxBytesFixed = newRxBytes - rxBytesTotal
                        if (newTxBytesFixed < 0 || txBytesTotal == 0L) newTxBytesFixed = 0
                        if (newRxBytesFixed < 0 || rxBytesTotal == 0L) newRxBytesFixed = 0
                        val elapsedSeconds = newTime / 1_000_000_000.0
                        txSpeed = Math.round(newTxBytesFixed / elapsedSeconds)
                        rxSpeed = Math.round(newRxBytesFixed / elapsedSeconds)
                        txBytesTotal = newTxBytes
                        rxBytesTotal = newRxBytes
                    } else {
                        txSpeed = 0
                        rxSpeed = 0
                    }
                }
            }
        })

        ModuleHelper.hookAllMethods(NetworkSpeedController, "updateText", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                val hideLow = MainModule.mPrefs.getBoolean("system_detailednetspeed_low")
                val lowLevel = MainModule.mPrefs.getInt("system_detailednetspeed_lowlevel", 1) * 1024

                val speedStyle = MainModule.mPrefs.getStringAsInt("system_detailednetspeed_style", 1)

                var txarrow = ""
                var rxarrow = ""
                if (speedStyle == 2) {
                    val icons = MainModule.mPrefs.getStringAsInt("system_detailednetspeed_icon", 2)
                    if (icons == 2) {
                        txarrow = if (txSpeed < lowLevel) "△" else "▲"
                        rxarrow = if (rxSpeed < lowLevel) "▽" else "▼"
                    } else if (icons == 3) {
                        txarrow = if (txSpeed < lowLevel) " ☖" else " ☗"
                        rxarrow = if (rxSpeed < lowLevel) " ⛉" else " ⛊"
                    }
                }

                val strArr = arrayOfNulls<String>(2)
                val rx = if (hideLow && rxSpeed < lowLevel) "" else humanReadableByteCount(mContext, rxSpeed) + rxarrow
                if (speedStyle == 2) {
                    val tx = if (hideLow && txSpeed < lowLevel) "" else humanReadableByteCount(mContext, txSpeed) + txarrow
                    strArr[0] = "$tx\n$rx"
                } else {
                    strArr[0] = rx
                }
                strArr[1] = ""
                param.getArgs()[0] = strArr
            }
        })
    }

    private class NetSpeedTypefaceState(var base: Typeface? = null, var target: Typeface? = null)

    private fun getNetSpeedNumberView(speedView: LinearLayout): TextView? {
        val cached = speedView.getTag(netspeedNumberViewTag) as? TextView
        if (cached != null) return cached
        val numberView = XposedHelpers.getObjectField(speedView, "mNetworkSpeedNumberText") as? TextView ?: return null
        speedView.setTag(netspeedNumberViewTag, numberView)
        return numberView
    }

    private fun getNetSpeedUnitView(speedView: LinearLayout): TextView? {
        val cached = speedView.getTag(netspeedUnitViewTag) as? TextView
        if (cached != null) return cached
        val unitView = XposedHelpers.getObjectField(speedView, "mNetworkSpeedUnitText") as? TextView ?: return null
        speedView.setTag(netspeedUnitViewTag, unitView)
        return unitView
    }

    /**
     * Returns a new [LinearLayout.LayoutParams] copied from [source], preserving the original
     * width, height, weight, gravity and margins. This prevents two Views from sharing a single
     * LayoutParams instance.
     */
    private fun copyLinearLayoutParams(source: ViewGroup.LayoutParams?): LinearLayout.LayoutParams {
        val original = source as? LinearLayout.LayoutParams
        val width = original?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val height = original?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val copy = LinearLayout.LayoutParams(width, height)
        if (original != null) {
            copy.weight = original.weight
            copy.gravity = original.gravity
            copy.topMargin = original.topMargin
            copy.bottomMargin = original.bottomMargin
            copy.leftMargin = original.leftMargin
            copy.rightMargin = original.rightMargin
            copy.marginStart = original.marginStart
            copy.marginEnd = original.marginEnd
        } else if (source is ViewGroup.MarginLayoutParams) {
            copy.topMargin = source.topMargin
            copy.bottomMargin = source.bottomMargin
            copy.leftMargin = source.leftMargin
            copy.rightMargin = source.rightMargin
            copy.marginStart = source.marginStart
            copy.marginEnd = source.marginEnd
        }
        return copy
    }

    private fun ensureNetSpeedTypeface(textView: TextView, bold: Boolean) {
        val state = textView.getTag(netspeedTypefaceStateTag) as? NetSpeedTypefaceState
            ?: NetSpeedTypefaceState().also { textView.setTag(netspeedTypefaceStateTag, it) }

        val current = textView.typeface
        if (state.target != null && current === state.target && textView.paint.isFakeBoldText == bold) return

        if (state.target != null && current !== state.target) {
            state.base = current
            state.target = null
        }

        if (state.base == null) state.base = current

        val base = state.base
        val targetStyle = resolveNetSpeedTypefaceStyle(base?.style ?: 0, bold)
        val target = Typeface.create(base, targetStyle)
        textView.typeface = target
        textView.paint.isFakeBoldText = bold
        state.target = target
    }

    private fun applyNetSpeedTextStyle(speedView: LinearLayout, typefaceOnly: Boolean = false) {
        if (speedView.tag as? String == "slot_text_icon") return

        val numberView = getNetSpeedNumberView(speedView) ?: return
        val unitView = getNetSpeedUnitView(speedView)

        val speedStyle = MainModule.mPrefs.getStringAsInt("system_detailednetspeed_style", 1)
        val unitVisible = speedStyle == 1
        val bold = MainModule.mPrefs.getBoolean("system_netspeed_boldfont")

        if (!typefaceOnly) {
            val fontSize = MainModule.mPrefs.getInt("system_netspeed_fontsize", 13)

            if (fontSize > 13) {
                val size = fontSize * 0.5f
                numberView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
                if (unitVisible) unitView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
            }

            val fixedWidth = MainModule.mPrefs.getInt("system_netspeed_fixedcontent_width", 10)
            val singleOrDual = speedStyle == 2 || speedStyle == 3
            if (singleOrDual) {
                numberView.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                unitView?.visibility = View.GONE
            }
            if (fixedWidth > 10 || singleOrDual) {
                val numberLp = copyLinearLayoutParams(numberView.layoutParams)
                if (fixedWidth > 10) {
                    numberLp.width = HookUtils.dp2px(fixedWidth.toFloat()).toInt()
                }
                if (singleOrDual) {
                    numberLp.topMargin = 0
                    numberLp.height = -1
                    numberLp.bottomMargin = 0
                }
                numberView.layoutParams = numberLp

                unitView?.let { unit ->
                    val unitLp = copyLinearLayoutParams(unit.layoutParams)
                    unit.layoutParams = unitLp
                }
            }

            var leftMargin = MainModule.mPrefs.getInt("system_netspeed_leftmargin", 0)
            leftMargin = HookUtils.dp2px(leftMargin * 0.5f).toInt()
            var rightMargin = MainModule.mPrefs.getInt("system_netspeed_rightmargin", 0)
            rightMargin = HookUtils.dp2px(rightMargin * 0.5f).toInt()
            val verticalOffset = MainModule.mPrefs.getInt("system_netspeed_verticaloffset", 8)
            val topMargin = if (verticalOffset == 8) 0 else HookUtils.dp2px((verticalOffset - 8) * 0.5f).toInt()
            speedView.translationY = topMargin.toFloat()
            speedView.setPaddingRelative(leftMargin, 0, rightMargin, 0)

            val align = MainModule.mPrefs.getStringAsInt("system_detailednetspeed_align", 1)
            if (align > 1) {
                val alignVal = when (align) {
                    3 -> View.TEXT_ALIGNMENT_CENTER
                    4 -> View.TEXT_ALIGNMENT_TEXT_END
                    else -> View.TEXT_ALIGNMENT_TEXT_START
                }
                numberView.textAlignment = alignVal
                if (unitVisible) unitView?.textAlignment = alignVal
            }

            if (speedStyle == 2) {
                val adjustment = MainModule.mPrefs.getInt("system_netspeed_rowspacing", 100)
                val spacing = resolveNetSpeedLineSpacing(fontSize, adjustment)
                numberView.setSingleLine(false)
                numberView.maxLines = 2
                numberView.setLineSpacing(0f, spacing)
            }

            speedView.setTag(viewInitedTag, true)
        }

        ensureNetSpeedTypeface(numberView, bold)
        if (unitVisible) unitView?.let { ensureNetSpeedTypeface(it, bold) }
    }

    @JvmStatic
    fun NetSpeedStyleHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("android.widget.TextView", lpparam.classLoader, "setTextAppearance", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val textView = param.getThisObject() as? TextView ?: return
                val state = textView.getTag(netspeedTypefaceStateTag) as? NetSpeedTypefaceState ?: return
                val speedView = textView.parent as? LinearLayout ?: return
                if (speedView.tag as? String == "slot_text_icon") return
                state.base = textView.typeface
                state.target = null
                applyNetSpeedTextStyle(speedView, false)
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.views.NetworkSpeedView", lpparam.classLoader, "setNetworkSpeed", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val speedView = param.getThisObject() as? LinearLayout ?: return
                if (speedView.tag as? String == "slot_text_icon") return
                if (speedView.getTag(viewInitedTag) == null) {
                    applyNetSpeedTextStyle(speedView, false)
                } else {
                    applyNetSpeedTextStyle(speedView, true)
                }
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.NetworkSpeedView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val speedView = param.getThisObject() as? LinearLayout ?: return
                if (speedView.tag as? String == "slot_text_icon") return
                if (speedView.getTag(viewInitedTag) != null) return

                val numberView = getNetSpeedNumberView(speedView) ?: return
                val unitView = getNetSpeedUnitView(speedView)

                numberView.getTag(netspeedTypefaceStateTag) as? NetSpeedTypefaceState
                    ?: NetSpeedTypefaceState().also { numberView.setTag(netspeedTypefaceStateTag, it) }
                unitView?.let { view ->
                    view.getTag(netspeedTypefaceStateTag) as? NetSpeedTypefaceState
                        ?: NetSpeedTypefaceState().also { view.setTag(netspeedTypefaceStateTag, it) }
                }

                val useClockStyle = MainModule.mPrefs.getBoolean("system_netspeed_use_clock_style")
                if (useClockStyle) {
                    val styleId = speedView.resources.getIdentifier("TextAppearance.StatusBar.Clock", "style", "com.android.systemui")
                    val speedStyle = MainModule.mPrefs.getStringAsInt("system_detailednetspeed_style", 1)
                    if (styleId != 0) {
                        numberView.setTextAppearance(styleId)
                        if (speedStyle == 1) unitView?.setTextAppearance(styleId)
                    }
                } else {
                    applyNetSpeedTextStyle(speedView, false)
                }
            }
        })
    }

    @JvmStatic
    fun NetSpeedIntervalHook(lpparam: PackageReadyParam) {
        val NetworkSpeedController = XposedHelpers.findClass("com.android.systemui.statusbar.policy.NetworkSpeedController", lpparam.classLoader)
        val mBgHandlerField = XposedHelpers.findField(NetworkSpeedController, "mBgHandler")
        ModuleHelper.findAndHookMethod(mBgHandlerField.type, "handleMessage", Message::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val message = param.getArgs()[0] as Message
                if (message.what == 200001) {
                    val mBgHandler = param.getThisObject() as Handler
                    mBgHandler.removeMessages(200001)
                    val newInterval = MainModule.mPrefs.getInt("system_netspeedinterval", 4) * 1000L
                    mBgHandler.sendEmptyMessageDelayed(200001, newInterval)
                }
            }
        })
    }

    @JvmStatic
    fun MobileNetworkTypeHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.connectivity.MobileSignalController", lpparam.classLoader, "getMobileTypeName", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val net = param.getResult() as String
                if (MainModule.mPrefs.getBoolean("system_4gtolte")) {
                    if ("4G" == net) param.setResult("LTE")
                    else if ("4G+" == net) param.setResult("LTE+")
                } else {
                    val mobileType = MainModule.mPrefs.getString("system_statusbar_mobile_showname", "")
                    param.setResult(mobileType)
                }
            }
        })
    }

    @JvmStatic
    fun DisableFakeClockAnimHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "setMNCSwitching", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mNCSwitching = param.getArgs()[0] as Boolean
                if (!mNCSwitching) {
                    val mFakeClock = XposedHelpers.getObjectField(param.getThisObject(), "fakeStatusBarClockController")
                    XposedHelpers.setObjectField(mFakeClock, "ncSwitching", true)
                }
            }
        })
    }

    @JvmStatic
    fun MobileTypeSingleHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateMobileTypeLayout", HookerClassHelper.DO_NOTHING)
        val stateHook = object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
            private var initAction = false

            override fun before(param: BeforeHookCallback) {
                XposedHelpers.setObjectField(param.getArg(0), "showMobileDataTypeSingle", true)
                if (param.getMember().name == "updateState") {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }

            override fun after(param: AfterHookCallback) {
                val updateStateMethod = param.getMember().name == "updateState"
                if (updateStateMethod || initAction) {
                    val mMobileLeftContainer = XposedHelpers.getObjectField(param.getThisObject(), "mMobileLeftContainer")
                    XposedHelpers.callMethod(mMobileLeftContainer, "setVisibility", View.GONE)
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateHook)

        val initHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mobileView = param.getThisObject() as View
                val inited = ModuleHelper.getViewInfo(mobileView, "mobileTypeHook")
                if (inited == null) {
                    ModuleHelper.setViewInfo(mobileView, "mobileTypeHook", true)
                } else {
                    return
                }
                val mMobileGroup = XposedHelpers.getObjectField(param.getThisObject(), "mMobileGroup") as LinearLayout
                val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                if (!MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single_atleft")) {
                    mMobileGroup.removeView(mMobileTypeSingle)
                    mMobileGroup.addView(mMobileTypeSingle)
                }
                val mlp = mMobileTypeSingle.layoutParams as ViewGroup.MarginLayoutParams
                var leftMargin = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_leftmargin", 4)
                mlp.leftMargin = HookUtils.dp2px(leftMargin * 0.5f).toInt()
                val rightMargin = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_rightmargin", 0)
                if (rightMargin > 0) {
                    mlp.rightMargin = HookUtils.dp2px(rightMargin * 0.5f).toInt()
                }
                val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_verticaloffset", 8)
                if (verticalOffset != 8) {
                    mlp.topMargin = HookUtils.dp2px((verticalOffset - 8) * 0.5f).toInt()
                }
                mMobileTypeSingle.layoutParams = mlp
                val fontSize = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_fontsize", 27)
                mMobileTypeSingle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize * 0.5f)
                if (MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single_bold")) {
                    mMobileTypeSingle.typeface = Typeface.DEFAULT_BOLD
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "setDripEnd", Boolean::class.javaPrimitiveType!!, initHook)
    }

    @JvmStatic
    fun HorizMarginHook(lpparam: PackageReadyParam) {
        val horizHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val leftMargin = MainModule.mPrefs.getInt("system_statusbar_horizmargin_left", 16)
                val leftMarginPx = HookUtils.dp2px(leftMargin.toFloat()).toInt()
                val rightMargin = MainModule.mPrefs.getInt("system_statusbar_horizmargin_right", 16)
                val rightMarginPx = HookUtils.dp2px(rightMargin.toFloat()).toInt()
                param.returnAndSkip(Pair(leftMarginPx, rightMarginPx))
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider", lpparam.classLoader, "getStatusBarContentInsetsForCurrentRotation", horizHook)
    }

    @JvmStatic
    fun HideIconsVoWiFiHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors("com.android.systemui.MiuiOperatorCustomizedPolicy\$MiuiOperatorConfig", lpparam.classLoader, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.getArgs()[3] = true
            }
        })
    }

    @JvmStatic
    fun HideIconsSignalHook(lpparam: PackageReadyParam) {
        val stateHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mobileIconState = param.getArg(0)
                var shouldUpdate = "updateState" == param.getMember().name
                if (!shouldUpdate) {
                    val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                    shouldUpdate = mState == null
                }
                if (!shouldUpdate) return
                if (MainModule.mPrefs.getBoolean("system_statusbaricons_signal")) {
                    if (!MainModule.mPrefs.getBoolean("system_statusbaricons_signal_wificonnected") || XposedHelpers.getBooleanField(mobileIconState, "wifiAvailable")) {
                        XposedHelpers.setObjectField(mobileIconState, "visible", false)
                        return
                    }
                }
                val subId = XposedHelpers.getObjectField(mobileIconState, "subId") as Int
                val dataSubId = SubscriptionManager.getActiveDataSubscriptionId()
                val slotId = SubscriptionManager.getSlotIndex(subId)
                if ((MainModule.mPrefs.getBoolean("system_statusbaricons_sim1") && slotId == 0)
                    || (MainModule.mPrefs.getBoolean("system_statusbaricons_sim2") && slotId == 1)
                    || (MainModule.mPrefs.getBoolean("system_statusbaricons_sim_nodata") && subId != dataSubId)
                ) {
                    XposedHelpers.setObjectField(mobileIconState, "visible", false)
                    return
                }
                if (MainModule.mPrefs.getBoolean("system_statusbaricons_roaming")) {
                    XposedHelpers.setObjectField(mobileIconState, "roaming", false)
                }
                if (MainModule.mPrefs.getBoolean("system_statusbaricons_volte")) {
                    XposedHelpers.setObjectField(mobileIconState, "volte", false)
                    XposedHelpers.setObjectField(mobileIconState, "speechHd", false)
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateHook)
    }

    private fun checkSlot(slotName: String?): Boolean {
        return try {
            ("headset" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_headset"))
                || ("volume" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_sound"))
                || ("zen" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_dnd"))
                || ("alarm_clock" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_alarm"))
                || ("managed_profile" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_profile"))
                || ("vpn" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_vpn"))
                || ("airplane" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_airplane"))
                || ("nfc" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_nfc"))
                || ("second_space" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_secondspace"))
                || ("location" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_gps"))
                || ("wifi" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_wifi"))
                || ("hotspot" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_hotspot"))
                || ("no_sim" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_nosims"))
                || ("bluetooth_handsfree_battery" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_btbattery"))
                || ("ble_unlock_mode" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_ble_unlock"))
                || ("bluetooth" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_bluetoothicn"))
                || ("hd" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_volte"))
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            false
        }
    }

    @JvmStatic
    fun HideIconsHook(lpparam: PackageReadyParam) {
        val iconHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val iconType = param.getArgs()[0] as String
                if (checkSlot(iconType)) {
                    param.getArgs()[1] = false
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setIconVisibility", String::class.java, Boolean::class.javaPrimitiveType!!, iconHook)
    }

    @JvmStatic
    fun HideIconsFromSystemManager(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.CommandQueue", lpparam.classLoader, "setIcon", String::class.java, "com.android.internal.statusbar.StatusBarIcon", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val slotName = param.getArg(0) as String
                if (("stealth" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_privacy"))
                    || ("mute" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_mute"))
                    || ("speakerphone" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_speaker"))
                    || ("call_record" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_record"))
                    || ("wireless_headset" == slotName && MainModule.mPrefs.getBoolean("system_statusbaricons_wireless_headset"))
                ) {
                    XposedHelpers.setObjectField(param.getArg(1), "visible", false)
                }
            }
        })
    }

    @JvmStatic
    fun BatteryIndicatorHook(lpparam: PackageReadyParam) {
        SystemUIBatteryHooks.BatteryIndicatorHook(lpparam)
    }

    @JvmStatic
    fun StatusBarStyleBatteryIconHook(lpparam: PackageReadyParam) {
        SystemUIBatteryHooks.StatusBarStyleBatteryIconHook(lpparam)
    }

    @JvmStatic
    fun ForceClockUseSystemFontsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.clock.MiuiBaseClock", lpparam.classLoader, "updateViewsTextSize", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mTimeText = XposedHelpers.getObjectField(param.getThisObject(), "mTimeText") as TextView
                mTimeText.typeface = Typeface.DEFAULT
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.clock.MiuiLeftTopLargeClock", lpparam.classLoader, "onLanguageChanged", String::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mTimeText = XposedHelpers.getObjectField(param.getThisObject(), "mCurrentDateLarge") as TextView
                mTimeText.typeface = Typeface.DEFAULT
            }
        })
    }

    @JvmStatic
    fun HideMobileNetworkIndicatorHook(lpparam: PackageReadyParam) {
        val singleMobileType = MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single")
        val showOnWifi = MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_show_wificonnected")
        val hideMobileActivity = object : MethodHook() {
            private var initAction = false
            override fun before(param: BeforeHookCallback) {
                if ("updateState" == param.getMember().name) {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }
            override fun after(param: AfterHookCallback) {
                val updateStateMethod = "updateState" == param.getMember().name
                if (updateStateMethod || initAction) {
                    val opt = MainModule.mPrefs.getStringAsInt("system_mobiletypeicon", 1)
                    val hideIndicator = MainModule.mPrefs.getBoolean("system_networkindicator_mobile")
                    val mMobileType = XposedHelpers.getObjectField(param.getThisObject(), "mMobileType") as View
                    val dataConnected = XposedHelpers.getBooleanField(param.getArgs()[0], "dataConnected")
                    val wifiAvailable = XposedHelpers.getObjectField(param.getArgs()[0], "wifiAvailable") as Boolean
                    if (opt == 3) {
                        if (singleMobileType) {
                            val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                            mMobileTypeSingle.visibility = View.GONE
                        } else {
                            mMobileType.visibility = View.GONE
                        }
                    } else if (opt == 1) {
                        val viz = if (dataConnected && (!wifiAvailable || showOnWifi)) View.VISIBLE else View.GONE
                        if (singleMobileType) {
                            val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                            mMobileTypeSingle.visibility = viz
                        } else {
                            mMobileType.visibility = viz
                        }
                    } else if (opt == 2) {
                        val viz = if (!wifiAvailable || showOnWifi) View.VISIBLE else View.GONE
                        if (singleMobileType) {
                            val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                            mMobileTypeSingle.visibility = viz
                        } else {
                            mMobileType.visibility = viz
                        }
                    }
                    val mLeftInOut = XposedHelpers.getObjectField(param.getThisObject(), "mLeftInOut") as View
                    if (hideIndicator) {
                        val mRightInOut = XposedHelpers.getObjectField(param.getThisObject(), "mRightInOut") as View
                        mLeftInOut.visibility = View.GONE
                        mRightInOut.visibility = View.GONE
                    }
                    if (wifiAvailable && showOnWifi && (dataConnected || opt == 2)) {
                        if (!Build.IS_INTERNATIONAL_BUILD) {
                            val mSmallHd = XposedHelpers.getObjectField(param.getThisObject(), "mSmallHd") as View
                            mSmallHd.visibility = View.GONE
                        }
                        if (opt != 2) {
                            val viz = View.VISIBLE
                            if (singleMobileType) {
                                val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                                mMobileTypeSingle.visibility = viz
                            } else {
                                mMobileType.visibility = viz
                            }
                        }
                    }
                    if (!singleMobileType) {
                        val mMobileLeftContainer = XposedHelpers.getObjectField(param.getThisObject(), "mMobileLeftContainer") as View
                        mMobileLeftContainer.visibility = if (mMobileType.visibility == View.GONE && mLeftInOut.visibility == View.GONE) View.GONE else View.VISIBLE
                    }
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", hideMobileActivity)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", hideMobileActivity)
    }

    @JvmStatic
    fun HidePrivacyIndicatorHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl", lpparam.classLoader, "setStatus", Int::class.javaPrimitiveType!!, String::class.java, Bundle::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.returnAndSkip(null)
            }
        })
    }

}
