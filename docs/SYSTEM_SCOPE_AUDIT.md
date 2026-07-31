# system_server 作用域代码路径审计

审计范围：`MainModule.onSystemServerStarting` 中所有条件分支（`app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` 第 209-292 行）。

目的：确认 `system` 作用域启用后，注入到 `system_server` 的 Hook 路径、条件开关、目标类、重启需求、热更新能力、单元测试覆盖和真机依赖关系。本轮只审计，不修改业务 Hook。

---

## 1. 检查结论

1. 所有 `onSystemServerStarting` 分支都通过 `SystemServerStartingParam` 注入，明确属于 `system_server`。
2. `app/src/main/resources/META-INF/xposed/scope.list` 已包含 `system`、`android`、`com.android.systemui`、`com.miui.home`；`system` 是 `system_server` 加载的必要条件。
3. `android` 作用域用于 `com.android.phone`、`com.android.settings` 等系统应用，不是 `system_server` 的替代项。
4. 除 `PackagePermissions.hook` 为无条件固定注入外，其余 Hook 均按偏好开关安装；功能关闭时不会安装对应业务 Hook。
5. 未发现无条件安装的高成本业务 Hook；`PackagePermissions` 仅修改一次静态数组，无持续成本。
6. 未发现 `while(true)` 轮询、持续反射、无界缓存或不必要的常驻线程。`GlobalActionSystemServerHooks` 注册了一个进程级 BroadcastReceiver，`Controls` 使用 Handler 处理按键事件序列，均属于事件驱动而非轮询。
7. 偏好不可用时 `initPrefs()` 会记录 `prefsUnavailable` 并退出；`HookDiagnostics` 会汇总但不做破坏性处理。这是已知风险，本轮未修改运行时代码。

---

## 2. 分支明细

