# FIX-A14-STATUS-BAR-HEIGHT-LIVE-APPLICATION

- Platform: A14
- Status: Active
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

让状态栏高度修改后实时、可靠地应用到 SystemUI 状态栏窗口、WindowManager 布局和 `statusBars` Insets，而不需要完整重启手机。

## 根因

- `StatusBarHeightConfig.configure` 使用 `Resources.getSystem()`，在 fuxi 上得到 `densityDpi=440`/`density=2.75`，但目标 display 的有效逻辑密度是 `densityDpi=469`/`density=2.93125`。
- 因此 `configuredPx=121`（44×2.75），而 WindowManager 实际状态栏窗口高度是 `129`（44×2.93125）。
- `InsetsSource.setFrame` Hook 只观察到 `type=128` 的 displayCutout source，未观察到 `type=1` 的 statusBars source，说明当前 ROM 的状态栏 insets 权威更新边界不是 `InsetsSource.setFrame()`。
- 偏好变化后没有通知 system_server 重新 layout，也没有触发布局刷新。

## 实现方向

1. `StatusBarHeightConfig` 改为基于目标 display 的 `DisplayMetrics` 计算 px，并增加 `generation` 字段。
2. 在 `system_server` 通过 `com.android.server.wm.DisplayPolicy.layoutWindowLw` 或 `WindowState.setFrames` 识别状态栏 `WindowState`，直接调整其 frame 高度。
3. 通过 `PreferenceObserver` 监听 `system_statusbarheight` 变化，更新 `StatusBarHeightConfig` 并触发一次安全的 WindowManager 重新 layout。
4. 保留 `InsetsSource.setFrame` 作为 fallback，但不再作为主要边界。
5. 增加 `[StatusBarHeightLive]` 有界诊断，记录 displayId/density/height 变化。

## 验证

- `python tools/verify.py fast --changed`
- `python -m compileall tools`
- `python -m unittest discover -s tools/tests -p "test_*.py"`
- `.​\gradlew.bat :app:testDebugUnitTest`
- `python tools/verify.py full`
- `git diff --check`

## 实机状态

NOT DEVICE_VERIFIED
