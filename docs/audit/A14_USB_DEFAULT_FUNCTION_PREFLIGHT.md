# A14 USB 默认功能架构 Preflight

## Scope

P1-A2 仅修正 P1-A 审计中的 architecture / ownership 理解，不做任何生产实现、资源、Preference、Feature ID、测试或构建变更。P1-B 仍未授权。

## 设备与 ROM 上下文

- 设备：Xiaomi 13 (`fuxi`)
- 目标 ROM：`HyperOS 1 OS1.0.7.0.UMCTWXM`（对应 MIUI 版本命名 `V816.0.7.0.UMCTWXM`）
- Android：14 / SDK 34
- 目标版本：`r14.20.0`

## Exact Target Artifact 状态

已尝试获取目标构建 `OS1.0.7.0.UMCTWXM` 的 system_server artifact：

1. `adb devices`：无已连接设备。
2. 本地文件系统：未找到 `OS1.0.7.0.UMCTWXM` 解包；仅有 `OS1.0.8.0.UMCTWXM_14.0` fastboot image set，不是目标构建。
3. 官方/镜像下载：从 `bigota.d.miui.com`、`hugeota.d.miui.com` 拉取 `miui_FUXITWGlobal_OS1.0.7.0.UMCTWXM_ad189913bf_14.0.zip` 均返回 `403 Forbidden`；`d.miwifi.com` 远程下载要求登录；未获得有效 artifact。

因此：

```text
HYPEROS_EXACT_USB_CHAIN = UNVERIFIED
EXACT_TARGET_ARTIFACT = UNAVAILABLE
P1_B_SAFE_TO_IMPLEMENT = NO
```

后续 P1-B 必须在取得 exact target `UsbDeviceManager` bytecode/smali 后，由独立 gate 重新评估。

## AOSP Android 14 默认 USB 调用链

### Settings → UsbBackend → UsbManager

```text
UsbDefaultFragment
→ UsbBackend.getDefaultUsbFunctions()
→ UsbManager.getScreenUnlockedFunctions()

UsbDefaultFragment.setDefaultKey(key)
→ UsbBackend.setDefaultUsbFunctions(functions)
→ UsbManager.setScreenUnlockedFunctions(functions)
```

- `UsbBackend.getDefaultUsbFunctions()` = `mUsbManager.getScreenUnlockedFunctions()`
- `UsbBackend.setDefaultUsbFunctions(long)` = `mUsbManager.setScreenUnlockedFunctions(long)`

### UsbManager → IUsbManager

- `UsbManager.setScreenUnlockedFunctions(long)` 调用 `IUsbManager.setScreenUnlockedFunctions(functions)`。
- `UsbManager.getScreenUnlockedFunctions()` 调用 `IUsbManager.getScreenUnlockedFunctions()`。
- `areSettableFunctions(long)` 仅允许 `FUNCTION_NONE` 或 `SETTABLE_FUNCTIONS` 中的单一位（或 `RNDIS | NCM`）。`FUNCTION_ADB` 不可设。

### IUsbManager → UsbService

- `UsbService.setScreenUnlockedFunctions(long)` 校验 `MANAGE_USB` 与 `areSettableFunctions`，调用 `mDeviceManager.setScreenUnlockedFunctions(functions)`。
- `UsbService.getScreenUnlockedFunctions()` 调用 `mDeviceManager.getScreenUnlockedFunctions()`。
- `UsbService.setCurrentFunctions(long, int)` 校验 `MANAGE_USB` 与 `areSettableFunctions`。

### UsbService → UsbDeviceManager → UsbHandler

- `UsbDeviceManager` 公开 `setScreenUnlockedFunctions(long)` 发送 `MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`。
- `UsbHandler` 处理持久化、屏幕解锁、USB 断开、用户切换、boot 完成等事件，最终调用 `setEnabledFunctions(...)` 应用 gadget。
- 真正“应用 native default”的最小边界是 `setEnabledFunctions` 调用链，而不是 Settings 或 `UsbManager` 公开 API。

