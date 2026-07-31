# Changelog

简体中文 | [English](CHANGELOG_EN.md)

本文件记录公开版本的用户可见变化、兼容边界、验证结论和回退价值。内部迁移批次、
Agent 工作记录、临时 APK 和未经同条件测量的性能数字不作为 Release changelog。

## 公开 Release

| 版本 | 日期 | 定位 |
| --- | --- | --- |
| `r14.15.3` | 2026-07-31 | 本地正式签名候选版本；静态检查和构建通过，待 Android 14 / HyperOS 实机及 LSPosed 日志验证 |
| `r14.15.1` | 2026-07-31 | 在 `r14.15.0` 基础上整合网速字体家族继承、双排行距与本地化；实机验证待完成 |
| `r14.15.0` | 2026-07-31 | `r14.13.9` 真机验证基线；增加 `system_server` Global Action Receiver 顶层异常隔离；完整人工冒烟测试延期 |
| `r14.13.9` | 2026-07-31 | 当前稳定版；恢复 A14 上游 `system` 作用域，修复 `system_server` Hook 未加载问题 |
| `r14.13.8` | 2026-07-30 | 结构整理收口、快速重启 Receiver 修复、LSPosed 2.1.1 实机验收 |
| `r14.13.7` | 2026-07-29 | 当前稳定版；未连接期间的设置不再丢失、快速重启不再误判、系统进程热路径容错 |
| `r14.13.6` | 2026-07-29 | 运行期健壮性加固、界面语言修复、hook 文件按功能域拆分 |
| `r14.8.0` | 2026-07-25 | 旧签名回退点；升级到新版本前必须备份并重装 |
| `r14.7.4` | 2026-07-25 | r14.7.x Kotlin/Coroutine 迁移合并版 |

Release 标题统一为纯版本号。已移除版本的资产名、大小与 SHA-256 见
[历史 Release 归档](docs/RELEASE_ARCHIVE.md)；对应源码仍可通过 Git tag 获取。

## [r14.15.3] - 2026-07-31

### 版本定位

本地正式签名候选版本。在 `r14.15.1` 基础上完成 A14 有效分支整合，纳入 UI 文本继承与 About
换行修复，并统一版本号至 `r14.15.3` / `191`。该版本尚未公开，待 Android 14 / HyperOS 实机及
LSPosed 日志验证。

### 变更

- `versionCode` 升级为 `191`。
- `versionName` 升级为 `r14.15.3`。
- 整合 `origin/integration/a14-r14.15.1` 作为主体基线（包含 `system_server` Global Action Receiver
  异常隔离与网速相关改动）。
- 从 `devin/r14-netspeed-font-spacing-i18n` 纳入双行网速行距、前置提示本地化与
  `feature-semantics/a14.json` 元数据；`prefs_system_detailednetspeed.xml` 与主体实现语义一致。
- 从 `fix/a14-ui-text-inheritance-and-about-wrap` 纳入 `SeekBarPreference` 系统文本样式继承
  保持与 About 页面换行修复。
- 修复 SystemUI 状态栏网速加粗不生效：新增 `applyNetSpeedTextStyle` 统一负责字号、字体、加粗、
  行距、对齐、边距与 fixed width；以当前字体为基线叠加 `Typeface.BOLD`，并配合 `Paint.isFakeBoldText`
  作为无有效粗体字形时的兜底；在 `TextView.setTextAppearance`、`NetworkSpeedView.onFinishInflate`、
  `NetworkSpeedView.setNetworkSpeed` 等可能覆盖字体的生命周期点后重新应用样式，避免反复叠加。
- 删除 `.github/workflows/ci.yml`（CI 不再维护）。
- 更新 `README`、`README_EN`、`docs/BUILD_AND_RELEASE.md`、`docs/MAINTENANCE_CHECKPOINT.md` 等
  当前文档到 `r14.15.3`。

### 验证

- 通过 `python tools/check-invariants.py`。
- 通过 `python tools/audit-feature-semantics.py --validate`。
- 通过 `python -m unittest discover -s tools/tests -p "test_*.py"`。
- 通过 `gradlew clean test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease`。
- 通过 `apksigner verify --verbose --print-certs`、`zipalign -c -v 4` 与
  `aapt2 dump badging`。
- `META-INF/xposed/scope.list` 校验包含 `system`、`android`、`com.android.systemui`、`com.miui.home`。

### 已知边界

- `r14.15.3` 是本地构建的正式签名候选 APK，**未创建 Git tag / GitHub Release**。
- 网速显示、About 布局与完整人工冒烟测试尚待实机验证。
- 不声称 `r14.15.3` 已公开或已完成实机 PASS。

### 产物

- APK：`CustoMIUIzer-A14-r14.15.3.apk`（正式签名候选） / `CustoMIUIzer-A14-r14.15.3-unsigned-ci.apk`（CI）。
- versionCode / versionName：`191 / r14.15.3`
- 构建信息：`../release-output/A14/BUILD_INFO_R14_15_3.txt`

