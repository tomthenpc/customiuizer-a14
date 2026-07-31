package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.FeatureId

/**
 * Typed identities for all features in the module.
 *
 * Keeping feature ids together makes it easy to see the complete list and avoids accidental
 * duplicate identities across different installers.
 */

data object PackagePermissionsFeatureId : FeatureId {
    override val name = "package_permissions"
}
data object TempHideOverlayAppFeatureId : FeatureId {
    override val name = "temp_hide_overlay_app"
}
data object OpenAppInFreeFormFeatureId : FeatureId {
    override val name = "open_app_in_free_form"
}
data object NavBarActionsFeatureId : FeatureId {
    override val name = "nav_bar_actions"
}
data object PowerDoubleTapActionFeatureId : FeatureId {
    override val name = "power_double_tap_action"
}
data object ScreenAnimFeatureId : FeatureId {
    override val name = "screen_anim"
}
data object AppLockTimeoutFeatureId : FeatureId {
    override val name = "app_lock_timeout"
}
data object ScreenDimTimeFeatureId : FeatureId {
    override val name = "screen_dim_time"
}
data object ToastTimeFeatureId : FeatureId {
    override val name = "toast_time"
}
data object RemoveSecureFeatureId : FeatureId {
    override val name = "remove_secure"
}
data object RemoveActStartConfirmFeatureId : FeatureId {
    override val name = "remove_act_start_confirm"
}
data object EnhancedSecurityFeatureId : FeatureId {
    override val name = "enhanced_security"
}
data object NoVersionCheckFeatureId : FeatureId {
    override val name = "no_version_check"
}
data object OrientationLockFeatureId : FeatureId {
    override val name = "orientation_lock"
}
data object NoDuckingFeatureId : FeatureId {
    override val name = "no_ducking"
}
data object CleanShareMenuServiceFeatureId : FeatureId {
    override val name = "clean_share_menu_service"
}
data object CleanOpenWithMenuServiceFeatureId : FeatureId {
    override val name = "clean_open_with_menu_service"
}
data object AutoBrightnessRangeFeatureId : FeatureId {
    override val name = "auto_brightness_range"
}
data object AutoBrightnessAfterScreenOffFeatureId : FeatureId {
    override val name = "auto_brightness_after_screen_off"
}
data object Disable72hStrongAuthFeatureId : FeatureId {
    override val name = "disable72h_strong_auth"
}
data object AppLockFeatureId : FeatureId {
    override val name = "app_lock"
}
data object SkipAppLockFeatureId : FeatureId {
    override val name = "skip_app_lock"
}
data object AlarmCompatServiceFeatureId : FeatureId {
    override val name = "alarm_compat_service"
}
data object NoCallInterruptionFeatureId : FeatureId {
    override val name = "no_call_interruption"
}
data object ForceCloseFeatureId : FeatureId {
    override val name = "force_close"
}
data object HideProximityWarningFeatureId : FeatureId {
    override val name = "hide_proximity_warning"
}
data object FirstVolumePressFeatureId : FeatureId {
    override val name = "first_volume_press"
}
data object NoSignatureVerifyServiceFeatureId : FeatureId {
    override val name = "no_signature_verify_service"
}
data object DisableSystemIntegrityFeatureId : FeatureId {
    override val name = "disable_system_integrity"
}
data object MuffledVibrationFeatureId : FeatureId {
    override val name = "muffled_vibration"
}
data object ClearAllTasksFeatureId : FeatureId {
    override val name = "clear_all_tasks"
}
data object ForceDarkAllAppsFeatureId : FeatureId {
    override val name = "force_dark_all_apps"
}
data object SetLockscreenWallpaperFeatureId : FeatureId {
    override val name = "set_lockscreen_wallpaper"
}
data object PowerKeyFeatureId : FeatureId {
    override val name = "power_key"
}
data object FingerprintHapticFailureFeatureId : FeatureId {
    override val name = "fingerprint_haptic_failure"
}
data object FingerprintScreenOnFeatureId : FeatureId {
    override val name = "fingerprint_screen_on"
}
data object NoFingerprintWakeFeatureId : FeatureId {
    override val name = "no_fingerprint_wake"
}
data object AppsDisableServiceFeatureId : FeatureId {
    override val name = "apps_disable_service"
}
data object DisableAnyNotificationBlockFeatureId : FeatureId {
    override val name = "disable_any_notification_block"
}
data object AllRotationsFeatureId : FeatureId {
    override val name = "all_rotations"
}
data object NoLightUpOnChargeFeatureId : FeatureId {
    override val name = "no_light_up_on_charge"
}
data object SelectiveVibrationFeatureId : FeatureId {
    override val name = "selective_vibration"
}
data object SelectiveToastsFeatureId : FeatureId {
    override val name = "selective_toasts"
}
data object FingerprintHapticSuccessFeatureId : FeatureId {
    override val name = "fingerprint_haptic_success"
}
data object VolumeMediaButtonsFeatureId : FeatureId {
    override val name = "volume_media_buttons"
}
data object MultiWindowPlusFeatureId : FeatureId {
    override val name = "multi_window_plus"
}
data object NoFloatingWindowBlacklistFeatureId : FeatureId {
    override val name = "no_floating_window_blacklist"
}
data object NoAccessDeviceLogsRequestFeatureId : FeatureId {
    override val name = "no_access_device_logs_request"
}
data object WallpaperScaleLevelFeatureId : FeatureId {
    override val name = "wallpaper_scale_level"
}
data object AllowUntrustedTouchFeatureId : FeatureId {
    override val name = "allow_untrusted_touch"
}