## ADB 所有权

```text
ADB_COMBINATION_OWNER = SYSTEM
```

- `UsbManager.areSettableFunctions` 不允许设置 `FUNCTION_ADB`。
- `UsbHandler.getAppliedFunctions` 与 `UsbHandlerLegacy.applyAdbFunction` 按 `isAdbEnabled()` 自动加/减 ADB。
- 模块只选择主功能，不碰 ADB。

## FUNCTION_NONE 语义修正

| 字段 | 值 / 含义 |
|------|----------|
| `FUNCTION_NONE_PRIMARY_MASK` | `0` |
| `FUNCTION_NONE_USER_SEMANTICS` | `CHARGING_ONLY_NO_DATA`（仅充电，无数据传输） |
| `FUNCTION_NONE_SCREEN_UNLOCKED_SENTINEL` | `DISABLE_AUTO_UNLOCK_FUNCTION`（关闭屏幕解锁自动切换） |
| `LOW_LEVEL_CHARGING_FUNCTIONS_OWNER` | `SYSTEM`（`UsbHandler.getAppliedFunctions`/`getChargingFunctions` 决定最终 gadget） |

- `FUNCTION_NONE` 是合法配置，不能等同于 `FOLLOW_SYSTEM`。
- `setScreenUnlockedFunctions(0)` 让屏幕解锁时不再自动切到某个数据功能。
- 模块只产生 `FUNCTION_NONE` / `FUNCTION_MTP` / `FUNCTION_PTP` 这些 primary mask，不解释/构造 low-level charging gadget。

## 模块功能偏好

| 模块偏好值 | 语义 | effective primary function |
|------------|------|----------------------------|
| `0` | `FOLLOW_SYSTEM` | 当前 native default |
| `1` | `CHARGING` | `UsbManager.FUNCTION_NONE` |
| `2` | `MTP` | `UsbManager.FUNCTION_MTP`（`1 << 2` = `4`） |
| `3` | `PTP` | `UsbManager.FUNCTION_PTP`（`1 << 4` = `16`） |

`FOLLOW_SYSTEM` 永远等于当前 native default，不是 `FUNCTION_NONE`。

## `FOLLOW_SYSTEM` 可逆性

场景：

1. 原生默认 = PTP。
2. 模块偏好 `FOLLOW_SYSTEM` → effective = PTP。
3. 用户切到模块 MTP → effective = MTP。
4. 原生默认被 Settings 改为 CHARGING。
5. 模块仍 `FOLLOW_SYSTEM` → effective = CHARGING（最新原生值，不是旧 PTP）。
6. 切回 MTP → effective = MTP。

因此 `FOLLOW_SYSTEM` 不依赖旧 baseline，而是读取当前 native default。

## 选项分析

### Option A：Native persisted 架构

REJECT。模块不应把 `mSettings` 或 `mScreenUnlockedFunctions` 的 native 持久化改写成 module effective value。这会破坏：

- Settings 与 native default 的 ownership；
- `FOLLOW_SYSTEM` 的可逆性；
- 多用户/政策路径。

### Option B：Non-destructive runtime override

ACCEPT。模块只影响“native default 准备真正进入 `setEnabledFunctions`”的那一刻，不修改 `mSettings`、不修改 `mScreenUnlockedFunctions`、不伪造 `getScreenUnlockedFunctions`。

模块运行时状态：

```text
MODULE_RUNTIME_STATE = {
    system_usb_default_function: int (0-3)
}
```

映射函数：

```text
resolveModuleOverride(nativeDefault):
    if module == 0 (FOLLOW_SYSTEM): return nativeDefault
    if module == 1 (CHARGING):     return FUNCTION_NONE
    if module == 2 (MTP):          return FUNCTION_MTP
    if module == 3 (PTP):          return FUNCTION_PTP
```

