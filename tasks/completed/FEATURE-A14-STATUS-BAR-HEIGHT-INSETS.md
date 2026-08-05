# FEATURE-A14-STATUS-BAR-HEIGHT-INSETS

- Platform: A14
- Status: Completed
- Priority: P0
- Owner: Devin
- Related version: A14
- Cross-repo task: no

## 目标

完整实现 A14 状态栏高度与 WindowInsets 同步：

```text
用户配置的状态栏高度
= Android framework 状态栏资源高度
= SystemUI 实际状态栏高度
= WindowManager statusBars InsetsSource 高度
= 应用收到的 statusBars WindowInsets 高度
```

## 关键改动

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarHeightConfig.kt`
  - 统一 `system_statusbarheight` dp 解析、px 缓存和 enabled 判断。
  - `dpToPx`、`resolveHeightDp`、`configure`、`recomputePx` 均为冷路径调用。

- `app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt`
  - `StatusBarHeightHook` 复用 `StatusBarHeightConfig`，保持 `status_bar_height*` 资源替换。

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt`
  - 新增 system_server InsetsSource.setFrame hook。
  - 仅对 status bar 类型源修改 `Rect.bottom`。
  - 几何逻辑 `computeStatusBarFrameBottom` 提取为纯函数，保留原 bottom 下限以维护 cutout-safe。
  - ROM 无法解析时记录 unsupported 并跳过。

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt`
  - 新增 `StatusBarHeightInsetsFeatureId` (id=245)。

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt`
  - 新增 `StatusBarHeightInsetsFeature` 并注册到 `SystemServerFeatures.all()`。
  - 同步更新 `SystemServerFeaturesWiringTest` 计数（50 features）。

- 新增测试
  - `StatusBarHeightConfigTest`
  - `StatusBarInsetsGeometryTest`
  - `StatusBarInsetsRoutingTest`

## 验证

- `python tools/verify.py full` PASS
- `git diff --check` PASS
- `gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL

## 构建产物

- `app/build/outputs/apk/debug/app-debug.apk`

## 完成记录

- Base SHA: cd935311621c07e660c159191302524c6795a177
- Final SHA: c07061e6
- Device evidence: 未在实机验证；依赖 AOSP 14 `android.view.InsetsSource` / `WindowInsets.Type.statusBars()` 契约。
- Known limits:
  - 当前几何逻辑以 `max(originalBottom, configuredPx)` 为底线，不支持把高度缩到低于系统当前计算值以下。
  - Insets 类型检测同时兼容 public mask 与 internal index 两种编码，极端 ROM 依赖 `isTopAnchored` 兜底。
