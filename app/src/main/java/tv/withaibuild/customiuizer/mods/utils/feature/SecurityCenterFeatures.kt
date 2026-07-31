package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object SecurityCenterFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        SecurityCenterAppInfoFeature(lpparam, mPrefs),
        SecurityCenterAppsDisableFeature(lpparam, mPrefs),
        SecurityCenterAppsRestrictFeature(lpparam, mPrefs),
        SecurityCenterHideReportButtonFeature(lpparam, mPrefs),
        SecurityCenterScrambleAppLockPinFeature(lpparam, mPrefs),
        SecurityCenterAppsDefaultSortFeature(lpparam, mPrefs),
        SecurityCenterInterceptPermFeature(lpparam, mPrefs),
        SecurityCenterOpenByDefaultFeature(lpparam, mPrefs),
        SecurityCenterSkipSecurityScanFeature(lpparam, mPrefs),
        SecurityCenterShowTempInBatteryFeature(lpparam, mPrefs),
        SecurityCenterDisableSideBarSuggestionFeature(lpparam, mPrefs),
        SecurityCenterDisableDockSuggestFeature(lpparam, mPrefs),
        SecurityCenterAddSideBarExpandReceiverFeature(lpparam, mPrefs),
        SecurityCenterNoLowBatteryWarningFeature(lpparam, mPrefs),
        SecurityCenterPrivacyAppsLayoutFeature(lpparam, mPrefs),
        SecurityCenterPersistPrivacyThumbnailBlurFeature(lpparam, mPrefs),
    )
}

internal class SecurityCenterAppInfoFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppInfoFeatureId,
    "Security Center App Info",
    "various_appdetails",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_appdetails")
    override fun installHook() = Various.AppInfoHook(lpparam)
}

internal class SecurityCenterAppsDisableFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppsDisableFeatureId,
    "Security Center Apps Disable",
    "various_disableapp",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_disableapp")
    override fun installHook() = Various.AppsDisableHook(lpparam)
}

internal class SecurityCenterAppsRestrictFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppsRestrictFeatureId,
    "Security Center Apps Restrict",
    "various_restrictapp",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_restrictapp")
    override fun installHook() = Various.AppsRestrictHook(lpparam)
}

internal class SecurityCenterHideReportButtonFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterHideReportButtonFeatureId,
    "Security Center Hide Report Button",
    "various_hide_report_ondetails",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_hide_report_ondetails")
    override fun installHook() = Various.HideReportButtonHook(lpparam)
}

internal class SecurityCenterScrambleAppLockPinFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterScrambleAppLockPinFeatureId,
    "Security Center Scramble App Lock Pin",
    "system_applock_scramblepin",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_applock_scramblepin")
    override fun installHook() = SystemLockScreenHooks.ScrambleAppLockPINHook(lpparam)
}

internal class SecurityCenterAppsDefaultSortFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAppsDefaultSortFeatureId,
    "Security Center Apps Default Sort",
    "various_appsort",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("various_appsort", 1) > 1
    override fun installHook() = Various.AppsDefaultSortHook(lpparam)
}

internal class SecurityCenterInterceptPermFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterInterceptPermFeatureId,
    "Security Center Intercept Perm",
    "various_skip_interceptperm",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_skip_interceptperm")
    override fun installHook() = Various.InterceptPermHook(lpparam)
}

internal class SecurityCenterOpenByDefaultFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterOpenByDefaultFeatureId,
    "Security Center Open By Default",
    "various_replace_defaultopen_with_openbydefault",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_replace_defaultopen_with_openbydefault")
    override fun installHook() = Various.OpenByDefaultHook(lpparam)
}

internal class SecurityCenterSkipSecurityScanFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterSkipSecurityScanFeatureId,
    "Security Center Skip Security Scan",
    "various_skip_securityscan",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_skip_securityscan")
    override fun installHook() = Various.SkipSecurityScanHook(lpparam)
}

internal class SecurityCenterShowTempInBatteryFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterShowTempInBatteryFeatureId,
    "Security Center Show Temp In Battery",
    "various_show_battery_temperature",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_show_battery_temperature")
    override fun installHook() = Various.ShowTempInBatteryHook(lpparam)
}

internal class SecurityCenterDisableSideBarSuggestionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterDisableSideBarSuggestionFeatureId,
    "Security Center Disable Side Bar Suggestion",
    "various_disable_freeform_suggest_blacklist",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_disable_freeform_suggest_blacklist")
    override fun installHook() = SystemWindowHooks.DisableSideBarSuggestionHook(lpparam)
}

internal class SecurityCenterDisableDockSuggestFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterDisableDockSuggestFeatureId,
    "Security Center Disable Dock Suggest",
    "various_disable_dock_suggest",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_disable_dock_suggest")
    override fun installHook() = Various.DisableDockSuggestHook(lpparam)
}

internal class SecurityCenterAddSideBarExpandReceiverFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterAddSideBarExpandReceiverFeatureId,
    "Security Center Add Side Bar Expand Receiver",
    "various_enable_expand_sidebar",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = HookDiagnostics.currentProcessName == "com.miui.securitycenter:ui" && prefs.getBoolean("various_enable_expand_sidebar")
    override fun installHook() = Various.AddSideBarExpandReceiverHook(lpparam)
}

internal class SecurityCenterNoLowBatteryWarningFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterNoLowBatteryWarningFeatureId,
    "Security Center No Low Battery Warning",
    "system_hidelowbatwarn",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_hidelowbatwarn")
    override fun installHook() = Various.NoLowBatteryWarningHook()
}

internal class SecurityCenterPrivacyAppsLayoutFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterPrivacyAppsLayoutFeatureId,
    "Security Center Privacy Apps Layout",
    "various_privacyapps_column_nums4",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_privacyapps_column_nums4")
    override fun installHook() = Various.PrivacyAppsLayoutHook(lpparam)
}

internal class SecurityCenterPersistPrivacyThumbnailBlurFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    SecurityCenterPersistPrivacyThumbnailBlurFeatureId,
    "Security Center Persist Privacy Thumbnail Blur",
    "various_disable_reset_recents_privacy_blur",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_disable_reset_recents_privacy_blur")
    override fun installHook() = Various.PersistPrivacyThumbnailBlur(lpparam)
}
