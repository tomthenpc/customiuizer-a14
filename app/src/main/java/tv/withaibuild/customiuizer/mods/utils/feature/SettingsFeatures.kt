package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object SettingsFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        SettingsMiuizerIconFeature(lpparam, mPrefs),
        SettingsDisableAnyNotificationFeature(lpparam, mPrefs),
        SettingsNotificationImportanceFeature(lpparam, mPrefs),
        SettingsViewWifiPasswordFeature(lpparam, mPrefs),
    )
}

internal class SettingsMiuizerIconFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsMiuizerIconFeatureId,
    "Settings Miuizer Icon",
    "miuizer_settingsiconpos",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("miuizer_settingsiconpos", 1) > 0
    override fun installHook() = GlobalActions.miuizerSettingsHook(lpparam)
}

internal class SettingsDisableAnyNotificationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsDisableAnyNotificationFeatureId,
    "Settings Disable Any Notification",
    "system_disableanynotif",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_disableanynotif")
    override fun install(): FeatureInstallResult = try {
        SystemNotificationHooks.DisableAnyNotificationHook(lpparam)
        SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam)
        FeatureInstallResult.INSTALLED
    } catch (t: Throwable) {
        FeatureInstallResult.FAILED_TRANSIENT
    }
}

internal class SettingsNotificationImportanceFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsNotificationImportanceFeatureId,
    "Settings Notification Importance",
    "system_notifimportance",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_notifimportance")
    override fun installHook() = SystemNotificationHooks.NotificationImportanceHook(lpparam)
}

internal class SettingsViewWifiPasswordFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SettingsViewWifiPasswordFeatureId,
    "Settings View Wifi Password",
    "system_wifipassword",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_wifipassword")
    override fun installHook() = ModsSystem.ViewWifiPasswordHook(lpparam)
}
