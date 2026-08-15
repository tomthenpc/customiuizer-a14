# A14 USB 默认功能架构 Preflight

## Scope

P1-A 仅做审计，不做任何生产实现、资源、Preference、Feature ID、测试或构建变更。
本文档确认 Android 14 / HyperOS 1 默认 USB 功能链、hook 边界、可逆语义，并给出 P1-B 之前必须被独立门接受的架构决定。

## 设备与 ROM 上下文

- 设备：Xiaomi 13 (`fuxi`)
- ROM：HyperOS 1 `V816.0.7.0.UMCTWXM`
- Android：14 / SDK 34
- 目标版本：`r14.20.0`

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

精确源码：

- `packages/apps/Settings/+/android-14.0.0_r1/src/com/android/settings/connecteddevice/usb/UsbBackend.java`
  - 第 95-97 行：`getDefaultUsbFunctions()` 直接委托 `mUsbManager.getScreenUnlockedFunctions()`
  - 第 99-101 行：`setDefaultUsbFunctions(long)` 直接委托 `mUsbManager.setScreenUnlockedFunctions(long)`
- `packages/apps/Settings/+/android-14.0.0_r1/src/com/android/settings/connecteddevice/usb/UsbDefaultFragment.java`
  - 第 155-161 行：`getDefaultKey()` 使用 `mUsbBackend.getDefaultUsbFunctions()`
  - 第 164-180 行：`setDefaultKey(String)` 调用 `mUsbBackend.setDefaultUsbFunctions(functions)`

### UsbManager → IUsbManager

- `core/java/android/hardware/usb/UsbManager.java`（AOSP `android-14.0.0_r1`）
  - 第 1252 行：`public void setScreenUnlockedFunctions(long functions)` 调用 `mService.setScreenUnlockedFunctions(functions)`
  - 第 1268 行：`public long getScreenUnlockedFunctions()` 调用 `mService.getScreenUnlockedFunctions()`
  - 第 1764-1770 行：`areSettableFunctions(long)` 仅允许 `FUNCTION_NONE` 或 `SETTABLE_FUNCTIONS` 中的单一位，或 `FUNCTION_RNDIS | FUNCTION_NCM`。
  - `SETTABLE_FUNCTIONS` 为 `FUNCTION_MTP | FUNCTION_PTP | FUNCTION_RNDIS | FUNCTION_MIDI | FUNCTION_NCM | FUNCTION_UVC`。
  - `FUNCTION_ADB` 不在可设掩码内。

### IUsbManager → UsbService

- `services/usb/java/com/android/server/usb/UsbService.java`（AOSP `android-14.0.0_r1`）
  - 第 654-660 行：`setScreenUnlockedFunctions(long)` 校验 `MANAGE_USB` 与 `areSettableFunctions`，然后调用 `mDeviceManager.setScreenUnlockedFunctions(functions)`
  - 第 662-667 行：`getScreenUnlockedFunctions()` 返回 `mDeviceManager.getScreenUnlockedFunctions()`
  - 第 629-634 行：`setCurrentFunctions(long, int)` 校验 `MANAGE_USB` 与 `areSettableFunctions`

### UsbService → UsbDeviceManager → UsbHandler

- `services/usb/java/com/android/server/usb/UsbDeviceManager.java`（AOSP `android-14.0.0_r1`）
  - 第 2411-2413 行：`getScreenUnlockedFunctions()` 返回 `mHandler.getScreenUnlockedFunctions()`
  - 第 2420-2441 行：`setCurrentFunctions(long, int)` 发送 `MSG_SET_CURRENT_FUNCTIONS`
  - 第 2448-2454 行：`setScreenUnlockedFunctions(long)` 发送 `MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`
  - 第 119-128 行：持久化文件 `UsbDeviceManagerPrefs.xml`，per-user key `usb-screen-unlocked-config-%d`
  - 第 613-617 行：构造时从 `mSettings.getString(...)` 读取 `mScreenUnlockedFunctions`
  - 第 952-954 行：私有方法 `setScreenUnlockedFunctions(int operationId)` 调用 `setEnabledFunctions(mScreenUnlockedFunctions, false, operationId)`
  - 第 1131-1147 行：`MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`：
    - 写 `mScreenUnlockedFunctions = (Long) msg.obj;`
    - `mSettings.edit().putString(...).commit()` 持久化
    - 若 `!mScreenLocked && mScreenUnlockedFunctions != FUNCTION_NONE` 调用 `setScreenUnlockedFunctions(operationId)`
    - 否则 `setEnabledFunctions(FUNCTION_NONE, ...)`
  - 第 1148-1168 行：`MSG_UPDATE_SCREEN_LOCK` 在解锁且 `mScreenUnlockedFunctions != FUNCTION_NONE && mCurrentFunctions == FUNCTION_NONE` 时调用 `setScreenUnlockedFunctions(operationId)`
  - 第 1005-1015 行：`MSG_UPDATE_STATE` 在 USB 断开、已解锁且 `mScreenUnlockedFunctions != FUNCTION_NONE` 时恢复 `setScreenUnlockedFunctions(operationId)`
  - 第 1256-1268 行：`finishBoot()` 在已解锁且 `mScreenUnlockedFunctions != FUNCTION_NONE` 时调用 `setScreenUnlockedFunctions(operationId)`
  - 第 1206-1222 行：`MSG_USER_SWITCHED` 切换用户时按新用户 key 重载 `mScreenUnlockedFunctions`

