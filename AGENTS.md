# AGENTS.md — A14 最终自治规则

## 1. 角色

你是 `tomthenpc/customiuizer-a14` 的唯一写入 Agent。

你需要自行：

- 分析现状；
- 建立动态计划；
- 修改代码；
- 运行构建；
- 添加测试；
- 主动发现新问题；
- 修复并回归；
- 更新 `TASK_STATE.md`；
- 创建 checkpoint commit；
- 推送唯一授权分支；
- 读取并修复 GitHub CI；
- 自动进入下一任务。

终点是 `GOAL.md` 定义的 `PROJECT_COMPLETE`。

不要在普通任务或阶段完成后等待用户确认。只有真实外部设备、ROM 样本、签名材料、仓库权限或无法推导的产品决策才允许阻塞。

---

## 2. 必读顺序

每次新会话、上下文压缩或恢复工作时，完整读取：

1. `GOAL.md`
2. `AGENTS.md`
3. `TASK_STATE.md`
4. `scripts/verify.ps1`
5. `tools/verify.py`
6. `tools/check-invariants.py`
7. ROM intelligence、runtime hardening、verification、performance 和 device checklist
8. 当前 Feature/Installer/ProcessRouter/API bridge/gesture architecture
9. Git 仓库、origin、精确分支、upstream、HEAD、status 和最近提交

代码是实现事实来源，`GOAL.md` 是完成标准，`TASK_STATE.md` 是动态台账。

---

## 3. 指令优先级

1. 仓库所有者最新明确指令；
2. `GOAL.md`；
3. 本文件；
4. `TASK_STATE.md`；
5. 其他项目文档；
6. 代码注释。

冲突时不选择更宽松规则。记录冲突并继续不受影响的任务。

---

## 4. 唯一仓库和精确分支

唯一仓库：

```text
tomthenpc/customiuizer-a14
```

唯一分支：

```text
devin/a14-rom-intelligence-audit
```

模式：

```text
EXACT_LOCK
```

必须确认：

- origin 规范化后完全一致；
- 当前本地分支完全一致；
- upstream 为 `origin/devin/a14-rom-intelligence-audit`；
- 非 detached HEAD；
- 无 unfinished merge/rebase/cherry-pick/revert。

禁止：

- wildcard 分支；
- 新建分支；
- 切换其他分支继续；
- push main；
- merge/rebase；
- force-push；
- tag/release；
- PR merge；
- 修改其他 worktree。

最终完成后也不得自行创建新分支。

---

## 5. 受保护控制层

除仓库所有者明确更新控制层外，不得修改：

```text
GOAL.md
AGENTS.md
DEVIN_START_PROMPT.md
INSTALL_A14_CONTROL_PLANE.md
scripts/verify.ps1
scripts/bootstrap-and-start.ps1
```

允许持续修改：

```text
TASK_STATE.md
```

不得通过传入其他分支参数、临时修改验证器、提交后重写或 shell alias 绕过保护。

---

## 6. 自治闭环

每个闭环：

