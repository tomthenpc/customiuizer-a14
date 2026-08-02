# A14 最终项目目标

## 0. 文件性质

本文件定义 `tomthenpc/customiuizer-a14` 的最终项目终点，不是一次迁移任务，也不是固定待办列表。

自动 Agent 可以根据证据动态更新实现路线和 `TASK_STATE.md`，但不得：

- 修改或弱化本文件；
- 将当前实现反向定义为目标；
- 将编译成功描述为项目完成；
- 将静态检查描述成实机验证；
- 为了通过门禁删除功能、测试或兼容边界；
- 在完成前切换到其他开发分支。

最终目标是持续构建、持续发现、持续修复，直到达到本文定义的完成状态。

---

## 1. 仓库与分支锁

唯一授权仓库：

```text
tomthenpc/customiuizer-a14
```

允许识别以下等价远程地址：

```text
https://github.com/tomthenpc/customiuizer-a14
https://github.com/tomthenpc/customiuizer-a14.git
git@github.com:tomthenpc/customiuizer-a14.git
ssh://git@github.com/tomthenpc/customiuizer-a14.git
```

唯一授权写入分支：

```text
devin/a14-rom-intelligence-audit
```

授权模式：

```text
EXACT_LOCK
```

禁止以下模糊分支授权：

```text
devin/a14-*
devin/*
*a14*
当前任意 devin 分支
```

在 `PROJECT_COMPLETE` 前：

- 只允许在 `devin/a14-rom-intelligence-audit` 修改、提交和推送；
- 不得新建分支；
- 不得切换到其他分支继续；
- 不得 merge、rebase、force-push 或重写历史；
- 不得合并或推送 `main`；
- 不得创建 tag、GitHub Release 或自动合并 PR。

达到 `PROJECT_COMPLETE` 后，Agent 同样不得自行创建下一分支。它必须输出证据报告、保持当前精确分支、不 merge/tag/release，并进入 `CONTINUOUS_MAINTENANCE`，继续 evidence-driven 维护。

---

## 2. 最终产品定义

A14 的最终产品是：

> 面向 HyperOS 1 / Android 14 的稳定、低开销、可诊断、可持续维护的 libxposed 系统定制模块。所有生产功能均具有稳定身份、惰性启用、明确目标进程和安装阶段、可验证的 ROM/成员契约、结构化安装结果、受控生命周期和可追溯构建；SystemUI、Launcher 与系统进程中的 Hook 不因单个功能失败而失稳。

固定产品边界：

```text
Android 主版本：14
ROM 主目标：HyperOS 1 / Android 14
minSdk：34
targetSdk：34
ABI：arm64-v8a
applicationId：tv.withaibuild.customiuizer.r14
JDK：17
libxposed：minApiVersion 101 / targetApiVersion 102
staticScope：false
```

`compileSdk` 与 Android 构建工具可以在 Android 14 产品边界内更新，但必须保持构建、lint、R8、运行时 API 边界和可重复性。

不扩展到：

```text
Android 13
Android 15+
HyperOS 2+
其他 applicationId
其他 Android 大版本产品线
```

A13、上游 CustoMIUIzer 和 MonwF 仓库只作为只读历史或实现参考。不得 reset/rebase 到上游，不得直接覆盖当前 A14 实现。

---

## 3. 总体原则

项目以以下原则持续收口：

> 功能关闭时接近零成本；功能开启时只响应真实事件；高频路径无重复反射、无阻塞、无无意义分配；兼容逻辑限制在 ROM/ClassLoader 边界；普通功能失败不得拖垮系统进程；fatal JVM 错误不得被吞掉。

优先级：

1. 行为正确与系统稳定；
2. 进程、生命周期和异常边界；
3. ROM 兼容与可诊断性；
4. 功能关闭成本、热路径和内存；
5. 架构统一；
6. 安全 Kotlin 化；
7. 文档与发布自动化；
8. 低风险清理和美化。

---

## 4. 最终目标

### 4.1 功能完整与行为保持