## [r14.15.0] - 2026-07-31

### 版本定位

`r14.13.9` 真机验证基线上的发布版。在相同运行时代码基线上，为 `GlobalActionSystemServerHooks` 注册的 `system_server` BroadcastReceiver 增加顶层 `ModuleHelper.guarded` 异常隔离，防止自定义动作异常逃逸并导致 `system_server` 崩溃。

该异常隔离不改变正常业务结果、action 名称、权限、信任验证或其他 Receiver。

### 变更

- `versionCode` 保持 `188`。
- `versionName` 保持 `r14.15.0`。
- `GlobalActionSystemServerHooks` 的 `phoneWindowManagerActionReceiver.onReceive()` 增加 `ModuleHelper.guarded` 顶层异常隔离：异常时调用 `XposedHelpers.log(t)`，有序广播设置 `GlobalActions.ACTION_FAILED`，不重新抛出。
- 新增 `app/src/test/java/tv/withaibuild/customiuizer/GlobalActionSystemServerReceiverSafetyTest.kt`，静态验证 Receiver 顶层边界、有序广播结果与信任验证。
- 新增 `docs/SYSTEM_SERVER_STARTING_AUDIT.md`，记录 `system_server` Global Action Receiver 的 P0 风险与修复。
- 更新 `docs/SYSTEM_SCOPE_AUDIT.md`，补充 P0 resolved 说明。

### 验证

- 通过 `python tools/check-invariants.py`。
- 通过 `python -m unittest discover -s tools/tests -p "test_*.py"`。
- 通过 `gradlew test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease`。
- 通过 `GlobalActionSystemServerReceiverSafetyTest` 静态契约测试。
- `r14.13.9` 真机日志确认：`system`、`SystemUI`、`Launcher` 均成功加载，Hook 安装无错误，`Toast` 禁用功能有效。
- 未声明 `system_server` Global Action Receiver 已完成真机逐项验证。

### 已知边界

- 完整人工冒烟测试（电源/音量/导航键、AppLock/锁屏、自由窗口/旋转、音频/震动/来电、安全/安装/壁纸/Global Actions）尚未执行，不作为当前发布阻塞项。
- 已发布的 `r14.15.0` 不声称全部 40 个 `system_server` Hook 均已人工验证。
- 不声称修复前或修复后发现过真实 `system_server` 崩溃日志。

### 产物

- APK：`CustoMIUIzer-A14-r14.15.0.apk`（正式签名时） / `CustoMIUIzer-A14-r14.15.0-unsigned-ci.apk`（CI）。
- versionCode / versionName：`188 / r14.15.0`

## [r14.15.1] - 2026-07-31

### 版本定位

在 `r14.15.0` 的 `system_server` Receiver 异常隔离基础上，整合网速显示改进：粗体保留 SystemUI 当前字体家族，新增双排网速行间距 70%–130%，补齐相关本地化和系统实时网速前置提示。

### 变更

- `versionCode` 升级为 `189`。
- `versionName` 升级为 `r14.15.1`。
- 恢复 `system` 作用域，使 `system_server` Hook 重新生效（继承自 `r14.13.9`）。
- 为 `GlobalActionSystemServerHooks` 的 `phoneWindowManagerActionReceiver` 增加完整 `ModuleHelper.guarded` 异常隔离。
- 修正 `guarded` lambda 中 `action == null` 与 `isTrustedBroadcast` 拒绝的提前返回语义，统一有序广播 `ACTION_HANDLED` / `ACTION_FAILED` 结果。
- 网速粗体保留当前 SystemUI 字体家族，不再使用 `Typeface.DEFAULT_BOLD`。
- 新增双排网速行间距 `70%–130%`（默认 `100%`），仅影响 `speedStyle == 2`。
- 补齐 `system_netspeed_rowspacing_title`、`system_netspeed_rowspacing_summ`、`system_netspeed_prerequisite_note` 等本地化。
- 新增 `app/src/test/java/tv/withaibuild/customiuizer/mods/NetSpeedLineSpacingTest.kt` 与 `tools/tests/test_netspeed_resources.py`。
- 更新 `feature-semantics/a14.json`，补充 `pref_key_system_netspeed_rowspacing` 审计记录。

### 验证

- 通过 `python tools/check-invariants.py`。
- 通过 `python tools/audit-feature-semantics.py --validate`。
- 通过 `python -m unittest discover -s tools/tests -p "test_*.py"`。
- 通过 `gradlew clean test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease`。
- `GlobalActionSystemServerReceiverSafetyTest` 与 `NetSpeedLineSpacingTest` 均通过。
- `META-INF/xposed/scope.list` 校验包含 `system`、`android`、`com.android.systemui`、`com.miui.home`。

### 已知边界

