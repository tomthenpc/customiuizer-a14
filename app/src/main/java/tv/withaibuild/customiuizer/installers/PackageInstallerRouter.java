package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.Various;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the MIUI Package Installer (com.miui.packageinstaller) process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the package-specific Package Installer hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
public final class PackageInstallerRouter {

    private PackageInstallerRouter() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        if (mPrefs.getBoolean("various_miuiinstaller")) Various.MiuiPackageInstallerHook(lpparam);
        if (mPrefs.getBoolean("various_installappinfo")) Various.AppInfoDuringMiuiInstallHook(lpparam);
        if (mPrefs.getBoolean("various_installer_purify")) Various.PurePackageInstallerHook(lpparam);
    }
}