- 保留当前有效功能、偏好键、默认值、重启/重载语义和用户可见行为。
- 不得通过删除功能、隐藏设置、改变默认值或吞掉失败使验证通过。
- 每个生产功能必须可追溯到：
  - stable Feature ID；
  - 名称与 canonical identity；
  - preference/system condition；
  - 默认值；
  - Feature target；
  - Install phase；
  - Installer owner；
  - Hook entry；
  - ROM/member contract；
  - compatibility/fallback policy；
  - FeatureInstallResult；
  - process-local state；
  - diagnostics；
  - resource/callback owner；
  - 自动测试；
  - 实机证据状态。
- 所有历史功能必须进入可生成的 inventory。
- 失效或疑似废弃功能必须先分类：
  - `KEEP`
  - `KEEP_GUARDED`
  - `REPAIR`
  - `EXPERIMENTAL`
  - `FREEZE_LEGACY`
  - `DELETE_CONFIRMED_DEAD`
- `DELETE_CONFIRMED_DEAD` 必须有机械证据，并取得仓库所有者明确批准。Agent 不得自行删除用户功能。

### 4.2 单一 Feature 生命周期

最终所有生产业务 Feature 必须使用统一生命周期：

```text
stable FeatureId
→ LazyFeatureSpec
→ isEnabled(prefs)
→ FeatureTarget
→ InstallPhase
→ create FeatureDefinition only when enabled
→ guarded install
→ FeatureInstallResult
→ process-local FeatureInstallState
→ diagnostics
→ lifecycle cleanup
```

要求：

- disabled Feature 不创建业务 `FeatureDefinition`、Hook 对象、Receiver、Observer、Controller、任务或反射工作。
- 同一 Feature 在同一进程只安装一次。
- `FeatureInstallState` 是唯一进程内安装状态来源。
- 不允许每个 Installer 各自维护重复 Registry/状态图。
- 不允许同一业务功能同时存在 Registry 路径与手工旧安装路径。
- Feature install transaction 不长期持有 FeatureDefinition。
- 重入、重复 package-ready、process recreation 和 transient retry 有明确语义。
- 普通 Feature 失败不阻止同批其他 Feature。
- 所有 production feature factory、spec、definition 和 install route 具备完整性门禁。

最终状态：

```text
duplicate feature identity = 0
duplicate business install path = 0
unknown production feature = 0
orphan feature spec = 0
eager disabled business object = 0
```

### 4.3 MainModule、ProcessRouter 与 Installer

最终架构：

- `MainModule` 只负责模块入口、偏好 bootstrap、资源入口和进程/包路由。
- 具体业务 Hook 由 dedicated Installer 或明确的 Feature 集合管理。
- `ProcessRouter`/`ProcessScope` 是进程判断的单一事实源。
- helper、secondary、remote、isolated process 默认拒绝，除非有显式模型与测试。
- package-specific Feature 不进入无边界的通用路径。
- `APPLICATION_ATTACHED` 只用于真正依赖目标应用 ClassLoader 的功能。
- SystemUI、Launcher、system_server、Settings、SecurityCenter、PackageInstaller、PowerKeeper、GuardProvider、Media、输入法和 generic app 路径必须可机械审计。
- `MainModule` 不恢复逐功能 preference branching。
- 不允许错误 ClassLoader、跨进程状态污染或重复初始化。

### 4.4 libxposed API 101/102 边界

API 101 是生产必经路径的最低能力。

要求：

- API 101 环境下核心功能完整可加载。
- API 102-only symbol 不得静态进入 API 101 必经路径。
- `HotReloadingParam`、hook stable ID、replaceHook 或其他 API 102 能力必须通过隔离桥和 capability check 使用。
- API 102 可选增强必须存在完整 API 101 fallback。
- 不允许永久保留无调用、无测试、无决策的 `READY_NOT_WIRED` 代码。
- 每个 API 102 bridge 最终必须分类：
  - `WIRED_WITH_SAFE_FALLBACK`
  - `INTENTIONALLY_UNWIRED_DOCUMENTED`
  - `REMOVE_CONFIRMED_DEAD`
- `INTENTIONALLY_UNWIRED_DOCUMENTED` 必须有原因、风险、重新评估条件和测试。
- `staticScope=false` 不得被无意改变。
- runtime path 不允许 legacy `de.robv.android.xposed` API，除受审计的 JVM/兼容边界文件外。

