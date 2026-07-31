package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.Controls;
import tv.withaibuild.customiuizer.mods.GlobalActions;
import tv.withaibuild.customiuizer.mods.System;
import tv.withaibuild.customiuizer.mods.SystemAudioHooks;
import tv.withaibuild.customiuizer.mods.SystemClockHooks;
import tv.withaibuild.customiuizer.mods.SystemColorizeNotificationHooks;
import tv.withaibuild.customiuizer.mods.SystemDisplayHooks;
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks;
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks;
import tv.withaibuild.customiuizer.mods.SystemStatusBarIconHooks;
import tv.withaibuild.customiuizer.mods.SystemUI;
import tv.withaibuild.customiuizer.mods.SystemUIControlCenterHooks;
import tv.withaibuild.customiuizer.mods.SystemUILockScreenHooks;
import tv.withaibuild.customiuizer.mods.SystemUINotificationHooks;
import tv.withaibuild.customiuizer.mods.SystemUIScreenshotHooks;
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks;
import tv.withaibuild.customiuizer.mods.SystemWindowHooks;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the SystemUI process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the long list of package-specific SystemUI hooks to a dedicated, stateless class.
 * Base hooks (SystemUIInitializer.init, fast-reboot receiver, status-bar setup and the 10-second
 * restart guard) stay in MainModule so the installer receives an already-validated load point.
 */
public final class SystemUiInstaller {

    private SystemUiInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        if (mPrefs.getStringAsInt("various_showcallui", 0) > 0
            || mPrefs.getBoolean("controls_volumecursor")
        ) GlobalActions.setupForegroundMonitor(lpparam);

        if (mPrefs.getBoolean("system_screenshot_overlay")) {
            SystemUIScreenshotHooks.TempHideOverlaySystemUIHook(lpparam);
        }

        if (
            mPrefs.getBoolean("system_fivegtile")
            || mPrefs.getBoolean("system_cc_fpstile")
            || mPrefs.getBoolean("system_cc_floatingtimetile")
        ) {
            SystemUIControlCenterHooks.AddCustomTileHook(lpparam);
        }

        if (mPrefs.getBoolean("system_hidestatusbar_whenscreenshot")) {
            SystemUIScreenshotHooks.HideStatusBarWhenCaptureHook(lpparam);
        }

        if (mPrefs.getBoolean("system_networkindicator_wifi")) System.NetworkIndicatorWifi(lpparam);

