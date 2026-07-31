package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.Launcher
import tv.withaibuild.customiuizer.mods.LauncherAnimationHooks
import tv.withaibuild.customiuizer.mods.LauncherFolderHooks
import tv.withaibuild.customiuizer.mods.LauncherGestureHooks
import tv.withaibuild.customiuizer.mods.LauncherIconHooks
import tv.withaibuild.customiuizer.mods.LauncherLayoutHooks
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.SystemWindowHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object LauncherPostAttachFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        LauncherHomescreenSwipesFeature(lpparam, mPrefs),
        LauncherHotSeatSwipesFeature(lpparam, mPrefs),
        LauncherShakeFeature(lpparam, mPrefs),
        LauncherDoubleTapFeature(lpparam, mPrefs),
        LauncherPinchFeature(lpparam, mPrefs),
        LauncherFolderColumnsFeature(lpparam, mPrefs),
        LauncherIconScaleFeature(lpparam, mPrefs),
        LauncherTitleFontSizeFeature(lpparam, mPrefs),
        LauncherTitleTopMarginFeature(lpparam, mPrefs),
        LauncherNoClockHideFeature(lpparam, mPrefs),
        LauncherRenameShortcutsFeature(lpparam, mPrefs),
        LauncherTitleShadowFeature(lpparam, mPrefs),
        LauncherHideNavBarFeature(lpparam, mPrefs),
        LauncherInfiniteScrollFeature(lpparam, mPrefs),
        LauncherHideTitlesFeature(lpparam, mPrefs),
        LauncherFixAppInfoLaunchFeature(lpparam, mPrefs),
        LauncherNoWidgetOnlyFeature(lpparam, mPrefs),
        LauncherReversePortraitFeature(lpparam, mPrefs),
        LauncherMaxHotseatIconsFeature(lpparam, mPrefs),
        LauncherCloseFolderOnLaunchFeature(lpparam, mPrefs),
        LauncherRecentsBlurFeature(lpparam, mPrefs),
        LauncherBackGestureAreaHeightFeature(lpparam, mPrefs),
        LauncherBackGestureAreaWidthFeature(lpparam, mPrefs),
        LauncherFsgesturesFeature(lpparam, mPrefs),
        LauncherHideMemoryCleanFeature(lpparam, mPrefs),
        LauncherDisableWallpaperScaleFeature(lpparam, mPrefs),
        LauncherHideStatusBarInRecentsFeature(lpparam, mPrefs),
        LauncherMultiWindowPlusFeature(lpparam, mPrefs),
        LauncherFixAnimFeature(lpparam, mPrefs),
        LauncherHideSeekPointsFeature(lpparam, mPrefs),
        LauncherPrivacyFolderFeature(lpparam, mPrefs),
        LauncherHideFromRecentsFeature(lpparam, mPrefs),
        LauncherFolderBlurFeature(lpparam, mPrefs),
        LauncherNoUnlockAnimationFeature(lpparam, mPrefs),
        LauncherNoZoomAnimationFeature(lpparam, mPrefs),
        LauncherUseOldLaunchAnimationFeature(lpparam, mPrefs),
        LauncherCloseDrawerOnLaunchFeature(lpparam, mPrefs),
        LauncherHorizontalWidgetSpacingFeature(lpparam, mPrefs),
        LauncherAssistGestureActionFeature(lpparam, mPrefs),
        LauncherSwipeAndStopActionFeature(lpparam, mPrefs),
        LauncherCloseOnLaunchFeature(lpparam, mPrefs),
        LauncherResizableWidgetsFeature(lpparam, mPrefs),
        LauncherWallpaperColorModeFeature(lpparam, mPrefs),
    )
}

internal class LauncherHomescreenSwipesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHomescreenSwipesFeatureId,
    "Launcher Homescreen Swipes",
    "launcher_swipedown_action",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_swipedown_action", 1) != 1 || prefs.getInt("launcher_swipeup_action", 1) != 1 || prefs.getInt("launcher_swipedown2_action", 1) != 1 || prefs.getInt("launcher_swipeup2_action", 1) != 1
    override fun installHook() = LauncherGestureHooks.HomescreenSwipesHook(lpparam)
}

internal class LauncherHotSeatSwipesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHotSeatSwipesFeatureId,
    "Launcher Hot Seat Swipes",
    "launcher_swipeleft_action",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_swipeleft_action", 1) != 1 || prefs.getInt("launcher_swiperight_action", 1) != 1
    override fun installHook() = LauncherGestureHooks.HotSeatSwipesHook(lpparam)
}

internal class LauncherShakeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherShakeFeatureId,
    "Launcher Shake",
    "launcher_shake_action",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_shake_action", 1) != 1
    override fun installHook() = LauncherGestureHooks.ShakeHook(lpparam)
}

internal class LauncherDoubleTapFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherDoubleTapFeatureId,
    "Launcher Double Tap",
    "launcher_doubletap_action",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_doubletap_action", 1) != 1
    override fun installHook() = LauncherGestureHooks.LauncherDoubleTapHook(lpparam)
}

internal class LauncherPinchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherPinchFeatureId,
    "Launcher Pinch",
    "launcher_pinch_action",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_pinch_action", 1) != 1
    override fun installHook() = LauncherGestureHooks.LauncherPinchHook(lpparam)
}

internal class LauncherFolderColumnsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFolderColumnsFeatureId,
    "Launcher Folder Columns",
    "launcher_folder_cols",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_folder_cols", 1) > 1
    override fun installHook() = LauncherFolderHooks.FolderColumnsHook(lpparam)
}

internal class LauncherIconScaleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherIconScaleFeatureId,
    "Launcher Icon Scale",
    "launcher_iconscale",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_iconscale", 45) > 45
    override fun installHook() = LauncherIconHooks.IconScaleHook(lpparam)
}

internal class LauncherTitleFontSizeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherTitleFontSizeFeatureId,
    "Launcher Title Font Size",
    "launcher_titlefontsize",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_titlefontsize", 5) > 5
    override fun installHook() = LauncherIconHooks.TitleFontSizeHook(lpparam)
}

internal class LauncherTitleTopMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherTitleTopMarginFeatureId,
    "Launcher Title Top Margin",
    "launcher_titletopmargin",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_titletopmargin", 0) > 0
    override fun installHook() = LauncherIconHooks.TitleTopMarginHook(lpparam)
}

internal class LauncherNoClockHideFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoClockHideFeatureId,
    "Launcher No Clock Hide",
    "launcher_noclockhide",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_noclockhide")
    override fun installHook() = LauncherIconHooks.NoClockHideHook(lpparam)
}

internal class LauncherRenameShortcutsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherRenameShortcutsFeatureId,
    "Launcher Rename Shortcuts",
    "launcher_renameapps",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_renameapps")
    override fun installHook() = LauncherIconHooks.RenameShortcutsHook(lpparam)
}

internal class LauncherTitleShadowFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherTitleShadowFeatureId,
    "Launcher Title Shadow",
    "launcher_darkershadow",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_darkershadow")
    override fun installHook() = LauncherIconHooks.TitleShadowHook(lpparam)
}

internal class LauncherHideNavBarFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideNavBarFeatureId,
    "Launcher Hide Nav Bar",
    "controls_nonavbar",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_nonavbar")
    override fun installHook() = Launcher.HideNavBarHook(lpparam)
}

internal class LauncherInfiniteScrollFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherInfiniteScrollFeatureId,
    "Launcher Infinite Scroll",
    "launcher_infinitescroll",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_infinitescroll")
    override fun installHook() = LauncherLayoutHooks.InfiniteScrollHook(lpparam)
}

internal class LauncherHideTitlesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideTitlesFeatureId,
    "Launcher Hide Titles",
    "launcher_hidetitles",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_hidetitles")
    override fun installHook() = LauncherIconHooks.HideTitlesHook(lpparam)
}

internal class LauncherFixAppInfoLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFixAppInfoLaunchFeatureId,
    "Launcher Fix App Info Launch",
    "launcher_fixlaunch",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_fixlaunch")
    override fun installHook() = Launcher.FixAppInfoLaunchHook(lpparam)
}

internal class LauncherNoWidgetOnlyFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoWidgetOnlyFeatureId,
    "Launcher No Widget Only",
    "launcher_nowidgetonly",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_nowidgetonly")
    override fun installHook() = LauncherLayoutHooks.NoWidgetOnlyHook(lpparam)
}

internal class LauncherReversePortraitFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherReversePortraitFeatureId,
    "Launcher Reverse Portrait",
    "launcher_sensorportrait",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_sensorportrait")
    override fun installHook() = Launcher.ReverseLauncherPortraitHook(lpparam)
}

internal class LauncherMaxHotseatIconsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherMaxHotseatIconsFeatureId,
    "Launcher Max Hotseat Icons",
    "launcher_unlockhotseat",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_unlockhotseat")
    override fun installHook() = LauncherLayoutHooks.MaxHotseatIconsCountHook(lpparam)
}

internal class LauncherCloseFolderOnLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherCloseFolderOnLaunchFeatureId,
    "Launcher Close Folder On Launch",
    "launcher_closefolders",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("launcher_closefolders", 1) > 1
    override fun installHook() = LauncherFolderHooks.CloseFolderOnLaunchHook(lpparam)
}

internal class LauncherRecentsBlurFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherRecentsBlurFeatureId,
    "Launcher Recents Blur",
    "system_recents_blur",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_recents_blur", 100) < 100
    override fun installHook() = Launcher.RecentsBlurRatioHook(lpparam)
}

internal class LauncherBackGestureAreaHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherBackGestureAreaHeightFeatureId,
    "Launcher Back Gesture Area Height",
    "controls_fsg_coverage",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("controls_fsg_coverage", 60) != 60
    override fun installHook() = Controls.BackGestureAreaHeightHook(lpparam)
}

internal class LauncherBackGestureAreaWidthFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherBackGestureAreaWidthFeatureId,
    "Launcher Back Gesture Area Width",
    "controls_fsg_width",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("controls_fsg_width", 100) > 100
    override fun installHook() = Controls.BackGestureAreaWidthHook(lpparam)
}

internal class LauncherFsgesturesFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFsgesturesFeatureId,
    "Launcher Fsgestures",
    "controls_fsg_horiz",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_fsg_horiz")
    override fun installHook() = LauncherGestureHooks.FSGesturesHook(lpparam)
}

internal class LauncherHideMemoryCleanFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideMemoryCleanFeatureId,
    "Launcher Hide Memory Clean",
    "system_removecleaner",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_removecleaner")
    override fun installHook() = ModsSystem.HideMemoryCleanHook(lpparam, true)
}

internal class LauncherDisableWallpaperScaleFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherDisableWallpaperScaleFeatureId,
    "Launcher Disable Wallpaper Scale",
    "system_recents_disable_wallpaperscale",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_recents_disable_wallpaperscale") || prefs.getBoolean("launcher_disable_wallpaperscale")
    override fun installHook() = LauncherAnimationHooks.DisableLauncherWallpaperScale(lpparam)
}

internal class LauncherHideStatusBarInRecentsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideStatusBarInRecentsFeatureId,
    "Launcher Hide Status Bar In Recents",
    "system_recents_hide_statusbar",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_recents_hide_statusbar")
    override fun installHook() = Launcher.HideStatusBarInRecentsHook(lpparam)
}

internal class LauncherMultiWindowPlusFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherMultiWindowPlusFeatureId,
    "Launcher Multi Window Plus",
    "system_fw_splitscreen",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_fw_splitscreen")
    override fun installHook() = SystemWindowHooks.MultiWindowPlusHook(lpparam)
}

internal class LauncherFixAnimFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFixAnimFeatureId,
    "Launcher Fix Anim",
    "launcher_fixanim",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_fixanim")
    override fun installHook() = LauncherAnimationHooks.FixAnimHook(lpparam)
}

internal class LauncherHideSeekPointsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideSeekPointsFeatureId,
    "Launcher Hide Seek Points",
    "launcher_hideseekpoints",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_hideseekpoints")
    override fun installHook() = LauncherLayoutHooks.HideSeekPointsHook(lpparam)
}

internal class LauncherPrivacyFolderFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherPrivacyFolderFeatureId,
    "Launcher Privacy Folder",
    "launcher_privacyapps_gest",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_privacyapps_gest") || prefs.getInt("launcher_spread_action", 1) != 1
    override fun installHook() = LauncherFolderHooks.PrivacyFolderHook(lpparam)
}

