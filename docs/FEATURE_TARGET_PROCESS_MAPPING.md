# 功能 — 偏好键 — 目标进程 — 生效重启要求

本文件是 `hardening/a14-lts-foundation` 分支的起始映射，后续应以自动化脚本从 `MainModule.java`、`PreferenceFragmentBase.kt` 和 `res/xml/*.xml` 增量补全。

## 说明

- **偏好键 (Preference Key)**: `MainModule.mPrefs` 中使用的键。
- **目标进程 (Target Process)**: 实际运行 hook 的进程。
- **重启要求 (Restart Requirement)**:
  - `软重启 (Soft Reboot)`: 发送 `GlobalActions.ACTION_PREFIX + "FastReboot"`，重启 SystemUI / Launcher / system_server 相关进程。
  - `SystemUI 重启`: 结束 `com.android.systemui`。
  - `Launcher 重启**: 强停 `com.miui.home`。
  - `立即生效**: 设置应用自身调整，无需重启。

## 全局操作 (Global Actions)

| 功能 / 动作 | 偏好键后缀 | 目标进程 | 生效重启要求 | 备注 |
| --- | --- | --- | --- | --- |
| FastReboot | （无，硬编码） | `com.android.systemui` | 软重启 | 通过有序广播触发 SystemUI 内模块 reboot |
| RestartSystemUI | `_action=18` | `com.android.systemui` | 立即 | 结束 SystemUI 进程 |
| RestartLauncher | `_action=19` | `com.miui.home` | 立即 | 强停 Launcher |
| RestartSecurityCenter | `_action=...` | `com.miui.securitycenter` | 立即 | 强停安全中心 |
| LockDevice | `_action=4` | `com.android.systemui` | 立即 | 调用 PowerManager.goToSleep |
| TakeScreenshot | `_action=6` | `com.android.systemui` | 立即 | 发送 `CAPTURE_SCREENSHOT` |
| GoToSleep | `_action=5` | `com.android.systemui` | 立即 | 息屏 |
| ExpandNotifications | `_action=2` | `com.android.systemui` | 立即 | |
| ExpandSettings | `_action=3` | `com.android.systemui` | 立即 | |
| OpenRecents | `_action=7` | `com.android.systemui` | 立即 | |
| OpenVolumeDialog | `_action=17` | `com.android.systemui` | 立即 | |
| ToggleGPS | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleWiFi | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleBluetooth | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleNFC | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleSoundProfile | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleAutoRotation | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleMobileData | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleHotspot | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleZenMode | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleFlashlight | `_action=...` | `com.android.systemui` | 立即 | |
| ToggleNightMode | `_action=...` | `com.android.systemui` | 立即 | |
| VolumeUp / VolumeDown | `_action=18/19` | `com.android.systemui` | 立即 | |
| SimulateMenu | `_action=16` | `android` (system_server) | 立即 | 注入 menu 键事件 |
| ForceClose | `_action=24` | `android` (system_server) | 立即 | 调用 phone window manager closeApp |
| SwitchToPrevApp | `_action=11` | `android` (system_server) | 立即 | |
| WakeUp | `_action=...` | `com.android.systemui` | 立即 | 指纹亮屏等 |

## 状态栏 / 控制中心

| 功能 | 偏好键 | 目标进程 | 生效重启要求 |
| --- | --- | --- | --- |
| 状态栏图标隐藏 | `system_statusbaricons_*` | `com.android.systemui` | 软重启 |
| 状态栏高度 | `system_statusbarheight` | `com.android.systemui` | 软重启 |
| 状态栏电池/温度显示 | `system_batterytemp_*` | `com.android.systemui` | 无需 SystemUI 重启（A14 重构后） |
| 5G 磁贴 | `system_fivegtile` | `com.android.systemui` | 软重启 |
| FPS 磁贴 | `system_cc_fpstile` | `com.android.systemui` | 软重启 |
| 悬浮时间磁贴 | `system_cc_floatingtimetile` | `com.android.systemui` | 软重启 |

## 锁屏 / 安全

| 功能 | 偏好键 | 目标进程 | 生效重启要求 |
| --- | --- | --- | --- |
| 跳过锁屏 | `system_noscreenlock_*` | `com.android.systemui` | 软重启 |
| 信任 Wi-Fi / 蓝牙自动解锁 | `system_noscreenlock` | `com.android.systemui` | 软重启 |

## 启动器

| 功能 | 偏好键 | 目标进程 | 生效重启要求 |
| --- | --- | --- | --- |
| 隐私应用手势 | `launcher_privacyapps_gest` | `com.miui.home` | 软重启 |
| 隐藏应用 | `launcher_privacyapps_list` | `com.miui.home` | 无需（列表动态读取） |
| 文件夹模糊 | `launcher_folderblur_*` | `com.miui.home` | 软重启 |
| 解锁壁纸缩放 | `launcher_disable_wallpaperscale` | `com.miui.miwallpaper` | 软重启 |

## 下一步

- 编写 `tools/generate-feature-mapping.py`，从 `MainModule.java` 的 `if (mPrefs.get...)` 语句和 `lpparam.packageName` 提取大部分条目。
- 通过 `res/values/strings.xml` 中的 `needs_*_restart` 字符串自动标注部分重启要求。
- 对映射做人工复核：同一个偏好键可能在多个进程消费（如 `system_statusbarheight` 仅在 SystemUI）。
