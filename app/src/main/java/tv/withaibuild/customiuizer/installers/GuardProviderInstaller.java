package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.MainModule;
import tv.withaibuild.customiuizer.mods.Various;
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the Guard Provider (com.miui.guardprovider) process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the package-specific Guard Provider hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
public final class GuardProviderInstaller {

    private GuardProviderInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        if (mPrefs.getBoolean("various_disable_defraud_apps_detect")) {
            try {
                MainModule.loadDexKit();
                XposedHelpers.createBridge(lpparam.getApplicationInfo().sourceDir);
                Various.DisableDefraudAppsCheck(lpparam);
            } catch (Throwable t) {
                XposedHelpers.log(t);
            } finally {
                XposedHelpers.closeBridge();
            }
        }
    }
}