internal class LauncherHideFromRecentsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHideFromRecentsFeatureId,
    "Launcher Hide From Recents",
    "system_hidefromrecents",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_hidefromrecents")
    override fun installHook() = Launcher.HideFromRecentsHook(lpparam)
}

internal class LauncherFolderBlurFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherFolderBlurFeatureId,
    "Launcher Folder Blur",
    "launcher_folderblur_opacity",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_folderblur_opacity", 0) > 0
    override fun installHook() = LauncherFolderHooks.FolderBlurHook(lpparam)
}

internal class LauncherNoUnlockAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoUnlockAnimationFeatureId,
    "Launcher No Unlock Animation",
    "launcher_nounlockanim",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_nounlockanim")
    override fun installHook() = LauncherAnimationHooks.NoUnlockAnimationHook(lpparam)
}

internal class LauncherNoZoomAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherNoZoomAnimationFeatureId,
    "Launcher No Zoom Animation",
    "launcher_nozoomanim",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_nozoomanim")
    override fun installHook() = LauncherAnimationHooks.NoZoomAnimationHook(lpparam)
}

internal class LauncherUseOldLaunchAnimationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherUseOldLaunchAnimationFeatureId,
    "Launcher Use Old Launch Animation",
    "launcher_oldlaunchanim",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_oldlaunchanim")
    override fun installHook() = LauncherAnimationHooks.UseOldLaunchAnimationHook(lpparam)
}

internal class LauncherCloseDrawerOnLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherCloseDrawerOnLaunchFeatureId,
    "Launcher Close Drawer On Launch",
    "launcher_closedrawer",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("launcher_closedrawer")
    override fun installHook() = LauncherFolderHooks.CloseDrawerOnLaunchHook(lpparam)
}

internal class LauncherHorizontalWidgetSpacingFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherHorizontalWidgetSpacingFeatureId,
    "Launcher Horizontal Widget Spacing",
    "launcher_horizwidgetmargin",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("launcher_horizwidgetmargin", 0) > 0
    override fun installHook() = LauncherLayoutHooks.HorizontalWidgetSpacingHook(lpparam)
}

internal class LauncherAssistGestureActionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherAssistGestureActionFeatureId,
    "Launcher Assist Gesture Action",
    "controls_fsg_assist_left_action",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("controls_fsg_assist_left_action", 1) > 1 || prefs.getInt("controls_fsg_assist_right_action", 1) > 1
    override fun installHook() = LauncherGestureHooks.AssistGestureActionHook(lpparam)
}

internal class LauncherSwipeAndStopActionFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherSwipeAndStopActionFeatureId,
    "Launcher Swipe And Stop Action",
    "controls_fsg_swipeandstop_action",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("controls_fsg_swipeandstop_action", 1) > 1
    override fun installHook() = LauncherGestureHooks.SwipeAndStopActionHook(lpparam)
}

internal class LauncherCloseOnLaunchFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherCloseOnLaunchFeatureId,
    "Launcher Close On Launch",
    "launcher_closefolders",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("launcher_closefolders", 1) > 1 || prefs.getBoolean("launcher_closedrawer")
    override fun installHook() = LauncherFolderHooks.CloseFolderOrDrawerOnLaunchShortcutMenuHook(lpparam)
}

internal class LauncherResizableWidgetsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherResizableWidgetsFeatureId,
    "Launcher Resizable Widgets",
    "system_resizablewidgets",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_resizablewidgets")
    override fun installHook() = LauncherLayoutHooks.ResizableWidgetsHook(lpparam)
}

internal class LauncherWallpaperColorModeFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BaseApplicationAttachedFeature(
    lpparam,
    mPrefs,
    LauncherWallpaperColorModeFeatureId,
    "Launcher Wallpaper Color Mode",
    "launcher_wallpaper_colormode",
    FeatureTarget.LAUNCHER,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("launcher_wallpaper_colormode", 1) > 1
    override fun installHook() = LauncherAnimationHooks.WallpaperColorModeHook(lpparam)
}
