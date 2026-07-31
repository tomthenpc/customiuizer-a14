package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemAudioHooks
import tv.withaibuild.customiuizer.mods.SystemDisplayHooks
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks
import tv.withaibuild.customiuizer.mods.SystemSecurityHooks
import tv.withaibuild.customiuizer.mods.SystemShareMenuHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureId
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Base class for all system_server features registered by [SystemServerInstaller].
 */
internal abstract class BaseSystemServerFeature(
    protected val lpparam: XposedModuleInterface.SystemServerStartingParam,
    override val id: FeatureId,
    override val name: String,
    override val preferenceKey: String?
) : FeatureDefinition {

    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING

    protected abstract fun isEnabledCondition(prefs: PrefMap): Boolean
    protected abstract fun installHook()

    final override fun isEnabled(prefs: PrefMap): Boolean = isEnabledCondition(prefs)

    final override fun install(): FeatureInstallResult = try {
        installHook()
        FeatureInstallResult.Installed
    } catch (t: Throwable) {
        FeatureInstallResult.FailedTransient(t.javaClass.name)
    }
}

internal class TempHideOverlayAppFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    TempHideOverlayAppFeatureId,
    "Temp Hide Overlay App",
    "system_screenshot_overlay"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_screenshot_overlay")
    override fun installHook() = SystemWindowHooks.TempHideOverlayAppHook(lpparam)
}

internal class OpenAppInFreeFormFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    OpenAppInFreeFormFeatureId,
    "Open App In Free Form",
    "system_notify_openinfw"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notify_openinfw") || prefs.getBoolean("system_fw_forcein_actionsend") || prefs.getBoolean("system_betterpopups_allowfloat") || prefs.getBoolean("system_cc_freeform_when_longclick")
    override fun installHook() = SystemWindowHooks.OpenAppInFreeFormHook(lpparam)
}

internal class NavBarActionsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NavBarActionsFeatureId,
    "Nav Bar Actions",
    "controls_backlong_action"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("controls_backlong_action", 1) > 1 || prefs.getInt("controls_homelong_action", 1) > 1 || prefs.getInt("controls_menulong_action", 1) > 1
    override fun installHook() = Controls.NavBarActionsHook(lpparam)
}

internal class PowerDoubleTapActionFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    PowerDoubleTapActionFeatureId,
    "Power Double Tap Action",
    "controls_powerdt_action"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("controls_powerdt_action", 1) > 1 || prefs.getBoolean("controls_volumedowndt_torch")
    override fun installHook() = Controls.PowerDoubleTapActionHook(lpparam)
}

internal class ScreenAnimFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ScreenAnimFeatureId,
    "Screen Anim",
    "system_screenanim_duration"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_screenanim_duration", 0) > 0
    override fun installHook() = SystemDisplayHooks.ScreenAnimHook(lpparam)
}

internal class AppLockTimeoutFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AppLockTimeoutFeatureId,
    "App Lock Timeout",
    "system_applock_timeout"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_applock_timeout", 1) > 1
    override fun installHook() = SystemLockScreenHooks.AppLockTimeoutHook(lpparam)
}

internal class ScreenDimTimeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ScreenDimTimeFeatureId,
    "Screen Dim Time",
    "system_dimtime"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_dimtime", 0) > 0
    override fun installHook() = SystemDisplayHooks.ScreenDimTimeHook(lpparam)
}

internal class ToastTimeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ToastTimeFeatureId,
    "Toast Time",
    "system_toasttime"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_toasttime", 0) > 0
    override fun installHook() = ModsSystem.ToastTimeHook(lpparam)
}

internal class RemoveSecureFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    RemoveSecureFeatureId,
    "Remove Secure",
    "system_removesecure"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_removesecure")
    override fun installHook() = SystemSecurityHooks.RemoveSecureHook(lpparam)
}

internal class RemoveActStartConfirmFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    RemoveActStartConfirmFeatureId,
    "Remove Act Start Confirm",
    "system_remove_startactconfirm"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_remove_startactconfirm")
    override fun installHook() = SystemSecurityHooks.RemoveActStartConfirmHook(lpparam)
}

internal class EnhancedSecurityFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    EnhancedSecurityFeatureId,
    "Enhanced Security",
    "system_securelock"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_securelock")
    override fun installHook() = SystemLockScreenHooks.EnhancedSecurityHook(lpparam)
}