| preference key | Hook 函数 | 功能组 | 目标系统服务 / 类 | 需完整重启 | 偏好热更新 | 单元测试 | 只能真机验证 |
|---|---|---|---|---|---|---|---|
| (无条件) | `PackagePermissions.hook` | 5. 安装策略 | `com.android.server.pm.MiuiDefaultPermissionGrantPolicy` | 是 | 否 | 否 | 是 |
| (GlobalActions 自定义动作) | `GlobalActionSystemServerHooks.setupGlobalActions` | 5. Global Actions | `com.android.server.policy.BaseMiuiPhoneWindowManager.initInternal` | 是 | 否 | 否 | 是 |
| `system_screenshot_overlay` | `SystemWindowHooks.TempHideOverlayAppHook` | 3. 窗口/显示 | `com.android.server.wm.WindowSurfaceController` | 是 | 否 | 否 | 是 |
| `system_notify_openinfw` / `system_fw_forcein_actionsend` / `system_betterpopups_allowfloat` / `system_cc_freeform_when_longclick` | `SystemWindowHooks.OpenAppInFreeFormHook` | 3. 窗口/显示 | `com.android.server.wm.ActivityTaskManagerService` | 是 | 否 | 否 | 是 |
| `controls_backlong_action` / `controls_homelong_action` / `controls_menulong_action` | `Controls.NavBarActionsHook` | 1. 按键/手势 | `com.android.server.policy.BaseMiuiPhoneWindowManager` | 是 | 否 | 否 | 是 |
| `controls_powerdt_action` / `controls_volumedowndt_torch` | `Controls.PowerDoubleTapActionHook` | 1. 按键/手势 | `com.miui.server.input.util.ShortCutActionsUtils` | 是 | 否 | 否 | 是 |
| `system_screenanim_duration` | `SystemDisplayHooks.ScreenAnimHook` | 3. 窗口/显示 | `com.android.server.display.DisplayPowerController` | 是 | 否 | 否 | 是 |
| `system_applock_timeout` | `SystemLockScreenHooks.AppLockTimeoutHook` | 2. 锁屏/认证 | `com.miui.server.SecurityManagerService` | 是 | 否 | 否 | 是 |
| `system_dimtime` | `SystemDisplayHooks.ScreenDimTimeHook` | 3. 窗口/显示 | `com.android.server.power.PowerManagerService` | 是 | 否 | 否 | 是 |
| `system_toasttime` | `System.ToastTimeHook` | 4. 音频/通知 | `com.android.server.notification.NotificationManagerService` | 是 | 否 | 否 | 是 |
| `system_removesecure` | `SystemSecurityHooks.RemoveSecureHook` | 5. 安全/安装 | `com.android.server.wm.WindowState` | 是 | 否 | 否 | 是 |
| `system_remove_startactconfirm` | `SystemSecurityHooks.RemoveActStartConfirmHook` | 5. 安全/安装 | `com.miui.server.SecurityManagerService$LocalService` | 是 | 否 | 否 | 是 |
| `system_securelock` | `SystemLockScreenHooks.EnhancedSecurityHook` | 2. 锁屏/认证 | `com.android.server.policy.PhoneWindowManager` | 是 | 否 | 否 | 是 |
| `system_downgrade` | `SystemSecurityHooks.NoVersionCheckHook` | 5. 安全/安装 | `com.android.server.pm.PackageManagerServiceUtils` | 是 | 否 | 否 | 是 |
| `system_orientationlock` | `SystemWindowHooks.OrientationLockHook` | 3. 窗口/显示 | `com.android.server.wm.DisplayRotation` | 是 | 否 | 否 | 是 |
| `system_noducking` | `SystemAudioHooks.NoDuckingHook` | 4. 音频/通知 | `com.android.server.audio.FocusRequester` | 是 | 否 | 否 | 是 |
| `system_cleanshare` | `SystemShareMenuHooks.CleanShareMenuServiceHook` | 5. 安全/安装 | `com.android.server.pm.ComputerEngine.queryIntentActivitiesInternal` | 是 | 否 | 否 | 是 |
| `system_cleanopenwith` | `SystemShareMenuHooks.CleanOpenWithMenuServiceHook` | 5. 安全/安装 | `com.android.server.pm.ComputerEngine.queryIntentActivitiesInternal` | 是 | 否 | 否 | 是 |
| `system_autobrightness` | `SystemDisplayHooks.AutoBrightnessRangeHook` | 3. 窗口/显示 | `com.android.server.display.AutomaticBrightnessController` | 是 | 否 | 否 | 是 |
| `system_autobrightness_reset_when_screenoff` | `SystemDisplayHooks.AutoBrightnessAfterScreenOffHook` | 3. 窗口/显示 | `com.android.server.display.DisplayPowerController` | 是 | 否 | 否 | 是 |
| `system_lockscreen_disable_strongauth_72h` | `SystemLockScreenHooks.Disable72hStrongAuthHook` | 2. 锁屏/认证 | `com.android.server.locksettings.LockSettingsStrongAuth` | 是 | 否 | 否 | 是 |
| `system_applock` | `SystemLockScreenHooks.AppLockHook` | 2. 锁屏/认证 | `com.miui.server.SecurityManagerService` | 是 | 否 | 否 | 是 |
| `system_applock_skip` | `SystemLockScreenHooks.SkipAppLockHook` | 2. 锁屏/认证 | `com.miui.server.AccessController` | 是 | 否 | 否 | 是 |
| `various_alarmcompat` | `Various.AlarmCompatServiceHook` | 4. 音频/通知 | `com.android.server.alarm.AlarmManagerService` | 是 | 否 | 否 | 是 |
| `system_ignorecalls` | `SystemAudioHooks.NoCallInterruptionHook` | 4. 音频/通知 | `com.android.server.audio.AudioService` | 是 | 否 | 否 | 是 |
| `system_forceclose` | `System.ForceCloseHook` | 1. 按键/手势 | `com.android.server.policy.BaseMiuiPhoneWindowManager` | 是 | 否 | 否 | 是 |
| `system_hideproxywarn` | `System.HideProximityWarningHook` | 4. 音频/通知 | `com.android.server.policy.MiuiScreenOnProximityLock` | 是 | 否 | 否 | 是 |
| `system_firstpress` | `SystemAudioHooks.FirstVolumePressHook` | 4. 音频/通知 | `com.android.server.audio.AudioService$VolumeController` | 是 | 否 | 否 | 是 |
| `system_apksign` | `SystemSecurityHooks.NoSignatureVerifyServiceHook` | 5. 安全/安装 | `android.util.jar.StrictJarVerifier` | 是 | 否 | 否 | 是 |
| `system_disableintegrity` | `SystemSecurityHooks.DisableSystemIntegrityHook` | 5. 安全/安装 | `android.util.apk.ApkSignatureVerifier` | 是 | 否 | 否 | 是 |
| `system_vibration_amp` | `SystemAudioHooks.MuffledVibrationHook` | 4. 音频/通知 | `com.android.server.VibratorService` | 是 | 否 | 否 | 是 |
| `system_clearalltasks` | `System.ClearAllTasksHook` | 3. 窗口/显示 | `com.android.server.wm.WindowProcessUtils.getPerceptibleRecentAppList` | 是 | 否 | 否 | 是 |
| `system_force_darken_allapps` | `SystemDisplayHooks.ForceDarkAllAppsHook` | 3. 窗口/显示 | `com.android.server.ForceDarkAppListProvider` | 是 | 否 | 否 | 是 |
| `system_lswallpaper` | `SystemLockScreenHooks.SetLockscreenWallpaperHook` | 2. 锁屏/认证 | `com.android.server.wallpaper.WallpaperManagerService` | 是 | 否 | 否 | 是 |
| `controls_powerflash` | `Controls.PowerKeyHook` | 1. 按键/手势 | `com.android.server.policy.PhoneWindowManager` | 是 | 否 | 否 | 是 |
| `controls_fingerprintfailure` | `Controls.FingerprintHapticFailureHook` | 1. 按键/手势 | `com.android.server.biometrics.sensors.AcquisitionClient` | 是 | 否 | 否 | 是 |
| `controls_fingerprintscreen` | `Controls.FingerprintScreenOnHook` | 1. 按键/手势 | `com.android.server.biometrics.sensors.AuthenticationClient` | 是 | 否 | 否 | 是 |
| `controls_fingerprintwake` | `Controls.NoFingerprintWakeHook` | 1. 按键/手势 | `com.android.server.policy.MiuiPhoneWindowManager` | 是 | 否 | 否 | 是 |
| `various_disableapp` | `Various.AppsDisableServiceHook` | 5. 安全/安装 | `com.android.server.pm.PackageManagerServiceImpl` | 是 | 否 | 否 | 是 |
| `system_disableanynotif` | `SystemNotificationHooks.DisableAnyNotificationBlockHook` | 5. 安全/安装 | `android.app.NotificationChannel` | 是 | 否 | 否 | 是 |
| `system_allrotations2` | `SystemWindowHooks.AllRotationsHook` | 3. 窗口/显示 | `com.android.server.wm.DisplayRotation` | 是 | 否 | 否 | 是 |
| `system_nolightuponcharges` | `SystemDisplayHooks.NoLightUpOnChargeHook` | 3. 窗口/显示 | `com.android.server.power.PowerManagerService` | 是 | 否 | 否 | 是 |
| `system_vibration` | `SystemAudioHooks.SelectiveVibrationHook` | 4. 音频/通知 | `com.android.server.vibrator.VibratorManagerService` | 是 | 否 | 否 | 是 |
| `system_blocktoasts` | `System.SelectiveToastsHook` | 4. 音频/通知 | `com.android.server.notification.NotificationManagerService.tryShowToast` | 是 | 否 | 否 | 是 |
| `controls_fingerprintsuccess` | `Controls.FingerprintHapticSuccessHook` | 1. 按键/手势 | `com.android.server.biometrics.sensors.AuthenticationClient` | 是 | 否 | 否 | 是 |
| `controls_volumemedia_up` / `controls_volumemedia_down` | `Controls.VolumeMediaButtonsHook` | 1. 按键/手势 | `com.android.server.policy.MiuiPhoneWindowManager` | 是 | 否 | 否 | 是 |
| `system_fw_splitscreen` | `SystemWindowHooks.MultiWindowPlusHook` | 3. 窗口/显示 | `com.android.server.wm.ActivityTaskManagerServiceImpl` | 是 | 否 | 否 | 是 |
| `system_fw_noblacklist` | `SystemWindowHooks.NoFloatingWindowBlacklistHook` | 3. 窗口/显示 | `com.android.server.wm.MiuiFreeformUtilImpl` | 是 | 否 | 否 | 是 |
| `various_disable_access_devicelogs` | `SystemSecurityHooks.NoAccessDeviceLogsRequest` | 5. 安全/安装 | `com.android.server.logcat.LogcatManagerService` | 是 | 否 | 否 | 是 |
| `system_other_wallpaper_scale` | `SystemDisplayHooks.WallpaperScaleLevelHook` | 3. 窗口/显示 | `com.android.server.wm.WallpaperController` | 是 | 否 | 否 | 是 |
| `various_allow_untrusted_touch` | `SystemWindowHooks.AllowUntrustedTouchHook` | 3. 窗口/显示 | `com.android.server.wm.WindowState` | 是 | 否 | 否 | 是 |

