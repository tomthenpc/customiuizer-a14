# A14 P3-A — Restart Target Evidence Map / Implementation Preflight

## Base

```text
BASE SHA = 4c5e7c5f997e026a4d6b2d4551a700d026624a0b
```

## Scope

本阶段只建立 preference -> restart target 数据。禁止 production implementation。

- 使用 `feature-semantics/a14.json` 作为辅助候选（非权威）。
- 结合 `LazyFeatureSpec` / `FeatureDefinition` / `ProcessRouter` 进行 package 反推。
- 对个别 key 做了源码级人工复核/override。
- 不修改 `feature-semantics` 生成器，不修改 production 代码。

## Existing restart architecture

当前 `PreferenceFragmentBase.kt` 提供两个底层原语：

1. `restartTarget(packageName, successRes, failureRes)`：root shell 执行 `am force-stop <package>`。
2. `restartTargetProcess(processName, successRes, failureRes)`：root shell 执行 `pidof <process>` 后 `kill -9`。

当前 `menu_mods.xml` 有：

- `restartlauncher`
- `restartsystemui`
- `restartsecuritycenter`
- `softreboot`

## Current page-name hardcoding inventory

```text
CategorySelector.kt:
  pref_key_system  -> activeMenus = 'systemui'
  pref_key_launcher -> activeMenus = 'launcher'

System.kt:
  pref_key_system_cat_recents  -> activeMenus = 'launcher'
  pref_key_system_cat_statusbar/lockscreen/qs/drawer -> activeMenus = 'systemui'
```

这些属于 `PAGE-NAME HARDCODE`，P3-B 必须替换。

## Target enum proposal

P3-A 自动重启目标只允许：

- `NONE`
- `LAUNCHER`
- `SYSTEMUI`
- `SECURITY_CENTER`

内部可记录但不得执行：

- `EXCLUDED_SYSTEM`（system_server / android / 需要 reboot / 无 proven primitive）
- `UNKNOWN`（证据不足）

## Page resolution model

- `CURRENT_PAGE_RESOLUTION = DIRECT_ONLY`：只看当前页面直接 setting。
- 不递归整个 subtree。
- 不包含 category key、navigation-only `PreferenceEx`、hidden/unsupported 项。
- app list / blacklist 等仅影响未来 attach 的 key 视为 `NONE`。

## Coverage summary

- `TOTAL_FUNCTIONAL_PREFERENCES = 455`
- `NONE = 62`
- `LAUNCHER = 51`
- `SYSTEMUI = 256`
- `SECURITY_CENTER = 17`
- `MULTI_TARGET_PAGES = 3`
- `MULTI_TARGET_PREFS (on multi-target pages) = 164`
- `EXCLUDED_SYSTEM = 35`
- `UNKNOWN = 34`

## Per-page executable target union

| page | executable targets | all classifications |
|---|---|---|
| prefs_controls.xml | LAUNCHER, SYSTEMUI | EXCLUDED_SYSTEM, LAUNCHER, NONE, SYSTEMUI, UNKNOWN |
| prefs_launcher.xml | LAUNCHER, SECURITY_CENTER | LAUNCHER, SECURITY_CENTER |
| prefs_main.xml |  | UNKNOWN |
| prefs_system.xml | LAUNCHER, SECURITY_CENTER, SYSTEMUI | EXCLUDED_SYSTEM, LAUNCHER, NONE, SECURITY_CENTER, SYSTEMUI, UNKNOWN |
| prefs_system_alarmonlock.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_albumartonlock.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_autobrightness.xml |  | EXCLUDED_SYSTEM, NONE |
| prefs_system_batteryindicator.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_charginginfo.xml | SYSTEMUI | NONE, SYSTEMUI |
| prefs_system_controlcenter_clock.xml | SYSTEMUI | NONE, SYSTEMUI, UNKNOWN |
| prefs_system_controlcenter_themestyle.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_detailednetspeed.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_hideicons.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_lockscreenshortcuts.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_noscreenlock.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_screenshot.xml |  | NONE, UNKNOWN |
| prefs_system_secureqs.xml | SYSTEMUI | NONE, SYSTEMUI |
| prefs_system_statusbar_batterystyle.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_batterytempandcurrent.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_clock.xml | SYSTEMUI | NONE, SYSTEMUI |
| prefs_system_statusbar_mobilesignal.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_righticons.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_showdevicetemperature.xml | SYSTEMUI | SYSTEMUI, UNKNOWN |
| prefs_system_statusbarcontrols.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_vibration_amp.xml |  | EXCLUDED_SYSTEM, UNKNOWN |
| prefs_system_visualizer.xml | SYSTEMUI | SYSTEMUI |
| prefs_various.xml | SECURITY_CENTER | EXCLUDED_SYSTEM, NONE, SECURITY_CENTER, UNKNOWN |
| prefs_various_calluibright.xml |  | NONE, UNKNOWN |

