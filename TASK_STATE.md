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
| `SystemUI` 分支初始化 | `COMPLETE` | `MainModule` 已委托 `SystemUiBootstrapCoordinator.install`，coordinator 负责 hook、context、fast-reboot receiver、status-bar setup、preference watch、10s restart guard，然后调用 `SystemUiInstaller.install`。 |
| `GenericAppEligibilityResolver` | `TODO` | 按 v4 方向：MainModule → GenericAppEligibilityResolver → immutable GenericAppSelection → GenericAppInstaller；resolver 读取 `mPrefs`，installer 只执行。 |
| `helper/remote/isolated process` | `COMPLETE` | `ProcessScope.isInstallable` 拒绝 `SYSTEM_UI_PLUGIN`、`SETTINGS_REMOTE`、`SECURITY_CENTER_REMOTE`、`SECURITY_CENTER_BOOTAWARE`、`NETWORK_STACK`、`UNSUPORTED`。 |
| `duplicate package handling` | `COMPLETE` | `MainModule.onPackageReady` 每个 `ProcessScope` 只调用一个 dedicated installer；`ProcessRouter.resolve` 保证 package→scope 唯一。 |
| `attach phase` | `COMPLETE` | `GenericAppInstaller.installPostAttach` 仅在 `Application.attach` 回调内创建 `FeatureInstallRegistry` 并安装 `LAUNCHER` / `ANY` features。 |
| `generic ANY target` | `COMPLETE` | `CommonPackageFeatures` 和 `GenericAppFeatures` 明确返回 `FeatureTarget.ANY` 并仅在 `PACKAGE_READY` / `APPLICATION_ATTACHED` 安装。 |
| `reflection lifecycle` | `COMPLETE` | `MainModule` 在 `SYSTEM_UI` 和 `LAUNCHER` 分支调用 `ReflectionCache.onSafeLifecycle(lpparam.getClassLoader())`。 |
| `process-local state` | `COMPLETE` | `MainModule.mPrefs` 是进程单例；各 installer 为无状态 static 工具类。 |

## P3.2 — SystemUI bootstrap 与 fatal 边界

State: `COMPLETE`

- 提取 `SystemUiBootstrapCoordinator`：已完成，路径 `mods/utils/SystemUiBootstrapCoordinator.kt`，显式状态枚举 `UNINITIALIZED → HOOK_INSTALLED → CONTEXT_READY → BASE_READY → PREFERENCE_READY → COMPLETE / FAILED_TRANSIENT`。
- 创建 `FatalErrors.rethrowIfFatal` / `FatalErrors.unwrapAndRethrowIfFatal` 共享 helper：已完成，路径 `mods/utils/FatalErrors.kt`。
- 用 `FatalErrors.rethrowIfFatal` 替换 `MainModule` 中单独 rethrow `OutOfMemoryError` 的 `catch(Throwable)` 块：已完成。
- `MainModule.onPackageReady` 中 `ProcessScope.SYSTEM_UI` 分支现在只调用 `SystemUiBootstrapCoordinator.install(lpparam, mPrefs, this::initPrefs)`。
- 更新 `rom-contracts/hyperos1-a14-core.json` sourceFile 指向 `SystemUiBootstrapCoordinator.kt`。
- 更新 `SystemUiInstallerTest` 和 `FastRebootContractTest` 以验证新入口点与调用顺序。
- 通过 `tools/verify.py full`（invariants + compile + test + lintDebug）。

命令：

```text
python tools/verify.py full
```

退出码：

```text
0
```

---

# P4 — API 101/102 边界

State: `COMPLETE`

## P4.1 API 101 完整路径

State: `COMPLETE`

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

State: `COMPLETE`

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

State: `COMPLETE`

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

## P5.5 Pointer contract / Gate / Arbiter

State: `COMPLETE`

- `MotionEvent.pointerCount` 语义已明确：`GestureEvent.pointerCount` 为 raw，`GestureEvent.activePointerCount` 为 post-action 归一化值（`ACTION_UP` -> 0，`ACTION_POINTER_UP` -> `pointerCount - 1`）。
- `GestureStateMachine` 和 `GestureSideEffectGate` 指纹已切换为 `activePointerCount`。
- `GestureSideEffectGate.filter` 已改用 `commands.any(::isBusinessEffect)`，避免热路径中间列表。
- `PhysicalGestureArbiter` 已增加 `MAX_HELD_TOKENS` 硬上限、`STALE_TOKEN_AGE_MS` stale cleanup、满载拒绝与 `PhysicalGestureArbiterTest`。
- `GestureMachine` 在 UP/CANCEL/Reset/detach 时释放 token。

---

# P6 — SystemUI/Launcher lifecycle

State: `IN_PROGRESS`

## P6.1 Status bar custom View

State: `VERIFIED_BUILD`

- `SystemUIStatusBarHooks` 使用 `WeakReference<View>` 持有 `statusbarTextIcons`；注册/更新时清理已回收引用；
- `DualRowsStatusbarHook` 用 `XposedHelpers.getAdditionalInstanceField` 的 `dualRowsLayoutAdded` 标记防止重复 attach；
- `SystemUiViewLifecycleTest` 验证 `mPctRef` 不是 strong reference；
- `BatteryIndicator` 在 `onDetachedFromWindow` 中 `unregisterOwnedReceiver`、`unregisterPreferenceObserver`、`viewScope.cancel()`、`mStatusBar = null`。

命令：

```text
.\gradlew.bat --no-daemon testDebugUnitTest
.\gradlew.bat --no-daemon lintDebug
```

退出码：

```text
0
0
```

## P6.1A Status bar dispatcher/controller 注册泄漏与重复 hook

State: `VERIFIED_BUILD`

