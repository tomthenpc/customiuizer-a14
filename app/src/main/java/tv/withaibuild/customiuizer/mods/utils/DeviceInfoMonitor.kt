package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemUI
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.Properties

/**
 * Battery / device-temperature status bar text icon monitor.
 *
 * Goals: screen off pauses the ticker; screen on resumes immediately; no
 * message is sent to the main thread when the computed text has not changed;
 * sysfs reads back off on repeated failures; all user preferences are captured
 * in an immutable snapshot that is refreshed when a relevant preference key
 * changes.
 */
object DeviceInfoMonitor {

    private const val MONITOR_MESSAGE = 200021
    private const val UPDATE_MESSAGE = 100021
    private const val BASE_MONITOR_DELAY_MS = 2000L
    private const val MAX_MONITOR_DELAY_MS = 60000L

    private data class ConfigSnapshot(
        val showBatteryDetail: Boolean,
        val showDeviceTemp: Boolean,
        val dualRows: Boolean,
        val batteryAtRight: Boolean,
        val tempAtRight: Boolean,
        val batteryAtLeft: Boolean,
        val tempAtLeft: Boolean,
        val batteryInCharge: Boolean,
        val batteryTempDecimal: Boolean,
        val batteryFixCurrentRatio: Boolean,
        val batteryPositive: Boolean,
        val batterySingleRow: Boolean,
        val batteryReverseOrder: Boolean,
        val batteryHideUnit: Int,
        val deviceTempSingleRow: Boolean,
        val deviceTempReverseOrder: Boolean,
        val deviceTempHideUnit: Boolean,
        val batteryContentOpt: Int,
        val deviceTempContentOpt: Int
    ) {
        val customIconTypes: List<Int> = buildList {
            if (batteryAtLeft || batteryAtRight) add(91)
            if (tempAtLeft || tempAtRight) add(92)
        }
    }

    private data class IconUpdate(
        val type: Int,
        val show: Boolean,
        val text: String
    )

    private data class DeviceData(
        val batteryShow: Boolean,
        val batteryText: String,
        val tempShow: Boolean,
        val tempText: String
    )

    private data class TextIconState(
        var show: Boolean = false,
        var text: String = ""
    )

    @Volatile
    private var screenOn: Boolean = true

    private val monitorLock = Any()
    private val batteryState = TextIconState()
    private val tempState = TextIconState()

    @Volatile
    private var config: ConfigSnapshot? = null

    private var activeContext: Context? = null
    private var activeMainHandler: Handler? = null
    private var activeBgHandler: Handler? = null
    private var screenReceiver: BroadcastReceiver? = null