        if (mPrefs.getInt("system_drawer_blur", 100) < 100) SystemDisplayHooks.DrawerBlurRatioHook(lpparam);
        if (mPrefs.getInt("system_chargeanimtime", 20) < 20) SystemDisplayHooks.ChargeAnimationHook(lpparam);
        if (mPrefs.getInt("system_betterpopups_delay", 0) > 0 && !mPrefs.getBoolean("system_betterpopups_nohide")) SystemNotificationHooks.BetterPopupsHideDelayHook(lpparam);
        if (mPrefs.getInt("controls_fsg_assist_left_action", 1) > 1
            || mPrefs.getInt("controls_fsg_assist_right_action", 1) > 1
        ) Controls.AssistGestureActionHook(lpparam);
        if (mPrefs.getInt("controls_navbarleft_action", 1) > 1 ||
                mPrefs.getInt("controls_navbarleftlong_action", 1) > 1 ||
                mPrefs.getInt("controls_navbarright_action", 1) > 1 ||
                mPrefs.getInt("controls_navbarrightlong_action", 1) > 1) Controls.NavBarButtonsHook(lpparam);
        if (mPrefs.getBoolean("system_scramblepin")) SystemLockScreenHooks.ScramblePINHook(lpparam);
        if (mPrefs.getBoolean("system_dttosleep")) SystemLockScreenHooks.DoubleTapToSleepHook(lpparam);
        if (mPrefs.getBoolean("system_statusbar_clocktweak")
            || mPrefs.getBoolean("system_cc_clocktweak")
            || mPrefs.getBoolean("system_cc_hidedate")
            || mPrefs.getBoolean("system_drawer_hidedate")
            || mPrefs.getBoolean("system_statusbaricons_clock")
            || mPrefs.getString("system_cc_dateformat", "").length() > 0
            || mPrefs.getString("system_drawer_dateformat", "").length() > 0
        ) SystemClockHooks.StatusBarClockTweakHook(lpparam);
        if (mPrefs.getBoolean("system_cc_clocktweak")
            || mPrefs.getBoolean("system_qs_force_systemfonts")
        ) SystemClockHooks.CCClockTweakHook(lpparam);
        if (mPrefs.getBoolean("system_qs_disable_fakeclock_anim")) {
            SystemUIStatusBarHooks.DisableFakeClockAnimHook(lpparam);
        }
        if (
            mPrefs.getBoolean("system_cc_clock_centeralign")
            || (!mPrefs.getBoolean("system_drawer_hidedate") && mPrefs.getBoolean("system_drawer_date_centeralign"))
        ) SystemClockHooks.CCClockCenterAlignHook(lpparam);
        if (mPrefs.getBoolean("system_noscreenlock_act")) SystemLockScreenHooks.NoScreenLockHook(lpparam);
        if (mPrefs.getBoolean("system_albumartonlock")) SystemUILockScreenHooks.LockScreenAlbumArtHook(lpparam);
        if (mPrefs.getStringAsInt("system_expandheadups", 1) > 1) SystemNotificationHooks.ExpandHeadsUpHook(lpparam);
        if (mPrefs.getBoolean("system_betterpopups_nohide")) SystemNotificationHooks.BetterPopupsNoHideHook(lpparam);
        if (mPrefs.getBoolean("system_betterpopups_center")) SystemNotificationHooks.BetterPopupsCenteredHook(lpparam);
        if (mPrefs.getBoolean("system_notifafterunlock")) SystemLockScreenHooks.ShowNotificationsAfterUnlockHook(lpparam);
        if (mPrefs.getBoolean("system_notifrowmenu")) SystemNotificationHooks.NotificationRowMenuHook(lpparam);
        if (mPrefs.getBoolean("system_removedismiss")) SystemUINotificationHooks.HideDismissViewHook(lpparam);
        if (mPrefs.getBoolean("system_drawer_removeshortcut")) SystemUINotificationHooks.HideNoficationAccessIconHook(lpparam);
        if (mPrefs.getBoolean("system_drawer_remove_emptynotify")) SystemUINotificationHooks.HideNoNotificationsHook(lpparam);
        if (mPrefs.getBoolean("controls_nonavbar")) Controls.HideNavBarHook(lpparam);
        else if (mPrefs.getBoolean("controls_hidenavbar_whenscreenshot")) SystemUIScreenshotHooks.HideNavBarBeforeScreenshotHook(lpparam);
        if (mPrefs.getBoolean("system_visualizer")) SystemAudioHooks.AudioVisualizerHook(lpparam);
        if (SystemUIControlCenterHooks.hasControlCenterModifications()) SystemUIControlCenterHooks.ControlCenterPluginHook(lpparam);
        if (mPrefs.getBoolean("system_batteryindicator")) SystemUIStatusBarHooks.BatteryIndicatorHook(lpparam);
        if (mPrefs.getBoolean("system_disableanynotif")) SystemNotificationHooks.DisableAnyNotificationHook(lpparam);
        if (mPrefs.getBoolean("system_lockscreenshortcuts")) SystemUILockScreenHooks.LockScreenShortcutHook(lpparam);
        if (mPrefs.getBoolean("system_4gtolte")
            || (mPrefs.getBoolean("system_statusbar_mobiletype_single")
            && !mPrefs.getString("system_statusbar_mobile_showname", "").equals(""))
        ) SystemUIStatusBarHooks.MobileNetworkTypeHook(lpparam);

        boolean dualRows = mPrefs.getBoolean("system_statusbar_dualrows");
        boolean netspeedAtRow2 = dualRows && mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow");
        boolean showBatteryDetail = mPrefs.getBoolean("system_statusbar_batterytempandcurrent");
        boolean showDeviceTemp = mPrefs.getBoolean("system_statusbar_showdevicetemperature");
        boolean batteryAtRight = showBatteryDetail && !dualRows && mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright");
        boolean tempAtRight = showDeviceTemp && !dualRows && mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright");
        boolean batteryAtLeft = showBatteryDetail && !mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright");
        boolean tempAtLeft = showDeviceTemp && !mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright");