边界调用：

```text
nativeDefault
→ resolveModuleOverride(nativeDefault)
→ setEnabledFunctions(effective, ...)
```

`mScreenUnlockedFunctions` 保持 ROM 原生值；`mSettings` 保持 ROM 原生值；`getScreenUnlockedFunctions()` 返回 ROM 原生值。

## Native State Invariants

```text
NATIVE_PERSISTED_STATE_MUTATED_BY_MODULE = NO
NATIVE_IN_MEMORY_STATE_MUTATED_BY_MODULE = NO
GET_SCREEN_UNLOCKED_FUNCTIONS_RETURNS_NATIVE = YES
```

- 模块不能把 MTP/PTP/CHARGING 写入 `mScreenUnlockedFunctions`。
- 不能 hook `getScreenUnlockedFunctions()` 返回 module effective value。
- 不能 Hook `setScreenUnlockedFunctions(long)` 公开入口并改写 `msg.obj` 为 effective value，否则 ROM 持久化会被 module 污染。

## 推荐架构（conceptual，exact method 等待 bytecode 验证）

```text
RECOMMENDED_ARCHITECTURE = OPTION_B_NON_DESTRUCTIVE_RUNTIME_OVERRIDE
DEFAULT_APPLICATION_BOUNDARY = UsbHandler.setScreenUnlockedFunctions(int) and/or handleMessage default-application branches
DEFAULT_APPLICATION_BOUNDARY_STATUS = HOLD_PENDING_EXACT_ARTIFACT
```

### Primary hook candidate

AOSP 中 `UsbHandler` 的 private/protected 方法：

```text
void setScreenUnlockedFunctions(int operationId)
```

它只调用：

```text
setEnabledFunctions(mScreenUnlockedFunctions, false, operationId)
```

Hook 该方法的 `before`：

1. 从 `param.thisObject` 读取 `mScreenUnlockedFunctions`（native default）。
2. 用 `resolveModuleOverride(nativeDefault)` 得到 effective。
3. 调用 `setEnabledFunctions(effective, false, operationId)`。
4. `returnAndSkip(null)` 跳过原方法，使 `mScreenUnlockedFunctions` 不被改写。

该 hook 只影响 ROM 主动重新应用 default 的路径：finishBoot、MSG_UPDATE_SCREEN_LOCK、MSG_UPDATE_STATE（disconnect）、MSG_SET_SCREEN_UNLOCKED_FUNCTIONS。

### Native FUNCTION_NONE edge case

AOSP 中，仅在 `mScreenUnlockedFunctions != FUNCTION_NONE` 时才调用 `setScreenUnlockedFunctions(int)`。当 native = `FUNCTION_NONE` 且模块偏好为 MTP/PTP 时，需要额外覆盖 ROM 进入 `setEnabledFunctions(FUNCTION_NONE, ...)` 的 `else` 分支。

候选方法：

```text
void handleMessage(Message msg)   // specific branches
void finishBoot(int operationId)
```

精确 message 和分支位置必须等 exact target bytecode 才能确认。在 exact artifact 取得之前，不猜测 message ID。

### Manual current function

- 用户通过系统 USB notification 或 Settings 临时切换 `current function` 时，ROM 走 `setCurrentFunctions` → `MSG_SET_CURRENT_FUNCTIONS` → `setEnabledFunctions(functions, false, ...)`。
- 该路径不进入 `setScreenUnlockedFunctions(int)`，因此不会被 primary hook 覆盖。
- 模块不要无条件 hook 所有 `setEnabledFunctions`，否则会破坏用户临时选择。

### Policy enforcement

- `MSG_UPDATE_USER_RESTRICTIONS` 在 `DISALLOW_USB_FILE_TRANSFER` 生效时调用 `setEnabledFunctions(FUNCTION_NONE, true, ...)`。
- 该路径不进入 `setScreenUnlockedFunctions(int)`。
- 如果 hook 设计无法区分 policy path，必须 REJECT 该设计。