    private var consecutiveFailCount = 0
    private var chargeUtilsClass: Class<*>? = null
    private var lpClassLoader: ClassLoader? = null

    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key == null ||
                key.startsWith("system_statusbar_batterytempandcurrent") ||
                key.startsWith("system_statusbar_showdevicetemperature")
            ) {
                onConfigMayHaveChanged()
            }
        }
    }

    @JvmStatic
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun hook(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        ModuleHelper.observePreferenceChange(preferenceObserver)

        val cfg = buildConfig(mPrefs)
        config = cfg
        lpClassLoader = lpparam.classLoader
        if (cfg.customIconTypes.isEmpty()) return

        if (cfg.showBatteryDetail && cfg.batteryInCharge) {
            chargeUtilsClass = XposedHelpers.findClassIfExists(
                "com.miui.charge.ChargeUtils",
                lpparam.classLoader
            )
        }

        hookIconSlots(lpparam, cfg)
        hookNetworkSpeedView(lpparam)
        hookMonitor(lpparam, cfg)
    }

    private fun buildConfig(mPrefs: PrefMap): ConfigSnapshot {
        val showBatteryDetail = mPrefs.getBoolean("system_statusbar_batterytempandcurrent")
        val showDeviceTemp = mPrefs.getBoolean("system_statusbar_showdevicetemperature")
        val dualRows = mPrefs.getBoolean("system_statusbar_dualrows")
        val batteryAtRight = showBatteryDetail && !dualRows && mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtRight = showDeviceTemp && !dualRows && mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")
        val batteryAtLeft = showBatteryDetail && !mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtLeft = showDeviceTemp && !mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")
        return ConfigSnapshot(
            showBatteryDetail = showBatteryDetail,
            showDeviceTemp = showDeviceTemp,
            dualRows = dualRows,
            batteryAtRight = batteryAtRight,
            tempAtRight = tempAtRight,
            batteryAtLeft = batteryAtLeft,
            tempAtLeft = tempAtLeft,
            batteryInCharge = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_incharge"),
            batteryTempDecimal = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_temp_decimal"),
            batteryFixCurrentRatio = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_fixcurrentratio"),
            batteryPositive = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_positive"),
            batterySingleRow = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_singlerow"),
            batteryReverseOrder = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_reverseorder"),
            batteryHideUnit = mPrefs.getStringAsInt("system_statusbar_batterytempandcurrent_hideunit", 0),
            deviceTempSingleRow = mPrefs.getBoolean("system_statusbar_showdevicetemperature_singlerow"),
            deviceTempReverseOrder = mPrefs.getBoolean("system_statusbar_showdevicetemperature_reverseorder"),
            deviceTempHideUnit = mPrefs.getBoolean("system_statusbar_showdevicetemperature_hideunit"),
            batteryContentOpt = mPrefs.getStringAsInt("system_statusbar_batterytempandcurrent_content", 1),
            deviceTempContentOpt = mPrefs.getStringAsInt("system_statusbar_showdevicetemperature_content", 1)
        )
    }

    private fun onConfigMayHaveChanged() {
        val mPrefs = MainModule.mPrefs ?: return
        val newConfig = buildConfig(mPrefs)
        config = newConfig

        if (newConfig.customIconTypes.isEmpty()) {
            stopMonitoring()
            return
        }

        if (newConfig.showBatteryDetail && newConfig.batteryInCharge && chargeUtilsClass == null) {
            chargeUtilsClass = XposedHelpers.findClassIfExists(
                "com.miui.charge.ChargeUtils",
                lpClassLoader
            )
        }

        synchronized(monitorLock) {
            activeBgHandler?.removeMessages(MONITOR_MESSAGE)
            activeBgHandler?.sendEmptyMessage(MONITOR_MESSAGE)
        }
    }

    private fun hookIconSlots(lpparam: PackageReadyParam, cfg: ConfigSnapshot) {
        val StatusBarIconHolder = XposedHelpers.findClass(
            "com.android.systemui.statusbar.phone.StatusBarIconHolder",
            lpparam.classLoader
        )

        ModuleHelper.hookAllConstructors(
            "com.android.systemui.statusbar.policy.NetworkSpeedController",
            lpparam.classLoader,
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val iconController = XposedHelpers.getObjectField(
                        XposedHelpers.getObjectField(param.getThisObject(), "mStatusBarIconController"),
                        "mStatusBarIconList"
                    )
                    for (iconType in cfg.customIconTypes) {
                        val slot = SystemUI.getSlotNameByType(iconType)
                        val mStatusBarIconList = XposedHelpers.getObjectField(iconController, "mStatusBarIconList")
                        var iconHolder = XposedHelpers.callMethod(mStatusBarIconList, "getIconHolder", 0, slot)
                        if (iconHolder == null) {
                            iconHolder = XposedHelpers.newInstance(StatusBarIconHolder)
                            XposedHelpers.setObjectField(iconHolder, "mType", iconType)
                            XposedHelpers.callMethod(iconController, "setIcon", slot, iconHolder)
                        }
                    }
                }
            }
        )

        ModuleHelper.hookAllMethods(
            "com.android.systemui.statusbar.phone.StatusBarIconController\$IconManager",
            lpparam.classLoader,
            "addHolder",
            object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    if (param.getArgs().size != 4) return
                    val iconHolder = param.getArg(3)
                    val type = XposedHelpers.getIntField(iconHolder, "mType")
                    if (type != 91 && type != 92) return

                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val lp = XposedHelpers.callMethod(param.getThisObject(), "onCreateLayoutParams") as LinearLayout.LayoutParams
                    val iconView = SystemUI.createStatusbarTextIcon(mContext, lp, type, true)
                    val i = param.getArg(0) as Int
                    val mGroup = XposedHelpers.getObjectField(param.getThisObject(), "mGroup") as ViewGroup
                    mGroup.addView(iconView, i)
                    SystemUI.registerStatusbarTextIcon(iconView)
                    param.returnAndSkip(iconView)
                }
            }
        )
    }

    private fun hookNetworkSpeedView(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(
            "com.android.systemui.statusbar.views.NetworkSpeedView",
            lpparam.classLoader,
            "getSlot",
            object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    val nsView = param.getThisObject() as View
                    val tagData = nsView.getTag(SystemUI.textIconTagId)
                    if (tagData != null) {
                        param.returnAndSkip(SystemUI.getSlotNameByType(tagData as Int))
                    }
                }
            }
        )
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun hookMonitor(lpparam: PackageReadyParam, cfg: ConfigSnapshot) {
        ModuleHelper.hookAllConstructors(
            "com.android.systemui.statusbar.policy.NetworkSpeedController",
            lpparam.classLoader,
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mContext = param.getArgs()[0] as Context
                    val looper = param.getArgs()[1] as Looper

                    synchronized(monitorLock) {
                        activeBgHandler?.removeMessages(MONITOR_MESSAGE)
                        activeBgHandler?.removeMessages(UPDATE_MESSAGE)
                        stopScreenReceiverLocked()

                        activeMainHandler = object : Handler(Looper.getMainLooper()) {
                            override fun handleMessage(msg: Message) = ModuleHelper.guarded {
                                if (msg.what == UPDATE_MESSAGE) {
                                    val update = msg.obj as? IconUpdate ?: return@guarded
                                    SystemUI.updateStatusbarTextIcons(update.type, update.show, update.text)
                                }
                            }
                        }

                        activeBgHandler = object : Handler(looper) {
                            override fun handleMessage(msg: Message) = ModuleHelper.guarded {
                                if (msg.what == MONITOR_MESSAGE) {
                                    doMonitorTick(mContext, cfg)
                                }
                            }
                        }

                        activeContext = mContext
                        consecutiveFailCount = 0
                        screenOn = true

                        startScreenReceiverLocked(mContext)
                    }

                    activeBgHandler?.sendEmptyMessage(MONITOR_MESSAGE)
                }
            }
        )
    }

    private fun startScreenReceiverLocked(context: Context) {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        activeBgHandler?.removeMessages(MONITOR_MESSAGE)
                        activeBgHandler?.sendEmptyMessage(MONITOR_MESSAGE)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        activeBgHandler?.removeMessages(MONITOR_MESSAGE)
                        activeBgHandler?.removeMessages(UPDATE_MESSAGE)
                    }
                }
            }
        }.also { receiver ->
            context.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                Context.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun stopScreenReceiverLocked() {
        val receiver = screenReceiver ?: return
        screenReceiver = null
        try {
            activeContext?.unregisterReceiver(receiver)
        } catch (_: Throwable) {
        }
    }

    private fun stopMonitoring() {
        synchronized(monitorLock) {
            activeBgHandler?.removeMessages(MONITOR_MESSAGE)
            activeBgHandler?.removeMessages(UPDATE_MESSAGE)
            stopScreenReceiverLocked()
            activeMainHandler = null
            activeBgHandler = null
            activeContext = null
            consecutiveFailCount = 0
        }
    }

    /**
     * One monitor pass on the NetworkSpeedController background thread.
     *
     * The next tick is scheduled from `finally`: a sysfs read that returns an unexpected shape
     * must degrade to "no reading this round", never to a ticker that stops for the rest of the
     * process lifetime.
     */
    private fun doMonitorTick(mContext: Context, cfg: ConfigSnapshot) {
        if (!screenOn) return

        val powerMgr = mContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerMgr != null && !powerMgr.isInteractive) {
            screenOn = false
            return
        }

        try {
            publishReadings(cfg, readDeviceData(mContext, cfg))
        } finally {
            scheduleNextTick()
        }
    }

    private fun publishReadings(cfg: ConfigSnapshot, data: DeviceData?) {
        if (data != null) {
            if (cfg.showBatteryDetail &&
                (data.batteryShow != batteryState.show || data.batteryText != batteryState.text)
            ) {
                batteryState.show = data.batteryShow
                batteryState.text = data.batteryText
                activeMainHandler?.let { handler ->
                    handler.sendMessage(handler.obtainMessage(UPDATE_MESSAGE, IconUpdate(91, data.batteryShow, data.batteryText)))
                }
            }
            if (cfg.showDeviceTemp &&
                (data.tempShow != tempState.show || data.tempText != tempState.text)
            ) {
                tempState.show = data.tempShow
                tempState.text = data.tempText
                activeMainHandler?.let { handler ->
                    handler.sendMessage(handler.obtainMessage(UPDATE_MESSAGE, IconUpdate(92, data.tempShow, data.tempText)))
                }
            }
        }
    }

    private fun scheduleNextTick() {
        synchronized(monitorLock) {
            if (!screenOn) return
            val delay = calculateDelay()
            activeBgHandler?.sendEmptyMessageDelayed(MONITOR_MESSAGE, delay)
        }
    }

    private fun calculateDelay(): Long {
        if (consecutiveFailCount <= 0) return BASE_MONITOR_DELAY_MS
        val multiplier = 1L shl consecutiveFailCount.coerceAtMost(5)
        return (BASE_MONITOR_DELAY_MS * multiplier).coerceAtMost(MAX_MONITOR_DELAY_MS)
    }

    private fun readDeviceData(mContext: Context, cfg: ConfigSnapshot): DeviceData? {
        val shouldShowBattery = if (cfg.showBatteryDetail) shouldShowBatteryInfo(cfg) else false
        val shouldShowTemp = cfg.showDeviceTemp

        if (!shouldShowBattery && !shouldShowTemp) return DeviceData(false, "", false, "")

        val props: Properties? = if (shouldShowBattery || shouldShowTemp) readBatteryProps() else null
        val cpuProps: String? = if (shouldShowTemp) readCpuTemp() else null

        val batteryFailed = shouldShowBattery && props == null
        val tempFailed = shouldShowTemp && cpuProps == null
        val anyFailed = batteryFailed || tempFailed

        if (anyFailed) {
            consecutiveFailCount++
            if (props == null && cpuProps == null) return null
        } else {
            consecutiveFailCount = 0
        }

        val batteryText = if (shouldShowBattery && props != null) buildBatteryInfo(cfg, props) else ""
        val deviceText = if (shouldShowTemp && props != null && cpuProps != null) buildDeviceInfo(cfg, props, cpuProps) else ""

        return DeviceData(
            batteryShow = shouldShowBattery,
            batteryText = batteryText,
            tempShow = shouldShowTemp,
            tempText = deviceText
        )
    }

    private fun shouldShowBatteryInfo(cfg: ConfigSnapshot): Boolean {
        if (!cfg.batteryInCharge) return true
        val chargeUtils = chargeUtilsClass ?: return true
        val batteryStatus = ModuleHelper.getStaticObjectFieldSilently(chargeUtils, "sBatteryStatus")
        if (ModuleHelper.NOT_EXIST_SYMBOL == batteryStatus) {
            chargeUtilsClass = null
            return true
        }
        return try {
            XposedHelpers.callMethod(batteryStatus, "isCharging") as Boolean
        } catch (_: Throwable) {
            true
        }
    }

    private fun readBatteryProps(): Properties? {
        return try {
            FileInputStream("/sys/class/power_supply/battery/uevent").use { fis ->
                Properties().apply { load(fis) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readCpuTemp(): String? {
        val thermalId = ModuleHelper.getCPUThermalId()
        if (thermalId == -1) return null
        return try {
            RandomAccessFile("/sys/devices/virtual/thermal/thermal_zone$thermalId/temp", "r").use { it.readLine() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Parses a sysfs value, falling back to [fallback].
     *
     * `/sys/class/power_supply/battery/uevent` is a vendor surface: keys go missing and values
     * appear with signs, whitespace or units depending on the kernel. Parsing runs on the monitor
     * thread every two seconds, so a malformed line must produce a wrong-but-harmless reading
     * rather than an exception.
     */
    private fun parseSysfsInt(raw: String?, fallback: Int = 0): Int {
        if (raw.isNullOrEmpty()) return fallback
        return raw.trim().toIntOrNull() ?: fallback
    }

    private fun buildBatteryInfo(cfg: ConfigSnapshot, props: Properties): String {
        val opt = cfg.batteryContentOpt
        var simpleTempVal = ""
        if (opt == 1 || opt == 4) {
            val tempVal = parseSysfsInt(props.getProperty("POWER_SUPPLY_TEMP"))
            simpleTempVal = if (cfg.batteryTempDecimal) {
                (tempVal / 10f).toString()
            } else {
                if (tempVal % 10 == 0) (tempVal / 10).toString() else (tempVal / 10f).toString()
            }
        }

        val currentRatio = if (cfg.batteryFixCurrentRatio) 1f else 1000f
        val curReadVal = parseSysfsInt(props.getProperty("POWER_SUPPLY_CURRENT_NOW"))
        var rawCurr = -1 * Math.round(curReadVal / currentRatio)

        var currVal = ""
        var currUnit = "mA"
        if (opt == 1 || opt == 3 || opt == 5) {
            if (cfg.batteryPositive) rawCurr = Math.abs(rawCurr)
            if (Math.abs(rawCurr) > 999) {
                currUnit = "A"
                currVal = String.format(Locale.ROOT, "%.2f", rawCurr / 1000f)
            } else {
                currVal = rawCurr.toString()
            }
        }

        val hideUnit = cfg.batteryHideUnit
        val tempUnit = if (hideUnit == 1 || hideUnit == 2) "" else "℃"
        val powerUnit = if (hideUnit == 1 || hideUnit == 3) "" else "W"
        val finalCurrUnit = if (hideUnit == 1 || hideUnit == 3) "" else currUnit

        var simpleWatt = ""
        if (opt == 2 || opt == 4 || opt == 5) {
            val voltVal = parseSysfsInt(props.getProperty("POWER_SUPPLY_VOLTAGE_NOW")) / 1000f / 1000f
            simpleWatt = String.format(Locale.ROOT, "%.2f", Math.abs(voltVal * rawCurr) / 1000)
        }

        val splitChar = if (cfg.batterySingleRow) " " else "\n"
        return when (opt) {
            1 -> if (cfg.batteryReverseOrder) {
                currVal + finalCurrUnit + splitChar + simpleTempVal + tempUnit
            } else {
                simpleTempVal + tempUnit + splitChar + currVal + finalCurrUnit
            }
            4 -> if (cfg.batteryReverseOrder) {
                simpleWatt + powerUnit + splitChar + simpleTempVal + tempUnit
            } else {
                simpleTempVal + tempUnit + splitChar + simpleWatt + powerUnit
            }
            2 -> simpleWatt + powerUnit
            5 -> if (cfg.batteryReverseOrder) {
                simpleWatt + powerUnit + splitChar + currVal + finalCurrUnit
            } else {
                currVal + finalCurrUnit + splitChar + simpleWatt + powerUnit
            }
            else -> currVal + finalCurrUnit
        }
    }

    private fun buildDeviceInfo(cfg: ConfigSnapshot, props: Properties, cpuProps: String): String {
        val batteryTempVal = parseSysfsInt(props.getProperty("POWER_SUPPLY_TEMP"))
        val cpuTempVal = parseSysfsInt(cpuProps)
        val simpleBatteryTemp = String.format(Locale.ROOT, "%.1f", batteryTempVal / 10f)
        val simpleCpuTemp = String.format(Locale.ROOT, "%.1f", cpuTempVal / 1000f)
        val opt = cfg.deviceTempContentOpt
        val tempUnit = if (cfg.deviceTempHideUnit) "" else "℃"
        val splitChar = if (cfg.deviceTempSingleRow) " " else "\n"
        return when (opt) {
            1 -> if (cfg.deviceTempReverseOrder) {
                simpleCpuTemp + tempUnit + splitChar + simpleBatteryTemp + tempUnit
            } else {
                simpleBatteryTemp + tempUnit + splitChar + simpleCpuTemp + tempUnit
            }
            2 -> simpleBatteryTemp + tempUnit
            else -> simpleCpuTemp + tempUnit
        }
    }
}
