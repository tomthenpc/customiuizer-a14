# FIX-A14-STATUS-BAR-HEIGHT-LIVE-APPLICATION

- Platform: A14
- Status: Completed
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

让状态栏高度修改后实时、可靠地应用到 SystemUI 状态栏窗口、WindowManager 布局和 `statusBars` Insets，而不需要完整重启手机。

## 根因

- `StatusBarHeightConfig` 直接修改 `mWindowFrames.mDisplayFrame` 的先前实现越过了 WindowManager 的 frame 所有权边界；`mDisplayFrame`/`mParentFrame`/`mRelFrame`/`mCompatFrame` 都不应被外部修改。
- `performSurfacePlacement` 被直接从 PreferenceObserver 线程调用，可能持有错误的锁或在错误的线程上触发 surface placement。
- `StatusBarHeightConfig` 的 generation 在相同 preference / 相同 density 下也会增长，导致无意义刷新和诊断膨胀。
- 刷新路径没有按 display 重新计算 density，`configuredPx` 在 fuxi 等机器上使用错误 density。

## 实现方向

1. `StatusBarHeightConfig` 增加 `State` / `ReconfigureResult` 快照，基于目标 display 的 `DisplayMetrics` 计算 px，`generation` 只在真正状态变化时递增。
2. 在 `DisplayPolicy.layoutWindowLw` 的 `before` 路径中，对 status bar `WindowState` 调整 `mAttrs.height`，让原生的 `WindowLayout.computeFrames` 和 `WindowState.setFrames` 写出正确的 `mFrame`。
3. `WindowState.setFrames` 只作为 fallback：只修改 `ClientWindowFrames.frame.bottom = frame.top + configuredPx`，并保留 `left/top/right`，不碰 `displayFrame`/`parentFrame`。
4. `PreferenceObserver` 更新配置后，通过 `WindowSurfacePlacer.requestTraversal()` 请求一次安全的重新 layout；不能直接 `performSurfacePlacement`。
5. 诊断日志 key 全部包含 generation：`preference-change:<gen>`、`layout:<displayId>:<gen>`、`frame:<displayId>:<gen>`、`insets:<sourceId>:<gen>`、`refresh:<gen>`。

## 提交

- Engineering: `48e63d40` FIX-A14-STATUS-BAR-HEIGHT-LIVE-APPLICATION: correct frame ownership, traversal threading and generation diagnostics.
- Closure: archive closure record with build provenance.
- Base SHA: `15bbe615`
- Final SHA: `48e63d40`

## 验证

- `python tools/verify.py fast --changed` — PASS
- `python tools/verify.py fast --tests "*StatusBarHeight*"` — PASS
- `python tools/verify.py fast --tests "*StatusBarInsets*"` — PASS
- `python tools/verify.py full` — PASS
- `git diff --check` — PASS
- `.\gradlew.bat :app:assembleDebug` — PASS

## 产物

- APK: `app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk`
- APK SHA-256: `8EF0ADF16692582B3FF2143B7FE286FD774ABB0B5AD9E7C41F35554285944EDD`
- Build revision: `48e63d40`
- Signature: Debug

## 实机状态

NOT DEVICE_VERIFIED
