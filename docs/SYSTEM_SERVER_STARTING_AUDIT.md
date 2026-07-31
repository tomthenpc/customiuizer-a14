# system_server Starting 审计

审计对象：`MainModule.onSystemServerStarting` 中 `GlobalActionSystemServerHooks.setupGlobalActions()` 注册的 `phoneWindowManagerActionReceiver`。

该 Receiver 运行 `system_server` 内，负责处理 CustoMIUIzer 自定义全局动作：`SimulateMenu`、`ForceClose`、`ToggleColorInversion`、`SwitchToPrevApp`。

---

## 历史风险定级

### P0 — 条件性发布阻塞项

`phoneWindowManagerActionReceiver.onReceive()` 中，业务分支（如 `ToggleColorInversion`、`SwitchToPrevApp`）仅对局部调用做了 try/catch，没有覆盖整个 Receiver 回调。

未捕获的 `RuntimeException`、`SecurityException`、反射异常、集合访问异常或 ROM API 异常可能逃逸到 `system_server`，导致 system_server 崩溃或系统软重启。

触发条件：用户启用任意 Global Actions 自定义动作，`GlobalActions.hasCustomActions()` 为 `true` 时 Receiver 才会注册。不是默认必现，但后果严重。

---

## 修复

### P0 resolved — system_server Global Action Receiver callback is now guarded

在 `app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt` 中：

- 将整个 `phoneWindowManagerActionReceiver.onReceive()` 业务体包裹在 `ModuleHelper.guarded { ... }` 内。
- 正常执行完成后，仍对有序广播设置 `GlobalActions.ACTION_HANDLED`。
- 若 `ModuleHelper.guarded` 捕获到任何 `Throwable`：
  - 调用 `XposedHelpers.log(t)`；
  - 对有序广播设置 `GlobalActions.ACTION_FAILED`；
  - 不重新抛出，异常不会传播到 `system_server`。
- `ModuleHelper.isTrustedBroadcast` 的拒绝行为保持不变：非信任发送者仍会得到 `ACTION_FAILED`。
- 未修改其他 Receiver、未修改 action code、未修改权限、未修改 scope.list、未修改 `initPrefs` 或 `HookDiagnostics`。

---

## 验证

- 新增 `app/src/test/java/tv/withaibuild/customiuizer/GlobalActionSystemServerReceiverSafetyTest.kt`：
  - 确认 `onReceive` 顶层存在 `ModuleHelper.guarded` 边界；
  - 确认未捕获异常不会重新抛出；
  - 确认有序广播失败路径使用 `ACTION_FAILED`；
  - 确认成功路径仍使用 `ACTION_HANDLED`；
  - 确认 `isTrustedBroadcast` 信任验证仍存在；
  - 确认 SystemUI 的 `statusBarActionReceiver`、`fastRebootReceiver`、`freeformModeReceiver` 等未被改动。
- 通过 `python tools/check-invariants.py`。
- 通过 `python -m unittest discover -s tools/tests -p "test_*.py"`。
- 通过 `gradlew test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease`。

---

## 明确边界

- 不声称此前或修复后发现过真实 `system_server` 崩溃日志。
- 不声称全部 Global Actions 已逐项在真机验证。
- `r14.13.9` 已完成 `system` 作用域、`system_server`、`SystemUI`、`Launcher` 和 `Toast` 的真机核心验证。
- `r14.15.0` 在相同运行时代码基线上增加了 `system_server` Receiver 的顶层异常隔离；该隔离不改变正常业务结果。