## SecurityCenter keys

Count: 17

```text
system_applock_scramblepin
system_hidelowbatwarn
various_appdetails
various_appsort
various_disable_dock_suggest
various_disable_freeform_suggest_blacklist
various_disable_reset_recents_privacy_blur
various_disableapp
various_enable_expand_sidebar
various_hide_report_ondetails
various_privacyapps_column_nums4
various_replace_defaultopen_with_openbydefault
various_restrictapp
various_show_battery_temperature
various_skip_interceptperm
various_skip_securityscan
various_swipe_expand_sidebar
```

## Launcher keys (count: 51)

```text
controls_fsg_coverage
controls_fsg_horiz
controls_fsg_swipeandstop_disablevibrate
controls_fsg_width
launcher_closedrawer
launcher_closefolders
launcher_darkershadow
launcher_disable_log
launcher_disable_wallpaperscale
launcher_dock_bottommargin
launcher_dock_height
launcher_dock_topmargin
launcher_docktitles
launcher_fixanim
launcher_fixlaunch
launcher_folder_cols
launcher_folderblur_disable
launcher_folderblur_opacity
launcher_folderspace
launcher_hideseekpoints
launcher_hideseekpoints_edit
launcher_hidetitles
launcher_horizmargin
launcher_horizwidgetmargin
launcher_iconscale
launcher_indicator_topmargin
launcher_indicatorheight
launcher_infinitescroll
launcher_noclockhide
launcher_nounlockanim
launcher_nowidgetonly
launcher_nozoomanim
launcher_oldlaunchanim
launcher_privacyapps_gest
launcher_renameapps
launcher_sensorportrait
launcher_titlefontsize
launcher_titletopmargin
launcher_topmargin
launcher_unlockgrids
launcher_unlockhotseat
launcher_wallpaper_colormode
system_fw_splitscreen
system_hidefromrecents
system_hidefromrecents_apps
system_recents_blur
system_recents_card_style
system_recents_disable_wallpaperscale
system_recents_hide_statusbar
system_removecleaner
... and 1 more
```

## SystemUI keys (count: 256)

```text
controls_hidenavbar_whenscreenshot
controls_navbarmargin
controls_nonavbar
controls_volumedowndt_torch
controls_volumemedia_vibrate
controls_volumemedia_vibrate_ignore
system_4gtolte
system_albumartonlock
system_albumartonlock_blur
system_albumartonlock_gray
system_albumartonlock_scale
system_allownotiffloat
system_allownotifonkeyguard
system_allrotations2
system_applock_timeout
system_batteryindicator
system_betterpopups_allowfloat
system_betterpopups_allowfloat_apps
system_betterpopups_autoclose_expanded
system_betterpopups_center
system_betterpopups_delay
system_betterpopups_disablewhenmute
system_betterpopups_nohide
system_calendar_app
system_cc_btandtorch_ascard
system_cc_card_enabled_color
system_cc_card_enabled_color_custom
system_cc_card_enabled_iconcolor_custom
system_cc_card_enabled_primary_textcolor
system_cc_card_enabled_secondary_textcolor
system_cc_clock_centeralign
system_cc_clock_fontsize
system_cc_clock_verticaloffset
system_cc_clocktweak
system_cc_collapse_after_clicked
system_cc_enable_style_switch
system_cc_floatingtimetile
system_cc_fpstile
system_cc_freeform_when_longclick
system_cc_hide_edit
system_cc_hide_profile_monitoring
system_cc_hideoperator_delimiter
system_cc_show_stepcount
system_cc_slider_color_enable
system_cc_slider_icon_color
system_cc_slider_progress_color
system_cc_switch_qsandnotification
system_cc_tile_enabled_color
system_cc_tile_enabled_color_custom
system_cc_tile_enabled_iconcolor_custom
system_cc_tile_roundedrect
system_cc_volume_showpct
system_ccgridcolumns
system_chargeanimtime
system_charginginfo
system_charginginfo_current
system_charginginfo_temp
system_charginginfo_voltage
system_charginginfo_wattage
system_clock_app
system_colorizenotifs
system_colorizenotifs_apps
system_detailednetspeed_align
system_detailednetspeed_icon
system_detailednetspeed_low
system_detailednetspeed_lowlevel
system_detailednetspeed_secunit
system_detailednetspeed_style
system_disableanynotif
system_drawer_blur
system_drawer_date_fontsize
system_drawer_remove_emptynotify
system_drawer_removeshortcut
system_dttosleep
system_epm
system_expandheadups
system_expandheadups_apps
system_expandnotifs
system_expandnotifs_apps
system_fivegtile
... and 176 more
```