- `system` scope、Toast 路径已在 `r14.13.9` 真机核心验证。
- Receiver guard 与网速改动已通过离线测试和构建。
- 网速实际显示与完整人工冒烟测试尚待实机验证。
- 不声称网速效果已在真机 PASS。
- 不声称 r14.15.1 已公开发布。

### 产物

- APK：`CustoMIUIzer-A14-r14.15.1.apk`（正式签名时） / `CustoMIUIzer-A14-r14.15.1-unsigned-ci.apk`（CI）。
- versionCode / versionName：`189 / r14.15.1`

## [r14.13.9] - 2026-07-31

### 版本定位

恢复 A14 上游原有的 `system` 作用域，修复 `system_server` 未加载导致系统服务类 Hook 静默失效的问题。
本轮不改动业务 Hook。

### 变更

- 在 `app/src/main/resources/META-INF/xposed/scope.list` 中恢复 `system` 作用域，保留现有 `android` 作用域。
- 改进 ADB regression 对 `system` / `system_server` 的进程归一化，保留 `rawProcess`。
- 增加作用域静态回归测试，要求 `MainModule.onSystemServerStarting` 存在时 `scope.list` 必须包含 `system`。

### 验证

- 通过 `python tools/check-invariants.py`、`python tools/audit-feature-semantics.py --validate`。
- 通过 `gradlew test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease`。
- 通过 GitHub Actions CI 全部任务。
- `META-INF/xposed/scope.list` 校验包含 `system`、`android`、`com.android.systemui`、`com.miui.home`。

### 已知边界

- `r14.13.9` 构建和 CI 已通过，但修复后的 `system_server` 真机加载、完整 `a14-smoke`、Broadcast 负向探测和 Tasker 人工检查尚未执行。

### 产物

- APK：`CustoMIUIzer-A14-r14.13.9.apk`
- versionCode / versionName：`187 / r14.13.9`

## [r14.13.8] - 2026-07-30

### 版本定位

在不改变现有功能语义的前提下收口结构整理，并修复快速重启 Receiver 的独立注册与结果判断。
本轮未改动 Toast 屏蔽逻辑、`AnimationScale` 或其他无关功能。

### 变更

- 优化 Hook 进程与设置应用工具代码的边界，拆分 `HookUtils`，减少系统进程无关类加载。
- 清理 GlobalActions 遗留的 6 个转发桩，调用点直接使用实际实现。
- 快速重启 Receiver 不再依赖是否配置自定义动作；未配置任何动作时，应用内“重启系统”
  仍可由 SystemUI 正常接收和执行。
- 区分广播无人接收与接收端执行失败。快速重启执行失败时不再错误提示“未连接 LSPosed 服务”，
  自定义动作的行为保持不变。

### 实机与静态验证

- Android 14 / HyperOS 1，LSPosed 2.1.1（7790）实机日志中 P0/P1 均为 0；
  模块在 SystemUI 与 Launcher 正常加载，两次快速重启完成，未发现目标进程崩溃、
  Hook 异常或 Receiver 重复注册。
- 完整 invariant、单元测试、`lintDebug`、`lintRelease`、`lintVitalRelease`、
  Debug / Release 与正式签名验证均通过。

### 已知问题

- 系统 Toast 屏蔽仍可能无效，本版本不处理该旧问题。

### 产物

- APK：`CustoMIUIzer-A14-r14.13.8.apk`
- 大小：3,085,209 bytes
- SHA-256：`B0E7D4A3CB50E39748531D5B0FD3CB95F81C1F777DDAC9E346B8C8D67B8CBE62`
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- versionCode / versionName：`186 / r14.13.8`

## [r14.13.7] - 2026-07-29

### 版本定位

`r14.13.6` 之后的一轮可靠性修复。核心是一条一直存在、但被上一版暴露出来的缺陷：设置应用与
LSPosed 服务断开期间，用户改的设置会被静默丢弃且永不补发。顺带修掉了在审查同一批调用链时
发现的四处系统进程侧问题。用户可见行为除下述修复外保持不变。

### 根因：LSPosed 服务的 binder 推送

抓取的实机日志显示，设置应用连续快速重启四次之后，LSPosed/Vector 守护进程**不再向本模块的
`XposedProvider` 推送 binder**：此后每次进程启动仍会向 system_server 的桥接请求，但守护进程
侧的 `Sent module binder` 直到日志结束（14 分钟、15 次进程启动）再未出现，也没有任何错误行。

反编译 `libxposed-service` 102.0.0 确认：binder 是守护进程**推**给应用的
（`XposedProvider.call("SendBinder")`），`XposedServiceHelper.registerListener()` 只是存下
listener 并排空静态缓存，**没有任何请求或重试路径**。因此加长超时、重复注册、轮询都无法解决，
这一条属于框架侧问题，应用内无解。

本版本做的是让这个状态不再造成数据丢失、不再误伤其他功能、并且明确告知用户。

### 修复

