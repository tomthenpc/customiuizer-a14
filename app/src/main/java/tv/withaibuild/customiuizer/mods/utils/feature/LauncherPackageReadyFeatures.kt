package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Launcher
import tv.withaibuild.customiuizer.mods.LauncherFolderHooks
import tv.withaibuild.customiuizer.mods.LauncherIconHooks
import tv.withaibuild.customiuizer.mods.LauncherLayoutHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object LauncherPackageReadyFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        LauncherFolderColumnsResFeature(lpparam, mPrefs),
        LauncherHorizontalSpacingFeature(lpparam, mPrefs),
        LauncherIndicatorHeightFeature(lpparam, mPrefs),
        LauncherIndicatorMarginTopFeature(lpparam, mPrefs),
        LauncherUnlockGridsFeature(lpparam, mPrefs),
        LauncherDockTitlesFeature(lpparam, mPrefs),
        LauncherDisableLogFeature(lpparam, mPrefs),
        LauncherWorkspaceCellPaddingTopFeature(lpparam, mPrefs),
        LauncherDockMarginTopFeature(lpparam, mPrefs),
        LauncherDockMarginBottomFeature(lpparam, mPrefs),
        LauncherDockHeightFeature(lpparam, mPrefs),
        LauncherPrivacyAppsGestFeature(lpparam, mPrefs),
    )
}

internal class LauncherFolderColumnsResFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherFolderColumnsResFeatureId,
    "Launcher Folder Columns Res",
    "launcher_folder_cols",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_folder_cols", 1) > 1
    override fun installHook() = LauncherFolderHooks.FolderColumnsRes(mPrefs.getInt("launcher_folder_cols", 1))
}

internal class LauncherHorizontalSpacingFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherHorizontalSpacingFeatureId,
    "Launcher Horizontal Spacing",
    "launcher_horizmargin",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_horizmargin", 0) > 0
    override fun installHook() = LauncherLayoutHooks.HorizontalSpacingRes()
}

internal class LauncherIndicatorHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherIndicatorHeightFeatureId,
    "Launcher Indicator Height",
    "launcher_indicatorheight",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_indicatorheight", 9) > 9
    override fun installHook() = LauncherLayoutHooks.IndicatorHeightRes()
}

internal class LauncherIndicatorMarginTopFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherIndicatorMarginTopFeatureId,
    "Launcher Indicator Margin Top",
    "launcher_indicator_topmargin",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_indicator_topmargin", 0) > 0
    override fun installHook() = LauncherLayoutHooks.IndicatorMarginTopHook(lpparam)
}

internal class LauncherUnlockGridsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherUnlockGridsFeatureId,
    "Launcher Unlock Grids",
    "launcher_unlockgrids",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_unlockgrids")
    override fun install(): FeatureInstallResult = try {
        LauncherLayoutHooks.UnlockGridsRes()
        LauncherLayoutHooks.UnlockGridsHook(lpparam)
        FeatureInstallResult.Installed
    } catch (t: Throwable) {
        FeatureInstallResult.FailedTransient(t.javaClass.name)
    }
}

internal class LauncherDockTitlesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockTitlesFeatureId,
    "Launcher Dock Titles",
    "launcher_docktitles",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_docktitles")
    override fun installHook() = LauncherIconHooks.ShowHotseatTitlesHook(lpparam)
}

internal class LauncherDisableLogFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDisableLogFeatureId,
    "Launcher Disable Log",
    "launcher_disable_log",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_disable_log")
    override fun installHook() = Launcher.DisableLauncherLogHook(lpparam)
}

internal class LauncherWorkspaceCellPaddingTopFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherWorkspaceCellPaddingTopFeatureId,
    "Launcher Workspace Cell Padding Top",
    "launcher_topmargin",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_topmargin", 0) > 0
    override fun installHook() = LauncherLayoutHooks.WorkspaceCellPaddingTopHook(lpparam)
}

internal class LauncherDockMarginTopFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockMarginTopFeatureId,
    "Launcher Dock Margin Top",
    "launcher_dock_topmargin",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_dock_topmargin", 0) > 0
    override fun installHook() = LauncherLayoutHooks.DockMarginTopHook(lpparam)
}

internal class LauncherDockMarginBottomFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockMarginBottomFeatureId,
    "Launcher Dock Margin Bottom",
    "launcher_dock_bottommargin",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_dock_bottommargin", 0) > 0
    override fun installHook() = LauncherLayoutHooks.DockMarginBottomHook(lpparam)
}

internal class LauncherDockHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherDockHeightFeatureId,
    "Launcher Dock Height",
    "launcher_dock_height",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_dock_height", 60) > 60
    override fun installHook() = LauncherLayoutHooks.DockHeightHook(lpparam)
}

internal class LauncherPrivacyAppsGestFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    LauncherPrivacyAppsGestFeatureId,
    "Launcher Privacy Apps Gest",
    "launcher_privacyapps_gest",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_privacyapps_gest")
    override fun installHook() = Launcher.setupLauncher(lpparam)
}