Task: A14-P6.1A-R1（re-attempt：修复上一轮 rejected 的 review 项）
Priority: P1
Files:
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/OwnedRegistrations.kt`（重写：弱 owner、exact-once handle、重入安全）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`（新增）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookInstallStateMachine.kt`（新增）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/RegistrationReleases.kt`（新增）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FatalErrors.kt`（扩展 unwrap 边界）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt`（hook 失败边界）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`（per-display 生命周期）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/OwnedRegistrationsTest.kt`（扩展）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistryTest.kt`（新增）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/HookInstallStateMachineTest.kt`（新增）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/RegistrationReleasesTest.kt`（新增）
- `tools/check-invariants.py`（status-bar-registration-cleanup 结构规则重写）
- `tools/tests/test_status_bar_registration_invariants.py`（新增反例测试）
- `docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md`（行号/数量更新）

Original behavior（R1 修复前本轮复核仍存在的问题）：

- `OwnedRegistrations` 只返回 opaque token，无 exact-once handle；重入 cleanup 可能重复执行；owner 是强引用，无法被回收；`cleanupWhere` 是单阶段直接遍历，回调里再次 `register/cleanup` 会修改 live list。
- `SystemUIStatusBarHooks` 使用单一全局 `statusBarRegistrations` + `statusBarGeneration`：多显示/折叠/多实例下旧实例互相清理、新实例错误清理另一显示的当前实例；`netSpeedSecondRowRef` 全局，只保存最近一个 second-row；`setNetworkSpeedIcon` hook 用 `hookAllMethods`（非 silent）且 `netSpeedSecondRowHookInstalled = true` 在调用之前设置，失败后不会恢复 retryable，安装异常被吞。
- `moveLeft` re-attach 路径仍手动 `ModuleHelper.callMethodSilently(iconController, "removeIconGroup", staleManager)`，无 handle，重复调用会多次尝试移除。
- `ModuleHelper.hookAllMethodsSilently` 只 rethrow `OutOfMemoryError`，`ThreadDeath` / `VirtualMachineError` 以及 `XposedHelpers.InvocationTargetError` 包裹的 fatal 可能被标记为失败并吞掉。
- `tools/check-invariants.py` 的 `status-bar-registration-cleanup` 规则只统计字符串，无法识别 M6/M7a 等反例。

Invariant（R1 强化）：

- 状态栏注册以 display + View 代际为 owner，不得使用全局单 generation；null display 必须按 View identity 隔离。
- `addDarkReceiver` / `addIconGroup` 必须配对 `removeDarkReceiver` / `removeIconGroup`，且 cleanup 与 add 参数严格一致。
- `setNetworkSpeedIcon` hook 安装使用显式状态机：`UNINSTALLED -> INSTALLING -> INSTALLED|UNINSTALLED`；只有 `hookAllMethodsSilently` 返回成功才进入 `INSTALLED`；class/method 缺失或普通异常后恢复 `UNINSTALLED` 可重试；fatal error 按 `FatalErrors` 传播。
- 所有 `catch(Throwable)` 在 reflection/Xposed 边界上先 `unwrapAndRethrowIfFatal`。
- 回调若不在主线程，不直接访问 View；提取不可变数据并 `post` 到目标 row；posted runnable 重新校验 row 仍属于当前 display generation。

Implementation：

- `OwnedRegistrations`：使用 `WeakReference<V>` owner；返回 `RegistrationHandle`，`cleanupNow` 严格一次；`cleanupWhere` 先 snapshot 再运行，重入安全；cleanup 失败逐项隔离，fatal 通过 `FatalErrors.unwrapAndRethrowIfFatal` 传播。
- `StatusBarDisplayRegistry`：按 `displayId` 维护 `StatusBarDisplayState`（generation + secondRow + `OwnedRegistrations`）；null display 使用 `IdentityHashMap` 临时 bucket；`bind()` 迁移 pending、替换旧 generation 时全量清理旧注册；`prune()` 移除 dead display/pending states。
- `HookInstallStateMachine`：提供 `UNINSTALLED/INSTALLING/INSTALLED` 三态，`install { }` 保证同一进程只安装一次；异常路径先置 `UNINSTALLED` 再 fatal rethrow；非 fatal 失败返回 `false`。
- `RegistrationReleases`：`releaseRegistrationSilently` 统一处理 target-null、method-missing 和 invocation failure，记录 `HookDiagnostics`（仅字符串/状态/异常类型），并 `unwrapAndRethrowIfFatal`。
- `SystemUIStatusBarHooks`：`DualRowsStatusbarHook` 与 `moveLeft` 的 `onAttachedToWindow` 均 `bind` 到 per-display state；network speed second row 存于 `state.secondRow`；network speed hook 通过 `installNetSpeedSecondRowHook` 安装；回调 `netSpeedSecondRowHookCallback` 遍历所有 display state，主线程外 `row.post`，校验 `isAttachedToWindow` / owner / row 一致后创建/更新 view；dark receiver / icon group 使用 `releaseRegistrationSilently` 通过 handle 清理；left icon manager 保存 `leftIconRegistrationHandle` additional instance field。
- `ModuleHelper`：`hookAllMethodsSilently`/`hookAllMethods`/`hookMethod`/`findAndHookConstructor`/`hookAllConstructors` 全部改为先 `FatalErrors.unwrapAndRethrowIfFatal(t)`，再记录/返回/日志。
- `FatalErrors`：`unwrapAndRethrowIfFatal` 增加对 `XposedHelpers.InvocationTargetError` 的解包。
- `tools/check-invariants.py`：`check_status_bar_registration_cleanup` 结构规则重写：检测旧全局 state、检测 add/remove 参数一致性、检测 `leftIconRegistrationHandle`、检测 `HookInstallStateMachine` / `StatusBarDisplayRegistry`、禁止手动 `ModuleHelper.callMethodSilently` remove。

Commands / Exit codes：

```text
python tools/check-invariants.py                                  -> 0 (203 files, no violations)
.\gradlew.bat :app:testDebugUnitTest                                -> 0
python -m unittest discover -s tools/tests -p "test_*.py"           -> 0 (Ran 286, OK)
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast -> 0
.\gradlew.bat :app:lintDebug                                        -> 0
```

Tests（新增/扩展）：

- `OwnedRegistrationsTest`：handle exact-once、cleanup after generation、reentrant cleanup、weak owner collection、fatal propagation、failure isolation。
- `StatusBarDisplayRegistryTest`：bind、pending isolation、same-display replacement cleanup、cross-display isolation、same owner reattach、pending migration、prune dead states、multi-display second rows。
- `HookInstallStateMachineTest`：first failure retryable、second success、success only once、reentrant install ignored、fatal error propagates and resets、`LinkageError`/`NoSuchMethodError` non-fatal retryable。
- `RegistrationReleasesTest`：successful release、target null silently skipped、missing method recorded、release failure recorded、fatal propagates。
- `tools/tests/test_status_bar_registration_invariants.py`：clean source passes + 8 counterexamples for M6/M7a/global state/delete cleanup/wrong args/etc.

CI：`Fast CI` 通过（GitHub Actions run 30864033691）；`Full CI` 默认仅在 commit message 含 `[full-ci]` 时触发，本次未触发，等待下轮显式触发或定期调度。

Commit: `f97661f2`（`fix(statusbar): harden per-display status bar registration lifecycle (A14-P6.1A-R1)`）

Progress snapshot commit: `5f46030e`（`chore(progress): update A14_PROGRESS_CURRENT snapshot after P6.1A-R1`）

Push: `origin/devin/a14-rom-intelligence-audit` 已推送至 `f97661f2` 和 `5f46030e`

Device evidence: `NOT_EXERCISED`（真机需验证：多显示/折叠/DPI/主题切换后，各 display 状态栏自定义图标只存在当前代、dark 着色正确、netspeed 第二行按 display 更新、left icons 无重复 group）。

Risks：

- `View.context.display` 在部分 ROM 上可能为 null，已提供 null display 临时 bucket + `onAttachedToWindow` 迁移。
- `releaseRegistrationSilently` 对 `XposedHelpers.InvocationTargetError` 解包后 fatal 仍传播，非 fatal 被记录但不会影响当前功能。
- 多显示场景依赖 `View.display.displayId` / `Context.display.displayId`，与 SystemUI 实际 display 映射一致；若 ROM 在 `onFinishInflate` 时仍未绑定 display，会保留 pending 直到 attach。

## P6.1A-R2 Status bar 生命周期再次加固

State: `VERIFIED_BUILD`

Task: `A14-P6.1A-R2`
Priority: `P1`
Files:
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/OwnedRegistrations.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/OwnedRegistrationsTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistryTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelperFatalBoundaryTest.kt`（新增）
- `tools/check-invariants.py`
- `tools/tests/test_a14_6g_invariants.py`
- `tools/tests/test_status_bar_registration_invariants.py`
- `docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md`
- `docs/progress/A14_PROGRESS_CURRENT.json` / `.md`

Original behavior（R2 修复前）：

- `OwnedRegistrations` cleanup callback 类型仍带 `owner: V` 参数，owner 被 GC 后 callback 无法获取 owner 参数，`cleanupWhere`/`cleanupNow` 仍存在以 `owner != null` 为条件的 gate，导致 weak owner cleanup 未执行。
- `StatusBarDisplayRegistry` pending bucket 仅依赖 `WeakHashMap` 的 value 可达性；owner 被 GC 后 `WeakHashMap` 在 `prune` 读取前已 expunge 条目，state 连同 `OwnedRegistrations` 一起被回收，cleanup 未运行。
- `StatusBarDisplayRegistry.prune` 对 dead display 和 pending state 先清理、再移除，但移除逻辑不够明确；dead display 的 second row 未在清理时释放。
- `SystemUIStatusBarHooks` 中网络速度回调 cleanup closure 仍捕获 `owner`（`{ owner -> ... }`），导致 `OwnedRegistrations` 的 entry 强持有 owner 无法被回收；posted runnable 只在 `applyNetworkSpeedToRow` 内部校验，未在 post 块内重新读取 row/owner/state。
- `ModuleHelper` 中 `callMethodSilently`/`getObjectFieldSilently`/`getStaticObjectFieldSilently` 已用 `FatalErrors.unwrapAndRethrowIfFatal`，但 `findContext` 与 `openAppInfo` 仍存在 `catch(_: Throwable)` 吞 fatal。
- `tools/check-invariants.py` 的 `status-bar-registration-cleanup` 仍可能误识别 `_ ->` cleanup closure 为通过，且未覆盖 `OwnedRegistrations`/`StatusBarDisplayRegistry` 结构、未覆盖 `row.post` 内 re-verify。

Invariant（R2）：

- `OwnedRegistrations.register` 的 cleanup 必须是无参 `() -> Unit`，不再在 cleanup 路径中读取 owner；`cleanupAll`/`cleanupWhere` 先 snapshot 再运行；callback 在运行前被消费（null），避免 fatal 导致重复执行；handle 返回 `RegistrationHandle.cleanupNow()` 为 exact-once。
- `StatusBarDisplayRegistry` pending state 必须能被 `prune` 找到并主动释放；`bind` 替换同 display 旧 generation 时先 `cleanupAll`；`prune` 清理 dead display/pending 后再移除，且移除前 re-verify generation 与 registration list 为空。
- 所有 `catch(Throwable)` 在 `ModuleHelper` 中必须先 `FatalErrors.unwrapAndRethrowIfFatal(t)`。
- `SystemUIStatusBarHooks` cleanup closures 不得捕获 owner view；`row.post` 必须重新读取 `state.secondRow` 与 `state.generation` 并传给 `applyNetworkSpeedToRow`；`applyNetworkSpeedToRow` 必须校验 `isAttachedToWindow` / generation / second row。
- 静态 invariant 必须能识别上述违规（无参 cleanup、no owner-capture、WeakHashMap + strong pending set、prune cleanup-before-remove、post re-read、ModuleHelper fatal boundary）。

Implementation：