### ADB 组合机制

- `UsbHandler.getAppliedFunctions(long)` 第 976-984 行：
  - `functions == FUNCTION_NONE` → `getChargingFunctions()`
  - 否则若 `isAdbEnabled()` → `functions | FUNCTION_ADB`
- `UsbHandlerLegacy.applyAdbFunction(String)` 第 1860-1872 行：根据 `isAdbEnabled()` 在字符串中 add/remove `"adb"`。
- `UsbHandlerLegacy.getChargingFunctions()` 第 1501-1509 行：若 ADB 启用返回 `FUNCTION_ADB`，否则返回 `FUNCTION_MTP`。

## 功能表

| 模块偏好值 | 语义 | 对应 `UsbManager` 常量 | 字面值 |
|------------|------|-------------------------|--------|
| `0` | 跟随系统默认 | `FOLLOW_SYSTEM`（自定义 sentinel）| — |
| `1` | 仅限充电 | `UsbManager.FUNCTION_NONE` | `0` |
| `2` | 传输文件（MTP） | `UsbManager.FUNCTION_MTP` | `1 << 2` = `4` |
| `3` | 传输照片（PTP） | `UsbManager.FUNCTION_PTP` | `1 << 4` = `16` |

- `FUNCTION_NONE` 不等于 `FOLLOW_SYSTEM`。
- `FOLLOW_SYSTEM` 表示把 effective function 映射回 `mSettings` 中保存的原始系统默认值。
- `FUNCTION_NONE` 本身是一个合法的“仅充电”配置，不能用作“恢复未知原生值”的通配符。

## ADB 所有权

```text
ADB_COMBINATION_OWNER = SYSTEM
```

依据：

1. `UsbManager.areSettableFunctions` 的 `SETTABLE_FUNCTIONS` 不包含 `FUNCTION_ADB`。
2. `UsbHandler.getAppliedFunctions` / `UsbHandlerLegacy.applyAdbFunction` 在决定下发给驱动的函数时自动加/减 ADB。
3. 模块在任何路径下都不应：
   - 把 `FUNCTION_ADB` 与主函数做 OR
   - 从主函数中移除 ADB
   - 写 `sys.usb.config`、`persist.sys.usb.config`
   - 直接构造 `"mtp,adb"` / `"ptp,adb"` / `"none,adb"` 字符串

## `FOLLOW_SYSTEM` 可逆性

场景：

1. 原生默认为 PTP。
2. 模块偏好 `FOLLOW_SYSTEM` → effective 保持 PTP。
3. 用户切到模块 MTP → effective 变为 MTP。
4. 用户再切回 `FOLLOW_SYSTEM` → effective 必须恢复为 PTP，而不是 `FUNCTION_NONE`。

结论：不能将 `FOLLOW_SYSTEM` 简单映射为 `FUNCTION_NONE`。`FUNCTION_NONE` 是“仅充电”的真实配置；把 `FOLLOW_SYSTEM` 当作 `FUNCTION_NONE` 会破坏可逆性，且把系统默认值误写死为充电。

正确做法：模块保存独立的 `system_usb_default_function` 偏好（0/1/2/3），并在运行时把 effective function 映射为：

- `0`（FOLLOW_SYSTEM）：原生 `mSettings` 中读取的值
- `1`：充电 = `FUNCTION_NONE`
- `2`：MTP
- `3`：PTP

## 选项分析

### Option A：Native persisted 架构

