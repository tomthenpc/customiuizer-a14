package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemAudioHooks
import tv.withaibuild.customiuizer.mods.SystemClockHooks
import tv.withaibuild.customiuizer.mods.SystemColorizeNotificationHooks
import tv.withaibuild.customiuizer.mods.SystemDisplayHooks
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks
import tv.withaibuild.customiuizer.mods.SystemStatusBarIconHooks
import tv.withaibuild.customiuizer.mods.SystemUI
import tv.withaibuild.customiuizer.mods.SystemUIControlCenterHooks
import tv.withaibuild.customiuizer.mods.SystemUILockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemUINotificationHooks
import tv.withaibuild.customiuizer.mods.SystemUIScreenshotHooks
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureId
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.LateInstallPolicy
import tv.withaibuild.customiuizer.utils.PrefMap
import tv.withaibuild.customiuizer.utils.RestartRequirement

/**
 * Base class for all SystemUI features registered by [SystemUiInstaller].
 */
internal abstract class BaseSystemUiFeature(
    protected val lpparam: PackageReadyParam,
    protected val mPrefs: PrefMap,
    override val id: FeatureId,
    override val name: String,
    override val preferenceKey: String?
) : FeatureDefinition {

    override val target = FeatureTarget.SYSTEM_UI
    override val phase = InstallPhase.PACKAGE_READY
    override val lateInstallPolicy = LateInstallPolicy.NONE
    override val restartRequirement = RestartRequirement.NONE

    protected abstract fun isEnabledCondition(prefs: PrefMap): Boolean
    protected abstract fun installHook()

    final override fun isEnabled(prefs: PrefMap) = isEnabledCondition(prefs)

    final override fun install(): FeatureInstallResult = try {
        installHook()
        FeatureInstallResult.Installed
    } catch (t: Throwable) {
        FeatureInstallResult.FailedTransient(t.javaClass.name)
    }
}

internal class ForegroundMonitorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ForegroundMonitorFeatureId,
    "Foreground Monitor",
    "various_showcallui"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getStringAsInt("various_showcallui", 0) > 0 || prefs.getBoolean("controls_volumecursor")
    override fun installHook() = GlobalActions.setupForegroundMonitor(lpparam)
}

internal class TempHideOverlaySystemUiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    TempHideOverlaySystemUiFeatureId,
    "Temp Hide Overlay System UI",
    "system_screenshot_overlay"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_screenshot_overlay")
    override fun installHook() = SystemUIScreenshotHooks.TempHideOverlaySystemUIHook(lpparam)
}

internal class AddCustomTileFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AddCustomTileFeatureId,
    "Add Custom Tile",
    "system_fivegtile"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_fivegtile") || prefs.getBoolean("system_cc_fpstile") || prefs.getBoolean("system_cc_floatingtimetile")
    override fun installHook() = SystemUIControlCenterHooks.AddCustomTileHook(lpparam)
}

internal class HideStatusBarWhenCaptureFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideStatusBarWhenCaptureFeatureId,
    "Hide Status Bar When Capture",
    "system_hidestatusbar_whenscreenshot"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_hidestatusbar_whenscreenshot")
    override fun installHook() = SystemUIScreenshotHooks.HideStatusBarWhenCaptureHook(lpparam)
}

internal class NetworkIndicatorWifiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NetworkIndicatorWifiFeatureId,
    "Network Indicator WiFi",
    "system_networkindicator_wifi"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_networkindicator_wifi")
    override fun installHook() = ModsSystem.NetworkIndicatorWifi(lpparam)
}

internal class DrawerBlurRatioFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DrawerBlurRatioFeatureId,
    "Drawer Blur Ratio",
    "system_drawer_blur"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_drawer_blur", 100) < 100
    override fun installHook() = SystemDisplayHooks.DrawerBlurRatioHook(lpparam)
}

internal class ChargeAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ChargeAnimationFeatureId,
    "Charge Animation",
    "system_chargeanimtime"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_chargeanimtime", 20) < 20
    override fun installHook() = SystemDisplayHooks.ChargeAnimationHook(lpparam)
}

internal class BetterPopupsHideDelayFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsHideDelayFeatureId,
    "Better Popups Hide Delay",
    "system_betterpopups_delay"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getInt("system_betterpopups_delay", 0) > 0 && !prefs.getBoolean("system_betterpopups_nohide")
    override fun installHook() = SystemNotificationHooks.BetterPopupsHideDelayHook(lpparam)
}

internal class AssistGestureActionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AssistGestureActionFeatureId,
    "Assist Gesture Action",
    "controls_fsg_assist_left_action"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getInt("controls_fsg_assist_left_action", 1) > 1 || prefs.getInt("controls_fsg_assist_right_action", 1) > 1
    override fun installHook() = Controls.AssistGestureActionHook(lpparam)
}

internal class NavBarButtonsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NavBarButtonsFeatureId,
    "Nav Bar Buttons",
    "controls_navbarleft_action"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getInt("controls_navbarleft_action", 1) > 1 ||
        prefs.getInt("controls_navbarleftlong_action", 1) > 1 ||
        prefs.getInt("controls_navbarright_action", 1) > 1 ||
        prefs.getInt("controls_navbarrightlong_action", 1) > 1
    override fun installHook() = Controls.NavBarButtonsHook(lpparam)
}