- `OwnedRegistrations`：将 `register` 签名改为 `register(owner: V, cleanup: () -> Unit)`；`Handle.cleanupNow`/`runCleanupOnce` 不读取 owner，先消费 `entry.cleanup` 再调用；新增 `cleanupAll()`；`cleanupWhere` 使用 `toRemove` 快照。
- `StatusBarDisplayRegistry`：新增 `pendingStates` 强引用集合保存 pending `StatusBarDisplayState`，确保 owner 被 GC 后 state 仍可达以便 `prune` 释放；`getOrCreatePending` 加入 `pendingStates`；`bind` 从 `pendingByOwner` 与 `pendingStates` 移除；`prune` 同时扫描 `byDisplay` 与 `pendingStates`，调用 `cleanupAll` 后 re-verify 再移除。
- `ModuleHelper`：将 `findContext` 与 `openAppInfo` 的 `catch(_: Throwable)` 改为 `catch(t: Throwable)` 并先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`，非 fatal 再继续原有 fallback。
- `SystemUIStatusBarHooks`：所有 `state.registrations.register(...)` 的 cleanup 改为无参 lambda；`netSpeedSecondRowHookCallback` 的 `row.post` 块内重新 `state.secondRow?.get()` / `state.generation?.get()` 并调用 `applyNetworkSpeedToRow`。
- `tools/check-invariants.py`：新增 `check_owned_registrations_model`、`check_status_bar_display_registry_prune`，扩展 `check_status_bar_registration_cleanup` 覆盖 `state.secondRow = WeakReference(secondRight)`、`leftIconRegistrationHandle`、posted 与 `applyNetworkSpeedToRow` 双重 re-verify、owner-capture cleanup closure；`check_module_helper_fatal_boundaries` 拒绝 `catch(_: Throwable)`，要求命名变量并 fatal unwrap。
- `tools/tests/test_status_bar_registration_invariants.py`：重写并扩展为 27 个测试（passing clean source、safe rewrites、11 counterexamples、owned model 5 tests、display registry 4 tests）。
- `tools/tests/test_a14_6g_invariants.py`：更新 `test_module_helper_requires_oom_rethrow_before_generic_catch` 反映新的命名变量与 `FatalErrors` 要求。

Commands / Exit codes：

```text
python tools/check-invariants.py                                                    -> 0 (203 files, no violations)
python -m unittest discover -s tools/tests -p "test_*.py"                             -> 0 (Ran 304, OK)
.\gradlew.bat :app:testDebugUnitTest                                                  -> 0 (710 tests, 0 failures/errors/skips)
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast    -> 0
python tools/progress_snapshot.py --write                                             -> 0
```

Tests（新增/扩展）：

- `OwnedRegistrationsTest`：新增 `cleanupWhereRunsCallbackAfterOwnerCollection`、`cleanupNowRunsCallbackAfterOwnerCollection`、`cleanupAllRunsCallbackAfterOwnerCollection`、handle 与 `cleanupAll` 不重复、`cleanupAllSnapshotDoesNotRunNewRegistrations`、`cleanupWhereReentryIsIsolated`、`cleanupAllFatalPropagates`、`cleanupAllIsExactOnce`。
- `StatusBarDisplayRegistryTest`：新增 `pruneReleasesRegistrationsForDeadDisplay`、`pruneReleasesRegistrationsForDeadPendingOwner`、`pruneKeepsStateWhenCleanupAddsNewRegistration`、`activeGenerationIsNotPruned`、`activeSecondRowWithoutGenerationIsPruned`、`displayReuseDoesNotInheritOldRegistrations`、`pendingOwnersAreWeaklyHeld`、`pendingABIdentityIsolation`、`pendingMigratesRegistrationsToDisplayOnBind`。
- `ModuleHelperFatalBoundaryTest`（新增）：验证 `callMethodSilently` 传播 `ThreadDeath`/`InternalError`/`OutOfMemoryError`，并验证 missing method/field 返回 `NOT_EXIST_SYMBOL`。
- `tools/tests/test_status_bar_registration_invariants.py`：27 个测试覆盖 R2 反例。
- `tools/tests/test_a14_6g_invariants.py`：ModuleHelper fatal boundary 测试更新。

CI：`Fast` 模式本地通过；GitHub Actions A14 Fast CI run 30871850423 / job 91875290860 在 67cfe83e 上通过（8m57s）。`Full` 模式需 `[full-ci]` 触发，等待下轮显式触发或定期调度。

Commit: c2904adbd01bb38fc4ea327f670d898e6644736b

Progress snapshot commit: 78ce1129

Push: `origin/devin/a14-rom-intelligence-audit` 4b30770a..67cfe83e

Device evidence: `NOT_EXERCISED`（真机需验证：弱 owner GC 后 dark receiver / icon group 被移除、多 display 切换无泄漏、折叠/DPI/主题切换后状态栏自定义图标只保留当前代、netspeed 第二行按 display 更新、left icons 无重复 group）。

Risks：

- `WeakHashMap` pending bucket 配合 `pendingStates` 强集合增加了少量对象存活时间，但保证 owner 被 GC 后 cleanup 仍可运行；`prune` 调用时机需由 SystemUI hook 触发（如 `onDetachedFromWindow` / `onDestroy`）或由调用方在 bind 前调用。
- 无参 cleanup lambda 要求所有调用点不捕获 owner；`tools/check-invariants.py` 已增加静态 owner-capture 检测，但仍需 review 未来新增调用点。
- `ModuleHelper.openAppInfo` fallback 现在先 rethrow fatal 再记录；原行为在 fatal 时也会记录到 Xposed log，现在 fatal 直接抛出，符合 AGENTS.md 要求。

## P6.1A-R3 Status bar 身份生命周期与主线程调度

State: `VERIFIED_BUILD`

Task: `A14-P6.1A-R3`
Priority: `P1`

Files:
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/WeakIdentityMap.kt`（新增）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`（重写 pending/identity 与 detach）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarNetworkSpeedDispatcher.kt`（新增：可测试主线程 dispatcher）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt`（`getCPUThermalId` fatal 边界与可测试拆分）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`（主线程 network speed dispatch、detach hook、prune scheduler）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/WeakIdentityMapTest.kt`（新增）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StatusBarNetworkSpeedDispatcherTest.kt`（新增）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistryTest.kt`（扩展）
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelperFatalBoundaryTest.kt`（扩展）
- `tools/check-invariants.py`（`WeakIdentityMap` 与 network-speed dispatcher 规则）
- `tools/tests/test_status_bar_registration_invariants.py`（R3 反例覆盖）
- `docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md`（行号/数量更新）

Original behavior（R3 修复前）：

- `StatusBarDisplayRegistry` 使用 `java.util.WeakHashMap` 作 pending bucket，`WeakHashMap` 按 `equals/hashCode` 比较 key，导致逻辑相同（`EqualOwner`）的不同 owner 实例共享同一份 pending `StatusBarDisplayState`。
- 使用强 `mutableSetOf<StatusBarDisplayState>` 保存 pending states，没有明确的释放边界；`StatusBarDisplayState` 作为 `mutable data class` 放在 `HashSet` 中，字段变更后 `hashCode` 变化，无法 `remove`。
- `StatusBarDisplayState` 的 `secondRow`/`generation` 是 `WeakReference`，但 `allStates()` 在后台线程被 network speed 回调读取，与 bind/prune 并发。
- `SystemUIStatusBarHooks` 网络速度回调在后台线程遍历 `allStates()`，对非主线程的每个 `row` 调用 `row.post { ... }`，posted closure 捕获旧的 `row`/`owner`/`state`/`allStates` iterator。
- 没有 `onDetachedFromWindow` 释放边界，status bar View 被 detach 后无法主动释放对应 display/pending state。
- `ModuleHelper.getCPUThermalId` 在 `catch (t: Throwable)` 中直接吞掉 fatal；且一旦开始扫描就写 `thermalIdScanned = true`，若扫描中遇到 fatal 会留下 stale `-1`。

Invariant（R3）：

- pending owner 容器必须使用 identity（`===` / `System.identityHashCode`）比较，不能依赖 `equals/hashCode`；必须按 key 的 `identityHashCode` 处理冲突；引用队列清理；无永久后台线程。
- `StatusBarDisplayState` 不再是 `data class` 且不再放入 `HashSet`；pending 容器必须弱引用 owner，state 被回收后仍能取出并运行 cleanup。
- network speed 回调只运行在主线程；posted closure 只能捕获 immutable payload 与 sequence；必须丢弃乱序旧 payload；应用时在主线程重新读取 registry snapshot，并验证当前 row/owner 仍属于 state。
- 必须安装 `MiuiPhoneStatusBarView.onDetachedFromWindow` 一次 guarded hook，detach 时切换到 SystemUI main thread 释放对应 owner 并 prune。
- `ModuleHelper.getCPUThermalId` 必须先 `FatalErrors.unwrapAndRethrowIfFatal(t)`；fatal 异常在写入 `thermalId`/`thermalIdScanned` 之前重新抛出。

Implementation：

- `WeakIdentityMap`：使用 `WeakReference` key 与 `System.identityHashCode(referent)`，segmented 链表处理 identity hash 冲突，`ReferenceQueue` 清理，`expunge()` 取出 cleared key 对应 value；lookup 使用临时 `WeakKey` 不 strong hold owner；remove 按 identity 匹配；无后台线程。
- `StatusBarDisplayRegistry`：`pendingByOwner = WeakIdentityMap()`；`byDisplay` 保持 strong display→state；`getOrCreatePending` 按 owner identity 隔离；`bind` 迁移 pending 并清理旧 generation；`detach` 按 identity 精确移除 owner 并 cleanup；`prune` 调用 `pendingByOwner.expunge()` 取出 dead pending states 并 `cleanupAll`；`allStatesSnapshot()` 返回不可变快照供主线程读取；新增 `onPendingChanged` 回调，外部可调度 prune runnable。
- `StatusBarNetworkSpeedDispatcher`：纯 Kotlin 对象，无 Android View/Xposed 依赖；`dispatch(payload, seq, lastApplied, registry, applier)` 在调用线程丢弃 stale sequence、读取 `allStatesSnapshot`、调用 applier。
- `SystemUIStatusBarHooks`：`netSpeedSecondRowHookCallback` 在后台线程仅构造 `NetworkSpeedPayload` 与 sequence，post 到 `netSpeedMainHandler`；main runnable 调用 `StatusBarNetworkSpeedDispatcher.dispatch` 并传入 `::applyNetworkSpeedToRow`；`applyNetworkSpeedToRow` 在主线程重新读取 `row`/`owner`，校验 `isAttachedToWindow`、state secondRow/generation 一致后再更新 View。新增 `statusBarViewDetachHookInstaller` / `installStatusBarViewLifecycleHook`，安装一次 `MiuiPhoneStatusBarView.onDetachedFromWindow`，detach 时 post 到 `statusBarMainHandler` 调用 `statusBarDisplayRegistry.detach(sbView)` 并 `prune`。`StatusBarDisplayRegistry` 通过 `onPendingChanged` 调度 `statusBarPendingPruneRunnable`（250ms delay）到主 looper，无永久后台线程。
- `ModuleHelper.getCPUThermalId`：拆出 `scanForCpuThermalId(readType)` 与 `readThermalType`；公共 `getCPUThermalId()` 只在完整非 fatal scan 后写 `thermalId` 与 `thermalIdScanned`；`scanForCpuThermalId` 中 `catch (t: Throwable)` 先 `FatalErrors.unwrapAndRethrowIfFatal(t)` 再 `null`。
- `tools/check-invariants.py`：`status-bar-display-registry-prune` 要求 `WeakIdentityMap`、禁止 `WeakHashMap`、要求 `detach`/`expunge`/`allStatesSnapshot`；`status-bar-registration-cleanup` 要求 `StatusBarNetworkSpeedDispatcher`、所有 `netSpeedMainHandler.post` block 不得捕获 stale row/owner/state/iterator，主 runnable 必须调用 `dispatch`。