## Settings 写覆盖场景

场景：

- native PTP；模块 MTP。
- 用户在 Android Settings 把 native default 改为 CHARGING。

必须保证：

```text
mSettings persisted default     = CHARGING
mScreenUnlockedFunctions        = CHARGING
getScreenUnlockedFunctions()    = CHARGING
module effective applied        = MTP (until module disabled)
```

当模块切 `FOLLOW_SYSTEM`：

```text
effective = current native = CHARGING
```

不是恢复旧 PTP。

## Live 模块偏好变化

- 使用项目已有 `PreferenceObserverRegistry` / `ModuleHelper.observePreferenceChange`。
- 变化时，通过 `UsbHandler` 的 `Handler` / `Looper` 发送已有消息（如 `MSG_UPDATE_SCREEN_LOCK`）让 ROM 重新评估，而不是从 observer 线程直接调用 USB 状态方法。
- 不新增 USB BroadcastReceiver、不轮询、不 Service。

```text
USB_CONNECTION_LISTENER = NONE
```

## 多用户修正

```text
NATIVE USB DEFAULT = PER_USER
MODULE system_usb_default_function = DEVICE_GLOBAL
MODULE_OVERRIDE_SCOPE = DEVICE_GLOBAL
MULTI_USER_LIMITATION = YES
```

- Android `UsbDeviceManager` 按 `usb-screen-unlocked-config-%d` 为每个用户保存 native default。
- 当前 CustoMIUIzer 偏好模型下，模块的 `system_usb_default_function` 是设备全局偏好。
- `FOLLOW_SYSTEM` 时每个用户回到自己的 native per-user default。
- `MTP/PTP/CHARGING` 时对当前用户生效，其他用户切换后仍按各自 native default 重新评估（若模块偏好仍为 override，则在新用户下同样 override）。
- 该限制对单用户自用场景可接受，但必须在审计中明确。

## 进程与生命周期

- 进程：`system_server`
- 安装点：`SystemServerInstaller` / `InstallPhase.SYSTEM_SERVER_STARTING`
- 偏好：`system_usb_default_function`，值 0-3，使用 `MainModule.mPrefs.getInt`。
- 无 SystemUI / Settings / 模块 app 侧 hook。

## 性能与失败模型

- 热路径只读 `MainModule.mPrefs` 内存快照，无 Binder/磁盘。
- DexKit / 反射只在 `SYSTEM_SERVER_STARTING` 使用。
- 失败：无法定位目标方法 / class / method mismatch / 不支持的 API → 放弃 mapping，原生行为继续。
- 偏好越界 → `FOLLOW_SYSTEM`。

## 拒绝的替代方案

1. **改写 `mSettings` 或 `mScreenUnlockedFunctions`**：污染 native ownership，伪造 `getScreenUnlockedFunctions`。
2. **Hook `UsbManager.setScreenUnlockedFunctions` / `IUsbManager.setScreenUnlockedFunctions` 公开 API**：只覆盖 Settings 显式写入，不覆盖 boot/用户切换/解锁/断开。
3. **无条件 Hook 所有 `setEnabledFunctions`**：会覆盖用户手动 current function、policy enforcement、tethering、accessory。
4. **写 `sys.usb.config` / `persist.sys.usb.config`**：破坏政策、多用户、OEM override、ADB 组合。
5. **模块 app 内调用 `UsbManager.setScreenUnlockedFunctions`**：无 `MANAGE_USB`。
6. **USB 插拔 BroadcastReceiver / 轮询 / Service**：违反无 listener 约束。

## HyperOS 精确验证状态

```text
HYPEROS_EXACT_USB_CHAIN = UNVERIFIED
HYPEROS_1_FUXI_USB_CHAIN = UNAVAILABLE (no exact build bytecode in hand)
HYPEROS_A14_COMMUNITY_SOURCE = REFERENCE_ONLY
```