### 4.5 ROM intelligence 与目标解析

HyperOS 1 / Android 14 是正式主要目标。

要求：

- 每个受支持 ROM/package/process/class/member/variant 形成可生成 inventory。
- required、optional、candidate、fallback 语义明确。
- required target 不得为了通过测试降级为 optional。
- variant 必须整体匹配，不得拼接不同 SystemUI/Launcher 版本的部分成员。
- target 缺失必须产生结构化结果并安全跳过对应 Feature。
- DexKit、反射扫描、文件读取和 ROM inventory 保留在冷路径。
- ROM sample 缺失必须标记为：
  - `NOT_EXERCISED`
  - `EXTERNAL_EVIDENCE_REQUIRED`
- candidate 不等于已兼容。
- process matrix、target matrix、Feature semantics、retirement audit 和 runtime route 必须一致。
- ROM/app 版本变化后可自动生成差异报告，并将差异转化为任务。

### 4.6 手势与控制中心状态机

A14 当前存在复杂的状态栏/控制中心手势路径。最终必须形成一个可解释、可测试、低副作用的物理手势状态机。

要求：

- 一个物理手势最多触发一次业务 side effect。
- 明确处理：
  - DOWN/MOVE/UP；
  - CANCEL；
  - pointer change；
  - multi-touch；
  - orientation；
  - RTL/LTR；
  - status bar 与 control center 竞争；
  - shade expansion；
  - velocity/distance threshold；
  - config change；
  - duplicate framework event；
  - stale runtime holder；
  - reentrant callback。
- 配置读取、几何判断、状态迁移、依赖解析和 side effect 执行分离。
- 不允许生产路径同时保留两个竞争的 Gesture machine。
- 选择唯一生产状态机后，其他实现必须分类为测试模型、兼容 adapter 或确认废弃。
- side effect gate 必须幂等。
- 手势路径不得阻塞主线程、频繁反射、创建临时集合或高频日志。
- stress/property-like tests 覆盖长序列、随机序列和边界事件。
- 实机验证覆盖误触、连续手势、锁屏/解锁、旋转、分屏、浮窗和主题重载。

### 4.7 SystemUI 与 Launcher 生命周期

重点对象：

- custom status bar View；
- battery/current/temperature monitor；
- weather/step/network speed controller；
- clock second ticker；
- notification/media/album art bitmap；
- navigation/control-center drawable；
- Receiver/Observer；
- Handler/Runnable；
- coroutine scope；
- animation/listener；
- Activity/Context；
- reflection cache；
- Launcher owner/view/controller。

必须满足：

- attach/re-attach/configuration/theme/display/fold change 幂等；
- `addIconGroup`、View insertion 和 controller registration 不重复；
- View index 始终 clamp 到有效范围；
- owner replacement 释放旧对象；
- detached View 不继续被周期任务更新；
- Bitmap/Drawable 中间对象及时释放；
- receiver/observer/task 可取消并有界；
- weak owner 失效后不继续回调；
- stale Context/Activity 不被静态持有；
- SystemUI/Launcher recreation 不产生重复 Hook、重复 view、重复 ticker 或旧状态；
- lifecycle 失败有 diagnostics，不伪装成功。

### 4.8 Runtime safety 与 fatal boundary

以下错误必须始终继续抛出：

```text
OutOfMemoryError
ThreadDeath
VirtualMachineError
```

要求：

- Registry、Hook installer、callback guard、reflection cache、Receiver registry、resource hook、日志边界和 Java/Kotlin bridge 均保持 fatal 传播。
- fatal 发生后，不留下永久 `INSTALLING`、错误 negative cache、错误已安装状态或半注册 owner。
- 非 fatal 错误只影响对应 Feature，并记录真实 failure status。
- framework callback 和 deferred callback 有外层 failure boundary。
- broad `catch(Throwable)` 必须显式 rethrow fatal，再处理普通错误。
- `runCatching` 不得用于可能吞 fatal 的系统路径。
- fallback 不得把 `FAILED` 记录成 `INSTALLED` 或 `DISPATCHED`。
- 并发、重入、锁顺序和 stale state 有测试或结构证明。

### 4.9 性能与内存

disabled path 目标：