internal class NoVersionCheckFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoVersionCheckFeatureId,
    "No Version Check",
    "system_downgrade"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_downgrade")
    override fun installHook() = SystemSecurityHooks.NoVersionCheckHook(lpparam)
}

internal class OrientationLockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    OrientationLockFeatureId,
    "Orientation Lock",
    "system_orientationlock"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_orientationlock")
    override fun installHook() = SystemWindowHooks.OrientationLockHook(lpparam)
}

internal class NoDuckingFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoDuckingFeatureId,
    "No Ducking",
    "system_noducking"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_noducking")
    override fun installHook() = SystemAudioHooks.NoDuckingHook(lpparam)
}

internal class CleanShareMenuServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    CleanShareMenuServiceFeatureId,
    "Clean Share Menu Service",
    "system_cleanshare"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_cleanshare")
    override fun installHook() = SystemShareMenuHooks.CleanShareMenuServiceHook(lpparam)
}

internal class CleanOpenWithMenuServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    CleanOpenWithMenuServiceFeatureId,
    "Clean Open With Menu Service",
    "system_cleanopenwith"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_cleanopenwith")
    override fun installHook() = SystemShareMenuHooks.CleanOpenWithMenuServiceHook(lpparam)
}

internal class AutoBrightnessRangeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AutoBrightnessRangeFeatureId,
    "Auto Brightness Range",
    "system_autobrightness"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_autobrightness")
    override fun installHook() = SystemDisplayHooks.AutoBrightnessRangeHook(lpparam)
}

internal class AutoBrightnessAfterScreenOffFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AutoBrightnessAfterScreenOffFeatureId,
    "Auto Brightness After Screen Off",
    "system_autobrightness_reset_when_screenoff"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_autobrightness_reset_when_screenoff")
    override fun installHook() = SystemDisplayHooks.AutoBrightnessAfterScreenOffHook(lpparam)
}

internal class Disable72hStrongAuthFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    Disable72hStrongAuthFeatureId,
    "Disable72h Strong Auth",
    "system_lockscreen_disable_strongauth_72h"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_lockscreen_disable_strongauth_72h")
    override fun installHook() = SystemLockScreenHooks.Disable72hStrongAuthHook(lpparam)
}

internal class AppLockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AppLockFeatureId,
    "App Lock",
    "system_applock"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_applock")
    override fun installHook() = SystemLockScreenHooks.AppLockHook(lpparam)
}

internal class SkipAppLockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SkipAppLockFeatureId,
    "Skip App Lock",
    "system_applock_skip"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_applock_skip")
    override fun installHook() = SystemLockScreenHooks.SkipAppLockHook(lpparam)
}

internal class AlarmCompatServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AlarmCompatServiceFeatureId,
    "Alarm Compat Service",
    "various_alarmcompat"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_alarmcompat")
    override fun installHook() = Various.AlarmCompatServiceHook(lpparam)
}

internal class NoCallInterruptionFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoCallInterruptionFeatureId,
    "No Call Interruption",
    "system_ignorecalls"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_ignorecalls")
    override fun installHook() = SystemAudioHooks.NoCallInterruptionHook(lpparam)
}

internal class ForceCloseFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ForceCloseFeatureId,
    "Force Close",
    "system_forceclose"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_forceclose")
    override fun installHook() = ModsSystem.ForceCloseHook(lpparam)
}

internal class HideProximityWarningFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    HideProximityWarningFeatureId,
    "Hide Proximity Warning",
    "system_hideproxywarn"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_hideproxywarn")
    override fun installHook() = ModsSystem.HideProximityWarningHook(lpparam)
}

internal class FirstVolumePressFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FirstVolumePressFeatureId,
    "First Volume Press",
    "system_firstpress"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_firstpress")
    override fun installHook() = SystemAudioHooks.FirstVolumePressHook(lpparam)
}

internal class NoSignatureVerifyServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoSignatureVerifyServiceFeatureId,
    "No Signature Verify Service",
    "system_apksign"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_apksign")
    override fun installHook() = SystemSecurityHooks.NoSignatureVerifyServiceHook(lpparam)
}

internal class DisableSystemIntegrityFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    DisableSystemIntegrityFeatureId,
    "Disable System Integrity",
    "system_disableintegrity"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_disableintegrity")
    override fun installHook() = SystemSecurityHooks.DisableSystemIntegrityHook(lpparam)
}