        boolean alwaysShowAtRight = mPrefs.getBoolean("system_statusbar_alarm_atright")
            || mPrefs.getBoolean("system_statusbar_nfc_atright")
            || mPrefs.getBoolean("system_statusbar_btbattery_atright")
            || mPrefs.getBoolean("system_statusbar_headset_atright")
            || mPrefs.getBoolean("system_statusbar_vpn_atright")
            || batteryAtRight || tempAtRight;
        boolean moveLeft = mPrefs.getBoolean("system_statusbar_alarm_atleft")
            || mPrefs.getBoolean("system_statusbar_sound_atleft")
            || mPrefs.getBoolean("system_statusbar_netspeed_atleft")
            || mPrefs.getBoolean("system_statusbar_dnd_atleft")
            || mPrefs.getBoolean("system_statusbar_gps_atleft")
            || mPrefs.getBoolean("system_statusbaricons_wifi_mobile_atleft")
            || batteryAtLeft || tempAtLeft;
        if (alwaysShowAtRight || moveLeft
            || netspeedAtRow2
            || mPrefs.getBoolean("system_statusbaricons_swap_wifi_mobile")
        ) {
            SystemUIStatusBarHooks.StatusBarIconsPositionAdjustHook(lpparam, moveLeft);
        }
        if (showBatteryDetail || showDeviceTemp) {
            SystemUIStatusBarHooks.MonitorDeviceInfoHook(lpparam, mPrefs);
        }
        if (mPrefs.getStringAsInt("system_statusbar_clock_position", 1) > 1 && !dualRows) {
            SystemUIStatusBarHooks.StatusBarClockPositionHook(lpparam);
        }
        if (mPrefs.getBoolean("system_statusbar_batterystyle")) {
            SystemUIStatusBarHooks.StatusBarStyleBatteryIconHook(lpparam);
        }
        if (mPrefs.getBoolean("system_statusbar_topmargin") && mPrefs.getBoolean("system_statusbar_topmargin_unset_lockscreen")) SystemUILockScreenHooks.LockScreenTopMarginHook(lpparam);
        if (mPrefs.getBoolean("system_statusbar_horizmargin")) SystemUIStatusBarHooks.HorizMarginHook(lpparam);
        if (mPrefs.getBoolean("system_showpct")) SystemUIControlCenterHooks.BrightnessPctHook(lpparam);
        if (mPrefs.getBoolean("system_hidelsstatusbar")) SystemLockScreenHooks.HideLockScreenStatusBarHook(lpparam);
        if (mPrefs.getBoolean("system_hidelsclock")) SystemLockScreenHooks.HideLockScreenClockHook(lpparam);
        if (mPrefs.getBoolean("system_ls_force_systemfonts")) SystemUIStatusBarHooks.ForceClockUseSystemFontsHook(lpparam);
        if (mPrefs.getBoolean("system_hidelshint")) SystemLockScreenHooks.HideLockScreenHintHook(lpparam);
        if (mPrefs.getBoolean("system_allownotifonkeyguard")) SystemLockScreenHooks.AllowAllKeyguardHook(lpparam);
        if (mPrefs.getBoolean("system_allownotiffloat")) SystemWindowHooks.AllowAllFloatHook(lpparam);
        if (mPrefs.getBoolean("system_lsalarm")) SystemLockScreenHooks.LockScreenAlarmHook(lpparam);
        if (mPrefs.getBoolean("system_statusbarcontrols")) SystemUIControlCenterHooks.StatusBarGesturesHook(lpparam);
        if (mPrefs.getInt("system_netspeedinterval", 4) != 4) SystemUIStatusBarHooks.NetSpeedIntervalHook(lpparam);
        if (mPrefs.getStringAsInt("system_detailednetspeed_style", 1) > 1) SystemUIStatusBarHooks.DetailedNetSpeedHook(lpparam);
        if (mPrefs.getStringAsInt("system_detailednetspeed_style", 1) > 1
            || mPrefs.getBoolean("system_netspeed_boldfont")
            || mPrefs.getBoolean("system_netspeed_use_clock_style")
            || mPrefs.getInt("system_netspeed_fontsize", 13) > 13
            || mPrefs.getInt("system_netspeed_fixedcontent_width", 10) > 10
            || mPrefs.getInt("system_netspeed_leftmargin", 0) > 0
            || mPrefs.getInt("system_netspeed_rightmargin", 0) > 0
            || mPrefs.getInt("system_netspeed_verticaloffset", 8) != 8
            || mPrefs.getStringAsInt("system_detailednetspeed_align", 1) > 1
        ) {
            SystemUIStatusBarHooks.NetSpeedStyleHook(lpparam);
        }
        if (mPrefs.getBoolean("system_taptounlock")) SystemLockScreenHooks.TapToUnlockHook(lpparam);
        if (mPrefs.getBoolean("system_nosos")) SystemLockScreenHooks.NoSOSHook(lpparam);
        if (mPrefs.getBoolean("system_morenotif")) SystemUINotificationHooks.RemovePackageNotificationsLimitHook(lpparam);
        if (mPrefs.getBoolean("system_notif_disable_fold")) SystemUINotificationHooks.DisableFoldNotificationsHook(lpparam);
        if (mPrefs.getBoolean("system_notif_disable_strong_toast")) SystemUI.DisableStrongToastHook(lpparam);
//            if (mPrefs.getInt("system_notif_strong_toast_width", 100) < 100) SystemUI.TweakStrongToastHook(lpparam);
        if (mPrefs.getBoolean("system_charginginfo")) SystemLockScreenHooks.ChargingInfoHook(lpparam);
        if (mPrefs.getBoolean("system_secureqs")) SystemUIControlCenterHooks.SecureQSTilesHook(lpparam);
        if (mPrefs.getBoolean("system_mutevisiblenotif")) SystemNotificationHooks.MuteVisibleNotificationsHook(lpparam);
        if (mPrefs.getBoolean("system_statusbaricons_battery1")) SystemStatusBarIconHooks.HideIconsBattery1Hook(lpparam);
        if (mPrefs.getBoolean("system_statusbaricons_battery3")
            || mPrefs.getBoolean("system_statusbaricons_battery4")
            || mPrefs.getBoolean("system_statusbaricons_battery2")
        ) SystemStatusBarIconHooks.HideIconsBattery2Hook(lpparam);
        if (mPrefs.getStringAsInt("system_statusbaricons_wifistandard", 1) > 1) SystemStatusBarIconHooks.DisplayWifiStandardHook(lpparam);
        if (mPrefs.getBoolean("system_statusbaricons_privacy_prompt")) SystemUIStatusBarHooks.HidePrivacyIndicatorHook(lpparam);
        if (mPrefs.getBoolean("system_statusbaricons_signal")
            || mPrefs.getBoolean("system_statusbaricons_sim1")
            || mPrefs.getBoolean("system_statusbaricons_sim2")
            || mPrefs.getBoolean("system_statusbaricons_sim_nodata")
            || mPrefs.getBoolean("system_statusbaricons_roaming")
            || mPrefs.getBoolean("system_statusbaricons_volte")
        ) SystemUIStatusBarHooks.HideIconsSignalHook(lpparam);
        if (mPrefs.getBoolean("system_statusbaricons_vowifi")) SystemUIStatusBarHooks.HideIconsVoWiFiHook(lpparam);
        if (!mPrefs.getBoolean("system_statusbaricons_alarm") && mPrefs.getInt("system_statusbaricons_alarmn", 0) > 0) SystemStatusBarIconHooks.HideIconsSelectiveAlarmHook(lpparam);
        if (!mPrefs.getString("system_shortcut_app", "").equals("")
            || !mPrefs.getString("system_calendar_app", "").equals("")
            || !mPrefs.getString("system_clock_app", "").equals("")) SystemUILockScreenHooks.ReplaceShortcutAppHook(lpparam);
        if (mPrefs.getStringAsInt("system_qshaptics", 1) > 1) SystemAudioHooks.QSHapticHook(lpparam);
        if (mPrefs.getBoolean("system_cc_collapse_after_clicked")) SystemUIControlCenterHooks.CollapseCCAfterClickHook(lpparam);
        if (mPrefs.getBoolean("system_cc_freeform_when_longclick")) SystemUIControlCenterHooks.LongClickTileOpenInFreeFormHook(lpparam);
        if (mPrefs.getBoolean("system_cc_switch_qsandnotification")) SystemUIControlCenterHooks.SwitchCCAndNotificationHook(lpparam);
        if (mPrefs.getStringAsInt("system_expandnotifs", 1) > 1) SystemNotificationHooks.ExpandNotificationsHook(lpparam);
        if (mPrefs.getStringAsInt("system_mobiletypeicon", 1) > 1
            || mPrefs.getBoolean("system_networkindicator_mobile")
            || mPrefs.getBoolean("system_statusbar_mobiletype_show_wificonnected")
        ) {
            SystemUIStatusBarHooks.HideMobileNetworkIndicatorHook(lpparam);
        }
        if (mPrefs.getBoolean("system_epm")) SystemUI.ExtendedPowerMenuHook(lpparam);

