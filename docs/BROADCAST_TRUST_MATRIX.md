# 模块广播信任矩阵

> 分支：hardening/a14-lts-foundation  
> 目标：给出每个跨进程 action 的「发送方 package/UID → 接收方 process → 认证方式」。

## 认证方式说明

| 方式 | 含义 | 适用场景 |
| --- | --- | --- |
| `signature` | 接收端 `registerReceiver` 要求 `android:protectionLevel="signature"` 的权限；发送方必须是与模块同签名的 `tv.withaibuild.customiuizer.r14` 应用。 | 模块设置应用主动发起的命令。 |
| `identity` | 发送方调用 `BroadcastOptions.setShareIdentityEnabled(true)` 并调用 `sendBroadcast(..., options)`；接收端 `onReceive` 校验 `getSentFromPackage()` 与 action 专属白名单。 | 宿主进程（SystemUI / Launcher / system_server）触发或被回调。 |
| `none` | 仅接收系统广播或生命周期/内部回调，不做额外认证（仍是 receiver 形状边界）。 | 系统广播（如 `WIFI_STATE_CHANGED_ACTION`）。 |
| `audit-only` | 保持当前行为，本轮仅记录风险，不增加权限，避免破坏兼容。 | `UnlockReceiver` 的 Tasker / Locale 插件入口。 |

---

## 高权限命令 / 状态控制

| action | 发送方 package/UID | 接收方 process | 认证方式 | 说明 |
| --- | --- | --- | --- | --- |
| `tv.withaibuild.customiuizer.mods.action.FastReboot` | `tv.withaibuild.customiuizer.r14`（设置应用） | `com.android.systemui` | `signature` | 软重启，仅由设置应用显式发起。 |
| `tv.withaibuild.customiuizer.mods.action.RestartSystemUI` | `android` / `com.android.systemui` / `com.miui.home` | `com.android.systemui` | `identity` | 用户手势在 system_server / SystemUI / Launcher 触发。 |
| `tv.withaibuild.customiuizer.mods.action.RestartLauncher` | `android` / `com.android.systemui` / `com.miui.home` | `com.android.systemui` | `identity` | SystemUI 内 mSBReceiver 强停 Launcher。 |
| `tv.withaibuild.customiuizer.mods.action.RestartSecurityCenter` | `android` / `com.android.systemui` / `com.miui.home` | `com.android.systemui` | `identity` | 强停 `com.miui.securitycenter`。 |
| `tv.withaibuild.customiuizer.mods.action.LockDevice` | `android` / `com.android.systemui` / `com.miui.home` | `com.android.systemui` | `identity` | 调用 PowerManager.goToSleep。 |
| `tv.withaibuild.customiuizer.mods.action.TakeScreenshot` | `android` / `com.android.systemui` / `com.miui.home` | `com.android.systemui` | `identity` | 发送 `miui.intent.TAKE_SCREENSHOT`。 |
| `tv.withaibuild.customiuizer.mods.action.GoToSleep` / `WakeUp` | `android` / `com.android.systemui` | `com.android.systemui` | `identity` | 电源/指纹等手势。 |
| `tv.withaibuild.customiuizer.mods.action.ForceClose` | `android` / `com.android.systemui` / `com.miui.home` | `android` (system_server) | `identity` | phoneWindowManagerActionReceiver 内 closeApp。 |
| `tv.withaibuild.customiuizer.mods.action.SimulateMenu` | `android` / `com.android.systemui` / `com.miui.home` | `android` (system_server) | `identity` | 注入 menu 键事件。 |
| `tv.withaibuild.customiuizer.mods.action.SwitchToPrevApp` | `android` / `com.android.systemui` / `com.miui.home` | `android` (system_server) | `identity` | 切换上一个应用。 |
| `tv.withaibuild.customiuizer.mods.action.ToggleColorInversion` | `android` / `com.android.systemui` / `com.miui.home` | `android` (system_server) | `identity` | 色彩反转。 |
| `tv.withaibuild.customiuizer.mods.action.LaunchIntent` | `android` / `com.android.systemui` / `com.miui.home` | `com.android.systemui` | `identity` | 由 LaunchAction 发起。 |
| `tv.withaibuild.customiuizer.mods.action.ShowSideBar` | `android` / `com.android.systemui` / `com.miui.home` | `com.miui.securitycenter` 或宿主 View 上下文 | `identity` | 发送给 `com.miui.securitycenter`。 |

