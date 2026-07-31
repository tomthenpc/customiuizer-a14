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

// Common package-ready (MainModule onPackageReady) features
data object StatusBarHeightFeatureId : FeatureId {
    override val name = "status_bar_height"
}
data object AlarmCompatFeatureId : FeatureId {
    override val name = "alarm_compat"
}

// Launcher package-ready features
data object LauncherFolderColumnsResFeatureId : FeatureId {
    override val name = "launcher_folder_columns_res"
}
data object LauncherHorizontalSpacingFeatureId : FeatureId {
    override val name = "launcher_horizontal_spacing"
}
data object LauncherIndicatorHeightFeatureId : FeatureId {
    override val name = "launcher_indicator_height"
}
data object LauncherIndicatorMarginTopFeatureId : FeatureId {
    override val name = "launcher_indicator_margin_top"
}
data object LauncherUnlockGridsFeatureId : FeatureId {
    override val name = "launcher_unlock_grids"
}
data object LauncherDockTitlesFeatureId : FeatureId {
    override val name = "launcher_dock_titles"
}
data object LauncherDisableLogFeatureId : FeatureId {
    override val name = "launcher_disable_log"
}
data object LauncherWorkspaceCellPaddingTopFeatureId : FeatureId {
    override val name = "launcher_workspace_cell_padding_top"
}
data object LauncherDockMarginTopFeatureId : FeatureId {
    override val name = "launcher_dock_margin_top"
}
data object LauncherDockMarginBottomFeatureId : FeatureId {
    override val name = "launcher_dock_margin_bottom"
}
data object LauncherDockHeightFeatureId : FeatureId {
    override val name = "launcher_dock_height"
}
data object LauncherPrivacyAppsGestFeatureId : FeatureId {
    override val name = "launcher_privacy_apps_gest"
}