        boolean hideIconsActive =
            mPrefs.getBoolean("system_statusbaricons_wifi") ||
            mPrefs.getBoolean("system_statusbaricons_dualwifi") ||
            mPrefs.getBoolean("system_statusbaricons_alarm") ||
            mPrefs.getBoolean("system_statusbaricons_profile") ||
            mPrefs.getBoolean("system_statusbaricons_sound") ||
            mPrefs.getBoolean("system_statusbaricons_dnd") ||
            mPrefs.getBoolean("system_statusbaricons_secondspace") ||
            mPrefs.getBoolean("system_statusbaricons_headset") ||
            mPrefs.getBoolean("system_statusbaricons_nfc") ||
            mPrefs.getBoolean("system_statusbaricons_vpn") ||
            mPrefs.getBoolean("system_statusbaricons_airplane") ||
            mPrefs.getBoolean("system_statusbaricons_hotspot") ||
            mPrefs.getBoolean("system_statusbaricons_nosims") ||
            mPrefs.getBoolean("system_statusbaricons_gps") ||
            mPrefs.getBoolean("system_statusbaricons_btbattery") ||
            mPrefs.getBoolean("system_statusbaricons_ble_unlock") ||
            mPrefs.getBoolean("system_statusbaricons_bluetoothicn") ||
            mPrefs.getBoolean("system_statusbaricons_volte");
        if (hideIconsActive) SystemUIStatusBarHooks.HideIconsHook(lpparam);