Commands / Exit codes：

```text
python tools/check-invariants.py                                                      -> 0 (205 files, no violations)
python -m unittest discover -s tools/tests -p "test_*.py"                              -> 0 (307 tests)
python tools/check_document_contracts.py                                              -> 0
python tools/check_automation_state.py                                                -> 0
python tools/progress_snapshot.py --write                                             -> 0
python tools/verify.py fast                                                           -> 0
.\gradlew.bat --no-daemon :app:testDebugUnitTest                                      -> 0 (736 tests, 0 failures)
.\gradlew.bat --no-daemon :app:lintDebug :app:assembleDebug :app:assembleDevelop      -> 0
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast   -> 0
```

Tests（新增/扩展）：

- `WeakIdentityMapTest`：same instance shares state、equal but distinct owners isolated、identity hash collision disambiguated、remove by identity exact、replace refreshes、expunge returns cleared values、values snapshot only reachable values。
- `StatusBarDisplayRegistryTest`：新增 equal-but-distinct pending owners、pending becomes bound count zero、modifying state fields does not break removal、detach bound owner releases state、delayed detach does not affect new generation、detach idempotent、pending count notifies scheduler、state object identity stable after field mutation。
- `StatusBarNetworkSpeedDispatcherTest`：applies payload to current snapshot、stale sequence dropped、newer sequence replaces old and old payload ignored、snapshot does not concurrently modify。
- `ModuleHelperFatalBoundaryTest`：新增 `scanForCpuThermalId` first match / no match、direct `ThreadDeath`/`OutOfMemoryError` 传播、wrapped `VirtualMachineError` 解包传播、ordinary errors ignored、`getCPUThermalId` memoizes no-match result。
- `tools/tests/test_status_bar_registration_invariants.py`：新增 `WeakHashMap` 反例、`detach`/`expunge`/`allStatesSnapshot` 缺失反例、posted runnable 捕获 stale state/iterator 反例。

CI：`Fast` 模式本地通过；GitHub Actions A14 Fast CI run 30959226850 / job 92159218435 在 8397bdbb 上通过（9m3s）。`Full` 模式需 `[full-ci]` 触发，本次未触发。

Commit: 674ea6a7

Progress snapshot commit: (state commit, see git log)

Push: origin/devin/a14-rom-intelligence-audit 7c72e81..(state commit)

Device evidence: `NOT_EXERCISED`（真机需验证：双 `EqualOwner` 实例在 pending 阶段获得不同 state；status bar detach 后 dark receiver / icon group 被释放；network speed 旧 payload 被丢弃且只应用最新值；多 display 切换时第二行按 display 更新；`getCPUThermalId` 在 fatal sysfs 错误时重新抛出且不写 memoized state）。

Risks：

- `WeakIdentityMap` 依赖 `System.identityHashCode` 与 `===`，所有 `getOrCreatePending`/`bind`/`detach` 调用点必须保证传入的是同一对象实例；不同但 `equals` 的 owner 不再共享 state，符合 R3 要求但调用方不能依赖旧 `WeakHashMap` 行为。
- `StatusBarNetworkSpeedDispatcher.dispatch` 要求调用线程已经是 SystemUI main looper；`SystemUIStatusBarHooks` 通过 `netSpeedMainHandler.post` 保证。若 future hook 绕过 dispatcher 直接遍历 `allStatesSnapshot` 将违反 invariant。
- `installStatusBarViewLifecycleHook` 使用 `HookInstallStateMachine` 保证一次安装，但依赖 `DualRowsStatusbarHook` 或 `StatusBarIconsPositionAdjustHook` 被启用；若两者都禁用，该进程内没有 status bar registry 使用，无需 detach hook。
- `getCPUThermalId` 的 `scanForCpuThermalId` 注入仅用于测试；生产路径仍直接读取 `/sys/devices/virtual/thermal/thermal_zone*/type`，HyperOS 14 上路径存在；其他 ROM 若无此路径会返回 `-1` 并 memoize，属既有行为。

## P6.2 周期与监控

State: `COMPLETE`

- `DeviceInfoMonitor` 在 `ACTION_SCREEN_ON` 恢复后台 tick，`ACTION_SCREEN_OFF` 移除消息；
- `PowerManager.isInteractive` 二次门控；
- 失败计数指数退避；配置变化触发 `stopMonitoring` 并重新 hook；
- `Handler` 在主/后台 looper 上创建，旧 `Handler` 在重新 hook 前移除消息并置 null。

## P6.3 Bitmap/Drawable/View

State: `COMPLETE`

- `BatteryIndicator` 的 `ShapeDrawable`、`Paint`、`Shader` 等按 View 实例创建；`onDetachedFromWindow` 释放引用；
- `SystemUIStatusBarHooks` 的状态栏文本图标使用 `WeakReference` 并由 `DeviceInfoMonitor` 在屏幕关闭时暂停；
- 无 strong 静态 View/Context 引用。

## P6.4 Launcher

State: `COMPLETE`

- `ProcessRouter.resolve` 将 `com.miui.home` 映射到 `ProcessScope.LAUNCHER`；
- `LauncherInstaller.install` 和 `LauncherInstaller.handleLoadLauncher` 分别安装 `PACKAGE_READY` 和 `APPLICATION_ATTACHED` 阶段；
- `MainModule` 的 `isFirstPackage` 守卫防止 process recreation 重复初始化；
- `MainModule` 在 `LAUNCHER` 分支调用 `ReflectionCache.onSafeLifecycle`。

## P6.5 Lifecycle owner inventory

State: `STATIC_OWNER_COMPLETE`

- `PhoneStatusBarView` 的 `onAttachedToWindow` / `onDetachedFromWindow` 调用 `statusBarMachine.clear(ownerId)`。
- `ControlCenterWindowViewImpl` 的 `onAttachedToWindow` / `onDetachedFromWindow` 在 `ControlCenterPluginRuntime.installControlCenterGestureHooks` 内调用 `controlCenterMachine.prepare(ownerId)` 和 `controlCenterMachine.clear(ownerId)`。
- `ControlCenterGestureRuntimeHolder.bind` 在检测到新 ClassLoader 时调用 `existing?.machine?.clear()`，确保旧 runtime 的机器状态释放。
- `ControlCenterPluginRuntime` 是 `PluginFactory.createPlugin` 的唯一所有者，提供对称 `clear()`，释放 `activeLoader`、`ControlCenterGestureRuntimeHolder` 和 `PhysicalGestureArbiter` 的全部状态。
- `ControlCenterPluginRuntime.bind` 在 fatal 安装失败时先 `clear()` 再 rethrow，不发布半安装状态。
- 已生成 `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` 记录 owner 清单、释放边界与已知缺口。
- `DEVICE_LIFECYCLE_ENTRY_BLOCKED`: 当前仓库、framework stub 和 ROM intelligence 文档中均未发现 `PluginInstance$PluginFactory` 的 `destroyPlugin` / `onPluginUnloaded` / `unload` 等可靠自动销毁入口，不猜测 Hook；`ControlCenterPluginRuntime.clear()` 已在架构上对称，可在发现可靠入口时直接调用。

---

# P7 — Runtime safety、并发与缓存

State: `COMPLETE`

## P7.1 Fatal propagation

State: `COMPLETE`

- `OutOfMemoryError`、`ThreadDeath`、`VirtualMachineError` 在 `CallbackGuard`、`ModuleHelper`、`XposedHelpers`、`FeatureInstallRegistry`、`HookerClassHelper`、`PreferenceBootstrap`、`ResourceHooks` 等关键路径中被识别并继续抛出；
- `MethodHook` 回调的 fatal 检查在 `HookerClassHelper` 中实现；
- `FeatureInstallRegistry` 在 fatal 后清理半安装状态，不写入 negative cache。

## P7.2 Half-state cleanup

State: `COMPLETE`

- `FeatureInstallRegistry` 的 `installAll` 在单个 Feature 失败时继续安装其他 Feature；失败结果记录为 `FAILED`，状态机不 stuck 在 `INSTALLING`；
- `ReceiverRegistry` 在 `WeakOwnerReceiver` 被收集后自动清理；
- `DeviceInfoMonitor` 在重新 hook 时停止旧的 Handler/Receiver 并置 null；
- `BatteryIndicator` 在 `onDetachedFromWindow` 中释放所有注册。

## P7.3 Callback/deferred boundary

State: `COMPLETE`

