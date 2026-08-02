# A14 Smart Continuous Operation

## 1. Control identity

```text
Repository: tomthenpc/customiuizer-a14
AuthorizedBranch: devin/a14-rom-intelligence-audit
BranchMode: EXACT_LOCK
PlatformTarget: HyperOS 1 / Android 14
OperationMode: SMART_CONTINUOUS_OPERATION
PlanningMode: EVIDENCE_DRIVEN_DYNAMIC
TestSelectionMode: RISK_ADAPTIVE
ToolCreationMode: AUTO_WHEN_REPEATABLE
CleanupMode: EVIDENCE_GATED
HumanReviewRequired: false
RoutineConfirmationRequired: false
AutoResume: true
ExternalEvidencePolicy: NON_BLOCKING
ProgressReporting: TASK_STATE_AND_GIT_ONLY
```

本文件只控制 Agent 的执行方式。

不得因为安装本文件而重置、替换、总结或重新生成：

```text
GOAL.md
TASK_STATE.md
scripts/verify.ps1
当前任务队列
当前阶段状态
现有代码
现有 commit
```

`TASK_STATE.md` 始终是动态事实台账。Agent 只能基于当前内容增量更新。

## 2. Core behavior

Agent 持续执行：

```text
observe
→ classify risk
→ collect evidence
→ plan smallest safe change
→ implement
→ choose tests dynamically
→ verify
→ review diff
→ record evidence
→ checkpoint commit
→ push
→ inspect CI
→ self-heal
→ discover next work
→ continue
```

不得因为任务、阶段、验证、CI 或完成里程碑而主动停止。

## 3. Dynamic planning

每次选择任务前，先根据当前证据确定：

- 用户可见影响；
- 目标进程；
- Hook 时序；
- ClassLoader；
- ROM/API 边界；
- 生命周期；
- 并发；
- 性能热度；
- 文件类型；
- 失败半径；
- 可回滚性；
- 可测试性。

任务优先级：

```text
P0  system/process crash, data loss, branch pollution, fatal swallowed
P1  feature failure, ROM mismatch, lifecycle leak, duplicate side effect, major regression
P2  architecture debt, test gap, stale docs, maintainability
P3  low-risk cleanup, generated evidence, minor efficiency
```

当新证据表明原计划错误时，允许重排任务和改变实现路线，但不得降低 `GOAL.md`、测试、分支、安全或完成标准。

## 4. Risk-adaptive test selection

Agent 必须根据改动类型自动选择测试，不允许固定只跑同一组命令。

### 4.1 Documentation-only

至少执行：

- `git diff --check`；
- 文档引用、路径、生成表格或链接一致性检查；
- 与代码/生成 inventory 的一致性检查；
- Fast verification，除非仓库验证器明确允许跳过 Gradle。

### 4.2 Python tools or audit scripts

至少执行：

- `python -m compileall tools`；
- 对应 Python unit tests；
- 输入异常、空输入、编码、路径和 Windows 兼容测试；
- 生成结果的确定性检查；
- Fast verification。

### 4.3 Kotlin/Java pure logic

至少执行：

- 对应 focused unit tests；
- Kotlin/Java compile；
- Fast verification；
- public/JVM signature 检查（适用时）。

### 4.4 Feature, Registry, process, phase, contract

至少执行：

- disabled；
- wrong process；
- wrong phase；
- incompatible；
- success；
- idempotency；
- transient/permanent failure；
- fatal propagation；
- targeted tests；
- Full verification。

### 4.5 SystemUI, Launcher, view, receiver, observer, callback

至少执行：

- lifecycle/owner replacement；
- duplicate registration/attach；
- stale owner；
- callback guard；
- recreation/configuration；
- focused stress test；
- Full verification；
- 标记需要的实机证据，但不阻塞其他机器任务。

### 4.6 Gesture, concurrency, cache, state machine

至少执行：

- deterministic sequence；
- boundary sequence；
- cancel/re-entry；
- concurrency/idempotency；
- seeded randomized/stress tests；
- cache bound；
- fatal/half-state cleanup；
- Full verification。

### 4.7 Gradle, dependency, build, packaging, R8

至少执行：

- configuration/static rules；
- compile；
- unit tests；
- lint；
- `:app:assembleDebug and :app:assembleDevelop`；
- artifact metadata/hash/size；
- dependency and reflection/R8 impact audit；
- Full verification。

测试失败时先找首个根因，不得只重复运行直到偶然通过。

## 5. Automatic tool creation

当工作可以机械化时，Agent 应主动创建小型、可测试、可复用工具。

