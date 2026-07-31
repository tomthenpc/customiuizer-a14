package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition;
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry;
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget;
import tv.withaibuild.customiuizer.mods.utils.InstallPhase;
import tv.withaibuild.customiuizer.mods.utils.feature.SystemUiFeatures;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the SystemUI process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the long list of package-specific SystemUI hooks to a dedicated, stateless class.
 * Base hooks (SystemUIInitializer.init, fast-reboot receiver, status-bar setup and the 10-second
 * restart guard) stay in MainModule so the installer receives an already-validated load point.
 */
public final class SystemUiInstaller {

    private SystemUiInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        FeatureInstallRegistry registry = new FeatureInstallRegistry();

        for (FeatureDefinition feature : SystemUiFeatures.all(lpparam, mPrefs)) {
            registry.register(feature);
        }

        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, mPrefs);
    }
}