internal class ScramblePinFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ScramblePinFeatureId,
    "Scramble PIN",
    "system_scramblepin"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_scramblepin")
    override fun installHook() = SystemLockScreenHooks.ScramblePINHook(lpparam)
}

internal class DoubleTapToSleepFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DoubleTapToSleepFeatureId,
    "Double Tap To Sleep",
    "system_dttosleep"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_dttosleep")
    override fun installHook() = SystemLockScreenHooks.DoubleTapToSleepHook(lpparam)
}

internal class StatusBarClockTweakFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarClockTweakFeatureId,
    "Status Bar Clock Tweak",
    "system_statusbar_clocktweak"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_statusbar_clocktweak") ||
        prefs.getBoolean("system_cc_clocktweak") ||
        prefs.getBoolean("system_cc_hidedate") ||
        prefs.getBoolean("system_drawer_hidedate") ||
        prefs.getBoolean("system_statusbaricons_clock") ||
        prefs.getString("system_cc_dateformat", "").isNotEmpty() ||
        prefs.getString("system_drawer_dateformat", "").isNotEmpty()
    override fun installHook() = SystemClockHooks.StatusBarClockTweakHook(lpparam)
}

internal class CcClockTweakFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    CcClockTweakFeatureId,
    "CC Clock Tweak",
    "system_cc_clocktweak"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_cc_clocktweak") || prefs.getBoolean("system_qs_force_systemfonts")
    override fun installHook() = SystemClockHooks.CCClockTweakHook(lpparam)
}

internal class DisableFakeClockAnimFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableFakeClockAnimFeatureId,
    "Disable Fake Clock Anim",
    "system_qs_disable_fakeclock_anim"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_qs_disable_fakeclock_anim")
    override fun installHook() = SystemUIStatusBarHooks.DisableFakeClockAnimHook(lpparam)
}

internal class CcClockCenterAlignFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    CcClockCenterAlignFeatureId,
    "CC Clock Center Align",
    "system_cc_clock_centeralign"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_cc_clock_centeralign") ||
        (!prefs.getBoolean("system_drawer_hidedate") && prefs.getBoolean("system_drawer_date_centeralign"))
    override fun installHook() = SystemClockHooks.CCClockCenterAlignHook(lpparam)
}

internal class NoScreenLockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoScreenLockFeatureId,
    "No Screen Lock",
    "system_noscreenlock_act"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_noscreenlock_act")
    override fun installHook() = SystemLockScreenHooks.NoScreenLockHook(lpparam)
}

internal class LockScreenAlbumArtFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenAlbumArtFeatureId,
    "Lock Screen Album Art",
    "system_albumartonlock"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_albumartonlock")
    override fun installHook() = SystemUILockScreenHooks.LockScreenAlbumArtHook(lpparam)
}

internal class ExpandHeadsUpFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ExpandHeadsUpFeatureId,
    "Expand Heads Up",
    "system_expandheadups"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_expandheadups", 1) > 1
    override fun installHook() = SystemNotificationHooks.ExpandHeadsUpHook(lpparam)
}

internal class BetterPopupsNoHideFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsNoHideFeatureId,
    "Better Popups No Hide",
    "system_betterpopups_nohide"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_betterpopups_nohide")
    override fun installHook() = SystemNotificationHooks.BetterPopupsNoHideHook(lpparam)
}

internal class BetterPopupsCenteredFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsCenteredFeatureId,
    "Better Popups Centered",
    "system_betterpopups_center"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_betterpopups_center")
    override fun installHook() = SystemNotificationHooks.BetterPopupsCenteredHook(lpparam)
}

internal class ShowNotificationsAfterUnlockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ShowNotificationsAfterUnlockFeatureId,
    "Show Notifications After Unlock",
    "system_notifafterunlock"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notifafterunlock")
    override fun installHook() = SystemLockScreenHooks.ShowNotificationsAfterUnlockHook(lpparam)
}

internal class NotificationRowMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NotificationRowMenuFeatureId,
    "Notification Row Menu",
    "system_notifrowmenu"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notifrowmenu")
    override fun installHook() = SystemNotificationHooks.NotificationRowMenuHook(lpparam)
}

internal class HideDismissViewFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideDismissViewFeatureId,
    "Hide Dismiss View",
    "system_removedismiss"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_removedismiss")
    override fun installHook() = SystemUINotificationHooks.HideDismissViewHook(lpparam)
}

internal class HideNotificationAccessIconFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNotificationAccessIconFeatureId,
    "Hide Notification Access Icon",
    "system_drawer_removeshortcut"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_drawer_removeshortcut")
    override fun installHook() = SystemUINotificationHooks.HideNoficationAccessIconHook(lpparam)
}

internal class HideNoNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNoNotificationsFeatureId,
    "Hide No Notifications",
    "system_drawer_remove_emptynotify"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_drawer_remove_emptynotify")
    override fun installHook() = SystemUINotificationHooks.HideNoNotificationsHook(lpparam)
}

internal class HideNavBarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNavBarFeatureId,
    "Hide Nav Bar",
    "controls_nonavbar"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_nonavbar")
    override fun installHook() = Controls.HideNavBarHook(lpparam)
}

internal class HideNavBarBeforeScreenshotFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideNavBarBeforeScreenshotFeatureId,
    "Hide Nav Bar Before Screenshot",
    "controls_hidenavbar_whenscreenshot"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        !prefs.getBoolean("controls_nonavbar") && prefs.getBoolean("controls_hidenavbar_whenscreenshot")
    override fun installHook() = SystemUIScreenshotHooks.HideNavBarBeforeScreenshotHook(lpparam)
}

