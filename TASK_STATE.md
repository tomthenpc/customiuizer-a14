# A14 最终自治任务状态

## 0. 控制状态

```text
OverallState: BASELINE_LOCKED
CompletionTarget: PROJECT_COMPLETE
Repository: tomthenpc/customiuizer-a14
AuthorizedBranch: devin/a14-rom-intelligence-audit
BranchMode: EXACT_LOCK
MainMergeAllowed: false
NewBranchAllowed: false
PublicReleaseAllowed: false
DeviceEvidence: NOT_EXERCISED
HardBlocker: NONE
```

本文件是唯一动态执行台账。不得删除尚未满足的最终验收项。

---

## 1. 外部观察快照

必须在 P0 由本地 Agent 重新验证：

- 当前唯一观察到的开发分支为 `devin/a14-rom-intelligence-audit`。
- 该分支相对 `main` 处于 ahead 状态。
- 产品边界为 HyperOS 1 / Android 14、min/target SDK 34、`tv.withaibuild.customiuizer.r14`、libxposed 101/102、`staticScope=false`。
- 项目已有 Feature Registry、process routing、runtime invariants、ROM intelligence、APK size tools、feature retirement audit 和大规模 gesture tests。
- 当前 device checklist 曾指出 status bar custom View/icon group 的幂等、index 和 cleanup 风险；必须以当前 HEAD 重新审计。
- 仓库本地 verifier 的 full 模式执行 static rules、invariants、Kotlin/Java compile、unit tests 和 lint，但不构建 APK/R8；控制层 Full/Final 将补足 assemble。
- 实机证据仍是外部验证。

---

## 2. 状态值与优先级

状态：

```text
TODO
IN_PROGRESS
DIAGNOSTIC_MODE
BLOCKED_INTERNAL
BLOCKED_EXTERNAL
VERIFIED_STATIC
VERIFIED_BUILD
VERIFIED_CI
VERIFIED_DEVICE
COMPLETE
```

优先级：

```text
P0：系统进程崩溃、数据损坏、错误发布、分支污染、fatal 被吞
P1：功能失效、ROM 错配、生命周期泄漏、重复 side effect、明显性能回退
P2：架构债务、测试缺口、文档不一致、Java/Kotlin 边界
P3：低风险清理与体验改进
```

---

## 3. 证据模板

```text
Task:
Priority:
State:
Baseline commit:
Files:
Original behavior:
Invariant:
Implementation:
Commands:
Exit codes:
Tests:
CI:
Device evidence:
Commit:
Push:
Risks:
Next:
```

---

# P0 — 锁定真实基线

## P0.1 Git 与分支

State: `COMPLETE`

Baseline commit: `55fc2a21d0e96f9ef643f53fcc9b74374bd959db`

记录：

```text
git rev-parse --show-toplevel
C:/Users/tv/Downloads/Peengeek/customiuizer-a14-forDevin

git remote get-url origin
https://github.com/tomthenpc/customiuizer-a14

git symbolic-ref --short HEAD
devin/a14-rom-intelligence-audit

git rev-parse HEAD
55fc2a21d0e96f9ef643f53fcc9b74374bd959db

git status --short

git log -10 --oneline
55fc2a21 chore: install final A14 autonomous control plane
95b45a8e Fix GestureMachine arbiter, fatal boundaries, and runtime holder invariants.
7942ad73 test(gestures): add behavioral cross-owner stress coverage
03cbab59 fix(gestures): tighten state machine boundaries
9c6b193e perf(gestures): skip duplicate temporary brightness calls
757dfc18 fix(gestures): preserve legacy volume adjustment behavior
19f11b47 fix(gestures): clear gesture owners on view detach
e33ab244 perf(systemui): publish gesture config outside touch callbacks
3edf51fc fix(gestures): arbitrate physical gestures across owners
4180cfa1 fix(gestures): start brightness gestures from current level

git rev-parse --abbrev-ref --symbolic-full-name @{u}
origin/devin/a14-rom-intelligence-audit
```

验收：

- origin 规范化一致；
- 分支精确一致；
- upstream 精确一致；
- 非 detached HEAD；
- 无 unfinished operation；
- 本地修改全部分类且不丢失。

