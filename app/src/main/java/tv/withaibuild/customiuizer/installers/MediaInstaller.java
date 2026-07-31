package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.MainModule;
import tv.withaibuild.customiuizer.mods.LauncherAnimationHooks;
import tv.withaibuild.customiuizer.mods.System;
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in media-related app processes.
 *
 * Handles the live wallpaper, screenshot and gallery packages.
 * DexKit is loaded and the bridge is created/closed only for the screenshot
 * hook, matching the original lifecycle.
 */
public final class MediaInstaller {

    private MediaInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        String pkg = lpparam.getPackageName();

        if ("com.miui.miwallpaper".equals(pkg)) {
            if (mPrefs.getBoolean("launcher_disable_wallpaperscale")) {
                LauncherAnimationHooks.DisableUnlockWallpaperScale(lpparam);
            }
            return;
        }

        if ("com.miui.screenshot".equals(pkg)) {
            if (mPrefs.getBoolean("system_screenshot")) {
                try {
                    MainModule.loadDexKit();
                    XposedHelpers.createBridge(lpparam.getApplicationInfo().sourceDir);
                    System.ScreenshotConfigHook(lpparam);
                } catch (Throwable t) {
                    XposedHelpers.log(t);
                } finally {
                    XposedHelpers.closeBridge();
                }
            }
            return;
        }

        if ("com.miui.gallery".equals(pkg)) {
            int folder = mPrefs.getStringAsInt("system_gallery_screenshots_path", 1);
            if (folder > 1) {
                System.GalleryScreenshotPathHook(lpparam);
            }
        }
    }
}
