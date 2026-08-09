package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.BatteryIndicator

@Suppress("MemberVisibilityCanBePrivate")
object SystemUIBatteryHooks {
    private const val StatusBarCls = "com.android.systemui.statusbar.phone.CentralSurfacesImpl"

    private const val PREF_SWAP = "system_statusbaricons_swap_batteryicon_percentage"
    private const val PREF_FONT_SIZE = "system_statusbar_batterystyle_fontsize"
    private const val PREF_MARK_FONT_SIZE = "system_statusbar_batterystyle_mark_fontsize"
    private const val PREF_BOLD = "system_statusbar_batterystyle_bold"
    private const val PREF_LEFT_MARGIN = "system_statusbar_batterystyle_leftmargin"
    private const val PREF_RIGHT_MARGIN = "system_statusbar_batterystyle_rightmargin"
    private const val PREF_VERTICAL_OFFSET = "system_statusbar_batterystyle_verticaloffset"
    private const val PREF_MARK_VERTICAL_OFFSET = "system_statusbar_batterystyle_mark_verticaloffset"
    private const val PREF_BATTERY4 = "system_statusbaricons_battery4"

    private val BATTERY_STYLE_KEYS = setOf(
        PREF_SWAP,
        PREF_FONT_SIZE,
        PREF_MARK_FONT_SIZE,
        PREF_BOLD,
        PREF_LEFT_MARGIN,
        PREF_RIGHT_MARGIN,
        PREF_VERTICAL_OFFSET,
        PREF_MARK_VERTICAL_OFFSET,
        PREF_BATTERY4,
    )

    /**
     * Immutable battery style state.
     *
     * `MiuiBatteryMeterView.updateAll` runs on every battery, dark mode and configuration
     * update, so it reads this snapshot instead of nine preference lookups and only writes a
     * view property when the current value actually differs.
     */
    private class BatteryStyle(
        val swap: Boolean,
        val fontSizeDp: Float,
        val markFontSizeDp: Float,
        val bold: Boolean,
        val leftMarginDp: Float,
        val rightMarginDp: Float,
        val verticalOffset: Int,
        val markVerticalOffset: Int,
        val battery4: Boolean,
    )

    @Volatile
    private var batteryStyle: BatteryStyle? = null

    @Volatile
    private var batteryStyleObserverRegistered = false

    private fun readBatteryStyle(): BatteryStyle {
        val prefs = MainModule.mPrefs
        return BatteryStyle(
            swap = prefs.getBoolean(PREF_SWAP),
            fontSizeDp = prefs.getInt(PREF_FONT_SIZE, 15) * 0.5f,
            markFontSizeDp = prefs.getInt(PREF_MARK_FONT_SIZE, 15) * 0.5f,
            bold = prefs.getBoolean(PREF_BOLD),
            leftMarginDp = prefs.getInt(PREF_LEFT_MARGIN, 0) * 0.5f,
            rightMarginDp = prefs.getInt(PREF_RIGHT_MARGIN, 0) * 0.5f,
            verticalOffset = prefs.getInt(PREF_VERTICAL_OFFSET, 8),
            markVerticalOffset = prefs.getInt(PREF_MARK_VERTICAL_OFFSET, 17),
            battery4 = prefs.getBoolean(PREF_BATTERY4),
        )
    }

