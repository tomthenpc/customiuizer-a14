package tv.withaibuild.customiuizer.installers;

import android.app.Application;
import android.content.Context;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.utils.FeatureSpec;
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry;
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget;
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics;
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback;
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook;
import tv.withaibuild.customiuizer.mods.utils.InstallPhase;
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper;
import tv.withaibuild.customiuizer.mods.utils.feature.GenericAppFeatures;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for the shared {@link Application#attach(Context)} hook.
 *
 * This is the single place where launcher post-attach hooks, status-bar color,
 * overscroll suppression and media-player volume hooks are installed, once the
 * target application has a live context.
 */
public final class GenericAppInstaller {

    private GenericAppInstaller() {}

    public static void installPostAttach(PackageReadyParam lpparam, PrefMap mPrefs, boolean isLauncherPkg, boolean isStatusBarColor, boolean isNoOverscroll, boolean controlMedia) {
        ModuleHelper.findAndHookMethod(
            Application.class,
            "attach",
            Context.class,
            new MethodHook() {
                @Override
                protected void after(AfterHookCallback param) throws Throwable {
                    FeatureInstallRegistry registry = new FeatureInstallRegistry();
                    for (FeatureSpec feature : GenericAppFeatures.selected(
                        lpparam,
                        mPrefs,
                        isLauncherPkg,
                        isStatusBarColor,
                        isNoOverscroll,
                        controlMedia
                    )) {
                        registry.register(feature);
                    }

                    if (isLauncherPkg) {
                        registry.installAll(FeatureTarget.LAUNCHER, InstallPhase.APPLICATION_ATTACHED, mPrefs);
                    }

                    if (isStatusBarColor || isNoOverscroll || controlMedia) {
                        registry.installAll(FeatureTarget.ANY, InstallPhase.APPLICATION_ATTACHED, mPrefs);
                    }

                    HookDiagnostics.printSummaryForStage("post-attach");
                }
            }
        );
    }
}
