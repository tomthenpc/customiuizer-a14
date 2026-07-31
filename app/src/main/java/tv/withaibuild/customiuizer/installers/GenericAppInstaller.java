package tv.withaibuild.customiuizer.installers;

import android.app.Application;
import android.content.Context;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.Controls;
import tv.withaibuild.customiuizer.mods.SystemStatusBarBackgroundHooks;
import tv.withaibuild.customiuizer.mods.SystemWindowHooks;
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics;
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback;
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook;
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper;
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
                    if (isLauncherPkg) {
                        LauncherInstaller.handleLoadLauncher(lpparam, mPrefs);
                    }

                    if (isStatusBarColor) {
                        SystemStatusBarBackgroundHooks.StatusBarBackgroundCompatHook(lpparam);
                        SystemStatusBarBackgroundHooks.StatusBarBackgroundHook(lpparam);
                    }

                    if (isNoOverscroll) {
                        SystemWindowHooks.NoOverscrollAppHook(lpparam);
                    }

                    if (controlMedia) {
                        Controls.VolumeMediaPlayerHook(lpparam);
                    }

                    HookDiagnostics.printSummaryForStage("post-attach");
                }
            }
        );
    }
}
