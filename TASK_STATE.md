# A14 最终自治任务状态

## 0. 控制状态

```text
OverallState: READY_FOR_BASELINE
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

State: `TODO`

记录：

```text
git rev-parse --show-toplevel
git remote get-url origin
git symbolic-ref --short HEAD
git rev-parse HEAD
git status --short
git log -10 --oneline
git rev-parse --abbrev-ref --symbolic-full-name @{u}
```

验收：

- origin 规范化一致；
- 分支精确一致；
- upstream 精确一致；
- 非 detached HEAD；
- 无 unfinished operation；
- 本地修改全部分类且不丢失。

## P0.2 工具链

State: `TODO`

记录：

- Windows/PowerShell；
- Git；
- JDK 17；
- Python；
- Gradle；
- Android SDK/build tools；
- 磁盘与内存；
- 网络依赖；
- GitHub/CI；
- 外部签名状态但不得读取密码。

## P0.3 全量基线验证

State: `TODO`

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Full
```

失败分类：

```text
PRE_EXISTING
NEW_CONTROL_PLANE
ENVIRONMENT
NETWORK
PRODUCT_DECISION
UNKNOWN
```

## P0.4 全量 inventory

State: `TODO`

生成：

- Feature IDs/specs/definitions；
- Registry/Installer/state；
- Hook entries；
- process/phase；
- API 101/102；
- gesture production path；
- Receiver/Observer/View/Bitmap owner；
- Java/Kotlin；
- tests/tools/docs；
- ROM/target/process matrix；
- APK/R8 size；
- device evidence。

完成后：

```text
OverallState: BASELINE_LOCKED
```

---

# P1 — 单一事实源

## P1.1 Feature identity

State: `TODO`

验证：

- stable ID 唯一；
- name/canonical identity 唯一；
- no duplicate registration；
- no orphan spec；
- no unknown install route；
- semantics inventory 与代码一致。

## P1.2 Hook ownership

State: `TODO`

全部生产 Hook 分类：

```text
REGISTRY_FEATURE
INSTALLER_INFRASTRUCTURE
RESOURCE_INFRASTRUCTURE
API_BRIDGE
LEGACY_EXCEPTION
DEAD_CANDIDATE
UNKNOWN
```

最终：

```text
UNKNOWN = 0
```

## P1.3 Process/phase inventory

State: `TODO`

MainModule、ProcessRouter、Installer、FeatureTarget、InstallPhase、scope list 和 generated matrix 一致。

---

# P2 — Feature Registry 最终收口

State: `TODO`

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

- [ ] identity
- [ ] enabled/default
- [ ] target
- [ ] phase
- [ ] lazy creation
- [ ] exact hook behavior
- [ ] disabled test
- [ ] mismatch test
- [ ] success test
- [ ] idempotency
- [ ] transient/permanent
- [ ] fatal
- [ ] inventory
- [ ] no second route

完成：

```text
duplicate business install route = 0
eager disabled definition = 0
unknown production Feature = 0
```

---

# P3 — MainModule、ProcessRouter 与 Installer

State: `TODO`

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

---

# P4 — API 101/102 边界

State: `TODO`

## P4.1 API 101 完整路径

- 核心功能只依赖 API 101 能力；
- API 102 类型不进入冷启动必经类加载；
- staticScope=false。

## P4.2 API 102 bridge

每项分类：

```text
WIRED_WITH_SAFE_FALLBACK
INTENTIONALLY_UNWIRED_DOCUMENTED
REMOVE_CONFIRMED_DEAD
```

处理 stable hook ID、replaceHook、hot reload/capability bridge。

完成：

```text
READY_NOT_WIRED without decision = 0
API102 leakage into API101 path = 0
```

---

# P5 — Gesture/Control Center

State: `TODO`

## P5.1 生产状态机

- 确认唯一生产 Gesture machine；
- 其他实现分类；
- 不允许竞争状态机。

## P5.2 事件模型

覆盖：

- DOWN/MOVE/UP/CANCEL；
- pointer/multi-touch；
- velocity/distance；
- orientation/RTL；
- statusbar/control center competition；
- shade expansion；
- config publish；
- stale runtime holder；
- duplicate event；
- reentry。

## P5.3 Side effect

- 每物理手势最多一次；
- gate 幂等；
- action launcher failure 隔离；
- 无主线程阻塞/反射。

## P5.4 Stress

- deterministic long sequence；
- randomized seeded sequence；
- repeated cancel；
- multi-touch；
- config changes；
- integration tests。

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
