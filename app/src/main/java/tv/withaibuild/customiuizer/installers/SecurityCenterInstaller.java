package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks;
import tv.withaibuild.customiuizer.mods.SystemWindowHooks;
import tv.withaibuild.customiuizer.mods.Various;
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the Security Center process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the package-specific Security Center hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
public final class SecurityCenterInstaller {

    private SecurityCenterInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        if (mPrefs.getBoolean("various_appdetails")) Various.AppInfoHook(lpparam);
        if (mPrefs.getBoolean("various_disableapp")) Various.AppsDisableHook(lpparam);
        if (mPrefs.getBoolean("various_restrictapp")) Various.AppsRestrictHook(lpparam);
        if (mPrefs.getBoolean("various_hide_report_ondetails")) Various.HideReportButtonHook(lpparam);
        if (mPrefs.getBoolean("system_applock_scramblepin")) SystemLockScreenHooks.ScrambleAppLockPINHook(lpparam);
        if (mPrefs.getStringAsInt("various_appsort", 1) > 1) Various.AppsDefaultSortHook(lpparam);
        if (mPrefs.getBoolean("various_skip_interceptperm")) Various.InterceptPermHook(lpparam);
        if (mPrefs.getBoolean("various_replace_defaultopen_with_openbydefault")) Various.OpenByDefaultHook(lpparam);
        if (mPrefs.getBoolean("various_skip_securityscan")) Various.SkipSecurityScanHook(lpparam);
        if (mPrefs.getBoolean("various_show_battery_temperature")) Various.ShowTempInBatteryHook(lpparam);
        if (mPrefs.getBoolean("various_disable_freeform_suggest_blacklist")) SystemWindowHooks.DisableSideBarSuggestionHook(lpparam);
        if (mPrefs.getBoolean("various_disable_dock_suggest")) Various.DisableDockSuggestHook(lpparam);
        if ("com.miui.securitycenter:ui".equals(HookDiagnostics.currentProcessName)
            && mPrefs.getBoolean("various_enable_expand_sidebar")) {
            Various.AddSideBarExpandReceiverHook(lpparam);
        }
        if (mPrefs.getBoolean("system_hidelowbatwarn")) {
            Various.NoLowBatteryWarningHook();
        }
        if (mPrefs.getBoolean("various_privacyapps_column_nums4")) {
            Various.PrivacyAppsLayoutHook(lpparam);
        }
        if (mPrefs.getBoolean("various_disable_reset_recents_privacy_blur")) {
            Various.PersistPrivacyThumbnailBlur(lpparam);
        }
    }
}