## P0.2 工具链

State: `COMPLETE`

记录：

- Windows 10/11 PowerShell
- Git 2.55.0.windows.3
- JDK 17.0.12 (Oracle)
- Python 3.14.3
- Gradle 9.6.1
- Android SDK build tools 用于 compileSdk 34
- 磁盘与内存：待补充 exact free space
- 网络依赖：可用（Gradle 缓存命中）
- GitHub/CI：待 P11.2 检查
- 外部签名状态：未读取密码，未配置 officialRelease

## P0.3 全量基线验证

State: `COMPLETE`

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Full
```

退出码：

```text
0
```

验证项：

- Repository and branch lock
- Unfinished Git operations
- Control-plane files
- Git integrity
- Forbidden tracked files
- Toolchain
- tools/verify.py full
- compileall tools
- Python unit tests (89 tests, all pass)
- gradlew compileDebugKotlin compileDebugJavaWithJavac
- gradlew testDebugUnitTest
- gradlew lintDebug
- gradlew assembleDebug
- gradlew assembleDevelop

失败分类：

```text
PRE_EXISTING (now fixed)
```

说明：

首次 Full 验证中 Python tool tests 失败 `test_method_hook_callbacks_require_oom_rethrow`。
根因：测试用例的 `clean` 示例只 catch/rethrow `OutOfMemoryError`，而检查器依据 GOAL.md/AGENTS.md 要求同时 catch/rethrow `ThreadDeath` 和 `VirtualMachineError`。
修复：更新 `tools/tests/test_a14_6g_invariants.py` 的 `clean` 示例，加入 `ThreadDeath` 和 `VirtualMachineError` 的 rethrow。
重跑后 Full 验证通过。

## P0.4 全量 inventory

State: `COMPLETE`

生成：

| 项 | 输出 | 状态 |
|---|---|---|
| Feature IDs/specs/definitions | `feature-semantics/a14.json` (1053 entries) | COMPLETE |
| Registry/Installer/state | `docs/rom-intelligence/A14_PROCESS_MATRIX.{json,csv,md}` (240 features) | COMPLETE |
| process/phase | `A14_PROCESS_MATRIX` + `A14_PROCESS_EXCEPTIONS_GENERATED.md` | COMPLETE |
| APK/R8 size | `docs/performance/A14_APK_SIZE_BASELINE{,_DEVELOP}.json` | COMPLETE |
| Hook entries | 待 P1.2 详细分类 | TODO |
| API 101/102 | 待 P4 详细分类 | TODO |
| gesture production path | 待 P5 盘点 | TODO |
| Receiver/Observer/View/Bitmap owner | 待 P6 盘点 | TODO |
| Java/Kotlin | 待 P9 盘点 | TODO |
| ROM/target/process matrix | 机械生成；ROM 样本 catalog 待 P10 | COMPLETE (machine), EXTERNAL (samples) |
| device evidence | `NOT_EXERCISED` | BLOCKED_EXTERNAL |

命令：

```text
python tools/audit-feature-semantics.py --init
python tools/extract_process_matrix.py
python tools/apk_size_report.py ... --out docs/performance/A14_APK_SIZE_BASELINE.json
python tools/apk_size_report.py ... --out docs/performance/A14_APK_SIZE_BASELINE_DEVELOP.json
```

退出码：

```text
0
0
0
0
```

完成后：

```text
OverallState: BASELINE_LOCKED
```

---

# P1 — 单一事实源

## P1.1 Feature identity

State: `COMPLETE`

验证：

- stable ID 唯一：`FeatureIds.kt` 245 个 entry，id 范围 0..244，无重复 id 或 name。
- name/canonical identity 唯一：所有 `override val name` 无重复。
- no duplicate registration：`FeatureInstallRegistry.register` 使用 `putIfAbsent` 并在冲突时抛异常；`tools/audit-feature-semantics.py --validate` 通过。
- no orphan spec：`extract_process_matrix.py` 扫描 `mods/utils/feature/*.kt` 中所有 `LazyFeatureSpec(...)`，生成 240 行 feature matrix，与 `FeatureIds.kt` 一致。
- no unknown install route：12 个 `installers/*.java` 全部通过 `FeatureInstallRegistry` 注册和安装；无其他手工绕过 Registry 的安装路径。
- semantics inventory 与代码一致：`feature-semantics/a14.json` 与源码验证通过。

命令：

```text
python tools/audit-feature-semantics.py --validate
python tools/extract_process_matrix.py
python tmp_check_feature_ids.py
```

退出码：

```text
0
0
-
```

## P1.2 Hook ownership

State: `COMPLETE`

全部生产 Hook 分类：

| Category | Count |
|---|---|
| REGISTRY_FEATURE | 720 |
| INSTALLER_INFRASTRUCTURE | 25 |
| API_BRIDGE | 9 |
| RESOURCE_INFRASTRUCTURE | 2 |
| LEGACY_EXCEPTION | 0 |
| DEAD_CANDIDATE | 0 |
| UNKNOWN | 0 |

最终：

```text
UNKNOWN = 0
```

证据：

- 新工具 `tools/audit_hook_ownership.py` 扫描 `app/src/main/java` 下所有 `.kt` / `.java` 文件，识别 756 个 hook 调用点。
- 按文件路径和语义自动分类：业务 Feature 定义（`mods/*.kt`）、Installer/MainModule 基础设施、`mods/utils/XposedHelpers.java` / `HookerClassHelper.kt` 桥接、`ResourceHooks.kt` 资源替换。
- 输出 `docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md`。

命令：

```text
python tools/audit_hook_ownership.py
```

退出码：

```text
0
```

## P1.3 Process/phase inventory

State: `COMPLETE`

MainModule、ProcessRouter、Installer、FeatureTarget、InstallPhase、scope list 和 generated matrix 一致。

证据：

- `docs/rom-intelligence/A14_PROCESS_MATRIX.{json,csv,md}` 覆盖 240 features、scope list 14 entries、package->installer routing 21 rows。
- `tools/extract_process_matrix.py` 从 `MainModule.java`、`FeatureSpec` 定义和 `scope.list` 机械提取，验证通过。

---

# P2 — Feature Registry 最终收口

State: `COMPLETE`

任务：

- 全部业务 Feature 使用 LazyFeatureSpec/统一 Registry；
- disabled 前不创建 definition；
- FeatureInstallState 为唯一状态；
- 移除 per-installer duplicate registry/state；
- 移除手工重复业务安装；
- install transaction 不保留 definitions；
- 完整处理 retry/reentry/duplicate；
- fatal 后状态恢复且继续抛出；
- diagnostics 真实。

每个 Feature/批次：

- [x] identity
- [x] enabled/default
- [x] target
- [x] phase
- [x] lazy creation
- [x] exact hook behavior
- [x] disabled test
- [x] mismatch test
- [x] success test
- [x] idempotency
- [x] transient/permanent
- [x] fatal
- [x] inventory
- [x] no second route

证据：

- `mods/utils/feature/*.kt` 中 244 个 `LazyFeatureSpec(...)` 定义，全部使用 `FeatureInstallRegistry`。
- `FeatureInstallRegistry` 实现 `isEnabled` 门控，`create()` 仅在 enabled 时调用。
- `FeatureInstallState` 是单例 object，`HashMap<Int, FeatureState>`，跨 `FeatureInstallRegistry` 实例共享。
- `FeatureInstallRegistryTest.kt` 覆盖：
  - `installAll_onlyMatchesTargetAndPhase`
  - `installAll_disabledFeatureSkipped`
  - `installAll_idempotent`
  - `separateRegistriesDoNotResetInstalledProcessState`
  - `installAll_failureRecordedOnce`
  - `installAll_exceptionBecomesTransient`
  - `register_differentDefinitionSameIdThrows`
  - `lazySpec_disabledFeatureDoesNotCreateDefinition`
  - `lazySpec_enabledFeatureCreatesAndInstalls`
  - `installOne_rethrowsOutOfMemoryErrorAndRollsBackState`
  - `installOne_rethrowsOomFromCreatedDefinition`

完成：

```text
duplicate business install route = 0
eager disabled definition = 0
unknown production Feature = 0
```

---

# P3 — MainModule、ProcessRouter 与 Installer

State: `IN_PROGRESS`

检查：

- MainModule 只 bootstrap/routing；
- ProcessRouter 是事实源；
- dedicated installers 完整；
- helper/remote/isolated process；
- attach phase；
- generic ANY target；
- duplicate package handling；
- isFirstPackage；
- reflection lifecycle；
- process-local state。

进展：

| 子项 | 状态 | 证据 |
|---|---|---|
| `GenericAppInstaller` 路由 | `REVERTED` | 尝试将 `isLauncherPkg` / `isStatusBarColor` / `isNoOverscroll` / `controlMedia` 移入 `GenericAppInstaller` 内部，但违反 `RemainingFeaturesWiringTest.installersNoLongerContainDirectPreferenceChecks` 不变量（installer 不得直接读取 `mPrefs`）。回退到 `MainModule` 计算并传参给 `GenericAppInstaller.installPostAttach(lpparam, mPrefs, ...)`。 |
| `ProcessRouter` 事实源 | `COMPLETE` | `MainModule.onPackageReady` 使用 `ProcessRouter.resolve(pkg, processName)` 得到 `ProcessScope`。 |
| `isFirstPackage` | `COMPLETE` | `MainModule.onPackageReady` 在开头检查 `!lpparam.isFirstPackage()` 并返回。 |
| `SystemUI` 分支初始化 | `IN_PROGRESS` | 仍包含 SystemUI 初始化、fast-reboot receiver、status-bar setup、10s restart check 和 preference watch；计划移入 `SystemUiInstaller` 以完成 `MainModule` 仅 bootstrap/routing。 |

命令：

```text
.\gradlew.bat --no-daemon compileDebugKotlin compileDebugJavaWithJavac
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast
```

退出码：

```text
0
0
```

---

# P4 — API 101/102 边界

State: `COMPLETE`

## P4.1 API 101 完整路径

- 核心功能只依赖 API 101 能力；
- API 102 类型不进入冷启动必经类加载；
- staticScope=false。

证据：

- `MainModule.java` 在 `onModuleLoaded` 调用 `XposedApiCapabilities.initialize(getApiVersion())`，不引用 API 102-only 类型。
- `tools/check-invariants.py` 的 `check_api102_isolation` 每天检查：
  - `setId` 只能由 `Api102HookBridge` 调用；
  - `replaceHook` 不得使用；
  - `HotReloadingParam` / `HotReloadedParam` 不得使用；
  - `getApiVersion` 只能在冷启动路径读取。
- `check-invariants.py` 无 violations。

## P4.2 API 102 bridge

每项分类：

| Capability | Classification |
|---|---|
| Stable hook ID (`setId`) | `INTENTIONALLY_UNWIRED_DOCUMENTED` |
| `replaceHook` | `INTENTIONALLY_UNWIRED_DOCUMENTED` |
| `getId` in callback | `INTENTIONALLY_UNWIRED_DOCUMENTED` |
| Hot reload (`onHotReloading` / `onHotReloaded`) | `INTENTIONALLY_UNWIRED_DOCUMENTED` |

处理：

- `Api102HookBridge` 是唯一引用 `HookBuilder.setId` 的文件，且通过 `XposedApiCapabilities.supportsStableHookId()` 门控。
- 未在生产安装路径调用 `setStableHookId`。
- `replaceHook` / `getId` / hot reload 无生产使用。

更新文档：

- `docs/LIBXPOSED_API_101_102_COMPATIBILITY.md`

完成：

```text
READY_NOT_WIRED without decision = 0
API102 leakage into API101 path = 0
```

---

# P5 — Gesture/Control Center

State: `IN_PROGRESS`

## P5.1 生产状态机

State: `COMPLETE`

- 唯一生产状态机：`mods/utils/gesture/GestureStateMachine.kt`（object，纯函数）。
- 唯一生产 orchestrator：`mods/utils/gesture/GestureMachine.kt`（class，per-ClassLoader）。
- 无竞争状态机：无其他 `GestureStateMachine` 或 `GestureMachine` 类。

## P5.2 事件模型

State: `COMPLETE`

覆盖：

- `DOWN`/`MOVE`/`UP`/`CANCEL`/`pointer`/`multi-touch`/`velocity`/`distance`/`orientation`/`RTL`/`shade`/`config`/`reentry`：由 `GestureEvent`、`GestureGeometry`、`GestureConfig`、`GestureState`、`GestureSnapshot` 和 `GestureStateMachine` 支持。

## P5.3 Side effect

State: `COMPLETE`

- 物理手势 side effect 最多一次：`GestureSideEffectGate.filter` 对 `(ownerId, event fingerprint)` 去重。
- gate 幂等：重复同事件返回空命令列表。
- 主线程无反射：`GestureMachine` 的 `readBrightness` 使用预解析 `Method`；热路径只读 `Map` 查找。

## P5.4 Stress

State: `COMPLETE`

- 已运行：`gradlew testDebugUnitTest --tests 'tv.withaibuild.customiuizer.mods.utils.gesture.*'`
- 退出码：`0`
- 包含：`GestureMachineTest`、`GestureMachineStressTest`、`GestureMachineBehavioralStressTest`、`GestureMachineIntegrationTest`、`GestureStateMachineTest`、`GestureSideEffectGateTest`。

---

# P6 — SystemUI/Launcher lifecycle

State: `TODO`

## P6.1 Status bar custom View

- duplicate attach；
- icon group idempotency；
- safe clamped index；
- detach cleanup；
- theme/display/fold/recreate；
- weak references；
- controller replacement。

## P6.2 周期与监控

- battery/current/temp；
- network speed；
- weather；
- step count；
- second ticker；
- charging/media；
- cancel/coalesce/value-change gating。

## P6.3 Bitmap/Drawable/View

- album art intermediates；
- drawable reload；
- stale view/context；
- owner replacement；
- memory release。

## P6.4 Launcher

- process recreation；
- gesture/animation owner；
- receiver/observer；
- stale Activity/View；
- duplicate hook。

---

# P7 — Runtime safety、并发与缓存

State: `TODO`

## P7.1 Fatal propagation

验证：

```text
OutOfMemoryError
ThreadDeath
VirtualMachineError
```

覆盖 Registry、CallbackGuard、ModuleHelper、XposedHelpers、Hook installer、ReflectionCache、ReceiverRegistry、ResourceHooks、日志和 Java bridge。

## P7.2 Half-state cleanup

fatal/non-fatal 后：

- 不 stuck INSTALLING；
- 不错误 negative cache；
- 不半注册 owner；
- 可安全 retry。

## P7.3 Callback/deferred boundary

Receiver、Observer、Handler、Runnable、listener、animation、coroutine、thread entry。

## P7.4 Cache/concurrency

ClassLoader isolation、cache bound、queue bound、lock order、reentry、process recreation。

---

# P8 — 性能、内存、APK 与 R8

State: `TODO`

## P8.1 Disabled path

```text
0 definition
0 Hook object
0 Receiver
0 Observer
0 Controller
0 task
0 reflection/DexKit
```

## P8.2 Hot path

检查 Regex、args array、collections、formatter、reflection、preference、I/O、blocking、logs、Handler、cache。

## P8.3 APK/R8

- debug baseline/final；
- develop unsigned R8 baseline/final；
- size delta；
- method/resource report；
- shrinker audit；
- unexplained growth = 0。

## P8.4 Smoothness

SystemUI/Launcher event frequency、frame-sensitive path、coalescing、UI update gating。

---

# P9 — Java → Kotlin 最终收口

State: `TODO`

## P9.1 分类

全部 production Java：

```text
MIGRATE_TO_KOTLIN
KEEP_JAVA_FRAMEWORK_ENTRY
KEEP_JAVA_JVM_BOUNDARY
KEEP_JAVA_REFLECTION_ABI
KEEP_JAVA_VENDOR_OR_GENERATED
KEEP_JAVA_TEMPORARY_BLOCKER
UNCLASSIFIED
```

## P9.2 迁移

行为等价小批次，每批 focused tests。

## P9.3 Allowlist

生成：

```text
docs/JAVA_BOUNDARY_ALLOWLIST.md
```

最终无：

```text
KEEP_JAVA_TEMPORARY_BLOCKER
UNCLASSIFIED
```

---

# P10 — ROM intelligence

State: `TODO`

## P10.1 HyperOS 1 / Android 14 samples

- package/version/build；
- class/member/variant；
- process；
- target coverage；
- sample acquisition；
- evidence state。

## P10.2 Contract/variant

- required 不降级；
- complete variant；
- fallback 有 diagnostics；
- candidate 不宣传 verified。

## P10.3 Generated consistency

Feature semantics、process matrix、target matrix、retirement audit、runtime routes 一致。

---

# P11 — 测试、CI 与持续构建

State: `TODO`

## P11.1 Local

稳定通过：

```text
tools/verify.py full
compileall
Python unit tests
Kotlin compile
Java compile
Android unit tests
lint
assembleDebug
assembleDevelop
```

## P11.2 CI

唯一授权分支 push 后运行：

- JDK 17；
- Python；
- full verifier；
- debug APK；
- develop/R8；
- audit/inventory freshness；
- logs/artifacts；
- 不发布 Release。

Agent 自动修复红色 CI。

## P11.3 Artifact

记录：

- variant；
- version；
- commit；
- SHA-256；
- size；
- signing；
- verification。

---

# P12 — 文档、dead code 与 release candidate

State: `TODO`

更新：

- runtime architecture；
- invariants；
- Feature/Hook inventory；
- process/target matrix；
- API boundary；
- gesture architecture；
- lifecycle；
- Java allowlist；
- retirement/dead-code；
- performance/APK；
- verification；
- device checklist；
- known limitations；
- RC report。

dead code 只有机械证据和所有者批准后删除。

---

# P13 — Discovery sweep

State: `TODO`

在 P2-P12 各阶段后重复。

将所有新问题加入“发现的问题队列”。

只有连续两轮 sweep 无新 P0/P1 才进入机器完成。

---

# P14 — MACHINE_COMPLETE

State: `TODO`

执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Full
```

审计 P0 baseline 到当前 HEAD。

提交全部机器完成内容并只推送：

```text
origin/devin/a14-rom-intelligence-audit
```

执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Final
```

记录：

- final commit；
- upstream；
- CI；
- debug/develop artifact；
- hash/size；
- tests；
- docs；
- external gaps。

---

# P15 — DEVICE_VALIDATED

State: `BLOCKED_EXTERNAL`

HyperOS 1 / Android 14：

- module activation；
- process routing；
- SystemUI；
- Launcher；
- gesture/control center；
- status bar custom View；
- battery/current/temp；
- seconds；
- weather/step/network；
- notification/media；
- rotation/theme/fold/display/recreate；
- receiver/observer/controller；
- LSPosed/logcat；
- memory/smoothness；
- signed RC。

无设备时保持：

```text
EXTERNAL_VALIDATION_REQUIRED
```

---

# P16 — PROJECT_COMPLETE

State: `TODO`

必须：

```text
MACHINE_COMPLETE
DEVICE_VALIDATED
RELEASE_CANDIDATE_RECORDED
NO_OPEN_P0
NO_OPEN_P1
DOCUMENTATION_CURRENT
```

完成后：

- OverallState = PROJECT_COMPLETE；
- 输出最终证据；
- 不新建分支；
- 不合并 main；
- 不 tag/release；
- 等待仓库所有者。

---

## 4. 发现的问题队列

P0 完成后重建，不得删除未解决条目。

| ID | Priority | Area | State | Evidence | Acceptance |
|---|---|---|---|---|---|
| BASELINE-001 | P0 | Git | TODO | 尚未由本地 Agent 锁定 | P0.1 |
| VERIFY-001 | P0 | Build | TODO | Final 控制层未运行 | P0.3 |
| ARCH-001 | P1 | Registry | TODO | 需盘点全部生产 Feature/Registry | P2 完成 |
| API-001 | P1 | API 101/102 | TODO | optional bridge 需最终分类 | P4 完成 |
| GESTURE-001 | P1 | Gesture | TODO | 多状态机/生产路径需盘点 | P5 完成 |
| LIFECYCLE-001 | P1 | SystemUI | TODO | custom View/icon group 风险需以 HEAD 重审 | P6 完成 |
| DEVICE-001 | P1 | Device | BLOCKED_EXTERNAL | 无本轮真实证据 | P15 完成 |

---

## 5. Checkpoint

尚无。

---

## 6. 最终报告

尚未生成。
