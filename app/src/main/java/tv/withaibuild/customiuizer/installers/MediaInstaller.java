package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition;
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry;
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget;
import tv.withaibuild.customiuizer.mods.utils.InstallPhase;
import tv.withaibuild.customiuizer.mods.utils.feature.MediaFeatures;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the Media process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the package-specific hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
public final class MediaInstaller {

    private MediaInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        FeatureInstallRegistry registry = new FeatureInstallRegistry();

        for (FeatureDefinition feature : MediaFeatures.all(lpparam, mPrefs)) {
            registry.register(feature);
        }

        registry.installAll(FeatureTarget.SYSTEM_PACKAGE, InstallPhase.PACKAGE_READY, mPrefs);
    }
}