## EXCLUDED_SYSTEM keys (count: 35)

```text
controls_fingerprintfailure
controls_fingerprintscreen
controls_fingerprintsuccess
controls_fingerprintwake
controls_powerflash
controls_volumemedia_down
controls_volumemedia_up
system_apksign
system_applock
system_applock_skip
system_autobrightness
system_autobrightness_reset_when_screenoff
system_cleanopenwith
system_cleanshare
system_clearalltasks
system_dimtime
system_disableintegrity
system_downgrade
system_firstpress
system_force_darken_allapps
system_forceclose
system_hideproxywarn
system_ignorecalls
system_lockscreen_disable_strongauth_72h
system_lswallpaper
system_noducking
system_orientationlock
system_other_wallpaper_scale
system_remove_startactconfirm
system_removesecure
system_screenanim_duration
system_securelock
system_vibration_amp
various_allow_untrusted_touch
various_disable_access_devicelogs
```

## UNKNOWN keys (count: 34)

```text
controls_nonavbar_fix_inputmethod
controls_volumecursor
controls_volumecursor_reverse
miuizer_settingsiconpos
system_blocktoasts
system_cc_hidedate
system_drawer_date_centeralign
system_drawer_hidedate
system_gallery_screenshots_path
system_nooverscroll
system_qs_force_systemfonts
system_screenshot
system_statusbar_showdevicetemperature_content
system_statusbar_showdevicetemperature_hideunit
system_statusbar_showdevicetemperature_reverseorder
system_statusbar_showdevicetemperature_singlerow
system_statusbarheight
system_toasttime
system_vibration
system_vibration_amp_notif
system_vibration_amp_other
system_vibration_amp_ringer
system_wifipassword
various_alarmcompat
various_answerinheadup
various_calluibright
various_disable_defraud_apps_detect
various_gboardpadding_land
various_gboardpadding_port
various_installappinfo
various_installer_purify
various_miuiinstaller
various_persist_batteryoptimization
various_showcallui
```

## Multi-target page examples

- `prefs_controls.xml`
- `prefs_launcher.xml`
- `prefs_system.xml`

## Old individual menu plan

`OLD_INDIVIDUAL_MENU_PLAN = REMOVE_FROM_ORDINARY_PAGES`：
- 在普通二级页面不再单独显示 `restartlauncher` / `restartsystemui` / `restartsecuritycenter`。
- 新增 `restartmatched`，仅在当前页可执行目标非空时显示。
- `softreboot` 保持独立，不加入自动匹配集合。

## Matched restart action

`MATCHED_RESTART_ACTION = YES`：
- Title：`重启相关组件` / `Restart affected components`。
- 单页统一显示一次。

## Multi-target failure model

`MULTI_TARGET_FAILURE_MODEL = ATTEMPT_ALL + AGGREGATE`：
- 顺序执行所有匹配 target。
- 独立 failure isolation。
- 最终只 Toast 一次聚合结果。

## P3-B implementation proposal

1. 新增 `RestartTarget` enum：`NONE`, `LAUNCHER`, `SYSTEMUI`, `SECURITY_CENTER`, `EXCLUDED_SYSTEM`, `UNKNOWN`。
2. 新增 `PreferenceRestartTargetRegistry`（checked-in mapping 或从 feature-semantics 生成后 hardcode），运行时不解析 JSON/不扫描 XML/不做磁盘 I/O。
3. 页面打开时遍历当前 `PreferenceScreen` canonical keys，in-memory lookup，set union，过滤 `NONE` / `EXCLUDED_SYSTEM` / `UNKNOWN`。
4. `restartmatched` 可见条件：union 中 executable target 非空。
5. 执行：Launcher / SystemUI / SecurityCenter 分别调用已有 proven primitive；多 target 顺序执行并聚合结果。
6. soft reboot 保持原范围，不自动触发。