data object ForegroundMonitorFeatureId : FeatureId {
    override val name = "foreground_monitor"
}
data object TempHideOverlaySystemUiFeatureId : FeatureId {
    override val name = "temp_hide_overlay_system_ui"
}
data object AddCustomTileFeatureId : FeatureId {
    override val name = "add_custom_tile"
}
data object HideStatusBarWhenCaptureFeatureId : FeatureId {
    override val name = "hide_status_bar_when_capture"
}
data object NetworkIndicatorWifiFeatureId : FeatureId {
    override val name = "network_indicator_wifi"
}
data object DrawerBlurRatioFeatureId : FeatureId {
    override val name = "drawer_blur_ratio"
}
data object ChargeAnimationFeatureId : FeatureId {
    override val name = "charge_animation"
}
data object BetterPopupsHideDelayFeatureId : FeatureId {
    override val name = "betterpopups_hide_delay"
}
data object AssistGestureActionFeatureId : FeatureId {
    override val name = "assist_gesture_action"
}
data object NavBarButtonsFeatureId : FeatureId {
    override val name = "nav_bar_buttons"
}
data object ScramblePinFeatureId : FeatureId {
    override val name = "scramble_pin"
}
data object DoubleTapToSleepFeatureId : FeatureId {
    override val name = "double_tap_to_sleep"
}
data object StatusBarClockTweakFeatureId : FeatureId {
    override val name = "status_bar_clock_tweak"
}
data object CcClockTweakFeatureId : FeatureId {
    override val name = "cc_clock_tweak"
}
data object DisableFakeClockAnimFeatureId : FeatureId {
    override val name = "disable_fake_clock_anim"
}
data object CcClockCenterAlignFeatureId : FeatureId {
    override val name = "cc_clock_center_align"
}
data object NoScreenLockFeatureId : FeatureId {
    override val name = "no_screen_lock"
}
data object LockScreenAlbumArtFeatureId : FeatureId {
    override val name = "lock_screen_album_art"
}
data object ExpandHeadsUpFeatureId : FeatureId {
    override val name = "expand_heads_up"
}
data object BetterPopupsNoHideFeatureId : FeatureId {
    override val name = "betterpopups_no_hide"
}
data object BetterPopupsCenteredFeatureId : FeatureId {
    override val name = "betterpopups_centered"
}
data object ShowNotificationsAfterUnlockFeatureId : FeatureId {
    override val name = "show_notifications_after_unlock"
}
data object NotificationRowMenuFeatureId : FeatureId {
    override val name = "notification_row_menu"
}
data object HideDismissViewFeatureId : FeatureId {
    override val name = "hide_dismiss_view"
}
data object HideNotificationAccessIconFeatureId : FeatureId {
    override val name = "hide_notification_access_icon"
}
data object HideNoNotificationsFeatureId : FeatureId {
    override val name = "hide_no_notifications"
}
data object HideNavBarFeatureId : FeatureId {
    override val name = "hide_nav_bar"
}
data object HideNavBarBeforeScreenshotFeatureId : FeatureId {
    override val name = "hide_nav_bar_before_screenshot"
}
data object AudioVisualizerFeatureId : FeatureId {
    override val name = "audio_visualizer"
}
data object ControlCenterPluginFeatureId : FeatureId {
    override val name = "control_center_plugin"
}
data object BatteryIndicatorFeatureId : FeatureId {
    override val name = "battery_indicator"
}
data object DisableAnyNotificationFeatureId : FeatureId {
    override val name = "disable_any_notification"
}
data object LockScreenShortcutFeatureId : FeatureId {
    override val name = "lock_screen_shortcut"
}
data object MobileNetworkTypeFeatureId : FeatureId {
    override val name = "mobile_network_type"
}
data object StatusBarIconsPositionAdjustFeatureId : FeatureId {
    override val name = "status_bar_icons_position_adjust"
}
data object MonitorDeviceInfoFeatureId : FeatureId {
    override val name = "monitor_device_info"
}
data object StatusBarClockPositionFeatureId : FeatureId {
    override val name = "status_bar_clock_position"
}
data object StatusBarStyleBatteryIconFeatureId : FeatureId {
    override val name = "status_bar_style_battery_icon"
}
data object LockScreenTopMarginFeatureId : FeatureId {
    override val name = "lock_screen_top_margin"
}
data object HorizMarginFeatureId : FeatureId {
    override val name = "horiz_margin"
}
data object BrightnessPctFeatureId : FeatureId {
    override val name = "brightness_pct"
}
data object HideLockScreenStatusBarFeatureId : FeatureId {
    override val name = "hide_lock_screen_status_bar"
}
data object HideLockScreenClockFeatureId : FeatureId {
    override val name = "hide_lock_screen_clock"
}
data object ForceClockUseSystemFontsFeatureId : FeatureId {
    override val name = "force_clock_use_system_fonts"
}
data object HideLockScreenHintFeatureId : FeatureId {
    override val name = "hide_lock_screen_hint"
}
data object AllowAllKeyguardFeatureId : FeatureId {
    override val name = "allow_all_keyguard"
}
data object AllowAllFloatFeatureId : FeatureId {
    override val name = "allow_all_float"
}
data object LockScreenAlarmFeatureId : FeatureId {
    override val name = "lock_screen_alarm"
}
data object StatusBarGesturesFeatureId : FeatureId {
    override val name = "status_bar_gestures"
}
data object NetSpeedIntervalFeatureId : FeatureId {
    override val name = "net_speed_interval"
}
data object DetailedNetSpeedFeatureId : FeatureId {
    override val name = "detailed_net_speed"
}
data object NetSpeedStyleFeatureId : FeatureId {
    override val name = "net_speed_style"
}
data object TapToUnlockFeatureId : FeatureId {
    override val name = "tap_to_unlock"
}
data object NoSOSFeatureId : FeatureId {
    override val name = "no_sos"
}
data object RemovePackageNotificationsLimitFeatureId : FeatureId {
    override val name = "remove_package_notifications_limit"
}
data object DisableFoldNotificationsFeatureId : FeatureId {
    override val name = "disable_fold_notifications"
}
data object DisableStrongToastFeatureId : FeatureId {
    override val name = "disable_strong_toast"
}
data object ChargingInfoFeatureId : FeatureId {
    override val name = "charging_info"
}
data object SecureQSTilesFeatureId : FeatureId {
    override val name = "secure_qs_tiles"
}
data object MuteVisibleNotificationsFeatureId : FeatureId {
    override val name = "mute_visible_notifications"
}
data object HideIconsBattery1FeatureId : FeatureId {
    override val name = "hide_icons_battery1"
}
data object HideIconsBattery2FeatureId : FeatureId {
    override val name = "hide_icons_battery2"
}
data object DisplayWifiStandardFeatureId : FeatureId {
    override val name = "display_wifi_standard"
}
data object HidePrivacyIndicatorFeatureId : FeatureId {
    override val name = "hide_privacy_indicator"
}
data object HideIconsSignalFeatureId : FeatureId {
    override val name = "hide_icons_signal"
}
data object HideIconsVoWiFiFeatureId : FeatureId {
    override val name = "hide_icons_vowifi"
}
data object HideIconsSelectiveAlarmFeatureId : FeatureId {
    override val name = "hide_icons_selective_alarm"
}
data object ReplaceShortcutAppFeatureId : FeatureId {
    override val name = "replace_shortcut_app"
}
data object QSHapticFeatureId : FeatureId {
    override val name = "qs_haptic"
}
data object CollapseCCAfterClickFeatureId : FeatureId {
    override val name = "collapse_cc_after_click"
}
data object LongClickTileOpenInFreeFormFeatureId : FeatureId {
    override val name = "long_click_tile_open_in_free_form"
}
data object SwitchCCAndNotificationFeatureId : FeatureId {
    override val name = "switch_cc_and_notification"
}
data object ExpandNotificationsFeatureId : FeatureId {
    override val name = "expand_notifications"
}
data object HideMobileNetworkIndicatorFeatureId : FeatureId {
    override val name = "hide_mobile_network_indicator"
}
data object ExtendedPowerMenuFeatureId : FeatureId {
    override val name = "extended_power_menu"
}
data object HideIconsFeatureId : FeatureId {
    override val name = "hide_icons"
}
data object HideIconsFromSystemManagerFeatureId : FeatureId {
    override val name = "hide_icons_from_system_manager"
}
data object BetterPopupsAllowFloatFeatureId : FeatureId {
    override val name = "betterpopups_allow_float"
}
data object AutoDismissExpandedPopupsFeatureId : FeatureId {
    override val name = "auto_dismiss_expanded_popups"
}
data object DisableHeadsUpWhenMuteFeatureId : FeatureId {
    override val name = "disable_heads_up_when_mute"
}
data object MinimalNotificationViewFeatureId : FeatureId {
    override val name = "minimal_notification_view"
}
data object NotificationChannelSettingsFeatureId : FeatureId {
    override val name = "notification_channel_settings"
}
data object MaxNotificationIconsFeatureId : FeatureId {
    override val name = "max_notification_icons"
}
data object MobileTypeSingleFeatureId : FeatureId {
    override val name = "mobile_type_single"
}
data object StatusBarDigitalSignalFeatureId : FeatureId {
    override val name = "status_bar_digital_signal"
}
data object DualRowSignalFeatureId : FeatureId {
    override val name = "dual_row_signal"
}
data object DualRowsStatusbarFeatureId : FeatureId {
    override val name = "dual_rows_statusbar"
}
data object ColorizeNotificationCardFeatureId : FeatureId {
    override val name = "colorize_notification_card"
}
data object OpenNotifyInFloatingWindowFeatureId : FeatureId {
    override val name = "open_notify_in_floating_window"
}
data object DisableSideBarSuggestionFeatureId : FeatureId {
    override val name = "disable_side_bar_suggestion"
}
data object HideSafeVolumeDlgFeatureId : FeatureId {
    override val name = "hide_safe_volume_dlg"
}
data object HideLockscreenZenModeFeatureId : FeatureId {
    override val name = "hide_lockscreen_zen_mode"
}
data object DisableKeyguardEditorFeatureId : FeatureId {
    override val name = "disable_keyguard_editor"
}
data object NoPasswordFeatureId : FeatureId {
    override val name = "no_password"
}
data object NotificationImportanceFeatureId : FeatureId {
    override val name = "notification_importance"
}
data object NoLightUpOnChargeSystemUiFeatureId : FeatureId {
    override val name = "no_light_up_on_charge_system_ui"
}