internal class AudioVisualizerFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AudioVisualizerFeatureId,
    "Audio Visualizer",
    "system_visualizer"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_visualizer")
    override fun installHook() = SystemAudioHooks.AudioVisualizerHook(lpparam)
}

internal class ControlCenterPluginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ControlCenterPluginFeatureId,
    "Control Center Plugin",
    null
) {
    override fun isEnabledCondition(prefs: PrefMap) = SystemUIControlCenterHooks.hasControlCenterModifications()
    override fun installHook() = SystemUIControlCenterHooks.ControlCenterPluginHook(lpparam)
}

internal class BatteryIndicatorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BatteryIndicatorFeatureId,
    "Battery Indicator",
    "system_batteryindicator"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_batteryindicator")
    override fun installHook() = SystemUIStatusBarHooks.BatteryIndicatorHook(lpparam)
}

internal class DisableAnyNotificationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableAnyNotificationFeatureId,
    "Disable Any Notification",
    "system_disableanynotif"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_disableanynotif")
    override fun installHook() = SystemNotificationHooks.DisableAnyNotificationHook(lpparam)
}

internal class LockScreenShortcutFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenShortcutFeatureId,
    "Lock Screen Shortcut",
    "system_lockscreenshortcuts"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_lockscreenshortcuts")
    override fun installHook() = SystemUILockScreenHooks.LockScreenShortcutHook(lpparam)
}

internal class MobileNetworkTypeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MobileNetworkTypeFeatureId,
    "Mobile Network Type",
    "system_4gtolte"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_4gtolte") ||
        (prefs.getBoolean("system_statusbar_mobiletype_single") &&
         prefs.getString("system_statusbar_mobile_showname", "").isNotEmpty())
    override fun installHook() = SystemUIStatusBarHooks.MobileNetworkTypeHook(lpparam)
}

internal class StatusBarIconsPositionAdjustFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarIconsPositionAdjustFeatureId,
    "Status Bar Icons Position Adjust",
    "system_statusbar_alarm_atright"
) {
    override fun isEnabledCondition(prefs: PrefMap) = computeStatusBarIconsAdjust(prefs).first
    override fun installHook() =
        SystemUIStatusBarHooks.StatusBarIconsPositionAdjustHook(lpparam, computeStatusBarIconsAdjust(mPrefs).second)

    private fun computeStatusBarIconsAdjust(prefs: PrefMap): Pair<Boolean, Boolean> {
        val dualRows = prefs.getBoolean("system_statusbar_dualrows")
        val netspeedAtRow2 = dualRows && prefs.getBoolean("system_statusbar_netspeed_atsecondrow")
        val showBatteryDetail = prefs.getBoolean("system_statusbar_batterytempandcurrent")
        val showDeviceTemp = prefs.getBoolean("system_statusbar_showdevicetemperature")
        val batteryAtRight = showBatteryDetail && !dualRows && prefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtRight = showDeviceTemp && !dualRows && prefs.getBoolean("system_statusbar_showdevicetemperature_atright")
        val batteryAtLeft = showBatteryDetail && !prefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtLeft = showDeviceTemp && !prefs.getBoolean("system_statusbar_showdevicetemperature_atright")

        val alwaysShowAtRight = prefs.getBoolean("system_statusbar_alarm_atright") ||
            prefs.getBoolean("system_statusbar_nfc_atright") ||
            prefs.getBoolean("system_statusbar_btbattery_atright") ||
            prefs.getBoolean("system_statusbar_headset_atright") ||
            prefs.getBoolean("system_statusbar_vpn_atright") ||
            batteryAtRight || tempAtRight
        val moveLeft = prefs.getBoolean("system_statusbar_alarm_atleft") ||
            prefs.getBoolean("system_statusbar_sound_atleft") ||
            prefs.getBoolean("system_statusbar_netspeed_atleft") ||
            prefs.getBoolean("system_statusbar_dnd_atleft") ||
            prefs.getBoolean("system_statusbar_gps_atleft") ||
            prefs.getBoolean("system_statusbaricons_wifi_mobile_atleft") ||
            batteryAtLeft || tempAtLeft
        val needsAdjust = alwaysShowAtRight || moveLeft || netspeedAtRow2 ||
            prefs.getBoolean("system_statusbaricons_swap_wifi_mobile")
        return needsAdjust to moveLeft
    }
}

internal class MonitorDeviceInfoFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MonitorDeviceInfoFeatureId,
    "Monitor Device Info",
    "system_statusbar_batterytempandcurrent"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_statusbar_batterytempandcurrent") ||
        prefs.getBoolean("system_statusbar_showdevicetemperature")
    override fun installHook() = SystemUIStatusBarHooks.MonitorDeviceInfoHook(lpparam, mPrefs)
}

internal class StatusBarClockPositionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarClockPositionFeatureId,
    "Status Bar Clock Position",
    "system_statusbar_clock_position"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getStringAsInt("system_statusbar_clock_position", 1) > 1 &&
        !prefs.getBoolean("system_statusbar_dualrows")
    override fun installHook() = SystemUIStatusBarHooks.StatusBarClockPositionHook(lpparam)
}

internal class StatusBarStyleBatteryIconFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarStyleBatteryIconFeatureId,
    "Status Bar Style Battery Icon",
    "system_statusbar_batterystyle"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbar_batterystyle")
    override fun installHook() = SystemUIStatusBarHooks.StatusBarStyleBatteryIconHook(lpparam)
}

internal class LockScreenTopMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenTopMarginFeatureId,
    "Lock Screen Top Margin",
    "system_statusbar_topmargin"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_statusbar_topmargin") &&
        prefs.getBoolean("system_statusbar_topmargin_unset_lockscreen")
    override fun installHook() = SystemUILockScreenHooks.LockScreenTopMarginHook(lpparam)
}

internal class HorizMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HorizMarginFeatureId,
    "Horiz Margin",
    "system_statusbar_horizmargin"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbar_horizmargin")
    override fun installHook() = SystemUIStatusBarHooks.HorizMarginHook(lpparam)
}

internal class BrightnessPctFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BrightnessPctFeatureId,
    "Brightness Pct",
    "system_showpct"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_showpct")
    override fun installHook() = SystemUIControlCenterHooks.BrightnessPctHook(lpparam)
}

internal class HideLockScreenStatusBarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockScreenStatusBarFeatureId,
    "Hide Lock Screen Status Bar",
    "system_hidelsstatusbar"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_hidelsstatusbar")
    override fun installHook() = SystemLockScreenHooks.HideLockScreenStatusBarHook(lpparam)
}

internal class HideLockScreenClockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockScreenClockFeatureId,
    "Hide Lock Screen Clock",
    "system_hidelsclock"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_hidelsclock")
    override fun installHook() = SystemLockScreenHooks.HideLockScreenClockHook(lpparam)
}

internal class ForceClockUseSystemFontsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ForceClockUseSystemFontsFeatureId,
    "Force Clock Use System Fonts",
    "system_ls_force_systemfonts"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_ls_force_systemfonts")
    override fun installHook() = SystemUIStatusBarHooks.ForceClockUseSystemFontsHook(lpparam)
}

internal class HideLockScreenHintFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockScreenHintFeatureId,
    "Hide Lock Screen Hint",
    "system_hidelshint"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_hidelshint")
    override fun installHook() = SystemLockScreenHooks.HideLockScreenHintHook(lpparam)
}

internal class AllowAllKeyguardFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AllowAllKeyguardFeatureId,
    "Allow All Keyguard",
    "system_allownotifonkeyguard"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_allownotifonkeyguard")
    override fun installHook() = SystemLockScreenHooks.AllowAllKeyguardHook(lpparam)
}

internal class AllowAllFloatFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AllowAllFloatFeatureId,
    "Allow All Float",
    "system_allownotiffloat"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_allownotiffloat")
    override fun installHook() = SystemWindowHooks.AllowAllFloatHook(lpparam)
}

internal class LockScreenAlarmFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LockScreenAlarmFeatureId,
    "Lock Screen Alarm",
    "system_lsalarm"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_lsalarm")
    override fun installHook() = SystemLockScreenHooks.LockScreenAlarmHook(lpparam)
}

internal class StatusBarGesturesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarGesturesFeatureId,
    "Status Bar Gestures",
    "system_statusbarcontrols"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbarcontrols")
    override fun installHook() = SystemUIControlCenterHooks.StatusBarGesturesHook(lpparam)
}

internal class NetSpeedIntervalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NetSpeedIntervalFeatureId,
    "Net Speed Interval",
    "system_netspeedinterval"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_netspeedinterval", 4) != 4
    override fun installHook() = SystemUIStatusBarHooks.NetSpeedIntervalHook(lpparam)
}

internal class DetailedNetSpeedFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DetailedNetSpeedFeatureId,
    "Detailed Net Speed",
    "system_detailednetspeed_style"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_detailednetspeed_style", 1) > 1
    override fun installHook() = SystemUIStatusBarHooks.DetailedNetSpeedHook(lpparam)
}

internal class NetSpeedStyleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NetSpeedStyleFeatureId,
    "Net Speed Style",
    "system_detailednetspeed_style"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getStringAsInt("system_detailednetspeed_style", 1) > 1 ||
        prefs.getBoolean("system_netspeed_boldfont") ||
        prefs.getBoolean("system_netspeed_use_clock_style") ||
        prefs.getInt("system_netspeed_fontsize", 13) > 13 ||
        prefs.getInt("system_netspeed_fixedcontent_width", 10) > 10 ||
        prefs.getInt("system_netspeed_leftmargin", 0) > 0 ||
        prefs.getInt("system_netspeed_rightmargin", 0) > 0 ||
        prefs.getInt("system_netspeed_verticaloffset", 8) != 8 ||
        prefs.getStringAsInt("system_detailednetspeed_align", 1) > 1
    override fun installHook() = SystemUIStatusBarHooks.NetSpeedStyleHook(lpparam)
}

internal class TapToUnlockFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    TapToUnlockFeatureId,
    "Tap To Unlock",
    "system_taptounlock"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_taptounlock")
    override fun installHook() = SystemLockScreenHooks.TapToUnlockHook(lpparam)
}

internal class NoSOSFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoSOSFeatureId,
    "No SOS",
    "system_nosos"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_nosos")
    override fun installHook() = SystemLockScreenHooks.NoSOSHook(lpparam)
}

internal class RemovePackageNotificationsLimitFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    RemovePackageNotificationsLimitFeatureId,
    "Remove Package Notifications Limit",
    "system_morenotif"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_morenotif")
    override fun installHook() = SystemUINotificationHooks.RemovePackageNotificationsLimitHook(lpparam)
}