思路：模块把偏好值直接通过 `UsbManager.setScreenUnlockedFunctions` / `IUsbManager.setScreenUnlockedFunctions` 写入原生持久化。

判定：REJECT。

理由：

1. 写入原生 `mSettings` 会覆盖系统默认值。
2. 回到 `FOLLOW_SYSTEM` 时需要预先保存的 baseline（原值），一旦用户在模块生效期间通过 Settings 改了一次默认值，baseline 就会变旧。
3. 模块直接持有系统持久化所有权，破坏“最小抽象、明确 ownership”，与 Settings 产生写冲突。
4. `MANAGE_USB` 虽然系统_server 有，但把模块偏好和系统持久化混在一起会引入不可逆状态。

### Option B：Non-destructive runtime override

思路：不修改原生 `mSettings`；只在 `UsbHandler` 读取/应用 `mScreenUnlockedFunctions` 的 authoritative boundary 做映射。

判定：ACCEPT。

理由：

1. 原生持久化始终保存系统默认值，模块只影响 in-memory `mScreenUnlockedFunctions` 和最终 `setEnabledFunctions` 调用。
2. `FOLLOW_SYSTEM` 无需 baseline，直接读当前原生值。
3. Settings 仍然可以正常写原生默认值；模块偏好变化不会破坏这个值。
4. ADB、OEM override、`DISALLOW_USB_FILE_TRANSFER` 等政策仍由系统 `UsbHandler` 处理，因为最终调用的是原有 `setEnabledFunctions` 路径。

## 推荐架构

```text
RECOMMENDED_ARCHITECTURE = OPTION_B_NON_DESTRUCTIVE_RUNTIME_OVERRIDE
```

### Hook 边界

- `HOOK CLASS`：`com.android.server.usb.UsbDeviceManager$UsbHandler`
- `HOOK METHOD`：`handleMessage(Message)` 针对 `MSG_SET_SCREEN_UNLOCKED_FUNCTIONS` 与 `MSG_USER_SWITCHED`；`UsbHandler` 构造函数（初始加载）；必要时可辅以 `setScreenUnlockedFunctions` 私有方法。
- `HOOK PHASE`：`InstallPhase.SYSTEM_SERVER_STARTING`
- `INPUT`：`Message msg`（`msg.what`、`msg.obj` 为原生 `long functions`）、`mCurrentUser`、`mSettings` per-user SharedPreferences、模块偏好键 `system_usb_default_function`
- `OUTPUT`：effective `long` functions 作为 `mScreenUnlockedFunctions` 的实际使用值，并触发正确的 `setEnabledFunctions` / `setScreenUnlockedFunctions` 应用。

### 为什么不是更稳定的公开方法

公开 `UsbDeviceManager.setScreenUnlockedFunctions(long)` 只在新默认值写入时被触发（Settings 显式设置），无法覆盖 boot、用户切换、屏幕解锁、USB 断开重连等系统事件。真正的“应用边界”是 `UsbHandler` 的 `handleMessage` 私有入口。由于该私有入口在 HyperOS 1 上确实与 AOSP `android-14.0.0_r1` 高度一致，但私有方法在不同 ROM 之间仍不稳定，P1-B 实现时应：

1. 优先尝试 hook `UsbHandler.handleMessage` 的指定 message what。
2. 若 DexKit/Xposed 找不到 `handleMessage` 的精确分支（例如 ROM 重写了 switch 表），则 fallback 到 hook `UsbDeviceManager.setScreenUnlockedFunctions` 作为写入拦截点，并额外处理 `UsbHandler` 事件触发点，作为 fail-open 降级。

### 映射策略

模块偏好 -> effective function：

```text
0 (FOLLOW_SYSTEM) -> native mScreenUnlockedFunctions from mSettings
1 (CHARGING)      -> UsbManager.FUNCTION_NONE
2 (MTP)           -> UsbManager.FUNCTION_MTP
3 (PTP)           -> UsbManager.FUNCTION_PTP
```

模块永远不碰 `FUNCTION_ADB`。`setEnabledFunctions` 会调用 `getAppliedFunctions` 或 `applyAdbFunction`，由系统按 `isAdbEnabled()` 自动拼接。

### 不破坏原生持久化的方法

在 `MSG_SET_SCREEN_UNLOCKED_FUNCTIONS` 的处理中，保留系统原本的 `mSettings.edit().putString(...)` 写原生值，随后把 `mScreenUnlockedFunctions` 改为映射后的 effective 值，再触发应用。`MSG_USER_SWITCHED` 只在用户切换后重载 in-memory 字段，不立即应用（屏幕锁定）。构造函数中按同样方式初始化。这样 `mSettings` 始终保存原生值，in-memory 字段保存 effective 值。