- **未连接期间的设置改动不再丢失**。偏好监听器过去在 `remotePrefs == null` 时直接 return，
  而 `onServiceBind` 只注册增量监听、从不补齐。模块每个宿主进程只读一次远端快照并据此决定
  装哪些 hook，所以断开期间打开的开关会永久无效且毫无提示 —— 「专辑封面设为壁纸」不生效
  就是这个原因，**不是封面处理器本身的缺陷**。现在连接建立时做一次全量对账；仍未下发时，
  「暂未连接」对话框会附带说明。
- **快速重启不再被设置应用的绑定状态误判**。该功能是向 SystemUI 里的模块发广播，与设置应用
  自己有没有拿到 service binder 无关。改为直接发送**有序广播**（显式限定
  `com.android.systemui`），SystemUI 侧在反射解析成功后才认领，只有确实无人认领时才提示。
- **`PrefMap.getStringAsInt()` 不再抛异常**。存储类型变化会抛 `ClassCastException`，
  非法字符串会抛 `NumberFormatException`，而它的调用点在 SystemUI 与 `system_server` 的
  hook 里、且多数在进程启动决定装哪些 hook 时执行。现在一律回退到调用方给的默认值，
  失败结果同样进缓存，坏值只解析一次。
- **状态栏电池/温度的格式与单位无需重启 SystemUI 即可生效**。ticker 一直使用 hook 时捕获的
  配置快照，`onConfigMayHaveChanged()` 刷新的 `@Volatile` 字段从来没有被读过。现在每次 tick
  读一次当前快照。真正无法热更新的是图标槽位本身（主开关与「显示在右侧」），这两项已在设置
  界面标注需要重启 SystemUI。
- **锁屏专辑封面的并发与缓存**。单槽调度器内部第一句就 `withContext(Dispatchers.Default)`，
  把工作交回无界线程池，快速切歌时可能并行生成多张全屏 ARGB_8888；缓存按「3 条」计数
  （1080×2400 下约 31 MB），且 cache key 里 blur 恒为 0、用的是模糊后新对象的 identity hash，
  实际永远不可能命中。现改为代次校验（`cancel()` 停不了无挂起点的 CPU blur）、按
  `allocationByteCount` 限额、按源图与真实参数建 key；音乐清空、主题不支持、目标尺寸变化时
  释放缓存。CENTER_CROP / fit 几何与画质未改动。
- **图标加载队列饱和不再让图标永久空白**。`DiscardOldestPolicy` 会静默丢弃已入队任务，而
  对应的在途标记不会释放，此后该图标的每个加载者都判定「已有人在加载」直接返回。改为
  `AbortPolicy` 并在提交处显式处理拒绝。

### 验证

- `check-invariants` 116 文件 / 8 规则 / 0 违规；171 项单元测试 0 失败；
  lint / lintRelease / lintVitalRelease 0 errors；Debug / Release 构建通过。
- **尚未完成实机验收即发布**：本版本改动了运行在 SystemUI 内的封面处理器与状态栏 ticker，
  影响面比 `r14.13.6` 更靠近系统进程。如遇 SystemUI 异常请回退 `r14.13.6`。

### 产物

- APK：`CustoMIUIzer-A14-r14.13.7.apk`
- 大小：3,084,589 bytes
- SHA-256：`11D01A737BED25C3C4D31153DE22CB918A651D0DD043D0374E2C0E41D32492CC`
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- versionCode / versionName：`185 / r14.13.7`

## [r14.13.6] - 2026-07-29

### 版本定位

在 `r14.13.5` 之后的一轮运行期健壮性与性能加固。修复三类会实际影响使用的缺陷，
并把三个超大 hook 文件按功能域拆开。用户可见行为除界面语言外保持不变。

### 修复

- **界面语言切换不生效**。`AppCompatDelegate.setApplicationLocales()` 在 `Application.onCreate`
  阶段是静默空操作 —— 它在 API 33+ 上通过「存活的 AppCompat Activity 委托集合」解析
  `LocaleManager`，而那时一个 Activity 都还没有。语言选择被正确保存，然后什么也没发生。
  改为直接调用框架的 `android.app.LocaleManager`。
- **关于页语言项会让设置界面报错、语言静默回退**。绑定期间写入 Preference 值，会在
  RecyclerView 布局过程中触发 `notifyItemChanged`，并把 XML 里的占位值持久化覆盖掉用户的语言。
  绑定现在对偏好状态只读。
- **误报「模块未被激活」**。「等待超时」与「确认未连接」原本是同一个状态值，且 UI 的等待时间
  比服务自身的判定窗口更短。二者现已区分，超时后会再等一轮才下结论。
- **搜索结果跳转后开关状态不立即刷新**。搜索高亮本应一次性播放，实际每次 bind 都重放，
  并且动画会把行原有的背景（含按下态）永久替换掉。
- **系统进程内的未保护回调**。模块在 hook 里注册出去的回调不在 `MethodHook` 的 try/catch 里；
  其中两处 `Runnable` 运行在 `system_server` 内，抛异常等于设备重启。共加固 23 处。