## P3-B safe to implement

`P3_B_SAFE_TO_IMPLEMENT = NO`

原因：仍有 34 个 `UNKNOWN` preference、35 个 `EXCLUDED_SYSTEM`、以及多包 master（Media/Common/Generic/Input/Phone/PackageInstaller/Settings 等）无法安全纳入自动匹配集合。必须先解决这些 key 的 package 证据或 UI 处理方案（如不纳入 matched action）。

## Evidence table (selected)

| page | canonical key | target | package / evidence |
|---|---|---|---|
| prefs_controls.xml | controls_fingerprintscreen | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_fingerprintwake | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_fingerprintfailure | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_fingerprintsuccess | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fingerprintsuccess_ignore | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_powerflash | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_powerflash_delay | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_volumecursor | UNKNOWN | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/InputMethodFeatures.kt |
| prefs_controls.xml | controls_volumecursor_reverse | UNKNOWN | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_volumecursor_apps | NONE | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_volumedowndt_torch | SYSTEMUI | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_mediaplayer_apps | NONE | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_controls.xml | controls_volumemedia_up | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_controls.xml | controls_volumemedia_down | EXCLUDED_SYSTEM | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_controls.xml | controls_volumemedia_vibrate | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_controls.xml | controls_volumemedia_vibrate_ignore | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_controls.xml | controls_nonavbar | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/InputMethodFeatures.kt |
| prefs_controls.xml | controls_nonavbar_fix_inputmethod | UNKNOWN | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/InputMethodFeatures.kt |
| prefs_controls.xml | controls_hidenavbar_whenscreenshot | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_controls.xml | controls_navbarmargin | SYSTEMUI | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fsg_horiz | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_controls.xml | controls_fsg_horiz_apps | NONE | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt |
| prefs_controls.xml | controls_fsg_coverage | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fsg_width | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fsg_swipeandstop_disablevibrate | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt |
| prefs_launcher.xml | launcher_closefolders | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_folderblur_opacity | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_folderblur_disable | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_folder_cols | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_folderspace | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_hidetitles | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_docktitles | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPackageReadyFeatures.kt |
| prefs_launcher.xml | launcher_titlefontsize | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt |
| prefs_launcher.xml | launcher_titletopmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt |
| prefs_launcher.xml | launcher_darkershadow | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_renameapps | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_privacyapps_gest | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | various_privacyapps_column_nums4 | SECURITY_CENTER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SecurityCenterFeatures.kt |
| prefs_launcher.xml | launcher_disable_log | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPackageReadyFeatures.kt |
| prefs_launcher.xml | launcher_fixanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_fixlaunch | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_infinitescroll | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_noclockhide | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_nowidgetonly | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | system_resizablewidgets | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_nounlockanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_disable_wallpaperscale | LAUNCHER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt |
| prefs_launcher.xml | launcher_wallpaper_colormode | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt |
| prefs_launcher.xml | launcher_nozoomanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_oldlaunchanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_sensorportrait | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_hideseekpoints | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_hideseekpoints_edit | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_indicatorheight | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_indicator_topmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_unlockgrids | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPackageReadyFeatures.kt |
| prefs_launcher.xml | launcher_closedrawer | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_horizmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_topmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_unlockhotseat | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_dock_height | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_dock_topmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_dock_bottommargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_horizwidgetmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_iconscale | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt |
| prefs_main.xml | miuizer_settingsiconpos | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_system.xml | system_allrotations2 | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_orientationlock | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_autobrightness_reset_when_screenoff | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_nolightuponcharges | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_dimtime | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt |
| prefs_system.xml | system_lstimeout | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_chargeanimtime | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt |
| prefs_system.xml | system_screenanim_duration | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt |
| prefs_system.xml | system_nosafevolume | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_noducking | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_firstpress | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_cc_volume_showpct | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumetimer | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumebar_blur_mtk | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volume_mode_button_colors | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumedialogdelay_collapsed | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumedialogdelay_expanded | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_ignorecalls | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_ignorecalls_apps | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_nosilentvibrate | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_vibration | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_vibration_apps | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_toasttime | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt |
| prefs_system.xml | system_blocktoasts | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt |
| prefs_system.xml | system_blocktoasts_apps | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt |
| prefs_system.xml | system_networkindicator_wifi | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbaricons_wifistandard | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt |
| prefs_system.xml | system_statusbar_dualrows | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_clock_span2rows | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_left_ratio | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_firstrow_horizmargin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_firstrow_horizmargin_left | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_firstrow_horizmargin_right | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbarheight | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/CommonPackageFeatures.kt |
| prefs_system.xml | system_strong_toast_mode | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_strong_toast_position | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_strong_toast_bottom_offset | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_iconsize | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_statusbar_topmargin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_topmargin_val | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_statusbar_topmargin_unset_lockscreen | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_horizmargin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_horizmargin_left | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_horizmargin_right | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_hidestatusbar_whenscreenshot | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_maxsbicons | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_statusbarcolor | SYSTEMUI | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_system.xml | system_statusbarcolor_apps | NONE | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_system.xml | system_removedismiss | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_drawer_removeshortcut | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_drawer_remove_emptynotify | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_drawer_hidedate | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_drawer_dateformat | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_drawer_date_centeralign | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_drawer_date_fontsize | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_shortcut_app | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system.xml | system_clock_app | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system.xml | system_calendar_app | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system.xml | system_drawer_blur | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_notifafterunlock | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_allownotifonkeyguard | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_allownotiffloat | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_disableanynotif | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SettingsFeatures.kt |
| prefs_system.xml | system_notifimportance | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SettingsFeatures.kt |
| prefs_system.xml | system_notif_disable_fold | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_morenotif | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_colorizenotifs | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt |
| prefs_system.xml | system_colorizenotifs_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt |
| prefs_system.xml | system_notify_openinfw | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_notify_openinfw_in_whitelist | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt |
| prefs_system.xml | system_notify_openinfw_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt |
| prefs_system.xml | system_mutevisiblenotif | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_notifrowmenu | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_expandnotifs | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandEffect.kt |
| prefs_system.xml | system_expandnotifs_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandEffect.kt |
| prefs_system.xml | system_minimalnotifview | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_notifchannelsettings | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_fivegtile | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt |
| prefs_system.xml | system_cc_floatingtimetile | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt |
| prefs_system.xml | system_cc_fpstile | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt |
| prefs_system.xml | system_cc_hidedate | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_cc_dateformat | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_qs_hideoperator | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_hideoperator_delimiter | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_show_stepcount | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_collapse_after_clicked | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_cc_freeform_when_longclick | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_cc_hide_edit | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_hide_profile_monitoring | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_ccgridcolumns | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_showpct | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_showpct_top | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_enable_style_switch | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_cc_switch_qsandnotification | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_qshaptics | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_qshaptics_ignore | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_recents_disable_wallpaperscale | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_recents_hide_statusbar | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_recents_card_style | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt |
| prefs_system.xml | system_removecleaner | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_clearalltasks | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_hidefromrecents | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_hidefromrecents_apps | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt |
| prefs_system.xml | system_recents_blur | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt |
| prefs_system.xml | system_betterpopups_center | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_expandheadups | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_expandheadups_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_betterpopups_autoclose_expanded | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_betterpopups_allowfloat | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_betterpopups_allowfloat_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_betterpopups_disablewhenmute | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_betterpopups_nohide | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_betterpopups_delay | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_fw_noblacklist | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_fw_splitscreen | LAUNCHER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_fw_forcein_actionsend | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_fw_forcein_actionsend_in_whitelist | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_fw_forcein_actionsend_apps | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_applock_scramblepin | SECURITY_CENTER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SecurityCenterFeatures.kt |
| prefs_system.xml | system_applock | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_applock_timeout | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt |
| prefs_system.xml | system_applock_skip | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_hidelsstatusbar | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_hidelsclock | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_lockscreen_hidezenmode | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_lockscreen_disable_edit | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_hidelshint | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_nosos | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_lswallpaper | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_taptounlock | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_dttosleep | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_securelock | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_scramblepin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_nopassword | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |

...（剩余行见 `feature-semantics/a14.json` 与源码）
