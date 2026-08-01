# A14 Upstream Issue Regression Audit

Upstream repository: `https://github.com/MonwF/customiuizer`
Scope: HyperOS 1 / Android 14 (SDK 34), `devin/a14-runtime-hardening` branch
Audit method: GitHub Issues API + page fetch, filtered by the requested topic keywords.

## Legend

- **DIRECTLY_APPLICABLE**: Upstream issue describes a defect in an A14 code path that is expected to reproduce on HyperOS 1 / Android 14.
- **PATTERN_APPLICABLE**: Upstream issue is a pattern or class of defect that can also affect A14, even if the exact ROM/version differs.
- **NOT_APPLICABLE**: Issue is about a different platform, a feature not present in A14, or a settings/UI request; no A14 risk.
- **NEEDS_DEVICE**: Cannot confirm A14 impact from static/build analysis; needs device verification.
- **INSUFFICIENT_INFORMATION**: Not enough detail to classify, or the issue is a generic support request.

## Issues by topic

| # | Title | State | Labels | Classification | One-sentence summary |
|---|-------|-------|--------|----------------|----------------------|
| 660 | 温度电流显示导致系统界面崩溃 | open | None | DIRECTLY_APPLICABLE | Enabling battery temperature/current display crashes SystemUI with `IndexOutOfBoundsException` in `IconManager.addHolder`; `DeviceInfoMonitor.hookIconSlots` uses `mGroup.addView(iconView, i)` without clamping the slot index to the current child count. |
| 624 | 时间显秒功能开了但是是静止的 | open | None | PATTERN_APPLICABLE | Status-bar seconds stay static on HyperOS 2 / A15; the same `SecondTicker`/`MiuiClock` update path exists in A14, so a per-second tick failure is a structural risk. |
| 522 | 控制 导航栏 长按功能无效与应用标题显示不全 | closed | None | NEEDS_DEVICE | Reported on HyperOS 1.0.4.0 / Android 14: navigation-bar long press and app-title display do not work; needs A14 device verification against `Controls` and `Launcher` hooks. |
| 538 | Bug: Display device temperature and display battery info show up on both the left and right sides, and all icons (like NFC, alarm, etc.) in the status bar are appearing twice on both sides | closed as not planned | None | DIRECTLY_APPLICABLE | Battery/temperature info and other icons are duplicated on both sides, matching the unguarded `MiuiPhoneStatusBarView.onAttachedToWindow` left-icon-container and `StatusBarIconController.addIconGroup` path. |
| 605 | 电池条指示器在息屏状态错误显示，息屏状态不应当显示电池条指示器 | closed as not planned | None | PATTERN_APPLICABLE | Battery bar remains visible during AOD after an unlock/lock cycle on HyperOS 2; A14 `BatteryIndicator` uses the same AOD lifecycle callbacks. |
| 655 | SystemUI crash loop due to smallRoamVisible type mismatch (FlowKt vs ReadonlyStateFlow) | closed | Bug | NOT_APPLICABLE | HyperOS 3 / A16 status-bar mobile-signal field type mismatch; A14 code does not hook `MiuiCellularIconVM.smallRoamVisible`. |
| 654 | Dismiss Expand Notification not working | closed | Bug | PATTERN_APPLICABLE | Expand notification and auto-dismiss popup features fail on OS 3.0.304; A14 uses the same `ExpandableNotificationRow` / `HeadsUpManagerPhone` hooks and is exposed to the same ROM class-layout drift. |
| 587 | 扩展通知菜单在200上失效了，麻烦修复谢谢 | closed | None | PATTERN_APPLICABLE | Extended notification menu fails on HyperOS 2.0.200; A14 `SystemNotificationHooks` extended-menu path may face the same class/method mismatch. |
| 602 | Floating notification blacklist doesn't work in 2.0.217 Android 16 | open | None | NOT_APPLICABLE | Floating notification blacklist on HyperOS 2 / A16 is outside the A14 feature set. |
| 603 | 升级209.2后桌面文件夹图标布局错误 | open | None | PATTERN_APPLICABLE | Launcher folder layout shifts after HyperOS 2.0.209; A14 `LauncherFolderHooks` folder-width logic follows the same pattern. |
| 646 | Switching to previous app has stopped working | open | None | NEEDS_DEVICE | The "switch to previous app" global action stopped working in recent builds; A14 `GlobalActionSystemServerHooks` uses the same `ActivityManager.getRecentTasks` path and must be verified on A14. |
| 600 | 控制中心音量条颜色修改在二级菜单失效 | open | None | NOT_APPLICABLE | Control-center volume-bar color in the secondary panel is not an A14 feature and is not present in `SystemUIControlCenterHooks`. |
| 631 | Lock screen mods not working | closed | None | PATTERN_APPLICABLE | Charging data and album-art wallpaper lock-screen features fail on HyperOS 3 / A16; A14 `SystemUILockScreenHooks` share the same reflection-based pattern. |
| 559 | 最新版状态栏时钟相关设置仍不起作用 | closed as not planned | None | PATTERN_APPLICABLE | Newer OS build breaks status-bar clock customizations and seconds display; A14 clock hooks use the same reflection targets and may break if the ROM changes. |
| 137 | seconds don't update | closed | None | PATTERN_APPLICABLE | Custom-format clock seconds stop updating on some ROMs; same per-second tick pattern as A14 `SecondTicker`. |
| 389 | The Actions section in the Recent app list settings has disappeared | closed as not planned | None | NOT_APPLICABLE | Settings UI layout request for a feature that was removed; not an A14 runtime hook regression. |
| 85 | freeze on unlocking | closed | None | INSUFFICIENT_INFORMATION | Generic lock-screen freeze report with no logs or stack trace; does not map to a specific A14 ClassLoader / Receiver / Observer defect. |
| 647 | Can API 101 be updated for HyperOS 1 on A14? | closed | None | NOT_APPLICABLE | Support question; the A14 branch already targets libxposed API 101/102 with API 102 isolation. |
| 580 | It is possible to adapt the latest version for Android 14? | closed | None | INSUFFICIENT_INFORMATION | Support request about A14 support; no concrete regression to audit. |
| 665 | Investigating a separate window crash | open | None | INSUFFICIENT_INFORMATION | A window / SystemUI crash is under investigation but the report lacks logs or a reproducible scenario. |

## Topics with no matching upstream issue

- **DexKit**: no upstream issue title or body explicitly discusses a DexKit failure in `MonwF/customiuizer`.
- **Receiver/Observer leaks**: no explicit upstream issue was found; the closest tangential match is #85, which is too vague.
- **ClassLoader**: only a tangential search hit in #85; no actionable ClassLoader-specific upstream issue.

## Notes

- All `closed as not planned` issues are still included when they match an A14 code path, because the A14 branch does not automatically inherit any upstream fix.
- HyperOS 2 / Android 15 and HyperOS 3 / Android 16 issues are treated as structural risks unless the A14 code has the exact same hook path; no HyperOS 2 fix is assumed to apply to A14.