- **注册泄漏**。清理逻辑以被 hook 实例为键，而每次都是新实例，因此从未生效。
  受影响的包括一个监听 `TIME_TICK` 的 receiver（每分钟一次无用唤醒）。
- **实例级附加字段按 `equals` 存储**。两个不同但相等的对象会共用字段表；改动参与 `hashCode`
  的字段后条目会永久丢失。改为按身份比较的弱引用键。

### 性能

- Hook 参数不再逐次复制与重新编排（117 处只读参数的调用点）。
- 反射缓存命中不再分配（字段查找 616 处调用点，无参方法查找 137 处）。
- 主界面搜索改为单次线性扫描，零分配；排序移到建索引时一次完成。

### 结构

- `mods/System.kt` 4898 → 593 行，`mods/SystemUI.kt` 3682 → 205 行，
  `mods/Launcher.kt` 2960 → 405 行，拆为 18 个按功能域组织的文件。
  搬移经逐字节比对，`MainModule` 调用序列不变，R8 保留方法数不变。

### 验证

- `check-invariants` 113 文件 8 规则 0 违规；122 单元测试 0 失败；
  lint / lintRelease / lintVitalRelease 0 errors；Debug/Release 构建通过。
- **未完成实机验收即发布**：本版本的改动尚未在设备上运行过。

## [r14.13.5] - 2026-07-28

### 版本定位

`r14.13.4` 的紧急热修版本。修复首页搜索功能在 `Various` 结果、子分类跳转和返回首页
过程中出现的导航回归，恢复 `0/1/2` 三态搜索状态机，统一 `sub` 空/空白字符串语义，并
修正 `openModCat()` 的返回语义。其余内容与 `r14.13.4` 保持一致。

本版本继续仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`，保持 libxposed
API 101/102 单 APK 兼容边界。使用与 `r14.13.4` 相同的新正式签名证书，可直接覆盖安装
`r14.13.4`。

### 修复

- 修复搜索结果属于 `Various` 页面或带子分类的 System/Launcher/Controls 项目时，
  点击后跳转随即返回首页、目标 Preference 未高亮的问题。
- 恢复明确的搜索导航状态机：
  - `0 = 普通首页`；
  - `1 = 正在显示搜索结果`；
  - `2 = 已从搜索结果进入目标页面，返回首页后清理搜索 UI`。
- 将 `ModData.sub` 改为可空 `String?`，搜索索引不再把无子分类项存成空字符串。
- `MainFragment.openModCat()` 对 System、Launcher、Controls、Various 四类均返回
  导航成功/失败语义，避免把事务结果与分类类型混用。
- `SubFragment` 增加 `sub` 空白保护，避免把空字符串误判为有效子分类并触发
  `PreferenceCategoryEx` 强制类型转换。
- 新增 `SearchRouteResolver` 与 `SearchStateMachine` 纯逻辑单元测试。

### 构建与兼容

- 继续使用已验证的 JDK 17、Gradle 9.6.1、AGP 9.2.1 和 Kotlin 2.3.21。
- Release 保持 R8、资源压缩、zipalign 和 APK Signature Scheme v2。
- 签名证书 SHA-256：
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。

### 验证

- 单元测试：68 tests, 0 failures, 0 skipped。
- Lint / `lintRelease` / `lintVitalRelease`：通过，107 deprecation warnings，0 errors。
- Debug / Release、R8 和资源压缩：通过（`BUILD SUCCESSFUL in 2m 8s`）。
- APK：`CustoMIUIzer-A14-r14.13.5.apk`。
- APK 大小：3,032,173 bytes。
- APK SHA-256：`89AE5046564F69D491DC44F7B853443113FEC7100FE997ABA9984181C4983EA5`。
- 签名：APK Signature Scheme v2，证书 SHA-256
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。
- versionCode/versionName：`183 / r14.13.5`。
- `minSdk/targetSdk`：`34 / 34`。
- Xposed metadata：`minApiVersion=101`、`targetApiVersion=102`、`staticScope=false`。

### 重要：r14.13.4 已撤回

- `r14.13.4` 存在首页搜索导航回归，已被 `r14.13.5` 取代。
- 已删除 `r14.13.4` 的 GitHub Release 与 tag；历史资产信息见
  [RELEASE_ARCHIVE.md](docs/RELEASE_ARCHIVE.md)。
- 如已安装 `r14.13.4`，可直接覆盖安装 `r14.13.5`，无需卸载。

## [r14.13.4] - 2026-07-28

### 版本定位

> 已撤回；被 `r14.13.5` 取代。

在 r14.12.0 稳定基线之上，完成设置应用、Locale、主题、生命周期和高频 Hook
路径的集中治理，并正式收口 r14.13 开发线中的架构审计、Kotlin 迁移回归和性能修复。

本版本继续仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`，保持 libxposed
API 101/102 单 APK 兼容边界。

