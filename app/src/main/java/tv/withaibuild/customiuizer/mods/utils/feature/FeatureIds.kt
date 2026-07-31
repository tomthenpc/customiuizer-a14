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