- AOSP `android-14.0.0_r1` 链已验证。
- 第三方社区 A14 base（`Mrick-stuffs/frameworks_base/fourteen`）与 AOSP 在关键字段/方法名上一致，但不能作为 exact HyperOS 1 authority。
- 目标构建 `OS1.0.7.0.UMCTWXM` 未拿到真实 `services.jar` / bytecode / smali；因此不猜测 private method message ID。

## 与项目现有架构的衔接

- 新增 feature 应注册在 `SystemServerFeatures.all` / `SystemServerInstaller`，`FeatureTarget.SYSTEM_SERVER` + `InstallPhase.SYSTEM_SERVER_STARTING`。
- 偏好读取使用 `MainModule.mPrefs.getInt("system_usb_default_function", 0)`。
- Feature ID / 资源 / `prefs_system.xml` 等 P1-B 实现阶段处理；P1-A2 不修改。

## 最终报告字段

```text
P1_A_INITIAL_AUDIT_ENDPOINT = 0e3136751cadf463820bcb3981dae6372449892c
ANDROID14_NATIVE_DEFAULT_API = android.hardware.usb.UsbManager.setScreenUnlockedFunctions / android.hardware.usb.UsbManager.getScreenUnlockedFunctions
USB SERVICE OWNER = system_server / com.android.server.usb.UsbService + UsbDeviceManager
NATIVE DEFAULT PERSISTED = YES

FUNCTION_NONE_PRIMARY_MASK = 0
FUNCTION_NONE_USER_SEMANTICS = CHARGING_ONLY_NO_DATA
FUNCTION_NONE_SCREEN_UNLOCKED_SENTINEL = DISABLE_AUTO_UNLOCK_FUNCTION
LOW_LEVEL_CHARGING_FUNCTIONS_OWNER = SYSTEM

FUNCTION_MTP = 1 << 2 = 4
FUNCTION_PTP = 1 << 4 = 16

ADB_COMBINATION_OWNER = SYSTEM
USB_CONNECTION_LISTENER = NONE

FOLLOW_SYSTEM_REVERSIBLE = YES

OPTION_A = REJECT: 破坏 native persisted，无法安全恢复 FOLLOW_SYSTEM
OPTION_B = ACCEPT: 非破坏性运行时映射，保留 native 持久化与 in-memory field

RECOMMENDED_ARCHITECTURE = OPTION_B_NON_DESTRUCTIVE_RUNTIME_OVERRIDE

NATIVE_PERSISTED_STATE_MUTATED_BY_MODULE = NO
NATIVE_IN_MEMORY_STATE_MUTATED_BY_MODULE = NO
GET_SCREEN_UNLOCKED_FUNCTIONS_RETURNS_NATIVE = YES

DEFAULT_APPLICATION_BOUNDARY = UsbHandler.setScreenUnlockedFunctions(int) and/or handleMessage default-application branches (exact method TBD)
DEFAULT_APPLICATION_BOUNDARY_STATUS = HOLD_PENDING_EXACT_ARTIFACT

MANUAL_CURRENT_FUNCTION_OVERRIDE = NO
POLICY_OVERRIDE = NO

NATIVE_NONE_CUSTOM_MTP = REQUIRES_EXACT_ARTIFACT
NATIVE_NONE_CUSTOM_PTP = REQUIRES_EXACT_ARTIFACT

MODULE_OVERRIDE_SCOPE = DEVICE_GLOBAL
MULTI_USER_LIMITATION = YES

HYPEROS_EXACT_USB_CHAIN = UNVERIFIED
EXACT_TARGET_ARTIFACT = UNAVAILABLE
P1_B_SAFE_TO_IMPLEMENT = NO

PRODUCTION CHANGE = NO
fast --changed = <待运行>
diff --check = <待运行>
```