    private fun installBatteryStyleSnapshot() {
        batteryStyle = readBatteryStyle()
        if (batteryStyleObserverRegistered) return
        batteryStyleObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in BATTERY_STYLE_KEYS) {
                    batteryStyle = readBatteryStyle()
                }
            }
        })
    }

    /** Moves [view] to [index] only when it is not already there, avoiding a needless relayout. */
    private fun moveChildTo(parent: LinearLayout, view: TextView, index: Int) {
        if (parent.indexOfChild(view) == index) return
        parent.removeView(view)
        parent.addView(view, index)
    }

    private fun setTextSizeIfChanged(view: TextView, sizeDp: Float) {
        val target = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDp, view.resources.displayMetrics)
        if (view.textSize != target) view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
    }

    private fun setPaddingRelativeIfChanged(view: TextView, start: Int, top: Int, end: Int, bottom: Int) {
        if (view.paddingStart == start && view.paddingTop == top &&
            view.paddingEnd == end && view.paddingBottom == bottom
        ) {
            return
        }
        view.setPaddingRelative(start, top, end, bottom)
    }

    @JvmStatic
    fun BatteryIndicatorHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(StatusBarCls, lpparam.classLoader, "start", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                val sbWindowController = XposedHelpers.getObjectField(param.getThisObject(), "mStatusBarWindowController")
                val mStatusBarWindow = XposedHelpers.getObjectField(sbWindowController, "mStatusBarWindowView") as ViewGroup

                val indicator = BatteryIndicator(mContext)
                mStatusBarWindow.addView(indicator)
                indicator.setAdjustViewBounds(false)
                indicator.init(param.getThisObject())
                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator", indicator)
                val mNotificationIconAreaController = XposedHelpers.getObjectField(param.getThisObject(), "mNotificationIconAreaController")
                XposedHelpers.setAdditionalInstanceField(mNotificationIconAreaController, "mBatteryIndicator", indicator)
                val mBatteryController = XposedHelpers.getObjectField(param.getThisObject(), "mBatteryController")
                XposedHelpers.setAdditionalInstanceField(mBatteryController, "mBatteryIndicator", indicator)
                XposedHelpers.callMethod(mBatteryController, "fireBatteryLevelChanged")
                XposedHelpers.callMethod(mBatteryController, "firePowerSaveChanged")
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "updatePanelExpanded", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mPanelExpanded = XposedHelpers.getBooleanField(param.getThisObject(), "mPanelExpanded")
                val isKeyguardShowing = XposedHelpers.callMethod(param.getThisObject(), "isKeyguardShowing") as Boolean
                val mStatusBar = XposedHelpers.getObjectField(param.getThisObject(), "mCentralSurfaces")
                val indicator = XposedHelpers.getAdditionalInstanceField(mStatusBar, "mBatteryIndicator") as BatteryIndicator?
                indicator?.onExpandingChanged(!isKeyguardShowing && mPanelExpanded)
            }
        })

        ModuleHelper.findAndHookMethod(StatusBarCls, lpparam.classLoader, "updateIsKeyguard", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val isKeyguardShowing = XposedHelpers.callMethod(param.getThisObject(), "isKeyguardShowing") as Boolean
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onKeyguardStateChanged(isKeyguardShowing)
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.NotificationIconAreaController", lpparam.classLoader, "onDarkChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onDarkModeChanged(param.getArgs()[1] as Float, param.getArgs()[2] as Int)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.MiuiBatteryControllerImpl", lpparam.classLoader, "fireBatteryLevelChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                val mLevel = XposedHelpers.getIntField(param.getThisObject(), "mLevel")
                val mCharging = XposedHelpers.getBooleanField(param.getThisObject(), "mCharging")
                val mCharged = XposedHelpers.getBooleanField(param.getThisObject(), "mCharged")
                indicator?.onBatteryLevelChanged(mLevel, mCharging, mCharged)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BatteryControllerImpl", lpparam.classLoader, "firePowerSaveChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onPowerSaveChanged(XposedHelpers.getBooleanField(param.getThisObject(), "mPowerSave"))
            }
        })
    }

    @JvmStatic
    fun StatusBarStyleBatteryIconHook(lpparam: PackageReadyParam) {
        installBatteryStyleSnapshot()
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiBatteryMeterView", lpparam.classLoader, "updateAll", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val style = batteryStyle ?: return
                val batteryView = param.getThisObject() as LinearLayout
                val mBatteryTextDigitView = XposedHelpers.getObjectField(param.getThisObject(), "mBatteryTextDigitView") as TextView
                val mBatteryPercentView = XposedHelpers.getObjectField(param.getThisObject(), "mBatteryPercentView") as TextView
                val mBatteryPercentMarkView = XposedHelpers.getObjectField(param.getThisObject(), "mBatteryPercentMarkView") as TextView
                if (style.swap) {
                    // Same result as remove/remove/add/add, but without touching the hierarchy
                    // when the percentage is already in front.
                    moveChildTo(batteryView, mBatteryPercentMarkView, 0)
                    moveChildTo(batteryView, mBatteryPercentView, 0)
                }
                if (style.fontSizeDp > 7.5) {
                    setTextSizeIfChanged(mBatteryTextDigitView, style.fontSizeDp)
                    setTextSizeIfChanged(mBatteryPercentView, style.fontSizeDp)
                }
                if (style.markFontSizeDp > 7.5) {
                    setTextSizeIfChanged(mBatteryPercentMarkView, style.markFontSizeDp)
                }
                if (style.bold) {
                    if (mBatteryTextDigitView.typeface !== Typeface.DEFAULT_BOLD) {
                        mBatteryTextDigitView.typeface = Typeface.DEFAULT_BOLD
                    }
                    if (mBatteryPercentView.typeface !== Typeface.DEFAULT_BOLD) {
                        mBatteryPercentView.typeface = Typeface.DEFAULT_BOLD
                    }
                }
                val metrics = batteryView.resources.displayMetrics
                val leftMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, style.leftMarginDp, metrics).toInt()
                var topMargin = 0
                if (style.verticalOffset != 8) {
                    topMargin = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        (style.verticalOffset - 8) * 0.5f,
                        metrics
                    ).toInt()
                }
                val rightMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, style.rightMarginDp, metrics).toInt()
                val (digitRightMargin, markRightMargin) = if (style.battery4) {
                    rightMargin to 0
                } else {
                    0 to rightMargin
                }
                if (leftMargin > 0 || topMargin != 8 || digitRightMargin > 0) {
                    setPaddingRelativeIfChanged(mBatteryPercentView, leftMargin, topMargin, digitRightMargin, 0)
                }

                val markTopMargin = if (style.markVerticalOffset < 17) {
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        (style.markVerticalOffset - 8) * 0.5f,
                        metrics
                    ).toInt()
                } else topMargin
                if (style.markVerticalOffset < 17 || markRightMargin > 0) {
                    setPaddingRelativeIfChanged(mBatteryPercentMarkView, 0, markTopMargin, markRightMargin, 0)
                }
            }
        })
    }
}