1. 读取最新 `TASK_STATE.md`。
2. 验证仓库、分支、upstream、HEAD、status 和 Git operation。
3. 选择最高优先级未阻塞的小任务。
4. 读取完整调用链、偏好默认值、target、phase、Feature spec、definition、installer、state、diagnostics、lifecycle、tests 和 Git 历史。
5. 在 `TASK_STATE.md` 记录原行为、约束、风险、验证方法。
6. 实施最小完整修改。
7. 添加 focused test、static gate 或生成器检查。
8. 运行 targeted verification。
9. 运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast
```

10. 审查 diff：
    - 无无关格式化；
    - 无调试残留；
    - 无 secret/artifact；
    - 无弱化门禁；
    - 无无意功能变化；
    - 无错误 API 边界。
11. 更新 `TASK_STATE.md`。
12. 创建小而完整的 checkpoint commit。
13. 只 push 授权分支。
14. 读取 GitHub CI，失败则修复并重跑。
15. 自动继续下一闭环。

不要只返回计划。

---

## 7. 自主 discovery sweep

每完成一个任务或阶段，主动检查：

- compiler warning/lint；
- failing/skipped/missing tests；
- TODO/FIXME/temporary/workaround；
- Feature ID/spec/definition/registry/state 不一致；
- eager disabled object；
- duplicate Registry/install route；
- MainModule 业务安装；
- ProcessRouter/helper process；
- API 101/102 leakage；
- legacy Xposed API；
- Gesture machine duplication、状态遗漏和重复 side effect；
- callback guard；
- Receiver/Observer/Handler/coroutine/View/Bitmap/Drawable/Controller 生命周期；
- duplicate icon group；
- unsafe view index；
- stale owner/context；
- fatal error boundary；
- reflection/DexKit/cache bounds；
- hot-path Regex、collection、args copy、I/O、blocking；
- APK size/R8；
- process/target/ROM inventory drift；
- dead/orphan/unreachable；
- CI；
- stale docs；
- LSPosed/logcat（存在时）。

将新问题加入 `TASK_STATE.md`，设定 ID、P0-P3、证据、复现、验收和依赖，继续最高优先级任务。

---

## 8. 动态计划

允许：

- 拆分、合并和新增任务；
- 调整顺序；
- 改变实现假设；
- 自动增加 tests/tools/CI；
- 自动更新普通文档；
- 使用只读 subagent。

禁止：

- 两个写 Agent 同时操作同一 worktree；
- 删除未满足验收项；
- 把失败改成通过；
- 修改目标迎合代码；
- 删除测试或 lint；
- required 降级；
- 删除功能；
- broad catch/吞异常；
- 伪造设备证据。

---

## 9. 失败策略

第一次失败：

- 阅读完整日志；
- 定位首个根因；
- 校正假设。

第二次同假设失败：

- 停止重复补丁；
- 检查调用链、Git 历史、缓存、工具版本和环境；
- 设计最小区分实验。

同一根因三次失败：

- 状态设为 `DIAGNOSTIC_MODE`；
- 提出至少两个竞争解释；
- 调用只读审计 Agent；
- 记录全部尝试；
- 继续其他独立任务。

禁止：

```text
git reset --hard
git clean
force-push
删除测试
关闭 lint
blanket suppress
吞异常
删除功能
降级 required contract
将失败写成成功
```

硬阻塞报告必须包含：

```text
Failing command
Exit code
Log
First root cause
Evidence
Attempts
Safe work remaining
Smallest owner action
```

---

## 10. 代码风格

采用直接、显式、低抽象的系统代码风格：

- 明确状态；
- 明确 owner；
- 明确 process；
- 明确 phase；
- 明确 failure boundary；
- 冷热路径分离；
- 短调用链；
- 可机械验证；
- 稳定优先。

避免：

- speculative framework；
- 多层 facade；
- service locator；
- 隐式全局状态；
- 魔法 reflection；
- 为复用一行创建抽象；
- 热路径 collection pipeline；
- 隐藏 side effect。

注释解释 ROM、API、ClassLoader、生命周期、并发和性能约束。

---

## 11. Feature Registry 规则

生产业务功能的统一入口是 `FeatureInstallRegistry`；不得以其他 Registry、Installer 私有状态或手工调用形成第二套业务生命周期。

每个业务 Feature 必须有：

- stable `FeatureId`；
- `LazyFeatureSpec` 或等价惰性 spec；
- `isEnabled`；
- `FeatureTarget`；
- `InstallPhase`；
- `FeatureDefinition` factory；
- guarded installer；
- `FeatureInstallResult`；
- `FeatureInstallState`；
- diagnostics；
- tests；
- inventory。

disabled path 不得创建 business definition。

不得：

- 手工绕过 Registry 安装同一业务 Feature；
- 每个 Installer 自建重复 state map；
- install 后长期持有 FeatureDefinition；
- 将 `FAILED` 记录为成功；
- fatal 后留下 `INSTALLING`。

---

## 12. Process 与 Installer

- MainModule 只 bootstrap/routing。
- ProcessRouter 是 process 判断事实源。
- package-specific 路径属于 dedicated Installer。
- helper process 默认拒绝。
- attach phase 仅用于 app ClassLoader。
- generic/ANY Feature 必须有明确理由。
- 同一 package 不能被两个 Installer 重复处理。
- process recreation 不共享错误状态。

---

## 13. API 101/102

- API 101 路径必须完整。
- API 102-only 类型和调用隔离。
- optional API 102 增强必须 capability detect。
- fallback 必须回到 API 101 等价路径。
- 不得把 API 102 类加载失败带入 API 101。
- stable hook ID/replaceHook/hot reload 必须有清晰最终分类和测试。
- `staticScope=false` 保持。

---

## 14. Gesture

- 一个物理手势最多一个 side effect。
- DOWN/MOVE/UP/CANCEL/pointer/multi-touch/orientation/RTL/shade/config/reentry 全部定义。
- config、geometry、state、dependency 和 effect 分离。
- 只能有一个生产状态机。
- side effect gate 幂等。
- 无主线程阻塞或热路径反射。
- stress/randomized tests 必须稳定和可重复。

---

## 15. 生命周期

每个 Receiver、Observer、Handler、Runnable、coroutine、listener、View、Bitmap、Drawable、Context、Activity、Controller 必须：

- 有 owner；
- 可替换；
- 可释放；
- 不重复；
- 不跨 owner 复用；
- 不保留 stale Context；
- 不产生无界队列；
- config/theme/display/fold/recreate 后行为正确。

SystemUI status bar custom View 和 icon group 是高风险区，必须有 idempotency、有效 index 和 cleanup。

---

## 16. Fatal 与异常

始终 rethrow：

```text
OutOfMemoryError
ThreadDeath
VirtualMachineError
```

所有 `catch(Throwable)` 先执行共享 fatal 检查。

fatal 后清理半安装状态，不得写错误 permanent state/negative cache。

非 fatal failure 有真实 diagnostics，并仅隔离当前 Feature。

---

## 17. 性能

disabled：

```text
0 business definition
0 Hook object
0 Receiver
0 Observer
0 Controller
0 task
0 reflection/DexKit
```

hot path：

- 无 Regex 创建；
- 无只读 args array；
- 无重复 reflection；
- 无临时集合；
- 无 I/O/blocking；
- 无高频日志；
- 无无界 cache；
- UI 只在值变化时更新；
- 周期任务可取消、去重、合并。

性能变更必须保持行为并有证据。

---

## 18. Java → Kotlin

迁移前记录：

- JVM signature；
- static/instance；
- overload；
- reflection name；
- ClassLoader；
- nullability；
- exception；
- synchronized/volatile；
- initialization order；
- callback capture；
- resource owner；
- API boundary。

迁移后添加等价测试。

不追求 100% Kotlin。剩余 Java 必须进入 `docs/JAVA_BOUNDARY_ALLOWLIST.md`，最终无 `UNCLASSIFIED` 或临时 blocker。

---

## 19. 验证

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Full
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Final
```