### 设置与界面

- 应用内语言入口集中到 About 页面，支持跟随系统及项目现有多语言。
- 修复语言和日间/夜间模式切换后的 Activity、系统栏与设置页面重建行为。
- About 页面分别显示维护者、上游来源和当前版本。
- 修复搜索结果进入功能后的返回状态及 Fragment 重建状态恢复。
- Launcher、SystemUI 和 Security Center 重启改为后台 Root 命令，并补充无 Root、
  目标未运行和执行失败反馈。
- 整理 Preference 标题、summary、弹窗、间距、圆角和多语言资源。

### 稳定性与性能

- 修复 SystemUI 状态栏温度、电流等文本图标长期强引用旧 View 的问题；主题、密度、
  横竖屏或状态栏重新创建后，失效 View 可以被回收。
- 优化资源替换 Hook 的未命中路径，减少资源读取中的整数装箱、JNI 方法名读取和无效
  资源名称解析，并为 Sparse 容器增加安全发布。
- 修复 Java → Kotlin 迁移后 CPU thermal zone 扫描丢失首次命中退出语义的问题，
  避免周期任务重复打开无关 sysfs 文件。
- 移除 `first|second` 配置解析中的重复 Regex 编译，并增加 PrefPair 回归测试。
- 缓存 application ClassLoader fallback，避免 ROM 合法类缺失时重复执行反射探测。
- 修复 RemotePreferences 早期空快照被永久视为已加载的问题。
- 仅在 preference listener 注册成功后设置注册状态。
- 防止 DexKitBridge 重复创建。

### 构建与兼容

- 继续使用已验证的 JDK 17、Gradle 9.6.1、AGP 9.2.1 和 Kotlin 2.3.21。
- 本版本不包含 AGP 9.3.1 或其他工具链升级。
- 使用 libxposed API/service 102 构建，`minApiVersion=101`、
  `targetApiVersion=102`、`staticScope=false`。
- 公共加载与 Hook 路径保持 API 101 可用；未启用 Hot Reload、hook ID 或原子
  replacement。
- Release 保持 R8、资源压缩、zipalign 和 APK Signature Scheme v2。

### 重要：签名密钥变更

- `r14.12.0` 及更早公开版本使用的旧签名私钥已经遗失，无法继续用于后续构建。
- `r14.13.4` 使用新的正式签名证书，因此不能直接覆盖安装旧公开版本。
- 升级前必须先在旧版本中备份模块设置，然后卸载旧版本、安装 `r14.13.4`、
  重新启用 LSPosed/Vector 作用域、恢复设置并完整重启设备。
- 新签名证书 SHA-256：
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`

### 验证

- 单元测试：45 tests, 0 failures, 0 skipped。
- Lint / `lintRelease` / `lintVitalRelease`：通过，107 deprecation warnings，0 errors。
- Debug / Develop / Release、R8 和资源压缩：通过（`BUILD SUCCESSFUL in 3m 32s`）。
- APK：`CustoMIUIzer-A14-r14.13.4.apk`。
- APK 大小：3,032,173 bytes。
- APK SHA-256：`E8A2BD362C0540972441B8D1DE0BCACE8FE85FEF71F31406F3B4DA1A4027D26C`。
- 签名：APK Signature Scheme v2，证书 SHA-256
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。
- applicationId：`tv.withaibuild.customiuizer.r14`。
- versionCode/versionName：`182 / r14.13.4`。
- `minSdk/targetSdk`：`34 / 34`。
- Xposed metadata：`minApiVersion=101`、`targetApiVersion=102`、
  `staticScope=false`。

### 已知限制

- 仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`。
- API 102 仍需在对应框架环境进行独立实机覆盖。
- 厂商系统应用更新可能改变 Hook 目标。
- 性能和功耗收益取决于 ROM、启用功能和使用方式，不声明未经同设备对照测量的固定比例。

## [r14.13.3] - 2026-07-27

### 版本定位

> 非公开候选版本；相关改动已由 `r14.13.4` 正式版收口发布。

针对 UI/Locale/About 页面、主题重建、LSPosed 日志审计和 DexKitBridge 初始化的维护性修复与文档同步候选。

### 修复

- 清理设置首页重复语言入口，集中到 About 页面并启用 `valueAsSummary`。
- About 页面拆分为 maintainer、based_on、version 三行信息。
- `MainActivity` `configChanges` 移除 `uiMode`，让系统正常重建以刷新日间/夜间主题。
- `XposedHelpers.createBridge` 增加非空守护，避免 DexKitBridge 重复创建。
- 补充 `prefs_about.xml` 缺失的 `xmlns:miuizer` 命名空间，修复 Release 资源合并。

### 验证

