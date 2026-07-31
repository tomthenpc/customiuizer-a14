package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.Controls;
import tv.withaibuild.customiuizer.mods.Various;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in third-party input method processes.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the input-method-specific hooks to a dedicated, stateless class.
 * Package filtering, the first-package guard and the onPackageReady diagnostic summary
 * stay in MainModule.
 */
public final class InputMethodInstaller {

    private InputMethodInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        String pkg = lpparam.getPackageName();

        if (mPrefs.getBoolean("controls_volumecursor")) Controls.VolumeCursorHook(lpparam);

        if (mPrefs.getBoolean("controls_nonavbar_fix_inputmethod")
            && mPrefs.getBoolean("controls_nonavbar")) {
            Various.FixInputMethodBottomMarginHook(lpparam);
        }

        if (pkg.startsWith("com.google.android.inputmethod")) {
            if (mPrefs.getInt("various_gboardpadding_port", 0) > 0
                || mPrefs.getInt("various_gboardpadding_land", 0) > 0) {
                Various.GboardPaddingHook(lpparam);
            }
        }
    }
}
