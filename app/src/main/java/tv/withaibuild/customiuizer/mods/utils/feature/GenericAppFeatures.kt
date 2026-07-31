package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.installers.LauncherInstaller
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.SystemStatusBarBackgroundHooks
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object GenericAppFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        LauncherPostAttachFeature(lpparam, mPrefs),
        GenericAppStatusBarBackgroundFeature(lpparam, mPrefs),
        GenericAppNoOverscrollFeature(lpparam, mPrefs),
        GenericAppVolumeMediaPlayerFeature(lpparam, mPrefs),
    )
}

internal class LauncherPostAttachFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherPostAttachFeatureId,
    "Launcher Post Attach",
    null,
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = packageName == "com.miui.home"
    override fun installHook() = LauncherInstaller.handleLoadLauncher(lpparam, mPrefs)
}

internal class GenericAppStatusBarBackgroundFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    GenericAppStatusBarBackgroundFeatureId,
    "Generic App Status Bar Background",
    "system_statusbarcolor",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_statusbarcolor") && prefs.getStringSet("system_statusbarcolor_apps").contains(packageName)
    override fun install(): FeatureInstallResult = try {
        SystemStatusBarBackgroundHooks.StatusBarBackgroundCompatHook(lpparam)
        SystemStatusBarBackgroundHooks.StatusBarBackgroundHook(lpparam)
        FeatureInstallResult.Installed
    } catch (t: Throwable) {
        FeatureInstallResult.FailedTransient(t.javaClass.name)
    }
}

internal class GenericAppNoOverscrollFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    GenericAppNoOverscrollFeatureId,
    "Generic App No Overscroll",
    "system_nooverscroll",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_nooverscroll") && prefs.getStringSet("system_nooverscroll_apps").contains(packageName)
    override fun installHook() = SystemWindowHooks.NoOverscrollAppHook(lpparam)
}

internal class GenericAppVolumeMediaPlayerFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    GenericAppVolumeMediaPlayerFeatureId,
    "Generic App Volume Media Player",
    "controls_volumemedia_up",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = (prefs.getStringAsInt("controls_volumemedia_up", 0) > 0 || prefs.getStringAsInt("controls_volumemedia_down", 0) > 0) && prefs.getStringSet("controls_mediaplayer_apps").contains(packageName)
    override fun installHook() = Controls.VolumeMediaPlayerHook(lpparam)
}