应写 Python/PowerShell 的典型条件：

- 同一人工检查重复两次以上；
- 需要解析大量源文件、日志、XML、JSON、CSV 或 Gradle 输出；
- 需要生成 Feature/Hook/process/contract/inventory；
- 需要检测重复、orphan、unreachable、stale 或 mismatch；
- 需要比较 APK size、ROM sample、target matrix 或 Git 差异；
- 需要执行确定性的批量检查；
- 人工 grep 容易漏项；
- 同类缺陷曾出现两次；
- 文档中的数量或状态经常过期。

工具规则：

- 只解决一个明确问题；
- 默认只读；
- 修改模式必须显式参数；
- 支持 dry-run；
- 输出稳定、可比较；
- Windows 路径和 UTF-8 兼容；
- 有 unit tests；
- 非零退出码表示失败；
- 不访问秘密；
- 不下载并执行未知代码；
- 不调用 ADB 或破坏性 Git；
- 纳入现有 verifier 或 CI（当长期有价值时）。

若临时一次性命令已足够，不为了“智能化”制造无价值框架。

## 6. Learning from repeated failures

出现以下情况时，必须把经验固化：

- 同类 bug 第二次出现：增加回归测试或静态门禁；
- 同一人工审计重复三次：创建工具；
- 同一文档数字第二次过期：改为生成；
- 同一 CI 根因第二次出现：增加 pre-submit gate；
- 同一生命周期问题第二次出现：增加 owner/lifecycle invariant；
- 同一 ROM target 漂移第二次出现：增加 inventory diff；
- flaky test 出现：修复根因，不允许仅重试掩盖。

将固化内容记录到现有 `TASK_STATE.md`，但不重置其结构。

## 7. Periodic audit cadence

“定期”采用事件周期，不依赖墙上时间。

### Light sweep

每个 checkpoint 后执行：

- diff；
- warning；
- targeted test result；
- secret/artifact；
- TODO/FIXME in changed files；
- CI state。

### Standard sweep

每 3 个安全 checkpoint，或完成一个 P 阶段后执行：

- Full verification；
- Feature/Hook/process/contract consistency；
- lifecycle and callback scan；
- test gap scan；
- stale docs scan；
- build artifact and size delta；
- untracked/temporary file scan。

### Deep sweep

每 10 个 checkpoint、完成重要架构阶段、CI 连续失败、或进入维护模式时执行：

- 全量 production Hook ownership；
- Registry/legacy/duplicate route；
- API/ROM/ClassLoader boundary；
- Java/Kotlin boundary；
- concurrency/re-entry/cache；
- SystemUI/Launcher lifecycle；
- performance hot path；
- R8/reflection/resource reachability；
- dead-code evidence；
- full docs/inventory regeneration；
- two independent discovery passes。

计数写入 `SMART_OPERATION_STATE.md`，不要占用或重写 `TASK_STATE.md`。

## 8. SMART_OPERATION_STATE.md

允许在仓库根目录维护一个独立执行状态文件：

```text
Mode: SMART_CONTINUOUS_OPERATION
CheckpointCount: 0
CheckpointsSinceStandardSweep: 0
CheckpointsSinceDeepSweep: 0
LastLightSweepCommit: pending
LastStandardSweepCommit: pending
LastDeepSweepCommit: pending
LastFullVerificationCommit: pending
LastCIState: pending
LastCleanupCommit: pending
LastToolCreated: none
LastFailureClass: none
ResumeTask: derive from TASK_STATE.md
```

该文件只记录执行节奏，不能代替 `TASK_STATE.md` 的产品任务和证据。

## 9. Evidence-gated cleanup

Agent 应定期清理无关文件，但不得进行盲目删除。

### Tier A — 可自动删除

满足明确规则时可以自动删除：

- 项目内临时文件；
- 已知编辑器 swap/backup；
- 未跟踪的测试临时输出；
- 已重新生成且不应提交的缓存；
- 明确由工具生成、可完全复现、且 `.gitignore` 应覆盖的中间文件；
- 空日志、旧本地报告和失败后遗留的临时目录。

删除前：

- 确认不在 Git 跟踪中，或有明确生成来源；
- 确认不被脚本、CI、Gradle、资源或文档引用；
- 先 dry-run 列表；
- 删除后运行相关验证；
- 必要时更新 `.gitignore`。

### Tier B — 需要双重机械证据

跟踪中的旧生成文件、重复文档、废弃工具或无引用资源，仅在同时满足以下条件时删除：

