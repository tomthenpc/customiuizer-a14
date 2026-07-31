package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedModuleInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.PackagePermissions
import tv.withaibuild.customiuizer.mods.System
import tv.withaibuild.customiuizer.mods.SystemAudioHooks
import tv.withaibuild.customiuizer.mods.SystemDisplayHooks
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks
import tv.withaibuild.customiuizer.mods.SystemSecurityHooks
import tv.withaibuild.customiuizer.mods.SystemShareMenuHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks

/**
 * Installer for hooks that must run in `system_server`.
 *
 * This keeps [MainModule] focused on module-level lifecycle and delegates the long list of
 * per-preference system-server hooks to a dedicated, stateless object.  Each hook is still guarded
 * by the same preference check; nothing is installed unless the user has enabled it.
 */
object SystemServerInstaller {

    @JvmStatic
    @JvmOverloads
    fun install(lpparam: XposedModuleInterface.SystemServerStartingParam, prefReady: Boolean = true) {
        val mPrefs = MainModule.mPrefs

        // Base system_server hook: not preference-controlled, always installed.
        PackagePermissions.hook(lpparam)

        if (prefReady && GlobalActions.hasCustomActions()) {
            GlobalActionSystemServerHooks.setupGlobalActions(lpparam)
        }

        if (!prefReady) return

        if (mPrefs.getBoolean("system_screenshot_overlay")) {
            SystemWindowHooks.TempHideOverlayAppHook(lpparam)
        }

        if (mPrefs.getBoolean("system_notify_openinfw")
            || mPrefs.getBoolean("system_fw_forcein_actionsend")
            || mPrefs.getBoolean("system_betterpopups_allowfloat")
            || mPrefs.getBoolean("system_cc_freeform_when_longclick")
        ) {
            SystemWindowHooks.OpenAppInFreeFormHook(lpparam)
        }

        if (mPrefs.getInt("controls_backlong_action", 1) > 1 ||
            mPrefs.getInt("controls_homelong_action", 1) > 1 ||
            mPrefs.getInt("controls_menulong_action", 1) > 1) {
            Controls.NavBarActionsHook(lpparam)
        }
        if (mPrefs.getInt("controls_powerdt_action", 1) > 1 || mPrefs.getBoolean("controls_volumedowndt_torch")) {
            Controls.PowerDoubleTapActionHook(lpparam)
        }
        if (mPrefs.getInt("system_screenanim_duration", 0) > 0) SystemDisplayHooks.ScreenAnimHook(lpparam)
        if (mPrefs.getInt("system_applock_timeout", 1) > 1) SystemLockScreenHooks.AppLockTimeoutHook(lpparam)
        if (mPrefs.getInt("system_dimtime", 0) > 0) SystemDisplayHooks.ScreenDimTimeHook(lpparam)
        if (mPrefs.getInt("system_toasttime", 0) > 0) System.ToastTimeHook(lpparam)
        if (mPrefs.getBoolean("system_removesecure")) SystemSecurityHooks.RemoveSecureHook(lpparam)
        if (mPrefs.getBoolean("system_remove_startactconfirm")) SystemSecurityHooks.RemoveActStartConfirmHook(lpparam)
        if (mPrefs.getBoolean("system_securelock")) SystemLockScreenHooks.EnhancedSecurityHook(lpparam)
        if (mPrefs.getBoolean("system_downgrade")) SystemSecurityHooks.NoVersionCheckHook(lpparam)
        if (mPrefs.getBoolean("system_orientationlock")) SystemWindowHooks.OrientationLockHook(lpparam)
        if (mPrefs.getBoolean("system_noducking")) SystemAudioHooks.NoDuckingHook(lpparam)
        if (mPrefs.getBoolean("system_cleanshare")) SystemShareMenuHooks.CleanShareMenuServiceHook(lpparam)
        if (mPrefs.getBoolean("system_cleanopenwith")) SystemShareMenuHooks.CleanOpenWithMenuServiceHook(lpparam)
        if (mPrefs.getBoolean("system_autobrightness")) SystemDisplayHooks.AutoBrightnessRangeHook(lpparam)
        if (mPrefs.getBoolean("system_autobrightness_reset_when_screenoff")) SystemDisplayHooks.AutoBrightnessAfterScreenOffHook(lpparam)
        if (mPrefs.getBoolean("system_lockscreen_disable_strongauth_72h")) SystemLockScreenHooks.Disable72hStrongAuthHook(lpparam)
        if (mPrefs.getBoolean("system_applock")) SystemLockScreenHooks.AppLockHook(lpparam)
        if (mPrefs.getBoolean("system_applock_skip")) SystemLockScreenHooks.SkipAppLockHook(lpparam)
        if (mPrefs.getBoolean("various_alarmcompat")) tv.withaibuild.customiuizer.mods.Various.AlarmCompatServiceHook(lpparam)
        if (mPrefs.getBoolean("system_ignorecalls")) SystemAudioHooks.NoCallInterruptionHook(lpparam)
        if (mPrefs.getBoolean("system_forceclose")) System.ForceCloseHook(lpparam)
        if (mPrefs.getBoolean("system_hideproxywarn")) System.HideProximityWarningHook(lpparam)
        if (mPrefs.getBoolean("system_firstpress")) SystemAudioHooks.FirstVolumePressHook(lpparam)
        if (mPrefs.getBoolean("system_apksign")) SystemSecurityHooks.NoSignatureVerifyServiceHook(lpparam)
        if (mPrefs.getBoolean("system_disableintegrity")) SystemSecurityHooks.DisableSystemIntegrityHook(lpparam)
        if (mPrefs.getBoolean("system_vibration_amp")) SystemAudioHooks.MuffledVibrationHook(lpparam)
        if (mPrefs.getBoolean("system_clearalltasks")) System.ClearAllTasksHook(lpparam)
        if (mPrefs.getBoolean("system_force_darken_allapps")) SystemDisplayHooks.ForceDarkAllAppsHook(lpparam)
        if (mPrefs.getBoolean("system_lswallpaper")) SystemLockScreenHooks.SetLockscreenWallpaperHook(lpparam)
        if (mPrefs.getBoolean("controls_powerflash")) Controls.PowerKeyHook(lpparam)
        if (mPrefs.getBoolean("controls_fingerprintfailure")) Controls.FingerprintHapticFailureHook(lpparam)
        if (mPrefs.getBoolean("controls_fingerprintscreen")) Controls.FingerprintScreenOnHook(lpparam)
        if (mPrefs.getBoolean("controls_fingerprintwake")) Controls.NoFingerprintWakeHook(lpparam)
        if (mPrefs.getBoolean("various_disableapp")) tv.withaibuild.customiuizer.mods.Various.AppsDisableServiceHook(lpparam)
        if (mPrefs.getBoolean("system_disableanynotif")) SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam)
        if (mPrefs.getStringAsInt("system_allrotations2", 1) > 1) SystemWindowHooks.AllRotationsHook(lpparam)
        if (mPrefs.getStringAsInt("system_nolightuponcharges", 1) == 2) SystemDisplayHooks.NoLightUpOnChargeHook(lpparam)
        if (mPrefs.getStringAsInt("system_vibration", 1) > 1) SystemAudioHooks.SelectiveVibrationHook(lpparam)
        if (mPrefs.getStringAsInt("system_blocktoasts", 1) > 1) System.SelectiveToastsHook(lpparam)
        if (mPrefs.getStringAsInt("controls_fingerprintsuccess", 1) > 1) Controls.FingerprintHapticSuccessHook(lpparam)
        if (mPrefs.getStringAsInt("controls_volumemedia_up", 0) > 0 ||
            mPrefs.getStringAsInt("controls_volumemedia_down", 0) > 0) {
            Controls.VolumeMediaButtonsHook(lpparam)
        }

        if (mPrefs.getBoolean("system_fw_splitscreen")) SystemWindowHooks.MultiWindowPlusHook(lpparam)
        if (mPrefs.getBoolean("system_fw_noblacklist")) SystemWindowHooks.NoFloatingWindowBlacklistHook(lpparam)
        if (mPrefs.getBoolean("various_disable_access_devicelogs")) {
            SystemSecurityHooks.NoAccessDeviceLogsRequest(lpparam)
        }
        if (mPrefs.getInt("system_other_wallpaper_scale", 6) > 6) SystemDisplayHooks.WallpaperScaleLevelHook(lpparam)
        if (mPrefs.getBoolean("various_allow_untrusted_touch")) SystemWindowHooks.AllowUntrustedTouchHook(lpparam)
    }
}
