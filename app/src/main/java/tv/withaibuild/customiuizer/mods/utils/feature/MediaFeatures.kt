package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.LauncherAnimationHooks
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

object MediaFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        MediaDisableUnlockWallpaperScaleFeature(lpparam, mPrefs),
        MediaScreenshotConfigFeature(lpparam, mPrefs),
        MediaGalleryScreenshotPathFeature(lpparam, mPrefs),
    )
}

internal class MediaDisableUnlockWallpaperScaleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    MediaDisableUnlockWallpaperScaleFeatureId,
    "Media Disable Unlock Wallpaper Scale",
    "launcher_disable_wallpaperscale",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = packageName == "com.miui.miwallpaper" && prefs.getBoolean("launcher_disable_wallpaperscale")
    override fun installHook() = LauncherAnimationHooks.DisableUnlockWallpaperScale(lpparam)
}

internal class MediaScreenshotConfigFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    MediaScreenshotConfigFeatureId,
    "Media Screenshot Config",
    "system_screenshot",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = packageName == "com.miui.screenshot" && prefs.getBoolean("system_screenshot")
    override fun install(): FeatureInstallResult = try {
        MainModule.loadDexKit()
        XposedHelpers.createBridge(lpparam.applicationInfo.sourceDir)
        ModsSystem.ScreenshotConfigHook(lpparam)
        FeatureInstallResult.INSTALLED
    } catch (t: Throwable) {
        XposedHelpers.log(t)
        FeatureInstallResult.FAILED_TRANSIENT
    } finally {
        XposedHelpers.closeBridge()
    }
}

internal class MediaGalleryScreenshotPathFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    MediaGalleryScreenshotPathFeatureId,
    "Media Gallery Screenshot Path",
    "system_gallery_screenshots_path",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = packageName == "com.miui.gallery" && prefs.getStringAsInt("system_gallery_screenshots_path", 1) > 1
    override fun installHook() = ModsSystem.GalleryScreenshotPathHook(lpparam)
}
