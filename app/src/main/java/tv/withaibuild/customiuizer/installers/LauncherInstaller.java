package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition;
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry;
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget;
import tv.withaibuild.customiuizer.mods.utils.InstallPhase;
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherPackageReadyFeatures;
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherPostAttachFeatures;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the Launcher process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the package-specific hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
public final class LauncherInstaller {

    private LauncherInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        FeatureInstallRegistry registry = new FeatureInstallRegistry();

        for (FeatureDefinition feature : LauncherPackageReadyFeatures.all(lpparam, mPrefs)) {
            registry.register(feature);
        }

        registry.installAll(FeatureTarget.LAUNCHER, InstallPhase.PACKAGE_READY, mPrefs);
    }

    public static void handleLoadLauncher(PackageReadyParam lpparam, PrefMap mPrefs) {
        FeatureInstallRegistry registry = new FeatureInstallRegistry();

        for (FeatureDefinition feature : LauncherPostAttachFeatures.all(lpparam, mPrefs)) {
            registry.register(feature);
        }

        registry.installAll(FeatureTarget.LAUNCHER, InstallPhase.APPLICATION_ATTACHED, mPrefs);
    }
}