---

## 3. 说明

- **需完整重启**：`onSystemServerStarting` 在 `system_server` 启动时只执行一次，Hook 安装后不能在不重启的情况下重新安装。偏好开关本身运行时会被读取，但新开关开启后需重启才能生效。
- **偏好热更新**：所有业务 Hook 在运行时会从 `MainModule.mPrefs` 读取当前偏好以决定行为，因此行为侧支持运行时更新；Hook 的安装侧不支持热更新。
- **单元测试**：`MainModule.onSystemServerStarting` 的加载标记和入口行为有 `MainModuleSystemServerLoadMarkerTest.kt` 覆盖，但上表中的各业务 Hook 本身没有独立单元测试。
- **真机验证**：所有目标类/服务均为系统 ROM 类，无法在 JVM 单元测试或模拟器中验证，必须依赖真机 LSPosed 日志。

---

## 4. 未发现项核对

- `while(true)` / `Timer()` / `ScheduledExecutorService` / 常驻轮询线程：无。
- 无条件安装的高成本业务 Hook：无（`PackagePermissions` 除外，且为一次性静态字段写入）。
- 功能关闭时仍安装的 Hook：无。
- `android` 作用域替代 `system` 作用域：无；`system` 为 `onSystemServerStarting` 触发必要条件。

---

## 5. 风险定级更新

- **P0 resolved — `system_server` Global Action Receiver callback is now guarded**
  - `GlobalActionSystemServerHooks.setupGlobalActions()` 注册的 `phoneWindowManagerActionReceiver` 已将整个 `onReceive` 业务体包裹在 `ModuleHelper.guarded { ... }` 内。
  - 异常时调用 `XposedHelpers.log(t)`，有序广播设置 `GlobalActions.ACTION_FAILED`，不重新抛出。
  - 该修复仅增加顶层隔离，未改变 action 名称、权限、信任验证、正常业务结果或其他 Receiver。
  - 不声称此前发现过真实 `system_server` 崩溃，也不声称全部 Global Actions 已逐项真机验证。