        if (
            mPrefs.getBoolean("system_statusbaricons_privacy")
            || mPrefs.getBoolean("system_statusbaricons_mute")
            || mPrefs.getBoolean("system_statusbaricons_speaker")
            || mPrefs.getBoolean("system_statusbaricons_record")
            || mPrefs.getBoolean("system_statusbaricons_wireless_headset")
        ) SystemUIStatusBarHooks.HideIconsFromSystemManager(lpparam);
        if (mPrefs.getBoolean("system_betterpopups_allowfloat")) SystemWindowHooks.BetterPopupsAllowFloatHook(lpparam);
        if (mPrefs.getBoolean("system_betterpopups_autoclose_expanded")) SystemNotificationHooks.AutoDismissExpandedPopupsHook(lpparam);
        if (mPrefs.getBoolean("system_betterpopups_disablewhenmute")) SystemUINotificationHooks.DisableHeadsUpWhenMuteHook(lpparam);
        if (mPrefs.getBoolean("system_minimalnotifview")) SystemNotificationHooks.MinimalNotificationViewHook(lpparam);
        if (mPrefs.getBoolean("system_notifchannelsettings")) SystemNotificationHooks.NotificationChannelSettingsHook(lpparam);
        if (mPrefs.getStringAsInt("system_maxsbicons", 0) != 0) SystemNotificationHooks.MaxNotificationIconsHook(lpparam);
        if (mPrefs.getBoolean("system_statusbar_mobiletype_single")) {
            SystemUIStatusBarHooks.MobileTypeSingleHook(lpparam);
        }
        if (mPrefs.getBoolean("system_statusbar_mobile_digital_signal")) {
            SystemUIStatusBarHooks.StatusBarDigitalSignalHook(lpparam);
        }
        else if (mPrefs.getBoolean("system_statusbar_dualsimin2rows")) {
            SystemUIStatusBarHooks.DualRowSignalHook(lpparam);
        }
        if (dualRows) {
            SystemUIStatusBarHooks.DualRowsStatusbarHook(lpparam);
        }
        if (mPrefs.getStringAsInt("system_colorizenotifs", 1) > 1) SystemColorizeNotificationHooks.ColorizeNotificationCardHook(lpparam);
        if (mPrefs.getBoolean("system_notify_openinfw")) SystemUINotificationHooks.OpenNotifyInFloatingWindowHook(lpparam);
        if (mPrefs.getBoolean("system_fw_noblacklist")) SystemWindowHooks.DisableSideBarSuggestionHook(lpparam);

        if (mPrefs.getBoolean("system_nosafevolume")) {
            SystemUIControlCenterHooks.HideSafeVolumeDlgHook(lpparam);
        }
        if (mPrefs.getBoolean("system_lockscreen_hidezenmode")) {
            SystemUILockScreenHooks.HideLockscreenZenModeHook(lpparam);
        }
        if (mPrefs.getBoolean("system_lockscreen_disable_edit")) {
            SystemUILockScreenHooks.DisableKeyguardEditorHook(lpparam);
        }
        if (mPrefs.getBoolean("system_nopassword")) SystemLockScreenHooks.NoPasswordHook(lpparam);

        if (mPrefs.getBoolean("system_notifimportance")) {
            SystemUINotificationHooks.NotificationImportanceHook(lpparam);
        }
        if (mPrefs.getStringAsInt("system_nolightuponcharges", 1) > 1) SystemUI.NoLightUpOnChargeHook(lpparam);
    }
}