```text
0 business FeatureDefinition
0 business Hook object
0 Receiver
0 Observer
0 Controller
0 coroutine/task
0 polling
0 DexKit/reflection scan
```

hot path 禁止：

- 临时 Regex；
- 只读参数的 args array copy；
- 重复 class/member lookup；
- 临时 List/Map/Set；
- 重复 formatter 创建；
- 高频字符串拼接；
- 文件/网络 I/O；
- 阻塞等待；
- 高频远程 preference 读取；
- 重复 Handler/Runnable；
- 无界 cache/queue；
- 每事件日志。

要求：

- Reflection cache 按 ClassLoader 隔离、有界、可安全失效。
- Resource replacement 使用稀疏结构或等价低成本结构。
- 周期任务按真实需要采样、可取消、可合并。
- UI 更新只在值或可见状态变化时执行。
- APK size、method/resource changes 和 R8 输出有基线与差异报告。
- 性能优化必须保持行为，并提供 benchmark、计数、结构证明或回归测试。

### 4.10 安全 Java → Kotlin 收口

最终目标不是 100% Kotlin，而是：

> 适合迁移的业务逻辑完成行为等价迁移；保留 Java 的文件全部有明确 JVM/反射/框架边界理由。

全部生产 Java 文件必须分类：

```text
MIGRATE_TO_KOTLIN
KEEP_JAVA_FRAMEWORK_ENTRY
KEEP_JAVA_JVM_BOUNDARY
KEEP_JAVA_REFLECTION_ABI
KEEP_JAVA_VENDOR_OR_GENERATED
KEEP_JAVA_TEMPORARY_BLOCKER
UNCLASSIFIED
```

最终禁止：

```text
KEEP_JAVA_TEMPORARY_BLOCKER
UNCLASSIFIED
```

迁移必须保持：

- JVM signatures；
- static/instance 语义；
- overload resolution；
- visibility；
- reflection-visible names；
- ClassLoader；
- nullability；
- exception propagation；
- synchronized/volatile；
- class initialization order；
- callback capture；
- resource ownership；
- Hook timing；
- API 101/102 边界。

最终生成并维护：

```text
docs/JAVA_BOUNDARY_ALLOWLIST.md
```

### 4.11 测试、CI 与持续构建

必须持续通过：

- product/static rules；
- runtime invariants；
- Feature identity uniqueness；
- Feature inventory consistency；
- ProcessRouter tests；
- disabled lazy-construction tests；
- target/phase mismatch；
- duplicate/reentrant/idempotency；
- transient/permanent failure；
- fatal propagation；
- callback guard；
- Receiver/Observer lifecycle；
- ReflectionCache；
- resource/view lifecycle；
- gesture state-machine stress；
- Kotlin compile；
- Java compile；
- Android unit tests；
- lint；
- Python tool tests；
- debug APK assemble；
- unsigned develop APK/R8 assemble；
- APK size report；
- dead-code/retirement evidence；
- GitHub Actions CI。

每次 push 到唯一授权分支后：

```text
Agent 读取 CI
→ 定位首个根因
→ 修复
→ 添加回归门禁
→ 再 push
→ 直到全部通过
```

禁止忽略红色 CI。

### 4.12 构建与签名

固定：

- 正式签名配置位于仓库外。
- `officialRelease=true` 只有外部签名齐全时运行。
- 不得提交 keystore、password、token、真实 `keystore.properties`、`local.properties`、`.env`、APK、AAB、私人日志或缓存。
- debug/develop/release artifact 必须记录：
  - versionName/versionCode；
  - commit SHA；
  - SHA-256；
  - file size；
  - signing status；
  - build variant；
  - verification state。
- unsigned develop 用于 R8/shrinker 验证，不得宣传为正式发布。
- Agent 不得自行创建公开 Release。

### 4.13 文档与审计闭环

最终文档至少包括：

- current runtime architecture；
- runtime invariants；
- Feature inventory；
- Hook ownership inventory；
- process matrix；
- target/variant matrix；
- ROM sample catalog；
- API 101/102 boundary；
- gesture architecture；
- SystemUI/Launcher lifecycle；
- Java boundary allowlist；
- dead-code/retirement audit；
- performance/APK size audit；
- verification matrix；
- device regression checklist；
- release candidate report；
- known limitations；
- external evidence gaps。