- 构建：单元测试、Lint、`lintRelease`、`lintVitalRelease`、Debug/Release 全部通过。
- APK：`CustoMIUIzer-A14-r14.13.3.apk`，3,039,311 bytes，SHA-256 `FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`，APK Signature Scheme v2 签名。
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。
- LSPosed 日志审计 r14.13.3 重启日志：未发现可归因于模块的崩溃、ANR、Hook 失败或 RemotePreferences 异常；tombstones 中未出现模块包名。
- `apksigner verify -v` 与 `aapt2 dump badging` 确认 applicationId、versionCode/versionName、`minSdk`/`targetSdk`、`module.prop` 元数据正确。
- 实机 UI/Locale/Hook 回归与 API 102 环境独立验证尚未完成。

### 签名

- 从 `r14.13.0-rc1` 开始更换 APK 签名证书；`r14.13.3` 继续使用该新证书。
- 原 `r14.12.0` 及更早版本使用的签名私钥已经遗失，无法继续用于后续构建。
- 新签名版本无法直接覆盖安装旧签名版本。
- 升级前需要备份模块设置，卸载旧版本后再安装新版本。
- 新证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。

### 已知限制

- 实机设置应用、日间/夜间主题、语言切换、Root 重启反馈仍需验证。
- API 102 独立框架环境尚未验证。

## [r14.13.0-rc1] - 非公开候选

> 该候选版本未单独公开发布；相关工作已由 `r14.13.4` 正式版收口。

### 重构

- 完成 r14.13 第一阶段 Kotlin 与设置层代码整理。
- 完成 Java/Kotlin 边界与核心热路径审计。

### 修复

- 修复 Bitmap 缓存线程池线程数边界计算。
- 恢复振动辅助函数的可空 Context 容错语义。
- 网络速度格式化固定使用 `Locale.ROOT`。

### 性能

- 缓存状态栏手势路径使用的 DisplayManager 与 displayId，减少高频反射和重复查询。

### 验证

- 单元测试、Lint、Debug 和 Release 构建通过。
- 尚需用户完成长期实机回归和 LSPosed/Vector 日志审计。

## [r14.12.0] - 2026-07-26

### 版本定位

完成核心 Kotlin 迁移、生命周期与热路径治理，并以同一 APK 支持 libxposed API 101
和 API 102。Android 支持范围保持 HyperOS 1 / Android 14。

### 主要变化

- 使用 API 102 编译，`minApiVersion=101`、`targetApiVersion=102`、
  `staticScope=false`。
- 公共 Hook 路径只依赖 API 101 已有接口；未启用 Hot Reload、hook ID 和原子
  replacement。
- 核心 Hook、设置 UI 和工具代码完成保守 Kotlin 迁移，保留 `MainModule.java`、
  libxposed 兼容层及必要 JVM 反射边界。
- 修复应用选择页加载状态、分享/打开方式去重、隐私应用和应用锁重复数据。
- 收紧截图 DexKit 目标匹配，避免 Hook 到签名不符的方法。

### 生命周期与性能

- `AudioVisualizer` 的 Observer、Coroutine、动画和原生 `Visualizer` 随 owner 释放。
- `BatteryIndicator` detach 后注销 Receiver/Observer 和绘制回调。
- 音量模糊、截图栏隐藏和锁屏专辑封面监听在 SystemUI 重建后不重复注册。
- 双排信号、定时振动和 Launcher 图标缩放热路径减少临时对象、格式化和资源读取。
- 反射、DexKit 和资源探测保留在初始化路径；未引入轮询、永久后台任务或大型抽象层。
- 功能关闭时尽量不注册对应 Hook 和长期监听。

### 构建与依赖

- Groovy 构建脚本迁移到 Kotlin DSL，直接依赖集中到 version catalog。
- Gradle Wrapper 9.6.1、Android Gradle Plugin 9.2.1、Kotlin BOM 2.3.21。
- kotlinx.coroutines 1.11.0、libxposed API/service 102.0.0。
- Release 启用 R8、资源压缩、zipalign 和 APK Signature Scheme v2。

### 验证

- 单元测试、Debug、Release、Lint、`lintRelease`、`lintVitalRelease` 通过。
- API 101 依赖回编译、API 102 正式构建、Legacy Xposed API 扫描通过。
- APK 入口、scope、`module.prop`、签名和 zip alignment 已检查。
- API 101 实机完成安装、整机重启和完整 `full.log` 审计，未发现模块相关崩溃、ANR、
  入口、Hook 或 API 链接错误。
- APK 摘要、设备环境、日志扫描项和验证边界见[验证记录](docs/VERIFICATION.md)。

### 已知限制