internal class DisableFoldNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableFoldNotificationsFeatureId,
    "Disable Fold Notifications",
    "system_notif_disable_fold"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notif_disable_fold")
    override fun installHook() = SystemUINotificationHooks.DisableFoldNotificationsHook(lpparam)
}

internal class DisableStrongToastFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableStrongToastFeatureId,
    "Disable Strong Toast",
    "system_notif_disable_strong_toast"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notif_disable_strong_toast")
    override fun installHook() = SystemUI.DisableStrongToastHook(lpparam)
}

internal class ChargingInfoFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ChargingInfoFeatureId,
    "Charging Info",
    "system_charginginfo"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_charginginfo")
    override fun installHook() = SystemLockScreenHooks.ChargingInfoHook(lpparam)
}

internal class SecureQSTilesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    SecureQSTilesFeatureId,
    "Secure QS Tiles",
    "system_secureqs"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_secureqs")
    override fun installHook() = SystemUIControlCenterHooks.SecureQSTilesHook(lpparam)
}

internal class MuteVisibleNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MuteVisibleNotificationsFeatureId,
    "Mute Visible Notifications",
    "system_mutevisiblenotif"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_mutevisiblenotif")
    override fun installHook() = SystemNotificationHooks.MuteVisibleNotificationsHook(lpparam)
}

internal class HideIconsBattery1Feature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsBattery1FeatureId,
    "Hide Icons Battery1",
    "system_statusbaricons_battery1"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbaricons_battery1")
    override fun installHook() = SystemStatusBarIconHooks.HideIconsBattery1Hook(lpparam)
}

internal class HideIconsBattery2Feature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsBattery2FeatureId,
    "Hide Icons Battery2",
    "system_statusbaricons_battery3"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_statusbaricons_battery3") ||
        prefs.getBoolean("system_statusbaricons_battery4") ||
        prefs.getBoolean("system_statusbaricons_battery2")
    override fun installHook() = SystemStatusBarIconHooks.HideIconsBattery2Hook(lpparam)
}

internal class DisplayWifiStandardFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisplayWifiStandardFeatureId,
    "Display WiFi Standard",
    "system_statusbaricons_wifistandard"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_statusbaricons_wifistandard", 1) > 1
    override fun installHook() = SystemStatusBarIconHooks.DisplayWifiStandardHook(lpparam)
}

internal class HidePrivacyIndicatorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HidePrivacyIndicatorFeatureId,
    "Hide Privacy Indicator",
    "system_statusbaricons_privacy_prompt"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbaricons_privacy_prompt")
    override fun installHook() = SystemUIStatusBarHooks.HidePrivacyIndicatorHook(lpparam)
}

internal class HideIconsSignalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsSignalFeatureId,
    "Hide Icons Signal",
    "system_statusbaricons_signal"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_statusbaricons_signal") ||
        prefs.getBoolean("system_statusbaricons_sim1") ||
        prefs.getBoolean("system_statusbaricons_sim2") ||
        prefs.getBoolean("system_statusbaricons_sim_nodata") ||
        prefs.getBoolean("system_statusbaricons_roaming") ||
        prefs.getBoolean("system_statusbaricons_volte")
    override fun installHook() = SystemUIStatusBarHooks.HideIconsSignalHook(lpparam)
}

internal class HideIconsVoWiFiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsVoWiFiFeatureId,
    "Hide Icons VoWiFi",
    "system_statusbaricons_vowifi"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbaricons_vowifi")
    override fun installHook() = SystemUIStatusBarHooks.HideIconsVoWiFiHook(lpparam)
}

internal class HideIconsSelectiveAlarmFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsSelectiveAlarmFeatureId,
    "Hide Icons Selective Alarm",
    "system_statusbaricons_alarm"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        !prefs.getBoolean("system_statusbaricons_alarm") &&
        prefs.getInt("system_statusbaricons_alarmn", 0) > 0
    override fun installHook() = SystemStatusBarIconHooks.HideIconsSelectiveAlarmHook(lpparam)
}

internal class ReplaceShortcutAppFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ReplaceShortcutAppFeatureId,
    "Replace Shortcut App",
    "system_shortcut_app"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getString("system_shortcut_app", "").isNotEmpty() ||
        prefs.getString("system_calendar_app", "").isNotEmpty() ||
        prefs.getString("system_clock_app", "").isNotEmpty()
    override fun installHook() = SystemUILockScreenHooks.ReplaceShortcutAppHook(lpparam)
}

internal class QSHapticFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    QSHapticFeatureId,
    "QS Haptic",
    "system_qshaptics"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_qshaptics", 1) > 1
    override fun installHook() = SystemAudioHooks.QSHapticHook(lpparam)
}

internal class CollapseCCAfterClickFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    CollapseCCAfterClickFeatureId,
    "Collapse CC After Click",
    "system_cc_collapse_after_clicked"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_cc_collapse_after_clicked")
    override fun installHook() = SystemUIControlCenterHooks.CollapseCCAfterClickHook(lpparam)
}

internal class LongClickTileOpenInFreeFormFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    LongClickTileOpenInFreeFormFeatureId,
    "Long Click Tile Open In Free Form",
    "system_cc_freeform_when_longclick"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_cc_freeform_when_longclick")
    override fun installHook() = SystemUIControlCenterHooks.LongClickTileOpenInFreeFormHook(lpparam)
}