- 所有 `BroadcastReceiver`、`ContentObserver`、`Handler`、`Runnable`、`animation` 回调和 `coroutine` 入口均经过 `ModuleHelper.guarded`；
- `CallbackGuard` 对 Runnable/Supplier 包装 fatal 检查；
- `GlobalActionSystemServerHooks` 的 fast-reboot receiver 经过 `ModuleHelper.registerOwnedReceiver` 并在失败时回滚设置。

## P7.4 Cache/concurrency

State: `COMPLETE`

- `ReflectionCache` 按 `ClassLoader` 隔离，缓存命中零分配；
- `ReflectionCacheAllocationTest` 验证 `findField`/`findMethodBestMatch` 缓存命中不分配；
- `ReceiverRegistry.ownedReceivers` 使用 `CopyOnWriteArrayList`，按 owner 弱引用清理；
- `DeviceInfoMonitor` 的 `monitorLock` 保护 Handler/Receiver 切换；
- `SystemUIStatusBarHooks.statusbarTextIcons` 使用 `WeakReference` 并在访问时清理。

## P7.5 Observe / hot-path eligibility

State: `COMPLETE`

- `SystemUIControlCenterHooks` 的 status bar `onInterceptTouchEvent` 不再调用 `statusBarMachine.observe()`；intercept 路径不拥有 touch 流，无需计算状态机/配置。
- 消除了无返回值消费的热路径状态机计算。

命令：

```text
.\gradlew.bat --no-daemon testDebugUnitTest
```

退出码：

```text
0
```

---

# P8 — 性能、内存、APK 与 R8

State: `VERIFIED_BUILD`

## P8.1 Disabled path

State: `VERIFIED_BUILD`

```text
0 definition
0 Hook object
0 Receiver
0 Observer
0 Controller
0 task
0 reflection/DexKit
```

证据：

- 全部生产 Feature 通过 `LazyFeatureSpec` 注册；`FeatureInstallRegistry.installOne` 仅在 `spec.isEnabled(prefs) == true` 时调用 `spec.create()`。
- `LazyFeatureSpec` 的 `factory` lambda 只在 `isEnabled` 返回 true 时执行，确认不触发 `FeatureDefinition` 构造。
- `FeatureInstallRegistryTest.lazySpec_disabledFeatureDoesNotCreateDefinition` 通过（`tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistryTest`）。
- 唯一非 `LazyFeatureSpec` 注册的是 `PackagePermissionsFeature`，但它 `isEnabled = true` 且 `preferenceKey = null`，属于强制基线 hook，不是 disabled path。
- 未发现 Installer 在 `installAll` 前绕过 Registry 直接创建 hook/receiver/observer/controller/task。

## P8.2 Hot path

State: `VERIFIED_BUILD`

检查 Regex、args array、collections、formatter、reflection、preference、I/O、blocking、logs、Handler、cache。

证据：

- 运行 `HotPathArgumentMaterializationTest` 通过：确认 launcher、gesture、screenshot、system_server pass-through 路径不物化 `XposedHelpers.getArgsArray(chain)`；
- 运行 `NavBarButtonsHotPathContractTest` 通过；
- 运行 `ReflectionCacheAllocationTest` 通过：反射/DexKit 查询结果按类缓存，不重复分配；
- 运行 `ResourceHooksTest` 通过：资源 hook 路径正确；
- 静态检查 `proguard-rules.pro` 与 `FeatureInstallRegistry` 代码：disabled path 不创建业务定义、hook 对象、receiver、observer、controller、task。

## P8.3 APK/R8

State: `VERIFIED_BUILD`

- debug baseline/final：已通过 `.\\gradlew.bat --no-daemon :app:assembleDebug` 构建，输出已记录；
- develop unsigned R8 baseline/final：已建立 baseline。
  - 文件：`app/build/outputs/apk/develop/CustoMIUIzer-A14-r14.16.1-develop-unsigned.apk`
  - 大小：`3398166` bytes（3.24 MB）
  - 构建命令：`.\gradlew.bat --no-daemon :app:assembleDevelop`
  - mapping/usage/config：`app/build/outputs/mapping/develop/`
  - R8 启用，shrinkResources 启用，isDebuggable = false；
- size delta：两次 `--no-build-cache --no-configuration-cache clean :app:assembleDevelop` 产物对比通过；`python tools/apk_semantic_diff.py --require-reproducible first-develop.apk second-develop.apk` 输出 `normalizedEqual=True`，0 added/removed/changed；
- method/resource report：从 develop R8 mapping 提取 baseline：
  - `mapping.txt`: 127492 lines
  - `usage.txt` (removed): 26856 lines
  - `seeds.txt` (kept): 5087 lines
  - `resources.txt`: 2827 lines
  - `configuration.txt`: 525 lines；
- shrinker audit：`proguard-rules.pro` 当前规则已审阅，未发现 `-dontshrink/-dontobfuscate/-dontoptimize` 或 `androidx/**/kotlinx/**` 类冗余 keep；
- unexplained growth = 0：已通过 `python tools/apk_semantic_diff.py --require-reproducible` 验证，两次 clean develop 构建 `normalizedEqual=True`，0 added/removed/changed。

## P8.4 Smoothness

State: `VERIFIED_BUILD`

SystemUI/Launcher event frequency、frame-sensitive path、coalescing、UI update gating。

证据：

- 运行 `GestureMachineBehavioralStressTest` 与 `GestureMachineStressTest` 通过：手势状态机在 stress 序列下行为稳定、无内存泄漏或重复 side effect；
- 手势架构已将 config、geometry、state、dependency、effect 分离，`GestureSideEffectGateTest` 与 `StatusBarGestureEffectExecutorTest` 验证 side effect 幂等与 UI 更新门控。

---

# P9 — Java → Kotlin 最终收口

State: `VERIFIED_BUILD`

## P9.1 分类

State: `VERIFIED_STATIC`

全部 production Java 已分类，结果写入 `docs/JAVA_BOUNDARY_ALLOWLIST.md`：

```text
MIGRATE_TO_KOTLIN     -> 13 installer + XposedHelpers
KEEP_JAVA_FRAMEWORK_ENTRY -> MainModule.java (XposedModule entry)
KEEP_JAVA_VENDOR_OR_GENERATED -> org/apache/commons/lang3/reflect/MemberUtilsX.java
KEEP_JAVA_TEMPORARY_BLOCKER -> 0
UNCLASSIFIED -> 0
```

无 `KEEP_JAVA_REFLECTION_ABI` 或 `KEEP_JAVA_JVM_BOUNDARY` 项；Installer 与 `XposedHelpers` 方法均为直接调用，不暴露反射 ABI。

## P9.2 迁移

State: `VERIFIED_CI`

行为等价小批次，每批 focused tests。

13 个 installer Java → Kotlin 已完成：

- `AndroidPackageInstaller`
- `GenericAppInstaller`
- `GuardProviderInstaller`
- `InputMethodInstaller`
- `LauncherInstaller`
- `MediaInstaller`
- `PackageInstallerRouter`
- `PhoneInstaller`
- `PowerKeeperInstaller`
- `SecurityCenterInstaller`
- `SettingsInstaller`
- `SystemUiInstaller`

全部转换为 Kotlin `object`，保留 `@JvmStatic` 供 `MainModule.java` 调用；`GenericAppInstaller` 中的 `MethodHook` 覆盖转为 `override fun after(param: AfterHookCallback)`；`LauncherInstaller.handleLoadLauncher` 同样保留 `@JvmStatic`。

`XposedHelpers.java` 的评估：

- LSPosed-derived GPL reflection/hooking core；
- 2136 行、87 KB；
- 大量 `public static` 重载；
- checked `Throwable`、vararg、array、generic 和 nullability ABI；
- allocation-sensitive reflection caches；
- weak identity-key lifecycle；
- 整体 Kotlin 迁移无已证明性能或维护收益；
- 按 `AGENTS.md` §18「不追求 100% Kotlin」，保留在 `docs/JAVA_BOUNDARY_ALLOWLIST.md` 的 `KEEP_JAVA_REFLECTION_ABI` 分类中。

剩余 Java 文件：

- `MainModule.java` -> `KEEP_JAVA_FRAMEWORK_ENTRY`
- `XposedHelpers.java` -> `KEEP_JAVA_REFLECTION_ABI`
- `MemberUtilsX.java` -> `KEEP_JAVA_VENDOR_OR_GENERATED`

无 `MIGRATE_TO_KOTLIN`、`KEEP_JAVA_TEMPORARY_BLOCKER` 或 `UNCLASSIFIED`。

## P9.3 Allowlist

State: `VERIFIED_STATIC`

已生成 `docs/JAVA_BOUNDARY_ALLOWLIST.md`，记录：

- `KEEP_JAVA_FRAMEWORK_ENTRY`：`MainModule.java`
- `KEEP_JAVA_VENDOR_OR_GENERATED`：`MemberUtilsX.java`
- `MIGRATE_TO_KOTLIN`：`XposedHelpers.java`
- `MIGRATED`：12 个 installer `.kt`
- 无 `KEEP_JAVA_TEMPORARY_BLOCKER` 或 `UNCLASSIFIED`

---

# P10 — ROM intelligence

State: `IN_PROGRESS`

## P10.1 HyperOS 1 / Android 14 samples

State: `BLOCKED_EXTERNAL`

- package/version/build；
- class/member/variant；
- process；
- target coverage；
- sample acquisition；
- evidence state。

证据：

- 当前环境无 `local-rom-samples/` 目录，也无实机 ROM dump；
- `tools/rom_inventory.py` 是离线扫描工具，必须有本地样本；
- 保持 `EXTERNAL_VALIDATION_REQUIRED`，待提供 ROM 样本后重跑 `rom_inventory.py`。

