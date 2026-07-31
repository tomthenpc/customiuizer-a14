package tv.withaibuild.customiuizer.installers;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import tv.withaibuild.customiuizer.mods.Controls;
import tv.withaibuild.customiuizer.mods.Launcher;
import tv.withaibuild.customiuizer.mods.LauncherAnimationHooks;
import tv.withaibuild.customiuizer.mods.LauncherFolderHooks;
import tv.withaibuild.customiuizer.mods.LauncherGestureHooks;
import tv.withaibuild.customiuizer.mods.LauncherIconHooks;
import tv.withaibuild.customiuizer.mods.LauncherLayoutHooks;
import tv.withaibuild.customiuizer.mods.System;
import tv.withaibuild.customiuizer.mods.SystemWindowHooks;
import tv.withaibuild.customiuizer.utils.PrefMap;

/**
 * Installer for hooks that run in the Launcher process.
 *
 * This keeps {@link tv.withaibuild.customiuizer.MainModule} focused on module-level lifecycle
 * and delegates the package-specific Launcher hooks to a dedicated, stateless class.
 * The {@code ReflectionCache.onSafeLifecycle} boundary, preference bootstrap and the
 * cross-package {@code Application.attach} hook stay in MainModule so the installer receives
 * an already-validated load point.
 */
public final class LauncherInstaller {

    private LauncherInstaller() {}

    public static void install(PackageReadyParam lpparam, PrefMap mPrefs) {
        int folderCols = mPrefs.getInt("launcher_folder_cols", 1);
        if (folderCols > 1) LauncherFolderHooks.FolderColumnsRes(folderCols);
        if (mPrefs.getInt("launcher_horizmargin", 0) > 0) LauncherLayoutHooks.HorizontalSpacingRes();
        if (mPrefs.getInt("launcher_indicatorheight", 9) > 9) LauncherLayoutHooks.IndicatorHeightRes();
        if (mPrefs.getInt("launcher_indicator_topmargin", 0) > 0) LauncherLayoutHooks.IndicatorMarginTopHook(lpparam);
        if (mPrefs.getBoolean("launcher_unlockgrids")) {
            LauncherLayoutHooks.UnlockGridsRes();
            LauncherLayoutHooks.UnlockGridsHook(lpparam);
        }
        if (mPrefs.getBoolean("launcher_docktitles")) LauncherIconHooks.ShowHotseatTitlesHook(lpparam);
        if (mPrefs.getBoolean("launcher_disable_log")) {
            Launcher.DisableLauncherLogHook(lpparam);
        }
        if (mPrefs.getInt("launcher_topmargin", 0) > 0) LauncherLayoutHooks.WorkspaceCellPaddingTopHook(lpparam);
        if (mPrefs.getInt("launcher_dock_topmargin", 0) > 0) LauncherLayoutHooks.DockMarginTopHook(lpparam);
        if (mPrefs.getInt("launcher_dock_bottommargin", 0) > 0) LauncherLayoutHooks.DockMarginBottomHook(lpparam);
        if (mPrefs.getInt("launcher_dock_height", 60) > 60) LauncherLayoutHooks.DockHeightHook(lpparam);
        if (mPrefs.getBoolean("launcher_privacyapps_gest")) Launcher.setupLauncher(lpparam);
    }