// Launcher post-attach features
data object LauncherHomescreenSwipesFeatureId : FeatureId {
    override val name = "launcher_homescreen_swipes"
}
data object LauncherHotSeatSwipesFeatureId : FeatureId {
    override val name = "launcher_hot_seat_swipes"
}
data object LauncherShakeFeatureId : FeatureId {
    override val name = "launcher_shake"
}
data object LauncherDoubleTapFeatureId : FeatureId {
    override val name = "launcher_double_tap"
}
data object LauncherPinchFeatureId : FeatureId {
    override val name = "launcher_pinch"
}
data object LauncherFolderColumnsFeatureId : FeatureId {
    override val name = "launcher_folder_columns"
}
data object LauncherIconScaleFeatureId : FeatureId {
    override val name = "launcher_icon_scale"
}
data object LauncherTitleFontSizeFeatureId : FeatureId {
    override val name = "launcher_title_font_size"
}
data object LauncherTitleTopMarginFeatureId : FeatureId {
    override val name = "launcher_title_top_margin"
}
data object LauncherNoClockHideFeatureId : FeatureId {
    override val name = "launcher_no_clock_hide"
}
data object LauncherRenameShortcutsFeatureId : FeatureId {
    override val name = "launcher_rename_shortcuts"
}
data object LauncherTitleShadowFeatureId : FeatureId {
    override val name = "launcher_title_shadow"
}
data object LauncherHideNavBarFeatureId : FeatureId {
    override val name = "launcher_hide_nav_bar"
}
data object LauncherInfiniteScrollFeatureId : FeatureId {
    override val name = "launcher_infinite_scroll"
}
data object LauncherHideTitlesFeatureId : FeatureId {
    override val name = "launcher_hide_titles"
}
data object LauncherFixAppInfoLaunchFeatureId : FeatureId {
    override val name = "launcher_fix_app_info_launch"
}
data object LauncherNoWidgetOnlyFeatureId : FeatureId {
    override val name = "launcher_no_widget_only"
}
data object LauncherReversePortraitFeatureId : FeatureId {
    override val name = "launcher_reverse_portrait"
}
data object LauncherMaxHotseatIconsFeatureId : FeatureId {
    override val name = "launcher_max_hotseat_icons"
}
data object LauncherCloseFolderOnLaunchFeatureId : FeatureId {
    override val name = "launcher_close_folder_on_launch"
}
data object LauncherRecentsBlurFeatureId : FeatureId {
    override val name = "launcher_recents_blur"
}
data object LauncherBackGestureAreaHeightFeatureId : FeatureId {
    override val name = "launcher_back_gesture_area_height"
}
data object LauncherBackGestureAreaWidthFeatureId : FeatureId {
    override val name = "launcher_back_gesture_area_width"
}
data object LauncherFsgesturesFeatureId : FeatureId {
    override val name = "launcher_fsgestures"
}
data object LauncherHideMemoryCleanFeatureId : FeatureId {
    override val name = "launcher_hide_memory_clean"
}
data object LauncherDisableWallpaperScaleFeatureId : FeatureId {
    override val name = "launcher_disable_wallpaper_scale"
}
data object LauncherHideStatusBarInRecentsFeatureId : FeatureId {
    override val name = "launcher_hide_status_bar_in_recents"
}
data object LauncherMultiWindowPlusFeatureId : FeatureId {
    override val name = "launcher_multi_window_plus"
}
data object LauncherFixAnimFeatureId : FeatureId {
    override val name = "launcher_fix_anim"
}
data object LauncherHideSeekPointsFeatureId : FeatureId {
    override val name = "launcher_hide_seek_points"
}
data object LauncherPrivacyFolderFeatureId : FeatureId {
    override val name = "launcher_privacy_folder"
}
data object LauncherHideFromRecentsFeatureId : FeatureId {
    override val name = "launcher_hide_from_recents"
}
data object LauncherFolderBlurFeatureId : FeatureId {
    override val name = "launcher_folder_blur"
}
data object LauncherNoUnlockAnimationFeatureId : FeatureId {
    override val name = "launcher_no_unlock_animation"
}
data object LauncherNoZoomAnimationFeatureId : FeatureId {
    override val name = "launcher_no_zoom_animation"
}
data object LauncherUseOldLaunchAnimationFeatureId : FeatureId {
    override val name = "launcher_use_old_launch_animation"
}
data object LauncherCloseDrawerOnLaunchFeatureId : FeatureId {
    override val name = "launcher_close_drawer_on_launch"
}
data object LauncherHorizontalWidgetSpacingFeatureId : FeatureId {
    override val name = "launcher_horizontal_widget_spacing"
}
data object LauncherAssistGestureActionFeatureId : FeatureId {
    override val name = "launcher_assist_gesture_action"
}
data object LauncherSwipeAndStopActionFeatureId : FeatureId {
    override val name = "launcher_swipe_and_stop_action"
}
data object LauncherCloseOnLaunchFeatureId : FeatureId {
    override val name = "launcher_close_on_launch"
}
data object LauncherResizableWidgetsFeatureId : FeatureId {
    override val name = "launcher_resizable_widgets"
}
data object LauncherWallpaperColorModeFeatureId : FeatureId {
    override val name = "launcher_wallpaper_color_mode"
}

// Input method features
data object InputMethodVolumeCursorFeatureId : FeatureId {
    override val name = "input_method_volume_cursor"
}
data object InputMethodFixBottomMarginFeatureId : FeatureId {
    override val name = "input_method_fix_bottom_margin"
}
data object InputMethodGboardPaddingFeatureId : FeatureId {
    override val name = "input_method_gboard_padding"
}

// Settings features
data object SettingsMiuizerIconFeatureId : FeatureId {
    override val name = "settings_miuizer_icon"
}
data object SettingsDisableAnyNotificationFeatureId : FeatureId {
    override val name = "settings_disable_any_notification"
}
data object SettingsNotificationImportanceFeatureId : FeatureId {
    override val name = "settings_notification_importance"
}
data object SettingsViewWifiPasswordFeatureId : FeatureId {
    override val name = "settings_view_wifi_password"
}