1. 全仓搜索无调用/引用；
2. 生成器、构建图、资源图或静态工具证明不可达；
3. Git 历史确认不是动态入口、反射字符串或 ROM 约定；
4. 对应测试和 Full verification 通过；
5. diff 可独立解释并形成单独 commit。

### Tier C — 禁止自动删除

不得仅凭“看起来没用”自动删除：

- 用户功能；
- preference/resource；
- Hook；
- 反射目标字符串；
- ROM candidate/optional target；
- migration compatibility；
- signing/build/release 配置；
- device checklist evidence；
- 测试失败所覆盖的代码；
- 可能由 XML、`R.*`、`getIdentifier`、DexKit、ClassLoader 或外部 ROM 动态访问的文件。

Tier C 只允许登记为 `DEAD_CANDIDATE`，继续收集证据。不得为了清洁度删除。

禁止使用：

```text
git clean
git reset --hard
git restore .
git checkout -- .
通配符递归删除源码或资源
```

## 10. Resource-aware execution

两个项目可能同时运行。Agent 应减少资源争用：

- 重型 Gradle/R8/全量测试前检查可用内存和磁盘；
- 内存紧张时先做静态分析、文档、工具或 focused tests；
- 不无意义并行多个 Gradle daemon；
- 默认 `--no-daemon` 时遵守仓库验证脚本；
- 避免同时启动多个高内存任务；
- 构建失败若源于 OOM/磁盘，记录环境根因，不修改代码伪装修复；
- 不删除全局 Gradle/Android SDK cache 作为常规清理；
- 只清理项目内可重建临时产物。

## 11. Self-healing CI

每次 push 后：

1. 获取最新 CI 状态；
2. 读取首个失败 job 和首个失败 step；
3. 获取完整日志；
4. 区分 code、test、environment、network、cache、permission；
5. 修复真实根因；
6. 增加回归测试或 pre-submit gate；
7. push；
8. 重新检查直到通过。

网络或平台瞬时错误可以有限重试，但不得把真实失败归类为瞬时错误。

## 12. Checkpoint and push

创建 checkpoint 的条件：

- 完成一个独立任务；
- 完成一个 defect + regression test；
- 完成一个迁移批次；
- 完成一个工具/门禁/审计；
- 大任务形成稳定中间状态；
- Standard/Deep sweep 产生了可验证改动。

提交前必须：

- targeted verification 通过；
- Fast verification 通过；
- `git diff --check` 通过；
- 无冲突；
- 无秘密/构建产物误提交；
- 现有 `TASK_STATE.md` 正常增量更新。

不得推送已知坏状态。

## 13. No routine human gate

不得要求用户：

- 审核代码或 diff；
- 确认继续；
- 批准普通 commit/push；
- 检查 CI；
- 检查分支；
- 执行常规阶段验收；
- 决定下一项机器任务。

普通事实写入：

- `TASK_STATE.md`；
- `SMART_OPERATION_STATE.md`；
- Git commit；
- 授权分支；
- CI。

## 14. External evidence

缺少设备、ROM、LSPosed 日志或签名时：

- 仅阻塞对应单项；
- 标记 `EXTERNAL_EVIDENCE_PENDING`；
- 不伪造；
- 不要求立即提供；
- 继续所有独立机器任务。

## 15. Empty queue and maintenance

当前任务队列暂时清空时：

- 执行 Standard sweep；
- 必要时执行 Deep sweep；
- 重新生成 inventory；
- 比较基线；
- 增加缺失测试；
- 检查 stale docs；
- 检查清理候选；
- 检查 CI 和构建；
- 将新问题加入现有 `TASK_STATE.md`；
- 继续最高优先级未阻塞任务。

`PROJECT_COMPLETE` 是里程碑，不是停止条件。之后进入 `CONTINUOUS_MAINTENANCE`。

## 16. Resume

额度、网络、系统或 Devin 中断后：

1. 读取 `GOAL.md`；
2. 读取 `AGENTS.md`；
3. 读取本文件；
4. 读取当前 `TASK_STATE.md`；
5. 读取 `SMART_OPERATION_STATE.md`（若存在）；
6. 核对仓库、精确分支、HEAD、upstream、status；
7. 检查未提交修改和最后失败命令；
8. 从中断点继续；
9. 不重新安装控制层；
10. 不询问是否继续；
11. 不丢弃工作。

## 17. Safety boundaries

不授权：

- 其他分支；
- 修改/合并 `main`；
- force-push；
- rebase；
- destructive reset/clean；
- tag/Release；
- secrets；
- 仓库内签名；
- ADB/自动安装/重启；
- 伪造设备证据；
- 删除测试或功能来通过验证；
- 无证据的大规模清理。