    public static void handleLoadLauncher(PackageReadyParam lpparam, PrefMap mPrefs) {
        boolean closeOnLaunch = false;
        if (mPrefs.getInt("launcher_swipedown_action", 1) != 1 ||
                mPrefs.getInt("launcher_swipeup_action", 1) != 1 ||
                mPrefs.getInt("launcher_swipedown2_action", 1) != 1 ||
                mPrefs.getInt("launcher_swipeup2_action", 1) != 1) LauncherGestureHooks.HomescreenSwipesHook(lpparam);
        if (mPrefs.getInt("launcher_swipeleft_action", 1) != 1 ||
                mPrefs.getInt("launcher_swiperight_action", 1) != 1) LauncherGestureHooks.HotSeatSwipesHook(lpparam);
        if (mPrefs.getInt("launcher_shake_action", 1) != 1) LauncherGestureHooks.ShakeHook(lpparam);
        if (mPrefs.getInt("launcher_doubletap_action", 1) != 1) LauncherGestureHooks.LauncherDoubleTapHook(lpparam);
        if (mPrefs.getInt("launcher_pinch_action", 1) != 1) LauncherGestureHooks.LauncherPinchHook(lpparam);
        if (mPrefs.getInt("launcher_folder_cols", 1) > 1) LauncherFolderHooks.FolderColumnsHook(lpparam);
        if (mPrefs.getInt("launcher_iconscale", 45) > 45) LauncherIconHooks.IconScaleHook(lpparam);
        if (mPrefs.getInt("launcher_titlefontsize", 5) > 5) LauncherIconHooks.TitleFontSizeHook(lpparam);
        if (mPrefs.getInt("launcher_titletopmargin", 0) > 0) LauncherIconHooks.TitleTopMarginHook(lpparam);
        if (mPrefs.getBoolean("launcher_noclockhide")) LauncherIconHooks.NoClockHideHook(lpparam);
        if (mPrefs.getBoolean("launcher_renameapps")) LauncherIconHooks.RenameShortcutsHook(lpparam);
        if (mPrefs.getBoolean("launcher_darkershadow")) LauncherIconHooks.TitleShadowHook(lpparam);
        if (mPrefs.getBoolean("controls_nonavbar")) Launcher.HideNavBarHook(lpparam);
        if (mPrefs.getBoolean("launcher_infinitescroll")) LauncherLayoutHooks.InfiniteScrollHook(lpparam);
        if (mPrefs.getBoolean("launcher_hidetitles")) LauncherIconHooks.HideTitlesHook(lpparam);
        if (mPrefs.getBoolean("launcher_fixlaunch")) Launcher.FixAppInfoLaunchHook(lpparam);
        if (mPrefs.getBoolean("launcher_nowidgetonly")) LauncherLayoutHooks.NoWidgetOnlyHook(lpparam);
        if (mPrefs.getBoolean("launcher_sensorportrait")) Launcher.ReverseLauncherPortraitHook(lpparam);
        if (mPrefs.getBoolean("launcher_unlockhotseat")) LauncherLayoutHooks.MaxHotseatIconsCountHook(lpparam);
        if (mPrefs.getStringAsInt("launcher_closefolders", 1) > 1) { LauncherFolderHooks.CloseFolderOnLaunchHook(lpparam); closeOnLaunch = true; }
        if (mPrefs.getInt("system_recents_blur", 100) < 100) Launcher.RecentsBlurRatioHook(lpparam);
        if (mPrefs.getInt("controls_fsg_coverage", 60) != 60) Controls.BackGestureAreaHeightHook(lpparam);
        if (mPrefs.getInt("controls_fsg_width", 100) > 100) Controls.BackGestureAreaWidthHook(lpparam);
        if (mPrefs.getBoolean("controls_fsg_horiz")) LauncherGestureHooks.FSGesturesHook(lpparam);
        if (mPrefs.getBoolean("system_removecleaner")) System.HideMemoryCleanHook(lpparam, true);
        if (mPrefs.getBoolean("system_recents_disable_wallpaperscale") || mPrefs.getBoolean("launcher_disable_wallpaperscale")) LauncherAnimationHooks.DisableLauncherWallpaperScale(lpparam);
        if (mPrefs.getBoolean("system_recents_hide_statusbar")) Launcher.HideStatusBarInRecentsHook(lpparam);
        if (mPrefs.getBoolean("system_fw_splitscreen")) SystemWindowHooks.MultiWindowPlusHook(lpparam);
        if (mPrefs.getBoolean("launcher_fixanim")) LauncherAnimationHooks.FixAnimHook(lpparam);
        if (mPrefs.getBoolean("launcher_hideseekpoints")) LauncherLayoutHooks.HideSeekPointsHook(lpparam);
        if (mPrefs.getBoolean("launcher_privacyapps_gest")
            || mPrefs.getInt("launcher_spread_action", 1) != 1) LauncherFolderHooks.PrivacyFolderHook(lpparam);
        if (mPrefs.getBoolean("system_hidefromrecents")) Launcher.HideFromRecentsHook(lpparam);
        if (mPrefs.getInt("launcher_folderblur_opacity", 0) > 0) LauncherFolderHooks.FolderBlurHook(lpparam);
        if (mPrefs.getBoolean("launcher_nounlockanim")) LauncherAnimationHooks.NoUnlockAnimationHook(lpparam);
        if (mPrefs.getBoolean("launcher_nozoomanim")) LauncherAnimationHooks.NoZoomAnimationHook(lpparam);
        if (mPrefs.getBoolean("launcher_oldlaunchanim")) LauncherAnimationHooks.UseOldLaunchAnimationHook(lpparam);
        if (mPrefs.getBoolean("launcher_closedrawer")) { LauncherFolderHooks.CloseDrawerOnLaunchHook(lpparam); closeOnLaunch = true; }
        if (mPrefs.getInt("launcher_horizwidgetmargin", 0) > 0) LauncherLayoutHooks.HorizontalWidgetSpacingHook(lpparam);
        if (mPrefs.getInt("controls_fsg_assist_left_action", 1) > 1
            || mPrefs.getInt("controls_fsg_assist_right_action", 1) > 1
        )  LauncherGestureHooks.AssistGestureActionHook(lpparam);
        if (mPrefs.getInt("controls_fsg_swipeandstop_action", 1) > 1) LauncherGestureHooks.SwipeAndStopActionHook(lpparam);
        if (closeOnLaunch) LauncherFolderHooks.CloseFolderOrDrawerOnLaunchShortcutMenuHook(lpparam);
        if (mPrefs.getBoolean("system_resizablewidgets")) LauncherLayoutHooks.ResizableWidgetsHook(lpparam);
        if (mPrefs.getStringAsInt("launcher_wallpaper_colormode", 1) > 1) LauncherAnimationHooks.WallpaperColorModeHook(lpparam);
    }
}