## P10.2 Contract/variant

State: `VERIFIED_STATIC`

- required 不降级；
- complete variant；
- fallback 有 diagnostics；
- candidate 不宣传 verified。

证据：

- `rom-contracts/hyperos1-a14-core.json` 中所有 hook entry 均带 `required` 布尔标记；`required=true` 的条目有完整 smali descriptor，`required=false` 的条目保留为 optional / candidate；
- `FeatureInstallResult` 定义了 `FAILED_TRANSIENT` 和 `FAILED_PERMANENT`，install 路径通过 bounded diagnostics 记录失败原因，不降级 required contract；
- `catalog_contract_probe.py` 通过 `244 specs / 245 FeatureIds / 244 matrix rows` 一致性检查，确保候选条目不混入 required-only 路径；
- `tools/check_document_contracts.py` 通过，文档不宣传未经 ROM 验证的 candidate 为 verified；
- 实机 ROM 变体验证仍依赖 P10.1 外部样本。

## P10.3 Generated consistency

State: `VERIFIED_STATIC`

Feature semantics、process matrix、target matrix、retirement audit、runtime routes 一致。

证据：

- `python tools/catalog_contract_probe.py --catalog ... --feature-id ... --matrix ...` 通过（`Catalog contract probe passed: 244 specs, 245 FeatureIds, 244 matrix rows`）
- `python tools/ci_contract_scan.py --expected-branch devin/a14-rom-intelligence-audit --default-branch main` 通过（`CI contract scan passed: 2 workflow(s)`）
- `python tools/check_document_contracts.py` 通过（`Document contract checks pass.`）
- `python tools/progress_snapshot.py --check` 通过（`Progress snapshot is fresh.`）
- Fast CI 30743003456 / job 91483652510 全部通过

---

# P11 — 测试、CI 与持续构建

State: `IN_PROGRESS`

## P11.1 Local

State: `COMPLETE`

稳定通过：

```text
python tools/verify.py full
python -m compileall tools/*.py
python -m unittest discover -s tools/tests -p "test_*.py"
.\gradlew.bat --no-daemon compileDebugKotlin compileDebugJavaWithJavac
.\gradlew.bat --no-daemon testDebugUnitTest
.\gradlew.bat --no-daemon lintDebug
.\gradlew.bat --no-daemon :app:assembleDebug
.\gradlew.bat --no-daemon :app:assembleDevelop
```

退出码：

```text
0
0
0
0
0
0
0
0
```

## P11.2 CI

State: `VERIFIED_CI`

证据：

- Fast CI run 30747399434 / job 91495213418 在 commit 782b3e50 全部通过；
- Fast CI job 91462556688 因 `platforms;android-37` 包名不存在而失败；
- 已把 Fast/Full workflow 改为先运行 `sdkmanager --list --channel=0` 探测 API 37 平台包（`platforms;android-37` 或 `platforms;android-CinnamonBun`）和 build-tools 37.x；
- 已移除 `app/build.gradle.kts` 中硬编码的 `buildToolsVersion`，让 AGP 选择已安装兼容版本；
- 安装并移植 A13_A14_BRUTAL_TEST_SUITE_V1：新增 `tools/catalog_contract_probe.py`、`ci_contract_scan.py`、`source_hazard_scan.py`、`apk_semantic_diff.py`、`hook_surface_probe.py`、`brutal_test_runner.py`、`brutal_test_config.json`；
- 重写 catalog_contract_probe 以扫描 `LazyFeatureSpec` 块并比对 `docs/rom-intelligence/A14_PROCESS_MATRIX.csv`；
- Fast CI 接入 `ci_contract_scan` 和 `catalog_contract_probe` 只读 gates；
- Full CI 接入两次 clean develop build、APK semantic diff 与 brutal mutation suite；
- Fast CI job 91467179591 在 HEAD 378d0eef 全部通过；
- 本地通过 `tools/verify.py Fast`、unit tests、brutal hermeticity 与 determinism。

唯一授权分支 push 后运行：

- JDK 17；
- Python；
- full verifier；
- debug APK；
- develop/R8；
- catalog/CI contract/progress 只读 gates；
- brutal suite（hermeticity/determinism/mutate）在 Full CI；
- audit/inventory freshness；
- logs/artifacts；
- 不发布 Release。

Agent 自动修复红色 CI。

## P11.3 Artifact

State: `VERIFIED_BUILD`

记录：

- debug APK：
  - variant: `debug`
  - version: `r14.16.1`
  - commit: `dc1981e81b2d3d8f7df82639a43e98b1028475f7`
  - SHA-256: `F51C6941165AD588E007D42A5CBF8B70DCA405F0AD69FE4A82CEB01C4722DE73`
  - size: `14593951` bytes
  - signing: unsigned debug keystore（`validateSigningDebug`）
  - 路径: `app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk`
- develop/R8 APK：
  - variant: `develop`
  - version: `r14.16.1`
  - commit: `dc1981e81b2d3d8f7df82639a43e98b1028475f7`
  - SHA-256: `C6883E4C8BDBBFA72C91FED6510EAA9E5C00F552C6F400B04D61DB65C0E670E4`
  - size: `3398166` bytes
  - signing: unsigned develop build（R8 shrink + obfuscation）
  - 路径: `app/build/outputs/apk/develop/CustoMIUIzer-A14-r14.16.1-develop-unsigned.apk`
- 验证命令：
  - `.\gradlew.bat --no-daemon :app:assembleDebug`
  - `.\gradlew.bat --no-daemon :app:assembleDevelop`
- 退出码：0 / 0

## P11.4 Brutal suite fail-closed hardening

State: `IN_PROGRESS`

### P11.4R1 Brutal runner truthful result semantics

TaskId: `A14-P11.4R1`
RiskTier: `R3`
State: `IN_PROGRESS`

原独立审查结论：`REJECT`（message 137）。R1A 正在修复配置自降、缺失 ledger、`a14_contract` 误用为 kill gate、self-test / validate / cleanup / hermeticity 的 fail-closed 问题。

文件：

- `tools/brutal_gate_protocol.py`：apply / kill 退出码协议与无 shell 执行。
- `tools/brutal_test_runner.py`：显式状态、不再把 self-detection 计为 kill、无 shell 注入。
- `tools/brutal_a14_contract_scan.py`：只做 apply check，修复 `_inject_hazard` AST 提取。
- `tools/brutal_test_config.json`：schema 2，11 个独立 gate mutation + 55 个 apply-check only mutation。
- `tools/tests/test_brutal_gate_protocol.py`、`tools/tests/test_brutal_a14_contract_scan.py`：回归测试。
- `.github/workflows/a14-fast-ci.yml`：fast subset 步骤。

不变量：

- `MUTATION_APPLIED`、`INDEPENDENT_GATE_KILLED`、`SELF_DETECTION_ONLY`、`SURVIVED`、`CANNOT_VERIFY`、`GATE_ERROR`、`CLEANUP_ERROR` 必须区分；
- `SELF_DETECTION_ONLY` 与 `CANNOT_VERIFY` 不得计入 semantic kill；
- `a14_contract` 只能用于 `apply_check`，不能作为 `kill_gate`；
- 总体 `coverage_target` 保持 74，不因为当前只有 11 个独立 gate 而永久降低目标；
- Fast CI 必须跑 `hermeticity` + `determinism` brutal subset。

实现：

- 新增 `tools/brutal_gate_protocol.py`；
- `brutal_test_runner.py` 改为 argv 数组、白名单、显式状态与 truth-summary；
- `brutal_a14_contract_scan.py` 退出码协议与 `_inject_hazard` 修复；
- `brutal_test_config.json` 升级 schema 2；
- 新增回归测试；
- `a14-fast-ci.yml` 增加 brutal fast subset。

验证命令：

```text
python -m unittest discover -s tools/tests -p "test_*.py"
python tools/brutal_test_runner.py --config tools/brutal_test_config.json hermeticity
python tools/brutal_test_runner.py --config tools/brutal_test_config.json determinism
python tools/brutal_test_runner.py --config tools/brutal_test_config.json mutate
python tools/progress_snapshot.py --check
```

退出码：0 / 0 / 0 / 0

CI：`https://github.com/tomthenpc/customiuizer-a14/actions/runs/30802727418`（A14 Fast CI, all green）
提交：`f5843d6a`（P11.4R1 主体）, `ad2f0296`（后续 hermeticity hash-diff 修复）
关键结果：Brutal suite fast subset、Full machine verifier、Safe debug and develop builds 全部通过。

---

### P11.4R1A Brutal fail-closed closeout

TaskId: `A14-P11.4R1A`
RiskTier: `R3`
State: `VERIFIED_BUILD`
ReviewerDecision: `PENDING`

文件：

- `tools/brutal_test_policy.py`：不可由 JSON 配置修改的 coverage/kill gate 策略。
- `tools/brutal_test_runner.py`：独立 `validate` 命令、fail-closed `validate_config`、cleanup `CleanupError`。
- `tools/brutal_test_config.json`：schema 2 + 完整 74 项 `coverage_ledger`（11 `ACTIVE_INDEPENDENT` + 55 `BLOCKED_NO_INDEPENDENT_GATE` + 8 `MUTATOR_STALE`）。
- `tools/brutal_a14_contract_scan.py`：`_inject_hazard` 自测与 synthetic apply 测试。
- `tools/brutal_gate_protocol.py`：`validate_id` 语义强化。
- `tools/audit_hook_ownership.py`：`--check` 模式，不覆盖 tracked 文件。
- `tools/tests/test_brutal_gate_protocol.py`：回归。
- `.github/workflows/a14-fast-ci.yml`：新增 `validate` 步骤。
- `docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md`：与源码同步刷新。