可机械生成的数字、表格和清单不得重复手工维护。旧阶段文档不能与当前代码冲突。

---

## 5. Agent 自主发现与持续修复

Agent 不能只执行初始任务表。每个任务、批次和阶段完成后必须主动扫描：

- compiler warning；
- lint；
- test failure、skip 和 coverage gap；
- TODO/FIXME/temporary/workaround；
- duplicate Registry/state/Feature route；
- orphan spec/preference/resource；
- unreachable installer；
- unknown Hook owner；
- stale architecture docs；
- callback guard；
- Receiver/Observer/Handler/coroutine/View/Bitmap lifecycle；
- unsafe view index；
- duplicate icon group；
- stale owner/context；
- OOM/fatal boundary；
- API 101/102 leakage；
- Gesture machine duplication；
- hot-path allocation/reflection/I/O/blocking；
- cache/queue bounds；
- APK size/R8；
- ROM target drift；
- CI；
- LSPosed/logcat evidence（存在时）。

新发现的问题写入 `TASK_STATE.md`，包含：

```text
ID
Priority
Evidence
Affected behavior
Reproduction
Acceptance
Dependencies
State
```

在后续会话开始时，选择最高优先级、未阻塞、可独立验收的 Task Slice。

---

## 6. 完成状态

### `BASELINE_LOCKED`

- 仓库、origin、精确分支、upstream、HEAD 已记录。
- 全量验证已运行。
- Feature、Hook、process、phase、Java/Kotlin、tests、docs、ROM、APK size 和 device evidence 已盘点。
- 初始失败已分类。

### `ARCHITECTURE_COMPLETE`

- MainModule 只 routing/bootstrap。
- 全部生产 Feature 使用统一 lazy Registry 生命周期。
- FeatureInstallState 是唯一安装状态来源。
- duplicate business install route = 0。
- unknown production Feature/Hook = 0。
- API 101/102 边界完成分类。
- 手势只有一个明确生产状态机。
- 所有 runtime owner/lifecycle 可审计。
- 文档与代码一致。

### `MACHINE_COMPLETE`

必须满足：

- `ARCHITECTURE_COMPLETE`；
- Java/Kotlin 收口完成，剩余 Java 全部进入 allowlist；
- invariants、编译、tests、lint、tools、debug assemble、develop/R8、APK size audit 全部通过；
- GitHub CI 通过；
- 无未解释 dead code、orphan、duplicate 或 unknown；
- 连续两轮 discovery sweep 无新增 P0/P1；
- working tree clean；
- local HEAD 与授权 upstream HEAD 一致；
- HEAD 已推送到唯一授权分支；
- 无 unfinished Git operation；
- `TASK_STATE.md` 包含完整机器证据。

### `DEVICE_VALIDATED`

HyperOS 1 / Android 14 实机完成：

- module activation；
- system_server；
- SystemUI；
- Launcher；
- gesture/control center；
- status bar custom View；
- battery/current/temperature；
- seconds ticker；
- weather/step/network；
- notification/media/album art；
- rotation/theme/fold/display/recreation；
- receiver/observer/controller cleanup；
- LSPosed/logcat fatal scan；
- 内存和流畅度观察；
- signed RC 可追溯。

### `PROJECT_COMPLETE`

必须同时满足：

```text
MACHINE_COMPLETE
DEVICE_VALIDATED
RELEASE_CANDIDATE_RECORDED
NO_OPEN_P0
NO_OPEN_P1
DOCUMENTATION_CURRENT
```

达到后：

- 输出最终证据报告；
- 不创建新分支；
- 不合并 main；
- 不 tag/release；
- 进入 `CONTINUOUS_MAINTENANCE`；
- 继续 evidence-driven 维护。

如果机器工作全部完成，但缺少手机、ROM、签名或其他外部证据，状态改为：

```text
EXTERNAL_VALIDATION_REQUIRED
```

不得伪造 `DEVICE_VALIDATED` 或 `PROJECT_COMPLETE`。

---

## 7. 允许动态调整

允许：

