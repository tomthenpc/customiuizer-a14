package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.MainModule;
import tv.withaibuild.customiuizer.mods.SystemShareMenuHooks;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the {@code android} process.
 *
 * Keeps the share-sheet and open-with chooser cleanup, plus the all-rotations
 * theme value replacement, out of {@link tv.withaibuild.customiuizer.MainModule}.
 */
public final class AndroidPackageInstaller {

    private AndroidPackageInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        if (mPrefs.getBoolean("system_cleanshare")) {
            SystemShareMenuHooks.CleanShareMenuHook(lpparam);
        }

        if (mPrefs.getBoolean("system_cleanopenwith")) {
            SystemShareMenuHooks.CleanOpenWithMenuHook(lpparam);
        }

        int allRotations = mPrefs.getStringAsInt("system_allrotations2", 1);
        if (allRotations > 1) {
            MainModule.resHooks.setThemeValueReplacement(
                "android",
                "bool",
                "config_allowAllRotations",
                allRotations == 2
            );
        }
    }
}