不变量：

- 策略与配置硬编码：`REQUIRED_COVERAGE_TARGET = 74`、`REQUIRED_INDEPENDENT_KILLS = 11`、`REQUIRED_INDEPENDENT_MUTATIONS` 固定、`FORBIDDEN_KILL_GATES` 包含 `a14_contract`；
- `validate` 命令必须 exit 0 才能继续；任何 fail-closed 违规均 exit 2；
- `a14_contract` 只能作为 `apply_check`，不得注册为 `kill_gate`；
- 完整 ledger 记录全部 74 coverage 目标，并解释缺失的 8 个 legacy mutation；
- `self-test`、`validate`、`cleanup`、`hermeticity`、`determinism`、`mutate` 全部 truthful；
- cleanup 失败不吞异常，作为 `CLEANUP_ERROR` 计数并导致退出码非零。

实现：

- 新增 `tools/brutal_test_policy.py`；
- `brutal_test_runner.py` 增加 `validate` 子命令与 policy 校验；
- `brutal_test_config.json` 增加 `coverage_ledger`；
- `brutal_a14_contract_scan.py` 修复 `_inject_hazard` baseline 为 0 的处理并增加 synthetic 验证；
- `brutal_gate_protocol.py` `validate_id` 拒绝 `.`、`..`、路径分隔符、控制字符与隐藏前缀；
- `audit_hook_ownership.py` 增加 `--check`，输出到临时目录对比；
- `a14-fast-ci.yml` 增加 `brutal_test_runner.py ... validate`。

验证命令：

```text
python tools/brutal_test_runner.py --config tools/brutal_test_config.json validate
python -m unittest discover -s tools/tests -p "test_*.py"
python tools/brutal_a14_contract_scan.py --self-test
python tools/audit_hook_ownership.py --check
python tools/brutal_test_runner.py --config tools/brutal_test_config.json hermeticity
python tools/brutal_test_runner.py --config tools/brutal_test_config.json determinism
python tools/progress_snapshot.py --check
```

退出码：0 / 0 / 0 / 0 / 0 / 0 / 0

CI：待本次提交后运行。

---

原行为：

- `tools/brutal_test_config.json` 无 schema 与 `required_mutations`，可因空/少 mutation 误报通过；
- `tools/brutal_test_runner.py` 未按 baseline 检测未跟踪文件，determinism 只检查 declared outputs；
- `a14-full-ci.yml` schedule 条件遗漏 `github.event_name == 'schedule'`，导致定时触发被静默跳过；
- `a14-fast-ci.yml` 缺少 `pull_request` 触发；
- `scripts/verify.ps1` Final 模式未调用 brutal suite；
- 当前 mutation 未覆盖 JVM ABI、reflection、R8、installer/process/ClassLoader、gesture 状态机与 fatal/OOM 边界。

不变量：

- brutal suite 必须是 fail-closed：配置缺失、mutation 全空/存活、gate 未命中、worktree 残留、hermeticity 或 determinism 失败都返回非 0；
- Full CI schedule 触发必须生效；Fast CI 必须在 push 与 pull_request 上运行；
- Final 验证必须接入 hermeticity + determinism + mutation；
- 所有 required mutation 必须被现有或新增 gate 杀死。

实现：

- 在 `tools/brutal_test_runner.py` 增加 schema 校验、`required_mutations` 校验、`minimum_mutations`、未跟踪文件 baseline 对比、worktree 残留检查、输出文件与跟踪文件完整性校验；
- 修正 `a14-full-ci.yml` 的 `if` 条件，增加 schedule 分支；
- 在 `a14-fast-ci.yml` 增加 `pull_request` 触发与 brutal fast-subset 步骤；
- 在 `scripts/verify.ps1` Final 模式接入 `brutal_test_runner.py hermeticity/determinism/mutate`；
- 扩展 `tools/brutal_test_config.json` 与 `tools/brutal_a14_mutators.py`，新增 JVM ABI、reflection、R8、installer/process/ClassLoader、gesture 与 fatal/OOM mutation；
- 新增 `tools/reflection_contract_scan.py` 与 `tools/r8_contract_scan.py` 作为 reflection/R8 静态 gate；
- 更新单元/契约测试以覆盖新增 mutation。

验证命令：

```text
python tools/brutal_test_runner.py --config tools/brutal_test_config.json hermeticity
python tools/brutal_test_runner.py --config tools/brutal_test_config.json determinism
python tools/brutal_test_runner.py --config tools/brutal_test_config.json mutate
```

退出码：待全部通过。

CI：待 Fast/Full CI 验证。

---

# P12 — 文档、dead code 与 release candidate

State: `IN_PROGRESS`

## P12.1 Gesture lifecycle owner inventory

State: `VERIFIED_STATIC`

EvidenceCommit: cd152365a1b258a7b36e978d1050db71f427fa83

文件：

- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`：更新 `EvidenceCommit`、`EvidenceState`、来源行号，并添加 `tools/tests/test_gesture_lifecycle_inventory.py` 为独立证据。
- `tools/tests/test_gesture_lifecycle_inventory.py`：解析文档元数据，验证 `EvidenceCommit` 是 `HEAD` 的祖先，并校验每条引用行范围包含声明的符号。

证据：

```text
python -m unittest discover -s tools/tests -p "test_*.py"          # 144 passed
python tools/check-invariants.py                                     # no violations
python tools/check_document_contracts.py                             # pass
python tools/progress_snapshot.py --check                            # fresh
python tools/verify.py fast --tests ControlCenterGestureRuntimeHolderTest ControlCenterPluginRuntimeTest GestureMachineTest GestureStateMachineTest PhysicalGestureArbiterTest  # pass
python tools/verify.py fast                                          # pass
python tools/verify.py full                                          # pass
```

补充产物：

- `docs/progress/A14_PROGRESS_CURRENT.json` 与 `.md` 已按 P12.1 进展重新生成，`--check` 与 `check_automation_state.py` 通过。

## P12.2 CURRENT architecture

State: `VERIFIED_STATIC`

EvidenceCommit: d189ad12fc50522ada4772fcb6e5afb510469e01

文件：

- `docs/A14_CURRENT_ARCHITECTURE.md`：描述当前源码架构，使用 EvidenceCommit 校验。
- `tools/tests/test_current_architecture.py`：机械验证文档元数据、路径、symbol 和限制。

证据：

```text
python -m unittest tools.tests.test_current_architecture
python -m unittest discover -s tools/tests -p "test_*.py"
python tools/check_document_contracts.py
python tools/check-invariants.py
```

## P12.3 Gesture event contract

State: `VERIFIED_STATIC`

EvidenceCommit: d189ad12fc50522ada4772fcb6e5afb510469e01

文件：

- `docs/A14_GESTURE_EVENT_CONTRACT.md`：描述当前 gesture 事件契约，包含 10 个必需章节、source evidence 和已知限制。
- `tools/tests/test_gesture_event_contract.py`：机械验证文档元数据、路径、symbol、生命周期、副作用约束和 mutation。

证据：

```text
python -m unittest tools.tests.test_gesture_event_contract
python -m unittest discover -s tools/tests -p "test_*.py"
python tools/check_document_contracts.py
python tools/check-invariants.py
```

## P12.4 APK delta

State: `VERIFIED_BUILD`

EvidenceCommit: 1856c4e229213dfae47ff575aee446ce6a7b5f22

文件：

- `docs/performance/A14_APK_SIZE_BASELINE.json`（P0 Debug baseline）
- `docs/performance/A14_APK_SIZE_BASELINE_DEVELOP.json`（P0 Develop baseline）
- `docs/performance/A14_APK_SIZE_CURRENT.json`（当前 Debug）
- `docs/performance/A14_APK_SIZE_CURRENT_DEVELOP.json`（当前 Develop）
- `docs/performance/A14_APK_SIZE_DELTA.json`（机器可校验的 delta 报告）
- `docs/performance/A14_APK_SIZE_DELTA.md`（人工可读报告）
- `tools/apk_size_report.py`（已有 APK 分析工具）
- `tools/apk_size_delta.py`（新增 delta 生成器）
- `tools/tests/test_apk_size_delta.py`（机械验证与 mutation）

证据：

```text
.\gradlew.bat --no-daemon clean :app:assembleDebug :app:assembleDevelop
python tools/apk_size_report.py app\build\outputs\apk\debug\CustoMIUIzer-A14-r14.16.1-debug.apk --out docs/performance/A14_APK_SIZE_CURRENT.json
python tools/apk_size_report.py app\build\outputs\apk\develop\CustoMIUIzer-A14-r14.16.1-develop-unsigned.apk --out docs/performance/A14_APK_SIZE_CURRENT_DEVELOP.json
python tools/apk_size_delta.py --baseline-commit 55fc2a21d0e96f9ef643f53fcc9b74374bd959db --current-commit 1856c4e229213dfae47ff575aee446ce6a7b5f22
python -m unittest tools.tests.test_apk_size_delta
python -m unittest discover -s tools/tests -p "test_*.py"
python tools/check_document_contracts.py
python tools/check-invariants.py
```

---

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

State: `COMPLETE`

EvidenceCommit: c4ab7e30d26f1357913a5705b9843c73cd9108d3

在 P2-P12 各阶段后重复。

本轮 P13 第一次发现（baseline HEAD `c46ffb1a`）：

- 工具：`python tools/source_hazard_scan.py --write-baseline`
- 输出：`docs/audit/SOURCE_HAZARD_BASELINE.json`
- 结果：重写后按 fingerprint 去重，`415` 条已评审 hazard（原 `1079` 含重复指纹），本轮 `0` 条新增
- 主要类别：
  - `CATCH_THROWABLE_NO_FATAL`：Xposed/反射/系统服务调用处吞非 fatal 异常并记录 diagnostics，需保持 fatal rethrow 语义；
  - `EMPTY_CATCH`：空 catch 块，多为不可恢复异常或预期不存在场景；
  - `PRINT_STACK_TRACE`：`.printStackTrace()` 调用点，应替换为 `AppHelper.log` 或 `XposedHelpers.log`；
  - `STATIC_STRONG_ANDROID_OWNER`：`Context`、`View`、`Drawable` 等对象静态持有风险；
  - `NATIVE_LOAD`：`MainModule.java` 的 `System.loadLibrary`，属于 libxposed 入口。

本轮 P13.2 第二次发现（baseline HEAD `f0cb5173`）：

- 工具：完整 discovery sweep 审计套件
- 输出：`docs/audit/A14_DISCOVERY_SWEEP_2.md`
- 结果：`0` 条新增，连续两轮无 P0/P1，无阻塞，结论 `NO_NEW_P0_P1`

文件：

- `docs/audit/SOURCE_HAZARD_BASELINE.json`（已建立 baseline）
- `docs/audit/A14_DISCOVERY_SWEEP_2.md`
- `docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md`
- `docs/process/tasks/A14-P13.2-SECOND-DISCOVERY-SWEEP.md`
- `TASK_STATE.md`

证据：

```text
python tools/source_hazard_scan.py
python tools/audit_hook_ownership.py
python tools/audit-feature-semantics.py --validate
python tools/extract_process_matrix.py
python -m unittest discover -s tools/tests -p "test_*.py"
python tools/check_document_contracts.py
python tools/check-invariants.py
python tools/check_automation_state.py
python tools/progress_snapshot.py --check
python tools/verify.py full
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Full
git diff --check
git ls-files *.apk *.aab
```

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
- 进入 `LTS`（Long-Term Support）生命周期；
- 继续 evidence-driven 维护。

---

## 4. 发现的问题队列

P0 完成后重建，不得删除未解决条目。

| ID | Priority | Area | State | Evidence | Acceptance |
|---|---|---|---|---|---|
| BASELINE-001 | P0 | Git | COMPLETE | 本地 Agent 已锁定分支、HEAD、origin 和 upstream | P0.1 |
| VERIFY-001 | P0 | Build | COMPLETE | 控制层 Fast/Full/Audit 验证器已运行并通过（含 targeted tests 和 Python 工具） | P0.3 |
| ARCH-001 | P1 | Registry | COMPLETE | 已盘点 Feature/Registry/Installer/state 并产出工具和文档 | P2 完成 |
| API-001 | P1 | API 101/102 | COMPLETE | API 102-only 类型/调用已分类并文档化，API 101 路径保持完整 | P4 完成 |
| GESTURE-001 | P1 | Gesture | COMPLETE | 唯一生产状态机、事件模型、side-effect gate、arbiter bound/cleanup、pointerCount contract 均已落地并通过测试 | P5.5 完成 |
| LIFECYCLE-001 | P1 | SystemUI | BLOCKED_EXTERNAL | `PhoneStatusBarView` 与 `ControlCenterWindowViewImpl` 的 `onDetachedFromWindow` 已清理 per-owner；`ControlCenterGestureRuntimeHolder` 已提供 `unbind()`；plugin/ClassLoader 销毁入口未发现，标记 DEVICE_LIFECYCLE_ENTRY_BLOCKED | P6.5 完成后重审 |
| ALG-001 | P1 | MainModule | COMPLETE | `SystemUiBootstrapCoordinator` 已提取，`MainModule` 只负责路由调用 | P3.2 完成 |
| ALG-002 | P1 | Fatal | COMPLETE | `FatalErrors` helper 已创建，`MainModule` 所有 catch(Throwable) 已调用 `rethrowIfFatal` | P3.2 完成 |
| ALG-003 | P1 | Gesture | COMPLETE | `GestureEvent` 新增 `activePointerCount`，默认归一化 `ACTION_UP`/`ACTION_POINTER_UP`；`GestureStateMachine` 使用 active 计数；测试已更新 | P5.5 完成 |
| ALG-004 | P1 | Gesture | COMPLETE | `GestureSideEffectGate.filter` 改用 `commands.any(::isBusinessEffect)`，避免热路径分配中间列表 | P5.5 完成 |
| ALG-005 | P1 | Gesture | COMPLETE | `PhysicalGestureArbiter` 已加 `MAX_HELD_TOKENS` 硬上限、`STALE_TOKEN_AGE_MS` 清理、`reapStaleTokens` 兜底；`GestureMachine` 在 UP/CANCEL/Reset/detach 时释放 token；测试覆盖 | P5.5 完成 |
| ALG-006 | P1 | Lifecycle | BLOCKED_EXTERNAL | `ControlCenterGestureRuntimeHolder.unbind()` 已实现并测试；per-View `onDetachedFromWindow` 调用 `controlCenterMachine.clear(ownerId)`；新 ClassLoader 触发 `bind` 时清理旧 runtime；plugin/ClassLoader 销毁入口未发现，不猜测 Hook | P6.5 完成 |
| ALG-007 | P1 | HotPath | COMPLETE | `SystemUIControlCenterHooks` 的 `onInterceptTouchEvent` 不再调用 `statusBarMachine.observe()`，避免未使用返回值的热路径计算 | P7.5 完成 |
| REPAIR-001 | P1 | Fatal | COMPLETE | `SystemUIControlCenterHooks.extractPluginLoader` 所有 `catch (Throwable)` 先调用 `FatalErrors.rethrowIfFatal`，fatal 错误继续抛出后再进入反射 fallback；新增 `SystemUIControlCenterHooksExtractPluginLoaderTest` | 紧急修复 V2 完成 |
| REPAIR-002 | P1 | Lifecycle | COMPLETE | 合并 `PluginFactory.createPlugin` 所有权到 `ControlCenterPluginRuntime`；唯一运行时管理 `activeLoader`、`ControlCenterGestureRuntimeHolder`、`PhysicalGestureArbiter` 并提供对称 `clear()`；新增 `ControlCenterPluginRuntimeTest` | 紧急修复 V2 完成 |
| REPAIR-003 | P2 | Docs | COMPLETE | `TASK_STATE.md` 所有 `VERIFIED` 已按证据拆分为 `VERIFIED_BUILD`/`VERIFIED_CI`/`VERIFIED_STATIC`；更新 P6.5、P8.3、P13 文档结论 | 紧急修复 V2 完成 |
| REPAIR-004 | P2 | Tool | COMPLETE | 重写 `tools/source_hazard_scan.py`：新增 `--scope`（production/test/tools/all）、指纹去重、平衡括号 catch 块解析（跳过字符串/注释/嵌套花括号），更新 `SOURCE_HAZARD_BASELINE.json`（415 条去重后 hazard） | 紧急修复 V2 完成 |
| REPAIR-005 | P1 | Test | COMPLETE | 新增参数化 `InstallerJvmAbiTest`：验证 12 个 installer 类的 `@JvmStatic install/installPostAttach(PackageReadyParam, PrefMap, ...)` 方法签名和 public static 修饰符 | 紧急修复 V2 完成 |
| DOC-001 | P2 | Docs | IN_PROGRESS | P12.1 lifecycle owner inventory 已验证并提交；剩余 CURRENT architecture、gesture event contract、APK delta | 继续 P12.2+ |
| A14-UX1 | P2 | UX | VERIFIED_BUILD | 锁屏充电信息字号调节实现、engineering commit d4803d9b、GitHub A14 Fast CI run 30802727418 PASS；实机重叠验证 PENDING | 实机验证 + R2 review |
| A14-UX2A | P2 | UX | VERIFIED_STATIC | engineering commit f7614738、GitHub A14 Fast CI run 30823639530 / job 91719557427 PASS；Status Bar / WindowInsets 一致性诊断工具、审计文档和 fixture 测试通过；DeviceEvidence PENDING；UX2B BLOCKED_BY_DIAGNOSTIC_EVIDENCE | 设备证据 + DEX 签名验证 |
| CI-001 | P2 | CI | TODO | 需建立 exact-branch Fast workflow 和 scheduled/manual Full workflow | P11 完成 |
| DEVICE-001 | P1 | Device | BLOCKED_EXTERNAL | 无本轮真实证据 | P15 完成 |

---

## 5. Checkpoint

最近真实 qualifying checkpoints（按时间顺序；state-only 和 overlay install 不计）：

```text
2b88afbd docs: P0.4 baseline inventory (process matrix, feature semantics, APK size)
d089148e fix(tests): align method-hook fatal boundary test with three fatal rethrow contract
d4781e02 feat(tools): add Hook ownership auditor and complete P1.2
16867eff docs: mark P2 Feature Registry complete
6534a948 docs: complete P4 API 101/102 boundary classification
95b45a8e Fix GestureMachine arbiter, fatal boundaries, and runtime holder invariants
aa839c8b docs: mark P5 Gesture/Control Center complete
00f159b9 perf: update debug and develop APK size baselines after standard sweep build
```

当前 ResumeTask：A14-UX1 — lock screen charging info font size（实机验证前 R2 review）；随后 P3 生命周期清理。

已加入队列：A14-UX1（P2 / VERIFIED_BUILD / 实机验证 PENDING）。

---

## 6. 最终报告

尚未生成。