## 限制/策略所有者

```text
USB RESTRICTION/POLICY OWNER = SYSTEM
```

- `UserManager.DISALLOW_USB_FILE_TRANSFER` 由 `UsbHandler.isUsbTransferAllowed()` 检查。
- `MSG_UPDATE_USER_RESTRICTIONS` 在数据功能被禁用时调用 `setEnabledFunctions(FUNCTION_NONE, true, ...)` 恢复充电。
- `UsbBackend.areFunctionsSupported` / `areFunctionDisallowed` 会在 Settings 层阻止选择受限功能。
- 模块只映射主数据功能；任何政策触发的 `setEnabledFunctions(FUNCTION_NONE, ...)` 应由原逻辑继续执行。

## 是否需要 USB 连接监听器

```text
USB_CONNECTION_LISTENER_REQUIRED = NO
```

系统已有 `UsbHandler.handleMessage(MSG_UPDATE_STATE)` / `MSG_UPDATE_SCREEN_LOCK` / `finishBoot()` 等事件路径在正确的时机重新应用默认值。模块 hook 嵌入这些路径即可，不需要额外的：

- `BroadcastReceiver`（`ACTION_USB_STATE`）
- USB 插拔 listener
- 轮询 / 周期 `Handler`
- 常驻 Service
- Boot receiver
- Connection observer

## 偏好生命周期

- 模块偏好键建议：`system_usb_default_function`
- 存储值：`int`（0-3），使用 `PrefMap.getInt` 读取。
- 变化通过 `PreferenceBootstrap` 已注册的 `OnSharedPreferenceChangeListener` 分发到 `MainModule.mPrefs`。
- hook 路径在每次系统事件触发时重新读取 `MainModule.mPrefs`，热路径上只访问内存快照，无 Binder/磁盘。
- 若需即时响应偏好变化，可在 `PreferenceObserverRegistry` 注册 `PreferenceObserver` 并通过 `mHandler.sendEmptyMessage(MSG_UPDATE_SCREEN_LOCK)` 等安全手段让 `UsbHandler` 重新评估；这仍然依赖既有系统消息，不新建 listener。

## 进程目标

- 目标进程：`system_server`
- 安装点：`SystemServerInstaller` / `InstallPhase.SYSTEM_SERVER_STARTING`
- 无需在 `SystemUI`、`Settings` 或 `com.android.settings` 安装 hook。

## 性能模型

- 每次系统 USB 事件触发 `handleMessage` 时只读取一次 `MainModule.mPrefs.getInt` 和一次 `usbFunctionsFromString`（从 mSettings 读取原生值已在系统路径内）。
- 无反射热路径；DexKit 仅在 `SYSTEM_SERVER_STARTING` 安装时使用一次。
- 不持有持久 Activity/View/Receiver；不新增后台 Service；不轮询。
- Fail-open：如果无法定位 `UsbHandler` 或 `handleMessage`，放弃映射，原生行为继续。

## 失败语义

- 如果模块偏好值不可读或越界，按 `FOLLOW_SYSTEM` 处理（`0`）。
- 如果 `mSettings` 为空或原生值读不出，按 `FUNCTION_NONE` 处理。
- 如果 `UsbHandler` 消息 what 值在 ROM 上改变，fallback 到公开 `setScreenUnlockedFunctions` 写入点，且接受可能无法覆盖所有系统事件。
- 如果 ADB 被启用，系统会自动 `FUNCTION_MTP | FUNCTION_ADB` 或等效字符串；模块不参与，避免 ADB 状态破坏。

## 多用户语义

- `UNLOCKED_CONFIG_PREF = "usb-screen-unlocked-config-%d"` 是 per-user key。
- `mSettings` 为 device-protected storage `UsbDeviceManagerPrefs.xml`。
- `MSG_USER_SWITCHED` 按新 `mCurrentUser` 重新加载。
- 模块 `system_usb_default_function` 偏好是全局值（与现有 CustoMIUIzer 偏好模型一致），effective 函数按当前用户对应的 native 默认值做 FOLLOW_SYSTEM 映射。
- 无多用户冲突：系统本身按当前用户隔离持久化。

## HyperOS 1 精确验证状态

```text
HYPEROS_EXACT_USB_CHAIN = PARTIALLY_VERIFIED
```

