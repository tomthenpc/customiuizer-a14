# r14.13 Phase 5 热路径审计与收口

分支：`devin/r14.13-kotlin-refactor`

审计日期：2026-07-26

## 审计范围

重点覆盖 `system_server`、`com.android.systemui`、`com.miui.home`、状态栏、
控制中心、通知、锁屏、网络速度、电池/图标、音频回调、动画和触摸、高频 Hook callback。

## 审计方法

- 全仓库搜索 `String.format`、DexKit/反射调用、临时集合、`mPrefs` 高频读取、
  `registerReceiver`/`Handler`/`Runnable` 生命周期等模式；
- 对嫌疑代码按以下原则判断：
  - 是否位于高频 callback（per-frame、per-tick、per-gesture-move）；
  - 是否存在明确正确性缺陷；
  - 是否可证明减少重复反射、分配或遍历；
  - 改动是否保持 Hook target、priority、before/after、参数、返回值、异常传播和 unhook 语义。

## 已修复项

### 1. 网络速度 `String.format` 默认 Locale 正确性

- **位置**：`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUI.kt`
  `humanReadableByteCount`
- **目标进程**：`com.android.systemui`（网络速度 `NetworkSpeedView.updateText`）
- **调用频率**：每个网络速度刷新 tick（默认 4s，可改为 1s）
- **问题**：`String.format("%.1f", f)` / `String.format("%.0f", f)` 使用默认 Locale，
  在某些语言环境下会产生 `3,5 KB/s` 这类逗号小数分隔符，导致状态栏显示异常；
  同时每次调用产生 2~3 次 `String.format`/`Formatter` 分配。
- **修改**：统一使用 `String.format(Locale.ROOT, ...)`，并将 `String.format` 调用合并为 2 次，
  单位前缀与后缀改为普通字符串拼接。
- **Hook 语义**：未改变 target、priority、before/after、参数、返回值或异常传播。
- **commit**：`773e57dc`

### 2. 状态栏手势中 `mDisplayManager`/`mDisplayId` 重复反射

- **位置**：`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUI.kt`
  `StatusBarGesturesHook`
- **目标进程**：`com.android.systemui`
- **Hook target**：
  - `PhoneStatusBarView.onInterceptTouchEvent` / `onTouchEvent`
  - `ControlCenterWindowViewImpl.handleMotionEvent`
- **调用频率**：`ACTION_MOVE` 在滑动亮度/音量手势期间高频触发（每帧可能一次）
- **原路径成本**：每次 `ACTION_MOVE` 和 `ACTION_UP` 都通过
  `XposedHelpers.getObjectField(mBrightnessController, "mDisplayManager")` 和
  `XposedHelpers.getIntField(mBrightnessController, "mDisplayId")` 反射取字段。
- **修改后成本**：在 `ACTION_DOWN` 中 `mBrightnessController` 首次获取时一并缓存
  `mDisplayManager` 和 `mDisplayId` 为 hook 对象字段；`ACTION_MOVE`/`ACTION_UP` 直接使用缓存。
- **Hook 语义**：保持；仅在 `mBrightnessController` 创建时缓存其字段，手势期间对象不变。
- **实机需验证**：状态栏左右滑动调节亮度/音量、长按状态栏动作、双击状态栏动作。
- **commit**：`453a8bc2`

## 审计后未修改项（记录备查）

| 位置 | 现象 | 未修改理由 |
| --- | --- | --- |
| `SystemUIMonitorAndTileHooks.kt` 电池/温度格式化 | 每 2s 使用 `String.format(Locale.ROOT, ...)` | 已显式使用 `Locale.ROOT`，无正确性问题；频率低，主成本在 sysfs 文件读取，不是字符串格式化 |
| `System.kt` 充电信息格式化 | 锁屏充电提示更新时使用 `String.format(Locale.US, ...)` | 已显式使用 `Locale.US`；主成本在 `/sys/class/power_supply/battery/uevent` 文件读取 |
| `System.kt`/`SystemUI.kt` 多处 `MainModule.mPrefs.get*` | 高频 callback 内读取 `mPrefs` | `mPrefs` 是本地 `PrefMap`（内存 HashMap），get 为 O(1)，不构成 IPC/磁盘访问；
  缓存需处理 Remote Preference 变更失效，收益/风险比不足 |
| `SystemUI.kt` `XposedHelpers.getObjectField(..., "mContext")` | 大量 callback 通过反射取 Context | 改动面过大且 `mContext` 多为高频初始化/回调一次性使用；
  未找到每帧重复取同一 `mContext` 的明确热点 |
| `SystemClockHooks.kt` `SecondTicker` | 每秒调用 `DateFormat.is24HourFormat` | 该值通常被 Android 框架缓存；频率为 1Hz，不构成热路径瓶颈 |
| `Launcher.kt` 手势/双击 | `GestureDetector`、`DoubleTapController` 状态管理正常 | 无重复注册或明显泄漏；无 per-frame 反射热点 |
| `System.kt` 通知过滤/展开 | `ExpandNotificationsHook`、`ExpandHeadsUpHook` 中使用 `getStringSet().contains(pkgName)` | `PrefMap.getStringSet` 返回已存 `Set` 引用，`contains` 为 O(1)；
  触发频率为通知变化，非 per-frame |

## 最终验证

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME='c:\Users\tv\Downloads\Peengeek\.tools\android-sdk'
.\gradlew --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease
```

- 结果：全部通过
- 单元测试：36 个
- Java 源文件：3 个
- Kotlin 源文件：88 个
- Debug APK：13,255,575 bytes，SHA-256 `DD55636B9F410F08C19D5F12128F67248C3DEB67B5220AE583E1CD4271A3DB07`
- Release APK：3,020,249 bytes，SHA-256 `82265AAEB106BECC0B90DB1F1DBA36D3C1E0BE436264645EE169F3C78AE3AE6F`

## 限制

- 性能改进为静态分析得出，未进行实机 systrace/Profiler 测量；
- 状态栏手势亮度/音量滑动为实机回归重点；
- 网络速度显示需在非英语 Locale 下验证不出现逗号小数点。