- 仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`。
- API 102 实机仍需在对应框架环境独立验证。
- Hot Reload 未启用。
- 厂商系统应用更新可能改变 Hook 目标。

## [r14.8.0] - 2026-07-25

### 版本定位

建立核心 mods 大规模 Kotlin 化之前的基础设施稳定点，用于区分后续 Hook 迁移问题与
工具层问题。

### 主要变化

- `Helpers`、`AppHelper`、`ModuleHelper`、`HookerClassHelper`、`ResourceHooks`、
  `ShakeManager` 和 `ResourceConstants` 保守迁移到 Kotlin。
- `AppHelperTest`、`PrefMapTest`、`XposedHelpersCacheTest` 迁移到 Kotlin。
- 保持 Java/Kotlin 静态互操作、反射入口、Hook priority 和异常传播语义。
- 修复 `MainFragment`、`SpinnerEx`、`SortableListView` 和 Intent flags 的 Lint 问题。
- 清理旧 APK、临时构建日志和无用产物。

### 验证

- versionCode 170 / versionName `r14.8.0`。
- 单元测试、编译、Release、R8、Lint 和签名检查通过。
- 完整重启日志中模块加载成功，未发现模块相关崩溃或 ANR。

### 回退价值

保留为核心 Hook Kotlin 化、API 101/102 改造和后续生命周期治理之前的基础设施对照点。

## [r14.7.4] - 2026-07-25

### 版本定位

合并 r14.7.0–r14.7.3 的 Coroutine、设置子页面、UI 控件和小型工具迁移，作为 r14.7.x
唯一公开稳定版。

### 主要变化

- `BitmapCachedLoader`、天气、步数、音频可视化和电池指示器迁移到有生命周期的
  Kotlin Coroutine。
- Activity/App 选择器、搜索子页面和设置 Fragment 使用 lifecycle scope。
- 列表 Adapter 引入 ViewHolder，偏好控件和小型设置页迁移到 Kotlin。
- 动画缩放改用 `Settings.Global` 公共 API，并保留必要回退。
- 清理废弃构建产物、旧 APK 和临时日志。

### 验证

- versionCode 169 / versionName `r14.7.4`。
- Release 构建和 `lintVitalRelease` 通过。
- 完整重启日志中入口加载成功，未发现模块相关崩溃或 ANR。
- APK SHA-256：
  `1B2026B6FFAEE33C3BE50E4695EE8BF19EAA6740124A199153D89C63251F2329`。

### 回退价值

保留为 r14.7.x Coroutine/UI 迁移合并点，可与 r14.8.0 工具基础设施版分层对照。

## [r14.5.0] - 2026-07-24

### 版本定位

建立当前独立包名、签名与 GitHub 发布路径，是后续 Kotlin 和 API 改造的长期回退基线。

### 主要变化

- 源码包迁移到 `tv.withaibuild.customiuizer`。
- namespace 使用 `tv.withaibuild.customiuizer`，applicationId 使用
  `tv.withaibuild.customiuizer.r14`。
- Manifest、XML、Preference、Shortcut、Tasker 组件和 R8 规则同步更新。
- 为数字格式和大小写比较指定稳定 Locale。
- UI 设置重置由同步 `commit()` 改为 `apply()`。
- Handler 显式绑定主线程 Looper，动态 Receiver 补全 Android 14 导出标志。
- 高频 `Resources.getIdentifier()` 收敛到线程安全的资源 ID 缓存。

### 验证

- versionCode 150 / versionName `r14.5.0`。
- `assembleRelease`、`lintVitalRelease` 和签名检查通过。
- 完整重启后未发现模块相关崩溃、ANR 或异常栈。
- APK SHA-256：
  `DCB9EBC4BBE7AEE721B58F83B5371E1030AD7CAB0C4FE6CC4EAD900C420E8C93`。

### 回退价值

当前包名与签名线的最早公开稳定点。更早版本包名或工程结构不同，不适合作为普通用户
回退版本。

## 非公开工程里程碑

### r14.10.0

- 建立 libxposed API 101/102 单 APK 兼容边界。
- 构建脚本迁移到 Kotlin DSL，并用 version catalog 固定直接依赖。
- 完成 API 101 依赖回编译、API 102 Release、R8、资源压缩和 Legacy API 扫描。
- 该版本未作为当前公开回退 Release 保留，其成果已经合并到 r14.12.0。

## 历史阶段

### r14.0–r14.3

- 建立 HyperOS 1 / Android 14 和现代 libxposed API 101 独立维护线。
- 完成早期资源、反射、状态栏绘制和无效 Hook 优化。

### r14.5–r14.6

- 建立当前独立包名、签名和发布路径。
- 推进生命周期、双排信号、资源查找、R8 和测试治理。

### r14.7–r14.8

- 推进 Coroutine、设置 UI、工具类和基础设施 Kotlin 迁移。
- 清理 hidden API、Lint、死代码和废弃资源。

### r14.9–r14.12

- 完成核心 Hook 的保守 Kotlin 化和 Kotlin/JVM 边界复核。
- 建立 API 101/102 单 APK 兼容、Kotlin DSL 与 version catalog。
- 完成生命周期、重复注册、热路径、设置 UI、依赖和工具链审计。

更细的提交历史可由 Git tag 和 commit 追溯，不再为每个内部批次创建公开 Release。