- `TASK_STATE.md`；
- 任务优先级与批次；
- 实现路线；
- 代码、测试、工具和 CI；
- 普通文档；
- 生成的 inventory/audit；
- 新发现问题；
- 只读 subagent 审计。

禁止动态调整：

- 仓库；
- 精确分支；
- HyperOS 1 / Android 14 产品边界；
- applicationId/minSdk/targetSdk/API 101/102/staticScope；
- 功能保持原则；
- fatal error 规则；
- required target 规则；
- 测试/CI/构建门禁；
- 完成定义；
- Git/secret/signing 安全；
- 本文件；
- `AGENTS.md`；
- `scripts/verify.ps1`。

---

## 8. 非目标

未经仓库所有者明确指令，不做：

- Android 13 或 Android 15+ 支持；
- HyperOS 2 适配；
- 新 UI 大改；
- 大规模新功能扩张；
- 强制 100% Kotlin；
- 与 A13 机械同构；
- reset/rebase 到上游；
- main 合并；
- tag/release；
- 仓库内签名；
- 无实机证据的兼容宣传。

---

## 9. 最终验证入口

控制层审计：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Audit
```

内循环：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast
```

完整机器验证：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Full
```

最终提交、推送和同步后：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Final
```

---

## 10. 长期产品与维护宪章（v5）

本章节由仓库所有者通过 `A14_GOAL_EXTREME_V5.md` 与 `COMMON_LONG_HORIZON_CONSTITUTION_V5.md` 授权加入，补充长期治理，不扩大 A14 Android/ROM 产品边界。

### 10.1 产品角色

```text
ProductRole: ANDROID_14_ACTIVE_STABLE_REFERENCE
PrimaryPlatform: HyperOS 1 / Android 14
DevelopmentPolicy: CORRECTNESS_AND_ARCHITECTURE_EVOLUTION
FeaturePolicy: CONTROLLED
CompatibilityPolicy: DEVICE_AND_CONTRACT_EVIDENCE
```

A14 同时承担：

1. HyperOS 1 / Android 14 上稳定、低开销、可诊断的生产模块。
2. 未来 Android/HyperOS 新仓库可继承的工程 reference 与迁移资产。

第二项不得损害第一项。

### 10.2 长期生命周期状态

```text
ACTIVE_HARDENING
RELEASE_CANDIDATE
STABLE
LTS
SECURITY_ONLY
EXTERNAL_VALIDATION_REQUIRED
ARCHIVE_READY
ARCHIVED
```

- `ACTIVE_HARDENING`：当前状态。允许架构修复、性能优化、兼容补强和安全迁移。
- `RELEASE_CANDIDATE`：机器门禁通过，等待真实设备、签名和最终回归。
- `STABLE`：有正式设备证据、RC 和已知限制。
- `LTS`：默认功能冻结，以稳定、兼容、构建恢复和高优先级修复为主。
- `SECURITY_ONLY`：只处理 P0/P1、构建链失效、严重兼容、安全与供应链、设备 bootloop/fatal。
- `ARCHIVE_READY`：源码可构建、依赖归档、artifact 可追溯、迁移资产已导出。
- `ARCHIVED`：只读历史状态。

### 10.3 永久不变量

```text
no silent fatal swallowing
no wrong-process business install
no duplicate business owner
no false device evidence
no mutable baseline
no secret in repository
no unbounded runtime container
no user feature deletion without owner approval
no current document contradicting current code
no unsupported version marketing
```

### 10.4 发布阶段

```text
MACHINE_CANDIDATE
DEVICE_CANDIDATE
SIGNED_RC
OWNER_APPROVED_RELEASE
```

Agent 只能推进到前三项，不能公开 `OWNER_APPROVED_RELEASE`、tag 或 Release。

### 10.5 未来版本边界

- A14 不直接扩展到 Android 15 / HyperOS 2+。
- 新 Android/HyperOS 大版本必须新建仓库并独立验证。
- A14 输出 `NEXT_REPO_BOOTSTRAP_KIT` 作为迁移资产。

### 10.6 治理文档

详细长期宪章、稳定性契约、性能预算、供应链与退役要求见：

```text
docs/governance/LONG_HORIZON_CONSTITUTION.md
```
