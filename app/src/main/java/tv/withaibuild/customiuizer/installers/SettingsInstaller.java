package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.GlobalActions;
import tv.withaibuild.customiuizer.mods.System;
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the Settings process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the package-specific Settings hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
public final class SettingsInstaller {

    private SettingsInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        if (mPrefs.getStringAsInt("miuizer_settingsiconpos", 1) > 0) {
            GlobalActions.miuizerSettingsHook(lpparam);
        }
        if (mPrefs.getBoolean("system_disableanynotif")) {
            SystemNotificationHooks.DisableAnyNotificationHook(lpparam);
            SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam);
        }
        if (mPrefs.getBoolean("system_notifimportance")) {
            SystemNotificationHooks.NotificationImportanceHook(lpparam);
        }
        if (mPrefs.getBoolean("system_wifipassword")) {
            System.ViewWifiPasswordHook(lpparam);
        }
    }
}