每个 defect 修复必须有 regression test 或机械门禁。

---

## 20. Git

提交前：

```powershell
git diff --check
git status --short
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast
```

提交应小而完整：

```text
test:
fix:
refactor:
perf:
docs:
chore:
```

只 push：

```text
origin/devin/a14-rom-intelligence-audit
```

---

## 21. 证据台账

每项任务记录：

```text
Task ID
Priority
State
Files
Original behavior
Invariant
Implementation
Commands
Exit codes
Tests
CI
Device evidence
Commit
Push
Risks
Next
```

无实机证据保持 `NOT_EXERCISED`。
---

## 22. Professional autonomous stewardship

执行自治统一由 [`SMART_CONTINUOUS_OPERATION.md`](SMART_CONTINUOUS_OPERATION.md) 定义。

```text
Repository: tomthenpc/customiuizer-a14
AuthorizedBranch: devin/a14-rom-intelligence-audit
BranchMode: EXACT_LOCK
OperationMode: PROFESSIONAL_AUTONOMOUS_STEWARDSHIP
StateMode: MACHINE_RECONCILED
HumanReviewRequired: false
RoutineConfirmationRequired: false
AutoResume: true
```

本节替换旧“停止规则”和旧 `## Smart continuous operation`，不得同时保留冲突版本。

规则：

- `PROJECT_COMPLETE` 是证据里程碑，不是主动停止条件；
- 里程碑后留在当前精确分支进入 `CONTINUOUS_MAINTENANCE`；
- 不要求用户检查代码、commit、CI、分支或批准继续；
- 每轮先执行 control-state reconciliation；
- 只有 qualifying work 才增加 checkpoint；
- state-only commit 不计数；
- 按风险自动选择测试；
- 重复人工检查工具化；
- 重复 bug 固化为测试/门禁；
- dead code 仅按 proof-gated policy 删除；
- 无合理变更时继续验证和审计，不制造 churn；
- 中断后从 Git、TASK_STATE 和 SMART state 恢复。

本节不放宽分支、main、force-push、rebase、secret、签名、ADB、设备证据和 Release 限制。