- AOSP `android-14.0.0_r1` 链（Settings / UsbBackend / UsbManager / UsbService / UsbDeviceManager / UsbHandler）已完全验证。
- Xiaomi HyperOS 1 完整源码未公开；通过第三方社区 A14 base `Mrick-stuffs/frameworks_base/fourteen` 拉取的 `UsbDeviceManager.java` 与 AOSP `android-14.0.0_r1` 在 `UNLOCKED_CONFIG_PREF`、`MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`、`setScreenUnlockedFunctions`、`applyAdbFunction`、`getAppliedFunctions` 等关键位置一致。
- 目标设备专属构建 `V816.0.7.0.UMCTWXM` 未拿到符号表或 smali 反编译，因此不标记为 `VERIFIED`。

## 拒绝的替代方案

1. **Hook `UsbManager.setScreenUnlockedFunctions` / `IUsbManager.setScreenUnlockedFunctions`（framework API）**：只能在 Settings 显式设置时触发，无法覆盖 boot / 用户切换 / 解锁 / 断开重连。
2. **Hook `Settings` 的 `UsbBackend` 或 `UsbDefaultFragment`**：只影响 UI，不修改系统实际应用函数；且目标进程不是 `system_server`，无法在无 `MANAGE_USB` 权限时生效。
3. **写入 `sys.usb.config` 或 `persist.sys.usb.config`**：直接破坏系统策略、ADB 组合、OEM override、多用户持久化，且为项目明确禁止。
4. **在模块 app 内调用 `UsbManager.setScreenUnlockedFunctions`**：需要 `MANAGE_USB`，模块 app 不满足；不可能通过公开 API 生效。
5. **添加 USB 插拔 BroadcastReceiver / 轮询**：违反“无监听、无轮询、无 Service”的性能与 ownership 约束。

## 与本项目现有架构的衔接

- 新增 feature 应注册在 `SystemServerFeatures.all` / `SystemServerInstaller`，属于 `FeatureTarget.SYSTEM_SERVER` + `InstallPhase.SYSTEM_SERVER_STARTING`。
- 偏好读取使用 `MainModule.mPrefs.getInt("system_usb_default_function", 0)`。
- Feature ID / 注册 / 资源 / `prefs_system.xml` 都留到 P1-B 实现阶段处理；P1-A 不修改这些文件。

## 最终报告字段

```text
BASE SHA = 30b5dd308aa590914dcfc0deefcb6213e1cf8baa
FINAL SHA = 3148cdd59031949b7af022e1ce6abf207648d371
REMOTE HEAD = 3148cdd59031949b7af022e1ce6abf207648d371
ANDROID14_NATIVE_DEFAULT_API = UsbManager.setScreenUnlockedFunctions / UsbManager.getScreenUnlockedFunctions
USB SERVICE OWNER = system_server / com.android.server.usb.UsbService + UsbDeviceManager
NATIVE DEFAULT PERSISTED = YES
FUNCTION_NONE SEMANTICS = charging only; zero mask; UsbHandler.getAppliedFunctions maps to getChargingFunctions()
FUNCTION_MTP = 1 << 2 = 4
FUNCTION_PTP = 1 << 4 = 16
ADB_COMBINATION_OWNER = SYSTEM
USB_CONNECTION_LISTENER_REQUIRED = NO
FOLLOW_SYSTEM_REVERSIBLE = YES（在 Option B 下）
OPTION_A = REJECT: 破坏原生持久化，需要旧 baseline，无法安全恢复 FOLLOW_SYSTEM
OPTION_B = ACCEPT: 非破坏性运行时映射，保留原生持久化，FOLLOW_SYSTEM 始终读当前原生值
RECOMMENDED_ARCHITECTURE = OPTION_B_NON_DESTRUCTIVE_RUNTIME_OVERRIDE
HOOK CLASS = com.android.server.usb.UsbDeviceManager$UsbHandler
HOOK METHOD = handleMessage (MSG_SET_SCREEN_UNLOCKED_FUNCTIONS, MSG_USER_SWITCHED) + constructor for initial load
HOOK PHASE = SYSTEM_SERVER_STARTING
SYSTEM_POLICY_PRESERVED = YES
MULTI_USER = per-user SharedPreferences key usb-screen-unlocked-config-%d in UsbDeviceManagerPrefs.xml
HYPEROS_EXACT_USB_CHAIN = PARTIALLY_VERIFIED
PRODUCTION CHANGE = NO
fast --changed = PASS
diff --check = PASS
```