internal class SwitchCCAndNotificationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    SwitchCCAndNotificationFeatureId,
    "Switch CC And Notification",
    "system_cc_switch_qsandnotification"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_cc_switch_qsandnotification")
    override fun installHook() = SystemUIControlCenterHooks.SwitchCCAndNotificationHook(lpparam)
}

internal class ExpandNotificationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ExpandNotificationsFeatureId,
    "Expand Notifications",
    "system_expandnotifs"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_expandnotifs", 1) > 1
    override fun installHook() = SystemNotificationHooks.ExpandNotificationsHook(lpparam)
}

internal class HideMobileNetworkIndicatorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideMobileNetworkIndicatorFeatureId,
    "Hide Mobile Network Indicator",
    "system_mobiletypeicon"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getStringAsInt("system_mobiletypeicon", 1) > 1 ||
        prefs.getBoolean("system_networkindicator_mobile") ||
        prefs.getBoolean("system_statusbar_mobiletype_show_wificonnected")
    override fun installHook() = SystemUIStatusBarHooks.HideMobileNetworkIndicatorHook(lpparam)
}

internal class ExtendedPowerMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ExtendedPowerMenuFeatureId,
    "Extended Power Menu",
    "system_epm"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_epm")
    override fun installHook() = SystemUI.ExtendedPowerMenuHook(lpparam)
}

internal class HideIconsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsFeatureId,
    "Hide Icons",
    "system_statusbaricons_wifi"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_statusbaricons_wifi") ||
        prefs.getBoolean("system_statusbaricons_dualwifi") ||
        prefs.getBoolean("system_statusbaricons_alarm") ||
        prefs.getBoolean("system_statusbaricons_profile") ||
        prefs.getBoolean("system_statusbaricons_sound") ||
        prefs.getBoolean("system_statusbaricons_dnd") ||
        prefs.getBoolean("system_statusbaricons_secondspace") ||
        prefs.getBoolean("system_statusbaricons_headset") ||
        prefs.getBoolean("system_statusbaricons_nfc") ||
        prefs.getBoolean("system_statusbaricons_vpn") ||
        prefs.getBoolean("system_statusbaricons_airplane") ||
        prefs.getBoolean("system_statusbaricons_hotspot") ||
        prefs.getBoolean("system_statusbaricons_nosims") ||
        prefs.getBoolean("system_statusbaricons_gps") ||
        prefs.getBoolean("system_statusbaricons_btbattery") ||
        prefs.getBoolean("system_statusbaricons_ble_unlock") ||
        prefs.getBoolean("system_statusbaricons_bluetoothicn") ||
        prefs.getBoolean("system_statusbaricons_volte")
    override fun installHook() = SystemUIStatusBarHooks.HideIconsHook(lpparam)
}

internal class HideIconsFromSystemManagerFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideIconsFromSystemManagerFeatureId,
    "Hide Icons From System Manager",
    "system_statusbaricons_privacy"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        prefs.getBoolean("system_statusbaricons_privacy") ||
        prefs.getBoolean("system_statusbaricons_mute") ||
        prefs.getBoolean("system_statusbaricons_speaker") ||
        prefs.getBoolean("system_statusbaricons_record") ||
        prefs.getBoolean("system_statusbaricons_wireless_headset")
    override fun installHook() = SystemUIStatusBarHooks.HideIconsFromSystemManager(lpparam)
}

internal class BetterPopupsAllowFloatFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    BetterPopupsAllowFloatFeatureId,
    "Better Popups Allow Float",
    "system_betterpopups_allowfloat"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_betterpopups_allowfloat")
    override fun installHook() = SystemWindowHooks.BetterPopupsAllowFloatHook(lpparam)
}

internal class AutoDismissExpandedPopupsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    AutoDismissExpandedPopupsFeatureId,
    "Auto Dismiss Expanded Popups",
    "system_betterpopups_autoclose_expanded"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_betterpopups_autoclose_expanded")
    override fun installHook() = SystemNotificationHooks.AutoDismissExpandedPopupsHook(lpparam)
}

internal class DisableHeadsUpWhenMuteFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableHeadsUpWhenMuteFeatureId,
    "Disable Heads Up When Mute",
    "system_betterpopups_disablewhenmute"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_betterpopups_disablewhenmute")
    override fun installHook() = SystemUINotificationHooks.DisableHeadsUpWhenMuteHook(lpparam)
}

internal class MinimalNotificationViewFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MinimalNotificationViewFeatureId,
    "Minimal Notification View",
    "system_minimalnotifview"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_minimalnotifview")
    override fun installHook() = SystemNotificationHooks.MinimalNotificationViewHook(lpparam)
}

internal class NotificationChannelSettingsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NotificationChannelSettingsFeatureId,
    "Notification Channel Settings",
    "system_notifchannelsettings"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notifchannelsettings")
    override fun installHook() = SystemNotificationHooks.NotificationChannelSettingsHook(lpparam)
}

internal class MaxNotificationIconsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MaxNotificationIconsFeatureId,
    "Max Notification Icons",
    "system_maxsbicons"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_maxsbicons", 0) != 0
    override fun installHook() = SystemNotificationHooks.MaxNotificationIconsHook(lpparam)
}

