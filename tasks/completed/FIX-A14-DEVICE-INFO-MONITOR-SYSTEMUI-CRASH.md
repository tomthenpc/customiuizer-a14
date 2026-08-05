# FIX-A14-DEVICE-INFO-MONITOR-SYSTEMUI-CRASH

- Platform: A14
- Status: Completed
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

修复或系统性封闭启用 `system_statusbar_batterytempandcurrent` 和 `system_statusbar_showdevicetemperature` 后可能导致 SystemUI 崩溃的问题。

## 主要代码范围

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`（已审计，无代码改动，现有生成清理机制被复用）

## 调用链

完整跟踪：

```text
Feature 安装
→ DeviceInfoMonitor.hook
→ NetworkSpeedController constructor hooks
→ 自定义 icon slot 创建
→ IconManager.addHolder
→ createStatusbarTextIcon
→ registerStatusbarTextIcon
→ 后台温度/电流读取
→ main Handler
→ updateStatusbarTextIcons
→ View 更新、替换和销毁
```

## 找到的具体崩溃路径与封闭措施

1. **NetworkSpeedController 多次构造导致旧 Handler 继续更新**
   - 新 `DeviceInfoMonitorState` 管理 handler generation；每个 `NetworkSpeedController` 创建时 `startNewGeneration()`，旧 handler 在 `handleMessage` 开头检查 `isActive(id)`，若已过期则移除消息并返回。
   - `doMonitorTick` 在读取、发布、调度前多次校验 generation，拒绝旧 generation 更新。

2. **旧 View 在 statusbar 重建/横竖屏/主题切换后继续更新**
   - `SystemUIStatusBarHooks.updateStatusbarTextIcons` 现在跳过 `!isAttachedToWindow` 的 View 并将其从 `statusbarTextIcons` 移除；
   - `setNetworkSpeed` / `setVisibilityByController` 调用失败时回退到直接设置 `View.visibility` 与 `network_speed_number` tag TextView 的 text，避免直接崩溃。

3. **`IconManager.addHolder` hook 中异常导致原方法带着自定义 type 91/92 继续执行**
   - 原 `addHolder` 对未知 type 返回 null，`onIconAdded` 会 NPE/ClassCastException。
   - 新 `interceptAddHolder` 对自定义 type 91/92 做完整 try/catch；任何反射/ROM 兼容失败都 `returnAndSkip(null)` 并记录日志，不再把异常抛给 SystemUI。
   - `hook` 时先探测 `NetworkSpeedView` 类；类不存在则直接跳过功能安装，避免后续自定义 slot 触发崩溃。

4. **`createStatusbarTextIcon`  inflated 失败或子 View 缺失导致 NPE**
   - 添加非空检查：`mNumber` / `mUnit` 不存在时跳过 `setObjectField`。
   - `getIconTextView` 改为可空，并在 `initStatusbarTextIcon` 中处理缺失情况。

5. **sysfs / ROM 反射异常可能逃逸**
   - `DeviceInfoFormatter` 提取为纯函数，I/O 与解析错误隔离并返回 `null`/fallback；
   - `readBatteryProps` / `readCpuTemp` / `shouldShowBatteryInfo` 继续抛出 OOM，其他 Throwable 吞掉；
   - `consecutiveFailCount` 改为 `AtomicInteger`，避免多线程竞争。

6. **Receiver 与 Context 生命周期**
   - `startScreenReceiverLocked` / `stopScreenReceiverLocked` 维持单次注册/释放闭环；
   - `stopMonitoring` 清理 handler、receiver、context 并重置 `DeviceInfoMonitorState`。

## 新增测试

- `DeviceInfoFormatterTest.kt`：格式化、内容选项、单位隐藏、单双排、反转、malformed/空值。
- `DeviceInfoMonitorStateTest.kt`：handler generation 单调递增、旧 generation 拒绝、stop 后 inactive、失败回退 delay。
- `DeviceInfoMonitorBoundaryTest.kt`：sysfs 读取失败、空/异常 properties 安全退化、parse overflow。

## 验证

- `python tools/verify.py fast --changed`：PASS
- `python tools/verify.py full`：PASS
- `.\gradlew.bat :app:testDebugUnitTest`：PASS
- `git diff --check`：PASS
- `.\gradlew.bat :app:assembleDebug`：PASS

## 完成记录

- Base SHA: c359bc79ee740a49bd0ad2af00f3d1f7042c8dc9
- Final SHA: 4e126c7b9981b5b642c19c6188e90b81305e817b
- Commits: 4e126c7b
- Behavior changed:
  - `DeviceInfoMonitor` handler 按 generation 失效；
  - `addHolder` 对自定义 type 91/92 增加完整 try/catch 与 null fallback；
  - `NetworkSpeedView` 类探测与跳过；
  - `updateStatusbarTextIcons` 清理 detached View 并带方法缺失 fallback；
  - `DeviceInfoFormatter` 独立且可测试；
  - `consecutiveFailCount` 原子化。
- Verification: PASS
- Device evidence: 无实机日志；静态/单元/构建验证通过。
- Known limits:
  - `StatusBarDisplayRegistry` 未改动，已通过现有测试验证生成隔离；
  - 实机 `addHolder` 真实参数签名、HyperOS 1 具体 `NetworkSpeedView` 字段/方法、自定义 type 被 StatusBarIconController 处理路径仍需实机日志确认；
  - 若 `NetworkSpeedView` 类名或布局资源完全不可用，功能会静默跳过。
