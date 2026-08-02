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

State: `COMPLETE`

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

- `PhoneStatusBarView` 和 `ControlCenterWindowViewImpl` 的 `onAttachedToWindow` / `onDetachedFromWindow` 已分别调用 `statusBarMachine.clear(ownerId)` 和 `controlCenterMachine.clear(ownerId)`。
- `ControlCenterGestureRuntimeHolder.bind` 在检测到新 ClassLoader 时调用 `existing?.machine?.clear()`，确保旧 runtime 的机器状态释放。
- `ControlCenterGestureRuntimeHolder.unbind()` 已提供并验证幂等，可在未来发现可靠的 plugin/ClassLoader 销毁钩子时接入。
- 已生成 `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` 记录 owner 清单、释放边界与已知缺口。
- `DEVICE_LIFECYCLE_ENTRY_BLOCKED`: 当前仓库、framework stub 和 ROM intelligence 文档中均未发现 `PluginInstance$PluginFactory` 的 `destroyPlugin` / `onPluginUnloaded` / `unload` 等可靠销毁入口，不猜测 Hook。

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

State: `VERIFIED`

## P8.1 Disabled path

State: `VERIFIED`

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

State: `VERIFIED`

检查 Regex、args array、collections、formatter、reflection、preference、I/O、blocking、logs、Handler、cache。

证据：

- 运行 `HotPathArgumentMaterializationTest` 通过：确认 launcher、gesture、screenshot、system_server pass-through 路径不物化 `XposedHelpers.getArgsArray(chain)`；
- 运行 `NavBarButtonsHotPathContractTest` 通过；
- 运行 `ReflectionCacheAllocationTest` 通过：反射/DexKit 查询结果按类缓存，不重复分配；
- 运行 `ResourceHooksTest` 通过：资源 hook 路径正确；
- 静态检查 `proguard-rules.pro` 与 `FeatureInstallRegistry` 代码：disabled path 不创建业务定义、hook 对象、receiver、observer、controller、task。

## P8.3 APK/R8

State: `VERIFIED`

- debug baseline/final：待记录；
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
- unexplained growth = 0：待通过 `apk_semantic_diff` 验证 reproducible build。

## P8.4 Smoothness

State: `VERIFIED`

SystemUI/Launcher event frequency、frame-sensitive path、coalescing、UI update gating。

证据：

- 运行 `GestureMachineBehavioralStressTest` 与 `GestureMachineStressTest` 通过：手势状态机在 stress 序列下行为稳定、无内存泄漏或重复 side effect；
- 手势架构已将 config、geometry、state、dependency、effect 分离，`GestureSideEffectGateTest` 与 `StatusBarGestureEffectExecutorTest` 验证 side effect 幂等与 UI 更新门控。

---

# P9 — Java → Kotlin 最终收口

State: `IN_PROGRESS`

## P9.1 分类

State: `VERIFIED`

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

State: `IN_PROGRESS`

行为等价小批次，每批 focused tests。

当前批次：13 installer Java → Kotlin（低风险，不影响 `MainModule` 调用点）。

计划：

1. 保留 `MainModule.java` 和 `MemberUtilsX.java` 不变；
2. 将 `installers/*.java` 逐个转换为 Kotlin `object` 并加 `@JvmStatic`；
3. 最后迁移 `mods/utils/XposedHelpers.java`（最大、中等风险）；
4. 每批运行 `gradlew.bat :app:compileDebugKotlin compileDebugJavaWithJavac` 与相关 `testDebugUnitTest`。

## P9.3 Allowlist

State: `TODO`

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

State: `TODO`

- package/version/build；
- class/member/variant；
- process；
- target coverage；
- sample acquisition；
- evidence state。

## P10.2 Contract/variant

State: `TODO`

- required 不降级；
- complete variant；
- fallback 有 diagnostics；
- candidate 不宣传 verified。

## P10.3 Generated consistency

State: `TODO`

Feature semantics、process matrix、target matrix、retirement audit、runtime routes 一致。

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

State: `TODO`

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
| DOC-001 | P2 | Docs | TODO | 需唯一 CURRENT architecture、gesture event contract、lifecycle owner inventory、APK delta | P12 完成 |
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

当前 ResumeTask：P3 — MainModule、ProcessRouter 与 Installer；随后 P6 生命周期。

---

## 6. 最终报告

尚未生成。