internal class MobileTypeSingleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    MobileTypeSingleFeatureId,
    "Mobile Type Single",
    "system_statusbar_mobiletype_single"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbar_mobiletype_single")
    override fun installHook() = SystemUIStatusBarHooks.MobileTypeSingleHook(lpparam)
}

internal class StatusBarDigitalSignalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    StatusBarDigitalSignalFeatureId,
    "Status Bar Digital Signal",
    "system_statusbar_mobile_digital_signal"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbar_mobile_digital_signal")
    override fun installHook() = SystemUIStatusBarHooks.StatusBarDigitalSignalHook(lpparam)
}

internal class DualRowSignalFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DualRowSignalFeatureId,
    "Dual Row Signal",
    "system_statusbar_dualsimin2rows"
) {
    override fun isEnabledCondition(prefs: PrefMap) =
        !prefs.getBoolean("system_statusbar_mobile_digital_signal") &&
        prefs.getBoolean("system_statusbar_dualsimin2rows")
    override fun installHook() = SystemUIStatusBarHooks.DualRowSignalHook(lpparam)
}

internal class DualRowsStatusbarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DualRowsStatusbarFeatureId,
    "Dual Rows Statusbar",
    "system_statusbar_dualrows"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbar_dualrows")
    override fun installHook() = SystemUIStatusBarHooks.DualRowsStatusbarHook(lpparam)
}

internal class ColorizeNotificationCardFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    ColorizeNotificationCardFeatureId,
    "Colorize Notification Card",
    "system_colorizenotifs"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_colorizenotifs", 1) > 1
    override fun installHook() = SystemColorizeNotificationHooks.ColorizeNotificationCardHook(lpparam)
}

internal class OpenNotifyInFloatingWindowFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    OpenNotifyInFloatingWindowFeatureId,
    "Open Notify In Floating Window",
    "system_notify_openinfw"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notify_openinfw")
    override fun installHook() = SystemUINotificationHooks.OpenNotifyInFloatingWindowHook(lpparam)
}

internal class DisableSideBarSuggestionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableSideBarSuggestionFeatureId,
    "Disable Side Bar Suggestion",
    "system_fw_noblacklist"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_fw_noblacklist")
    override fun installHook() = SystemWindowHooks.DisableSideBarSuggestionHook(lpparam)
}

internal class HideSafeVolumeDlgFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideSafeVolumeDlgFeatureId,
    "Hide Safe Volume Dlg",
    "system_nosafevolume"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_nosafevolume")
    override fun installHook() = SystemUIControlCenterHooks.HideSafeVolumeDlgHook(lpparam)
}

internal class HideLockscreenZenModeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    HideLockscreenZenModeFeatureId,
    "Hide Lockscreen Zen Mode",
    "system_lockscreen_hidezenmode"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_lockscreen_hidezenmode")
    override fun installHook() = SystemUILockScreenHooks.HideLockscreenZenModeHook(lpparam)
}

internal class DisableKeyguardEditorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    DisableKeyguardEditorFeatureId,
    "Disable Keyguard Editor",
    "system_lockscreen_disable_edit"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_lockscreen_disable_edit")
    override fun installHook() = SystemUILockScreenHooks.DisableKeyguardEditorHook(lpparam)
}

internal class NoPasswordFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoPasswordFeatureId,
    "No Password",
    "system_nopassword"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_nopassword")
    override fun installHook() = SystemLockScreenHooks.NoPasswordHook(lpparam)
}

internal class NotificationImportanceFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NotificationImportanceFeatureId,
    "Notification Importance",
    "system_notifimportance"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notifimportance")
    override fun installHook() = SystemUINotificationHooks.NotificationImportanceHook(lpparam)
}

internal class NoLightUpOnChargeSystemUiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseSystemUiFeature(
    lpparam,
    mPrefs,
    NoLightUpOnChargeSystemUiFeatureId,
    "No Light Up On Charge System UI",
    "system_nolightuponcharges"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_nolightuponcharges", 1) > 1
    override fun installHook() = SystemUI.NoLightUpOnChargeHook(lpparam)
}

/**
 * All preference-guarded features that belong in the SystemUI process.
 */