// Security center features
data object SecurityCenterAppInfoFeatureId : FeatureId {
    override val name = "security_center_app_info"
}
data object SecurityCenterAppsDisableFeatureId : FeatureId {
    override val name = "security_center_apps_disable"
}
data object SecurityCenterAppsRestrictFeatureId : FeatureId {
    override val name = "security_center_apps_restrict"
}
data object SecurityCenterHideReportButtonFeatureId : FeatureId {
    override val name = "security_center_hide_report_button"
}
data object SecurityCenterScrambleAppLockPinFeatureId : FeatureId {
    override val name = "security_center_scramble_app_lock_pin"
}
data object SecurityCenterAppsDefaultSortFeatureId : FeatureId {
    override val name = "security_center_apps_default_sort"
}
data object SecurityCenterInterceptPermFeatureId : FeatureId {
    override val name = "security_center_intercept_perm"
}
data object SecurityCenterOpenByDefaultFeatureId : FeatureId {
    override val name = "security_center_open_by_default"
}
data object SecurityCenterSkipSecurityScanFeatureId : FeatureId {
    override val name = "security_center_skip_security_scan"
}
data object SecurityCenterShowTempInBatteryFeatureId : FeatureId {
    override val name = "security_center_show_temp_in_battery"
}
data object SecurityCenterDisableSideBarSuggestionFeatureId : FeatureId {
    override val name = "security_center_disable_side_bar_suggestion"
}
data object SecurityCenterDisableDockSuggestFeatureId : FeatureId {
    override val name = "security_center_disable_dock_suggest"
}
data object SecurityCenterAddSideBarExpandReceiverFeatureId : FeatureId {
    override val name = "security_center_add_side_bar_expand_receiver"
}
data object SecurityCenterNoLowBatteryWarningFeatureId : FeatureId {
    override val name = "security_center_no_low_battery_warning"
}
data object SecurityCenterPrivacyAppsLayoutFeatureId : FeatureId {
    override val name = "security_center_privacy_apps_layout"
}
data object SecurityCenterPersistPrivacyThumbnailBlurFeatureId : FeatureId {
    override val name = "security_center_persist_privacy_thumbnail_blur"
}

// Phone features
data object PhoneShowCallUiFeatureId : FeatureId {
    override val name = "phone_show_call_ui"
}
data object PhoneInCallBrightnessFeatureId : FeatureId {
    override val name = "phone_in_call_brightness"
}
data object PhoneAnswerCallInHeadUpFeatureId : FeatureId {
    override val name = "phone_answer_call_in_head_up"
}

// Power keeper features
data object PowerKeeperAppsRestrictFeatureId : FeatureId {
    override val name = "power_keeper_apps_restrict"
}
data object PowerKeeperPersistBatteryOptimizationFeatureId : FeatureId {
    override val name = "power_keeper_persist_battery_optimization"
}

// Guard provider features
data object GuardProviderDisableDefraudAppsFeatureId : FeatureId {
    override val name = "guard_provider_disable_defraud_apps"
}

// Package installer features
data object PackageInstallerMiuiPackageFeatureId : FeatureId {
    override val name = "package_installer_miui_package"
}
data object PackageInstallerAppInfoFeatureId : FeatureId {
    override val name = "package_installer_app_info"
}
data object PackageInstallerPurifyFeatureId : FeatureId {
    override val name = "package_installer_purify"
}

// Media features
data object MediaDisableUnlockWallpaperScaleFeatureId : FeatureId {
    override val name = "media_disable_unlock_wallpaper_scale"
}
data object MediaScreenshotConfigFeatureId : FeatureId {
    override val name = "media_screenshot_config"
}
data object MediaGalleryScreenshotPathFeatureId : FeatureId {
    override val name = "media_gallery_screenshot_path"
}

// Android package features
data object AndroidCleanShareMenuFeatureId : FeatureId {
    override val name = "android_clean_share_menu"
}
data object AndroidCleanOpenWithMenuFeatureId : FeatureId {
    override val name = "android_clean_open_with_menu"
}
data object AndroidAllRotationsFeatureId : FeatureId {
    override val name = "android_all_rotations"
}

// Generic app post-attach features
data object LauncherPostAttachFeatureId : FeatureId {
    override val name = "launcher_post_attach"
}
data object GenericAppStatusBarBackgroundFeatureId : FeatureId {
    override val name = "generic_app_status_bar_background"
}
data object GenericAppNoOverscrollFeatureId : FeatureId {
    override val name = "generic_app_no_overscroll"
}
data object GenericAppVolumeMediaPlayerFeatureId : FeatureId {
    override val name = "generic_app_volume_media_player"
}