internal class MuffledVibrationFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    MuffledVibrationFeatureId,
    "Muffled Vibration",
    "system_vibration_amp"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_vibration_amp")
    override fun installHook() = SystemAudioHooks.MuffledVibrationHook(lpparam)
}

internal class ClearAllTasksFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ClearAllTasksFeatureId,
    "Clear All Tasks",
    "system_clearalltasks"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_clearalltasks")
    override fun installHook() = ModsSystem.ClearAllTasksHook(lpparam)
}

internal class ForceDarkAllAppsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    ForceDarkAllAppsFeatureId,
    "Force Dark All Apps",
    "system_force_darken_allapps"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_force_darken_allapps")
    override fun installHook() = SystemDisplayHooks.ForceDarkAllAppsHook(lpparam)
}

internal class SetLockscreenWallpaperFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SetLockscreenWallpaperFeatureId,
    "Set Lockscreen Wallpaper",
    "system_lswallpaper"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_lswallpaper")
    override fun installHook() = SystemLockScreenHooks.SetLockscreenWallpaperHook(lpparam)
}

internal class PowerKeyFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    PowerKeyFeatureId,
    "Power Key",
    "controls_powerflash"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_powerflash")
    override fun installHook() = Controls.PowerKeyHook(lpparam)
}

internal class FingerprintHapticFailureFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FingerprintHapticFailureFeatureId,
    "Fingerprint Haptic Failure",
    "controls_fingerprintfailure"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_fingerprintfailure")
    override fun installHook() = Controls.FingerprintHapticFailureHook(lpparam)
}

internal class FingerprintScreenOnFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FingerprintScreenOnFeatureId,
    "Fingerprint Screen On",
    "controls_fingerprintscreen"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_fingerprintscreen")
    override fun installHook() = Controls.FingerprintScreenOnHook(lpparam)
}

internal class NoFingerprintWakeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoFingerprintWakeFeatureId,
    "No Fingerprint Wake",
    "controls_fingerprintwake"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_fingerprintwake")
    override fun installHook() = Controls.NoFingerprintWakeHook(lpparam)
}

internal class AppsDisableServiceFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AppsDisableServiceFeatureId,
    "Apps Disable Service",
    "various_disableapp"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_disableapp")
    override fun installHook() = Various.AppsDisableServiceHook(lpparam)
}

internal class DisableAnyNotificationBlockFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    DisableAnyNotificationBlockFeatureId,
    "Disable Any Notification Block",
    "system_disableanynotif"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_disableanynotif")
    override fun installHook() = SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam)
}

internal class AllRotationsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AllRotationsFeatureId,
    "All Rotations",
    "system_allrotations2"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_allrotations2", 1) > 1
    override fun installHook() = SystemWindowHooks.AllRotationsHook(lpparam)
}

internal class NoLightUpOnChargeFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoLightUpOnChargeFeatureId,
    "No Light Up On Charge",
    "system_nolightuponcharges"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_nolightuponcharges", 1) == 2
    override fun installHook() = SystemDisplayHooks.NoLightUpOnChargeHook(lpparam)
}

internal class SelectiveVibrationFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SelectiveVibrationFeatureId,
    "Selective Vibration",
    "system_vibration"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_vibration", 1) > 1
    override fun installHook() = SystemAudioHooks.SelectiveVibrationHook(lpparam)
}

internal class SelectiveToastsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    SelectiveToastsFeatureId,
    "Selective Toasts",
    "system_blocktoasts"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_blocktoasts", 1) > 1
    override fun installHook() = ModsSystem.SelectiveToastsHook(lpparam)
}

internal class FingerprintHapticSuccessFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    FingerprintHapticSuccessFeatureId,
    "Fingerprint Haptic Success",
    "controls_fingerprintsuccess"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("controls_fingerprintsuccess", 1) > 1
    override fun installHook() = Controls.FingerprintHapticSuccessHook(lpparam)
}

internal class VolumeMediaButtonsFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    VolumeMediaButtonsFeatureId,
    "Volume Media Buttons",
    "controls_volumemedia_up"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("controls_volumemedia_up", 0) > 0 || prefs.getStringAsInt("controls_volumemedia_down", 0) > 0
    override fun installHook() = Controls.VolumeMediaButtonsHook(lpparam)
}

internal class MultiWindowPlusFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    MultiWindowPlusFeatureId,
    "Multi Window Plus",
    "system_fw_splitscreen"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_fw_splitscreen")
    override fun installHook() = SystemWindowHooks.MultiWindowPlusHook(lpparam)
}

internal class NoFloatingWindowBlacklistFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoFloatingWindowBlacklistFeatureId,
    "No Floating Window Blacklist",
    "system_fw_noblacklist"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_fw_noblacklist")
    override fun installHook() = SystemWindowHooks.NoFloatingWindowBlacklistHook(lpparam)
}

internal class NoAccessDeviceLogsRequestFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    NoAccessDeviceLogsRequestFeatureId,
    "No Access Device Logs Request",
    "various_disable_access_devicelogs"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_disable_access_devicelogs")
    override fun installHook() = SystemSecurityHooks.NoAccessDeviceLogsRequest(lpparam)
}

internal class WallpaperScaleLevelFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    WallpaperScaleLevelFeatureId,
    "Wallpaper Scale Level",
    "system_other_wallpaper_scale"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_other_wallpaper_scale", 6) > 6
    override fun installHook() = SystemDisplayHooks.WallpaperScaleLevelHook(lpparam)
}

internal class AllowUntrustedTouchFeature(
    lpparam: XposedModuleInterface.SystemServerStartingParam
) : BaseSystemServerFeature(
    lpparam,
    AllowUntrustedTouchFeatureId,
    "Allow Untrusted Touch",
    "various_allow_untrusted_touch"
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_allow_untrusted_touch")
    override fun installHook() = SystemWindowHooks.AllowUntrustedTouchHook(lpparam)
}

/**
 * All preference-guarded features that belong in system_server.
 *
 * This keeps [SystemServerInstaller.install] a thin registration loop while still
 * making every feature individually testable and traceable.
 */
object SystemServerFeatures {
    fun all(
        lpparam: XposedModuleInterface.SystemServerStartingParam
    ): List<FeatureDefinition> = listOf(
        TempHideOverlayAppFeature(lpparam),
        OpenAppInFreeFormFeature(lpparam),
        NavBarActionsFeature(lpparam),
        PowerDoubleTapActionFeature(lpparam),
        ScreenAnimFeature(lpparam),
        AppLockTimeoutFeature(lpparam),
        ScreenDimTimeFeature(lpparam),
        ToastTimeFeature(lpparam),
        RemoveSecureFeature(lpparam),
        RemoveActStartConfirmFeature(lpparam),
        EnhancedSecurityFeature(lpparam),
        NoVersionCheckFeature(lpparam),
        OrientationLockFeature(lpparam),
        NoDuckingFeature(lpparam),
        CleanShareMenuServiceFeature(lpparam),
        CleanOpenWithMenuServiceFeature(lpparam),
        AutoBrightnessRangeFeature(lpparam),
        AutoBrightnessAfterScreenOffFeature(lpparam),
        Disable72hStrongAuthFeature(lpparam),
        AppLockFeature(lpparam),
        SkipAppLockFeature(lpparam),
        AlarmCompatServiceFeature(lpparam),
        NoCallInterruptionFeature(lpparam),
        ForceCloseFeature(lpparam),
        HideProximityWarningFeature(lpparam),
        FirstVolumePressFeature(lpparam),
        NoSignatureVerifyServiceFeature(lpparam),
        DisableSystemIntegrityFeature(lpparam),
        MuffledVibrationFeature(lpparam),
        ClearAllTasksFeature(lpparam),
        ForceDarkAllAppsFeature(lpparam),
        SetLockscreenWallpaperFeature(lpparam),
        PowerKeyFeature(lpparam),
        FingerprintHapticFailureFeature(lpparam),
        FingerprintScreenOnFeature(lpparam),
        NoFingerprintWakeFeature(lpparam),
        AppsDisableServiceFeature(lpparam),
        DisableAnyNotificationBlockFeature(lpparam),
        AllRotationsFeature(lpparam),
        NoLightUpOnChargeFeature(lpparam),
        SelectiveVibrationFeature(lpparam),
        SelectiveToastsFeature(lpparam),
        FingerprintHapticSuccessFeature(lpparam),
        VolumeMediaButtonsFeature(lpparam),
        MultiWindowPlusFeature(lpparam),
        NoFloatingWindowBlacklistFeature(lpparam),
        NoAccessDeviceLogsRequestFeature(lpparam),
        WallpaperScaleLevelFeature(lpparam),
        AllowUntrustedTouchFeature(lpparam),
    )
}