object SystemUiFeatures {
    @JvmStatic
    fun all(
        lpparam: PackageReadyParam,
        mPrefs: PrefMap
    ): List<FeatureDefinition> = listOf(
        ForegroundMonitorFeature(lpparam, mPrefs),
        TempHideOverlaySystemUiFeature(lpparam, mPrefs),
        AddCustomTileFeature(lpparam, mPrefs),
        HideStatusBarWhenCaptureFeature(lpparam, mPrefs),
        NetworkIndicatorWifiFeature(lpparam, mPrefs),
        DrawerBlurRatioFeature(lpparam, mPrefs),
        ChargeAnimationFeature(lpparam, mPrefs),
        BetterPopupsHideDelayFeature(lpparam, mPrefs),
        AssistGestureActionFeature(lpparam, mPrefs),
        NavBarButtonsFeature(lpparam, mPrefs),
        ScramblePinFeature(lpparam, mPrefs),
        DoubleTapToSleepFeature(lpparam, mPrefs),
        StatusBarClockTweakFeature(lpparam, mPrefs),
        CcClockTweakFeature(lpparam, mPrefs),
        DisableFakeClockAnimFeature(lpparam, mPrefs),
        CcClockCenterAlignFeature(lpparam, mPrefs),
        NoScreenLockFeature(lpparam, mPrefs),
        LockScreenAlbumArtFeature(lpparam, mPrefs),
        ExpandHeadsUpFeature(lpparam, mPrefs),
        BetterPopupsNoHideFeature(lpparam, mPrefs),
        BetterPopupsCenteredFeature(lpparam, mPrefs),
        ShowNotificationsAfterUnlockFeature(lpparam, mPrefs),
        NotificationRowMenuFeature(lpparam, mPrefs),
        HideDismissViewFeature(lpparam, mPrefs),
        HideNotificationAccessIconFeature(lpparam, mPrefs),
        HideNoNotificationsFeature(lpparam, mPrefs),
        HideNavBarFeature(lpparam, mPrefs),
        HideNavBarBeforeScreenshotFeature(lpparam, mPrefs),
        AudioVisualizerFeature(lpparam, mPrefs),
        ControlCenterPluginFeature(lpparam, mPrefs),
        BatteryIndicatorFeature(lpparam, mPrefs),
        DisableAnyNotificationFeature(lpparam, mPrefs),
        LockScreenShortcutFeature(lpparam, mPrefs),
        MobileNetworkTypeFeature(lpparam, mPrefs),
        StatusBarIconsPositionAdjustFeature(lpparam, mPrefs),
        MonitorDeviceInfoFeature(lpparam, mPrefs),
        StatusBarClockPositionFeature(lpparam, mPrefs),
        StatusBarStyleBatteryIconFeature(lpparam, mPrefs),
        LockScreenTopMarginFeature(lpparam, mPrefs),
        HorizMarginFeature(lpparam, mPrefs),
        BrightnessPctFeature(lpparam, mPrefs),
        HideLockScreenStatusBarFeature(lpparam, mPrefs),
        HideLockScreenClockFeature(lpparam, mPrefs),
        ForceClockUseSystemFontsFeature(lpparam, mPrefs),
        HideLockScreenHintFeature(lpparam, mPrefs),
        AllowAllKeyguardFeature(lpparam, mPrefs),
        AllowAllFloatFeature(lpparam, mPrefs),
        LockScreenAlarmFeature(lpparam, mPrefs),
        StatusBarGesturesFeature(lpparam, mPrefs),
        NetSpeedIntervalFeature(lpparam, mPrefs),
        DetailedNetSpeedFeature(lpparam, mPrefs),
        NetSpeedStyleFeature(lpparam, mPrefs),
        TapToUnlockFeature(lpparam, mPrefs),
        NoSOSFeature(lpparam, mPrefs),
        RemovePackageNotificationsLimitFeature(lpparam, mPrefs),
        DisableFoldNotificationsFeature(lpparam, mPrefs),
        DisableStrongToastFeature(lpparam, mPrefs),
        ChargingInfoFeature(lpparam, mPrefs),
        SecureQSTilesFeature(lpparam, mPrefs),
        MuteVisibleNotificationsFeature(lpparam, mPrefs),
        HideIconsBattery1Feature(lpparam, mPrefs),
        HideIconsBattery2Feature(lpparam, mPrefs),
        DisplayWifiStandardFeature(lpparam, mPrefs),
        HidePrivacyIndicatorFeature(lpparam, mPrefs),
        HideIconsSignalFeature(lpparam, mPrefs),
        HideIconsVoWiFiFeature(lpparam, mPrefs),
        HideIconsSelectiveAlarmFeature(lpparam, mPrefs),
        ReplaceShortcutAppFeature(lpparam, mPrefs),
        QSHapticFeature(lpparam, mPrefs),
        CollapseCCAfterClickFeature(lpparam, mPrefs),
        LongClickTileOpenInFreeFormFeature(lpparam, mPrefs),
        SwitchCCAndNotificationFeature(lpparam, mPrefs),
        ExpandNotificationsFeature(lpparam, mPrefs),
        HideMobileNetworkIndicatorFeature(lpparam, mPrefs),
        ExtendedPowerMenuFeature(lpparam, mPrefs),
        HideIconsFeature(lpparam, mPrefs),
        HideIconsFromSystemManagerFeature(lpparam, mPrefs),
        BetterPopupsAllowFloatFeature(lpparam, mPrefs),
        AutoDismissExpandedPopupsFeature(lpparam, mPrefs),
        DisableHeadsUpWhenMuteFeature(lpparam, mPrefs),
        MinimalNotificationViewFeature(lpparam, mPrefs),
        NotificationChannelSettingsFeature(lpparam, mPrefs),
        MaxNotificationIconsFeature(lpparam, mPrefs),
        MobileTypeSingleFeature(lpparam, mPrefs),
        StatusBarDigitalSignalFeature(lpparam, mPrefs),
        DualRowSignalFeature(lpparam, mPrefs),
        DualRowsStatusbarFeature(lpparam, mPrefs),
        ColorizeNotificationCardFeature(lpparam, mPrefs),
        OpenNotifyInFloatingWindowFeature(lpparam, mPrefs),
        DisableSideBarSuggestionFeature(lpparam, mPrefs),
        HideSafeVolumeDlgFeature(lpparam, mPrefs),
        HideLockscreenZenModeFeature(lpparam, mPrefs),
        DisableKeyguardEditorFeature(lpparam, mPrefs),
        NoPasswordFeature(lpparam, mPrefs),
        NotificationImportanceFeature(lpparam, mPrefs),
        NoLightUpOnChargeSystemUiFeature(lpparam, mPrefs),
    )
}