## 数据/配置同步

| action | 发送方 package/UID | 接收方 process | 认证方式 | 说明 |
| --- | --- | --- | --- | --- |
| `tv.withaibuild.customiuizer.mods.event.FETCHAPPCONFIG` | `tv.withaibuild.customiuizer.r14`（AppSelector） | `com.miui.home` | `signature` | 模块请求 Launcher 的隐私应用配置。 |
| `tv.withaibuild.customiuizer.mods.event.PUSHAPPCONFIG` | `com.miui.home` | `tv.withaibuild.customiuizer.r14`（AppSelector） | `identity` | Launcher 返回隐私应用配置；必须显式 `setPackage(modulePkg)` 并共享身份。 |
| `tv.withaibuild.customiuizer.mods.action.FetchCachedDevices` | `tv.withaibuild.customiuizer.r14`（BTList） | `com.android.systemui` | `signature` | 模块请求 SystemUI 缓存的蓝牙设备。 |
| `tv.withaibuild.customiuizer.mods.event.CACHEDDEVICESUPDATE` | `com.android.systemui` | `tv.withaibuild.customiuizer.r14`（BTList） | `identity` | SystemUI 返回蓝牙设备；必须共享身份。 |
| `tv.withaibuild.customiuizer.mods.action.UnlockSetForced` | `tv.withaibuild.customiuizer.r14`（UnlockReceiver） | `com.android.systemui` | `identity` + `per-host token`（或 `explicit component` + `per-host token` fallback） | 强制设置解锁状态。`UnlockSettings` 打开时只读取调用方信息，用户点 OK 后才首次签发或复用 per-host token。`UnlockReceiver` 首选 `getSentFromPackage()` 与 Bundle host 比对；若宿主未共享身份但广播显式指向本组件，则以 256-bit per-host token 作为唯一认证继续；隐式广播且无身份则拒绝。验证通过后以 module 身份重新广播给 SystemUI。 |
| `tv.withaibuild.customiuizer.mods.action.BTConnectionChanged` | `com.android.systemui` | `com.android.systemui` | `identity` | 同进程，共享身份即可证明来源。 |
| `android.net.wifi.STATE_CHANGE` | `android` (system) | `com.android.systemui` | `none` | 系统广播，身份不一定可获取；只做网络状态检查，不执行直接解锁。 |

## 兼容入口

| action / 入口 | 当前状态 | 本轮策略 | 风险 |
| --- | --- | --- | --- |
| `com.twofortyfouram.locale.intent.action.FIRE_SETTING` | `UnlockReceiver` exported | `per-host token` | `UnlockSettings` 打开时只读取 `getCallingPackage()` 与签名证书历史，用户点 OK 后才首次签发或复用 per-host token；取消、返回或异常时不写入任何状态。插件 Bundle 携带 host package 与 token。`UnlockReceiver` 首选 `getSentFromPackage()` 并与 Bundle host 比对；若宿主未共享身份但广播显式指向本组件（`intent.component` 为本 Receiver），则以 per-host token 作为唯一认证继续；隐式广播且无身份则安全拒绝。不同 host 不能共用 token，同一包名但签名不匹配会被拒绝，合法证书轮换在确认后更新历史。旧的全局 token 已失效，老任务需重新保存。 |

## 不再使用的方案

- `tv.withaibuild.customiuizer.r14.permission.BROADCAST` 不再使用 `signatureOrSystem` 作为统一保护。原因：`signatureOrSystem` 已废弃；宿主进程 UID 不继承模块签名；在 Android 14 上被解析为 `signature|privileged`，且 `com.android.systemui` / `com.miui.home` 不会自动被授予未请求的权限。
