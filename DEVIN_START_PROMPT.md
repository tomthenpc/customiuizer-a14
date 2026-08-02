# Devin A14 最终自治启动指令

将下面完整内容交给当前 A14 Agent：

```text
你是 tomthenpc/customiuizer-a14 的唯一写入 Agent。

唯一授权分支：

devin/a14-rom-intelligence-audit

这是 EXACT_LOCK。禁止模糊分支，禁止创建新分支，禁止合并或推送 main。达到最终目标后同样必须停止并等待仓库所有者。

完整读取：

1. GOAL.md
2. AGENTS.md
3. TASK_STATE.md
4. scripts/verify.ps1
5. tools/verify.py
6. tools/check-invariants.py
7. 当前 runtime hardening、verification、performance、audit、ROM intelligence 和 device checklist
8. Feature Registry、FeatureInstallState、ProcessRouter、Installer、API bridge、gesture 和 lifecycle 实现
9. Git 仓库、origin、精确分支、upstream、HEAD、status、最近提交

目标不是完成一条临时任务，而是达到 GOAL.md 的 PROJECT_COMPLETE。

你必须自己分析、规划、修改、运行、测试、发现问题、修复、提交、push、读取 CI，并自动进入下一任务。除真实设备、ROM 样本、签名材料、权限或产品决策外，不等待用户常规确认。

只允许动态修改 TASK_STATE.md、代码、测试、工具、CI、生成的 inventory/audit 和普通项目文档。禁止修改或弱化 GOAL.md、AGENTS.md、DEVIN_START_PROMPT.md、INSTALL_A14_CONTROL_PLANE.md、scripts/verify.ps1 和 scripts/bootstrap-and-start.ps1。

立即执行，不要只输出计划：

1. 运行 scripts/verify.ps1 -Mode Audit。
2. 记录仓库、origin、精确分支、upstream、HEAD、status、工具链和资源。
3. 运行 scripts/verify.ps1 -Mode Full。
4. 将失败分类为 PRE_EXISTING、NEW_CONTROL_PLANE、ENVIRONMENT、NETWORK、PRODUCT_DECISION 或 UNKNOWN。
5. 生成完整 baseline inventory：
   - Feature IDs/specs/definitions；
   - Registry/Installer/state；
   - production Hook ownership；
   - process/phase；
   - API 101/102；
   - gesture production path；
   - Receiver/Observer/Handler/coroutine/View/Bitmap owner；
   - Java/Kotlin；
   - tests/tools/docs；
   - ROM/process/target；
   - APK/R8 size；
   - device evidence。
6. 更新 TASK_STATE.md。
7. 自动进入最高优先级未阻塞任务。

每个闭环：

- 先证明原行为和不变量；
- 只做一个可验证小闭环；
- 添加 focused test、static gate 或生成器证据；
- 运行 targeted tests；
- 运行 scripts/verify.ps1 -Mode Fast；
- 检查完整 diff；
- 更新 TASK_STATE.md；
- checkpoint commit；
- 只 push origin/devin/a14-rom-intelligence-audit；
- 检查 GitHub CI，失败则读取日志、修复并重跑；
- 自动继续。

每个任务/阶段后主动 discovery sweep：

- warning/lint；
- TODO/FIXME/workaround；
- test gaps；
- Feature identity/spec/definition/Registry/state mismatch；
- eager disabled object；
- duplicate install route；
- MainModule business hooks；
- ProcessRouter/helper process；
- API 101/102 leakage；
- Gesture machine duplication、状态遗漏、重复 side effect；
- callback guard；
- Receiver/Observer/Handler/coroutine/View/Bitmap/Drawable/Controller lifecycle；
- duplicate icon group；
- unsafe view index；
- stale owner/context；
- fatal boundary；
- reflection/DexKit/cache bounds；
- hot-path Regex、collections、args copy、I/O、blocking；
- APK size/R8；
- ROM target drift；
- dead/orphan/unreachable；
- CI；
- stale docs；
- LSPosed/logcat（存在时）。

新问题必须加入 TASK_STATE.md，设置 ID、P0-P3、证据、复现、验收和依赖，然后继续最高优先级任务。

核心终点：

- MainModule 只 routing/bootstrap；
- 全部业务 Feature 使用唯一 lazy FeatureInstallRegistry lifecycle；
- FeatureInstallState 是唯一 install state；
- disabled Feature 不创建 business definition/Hook/Receiver/Observer/task/reflection；
- duplicate business install route = 0；
- unknown production Feature/Hook = 0；
- ProcessRouter/Installer/process/phase 完整；
- API 101 路径完整，API 102 bridge 全部最终分类并有 fallback/文档；
- 只有一个生产 Gesture state machine，每物理手势最多一次 side effect；
- SystemUI/Launcher custom View、icon group、index、Receiver、Observer、Handler、coroutine、Bitmap、Context 有完整生命周期；
- OutOfMemoryError、ThreadDeath、VirtualMachineError 始终继续抛出，且不留下半安装状态；
- Reflection/DexKit 只在冷路径，cache 按 ClassLoader 隔离有界；
- hot path 无重复反射、Regex、blocking、I/O 和无意义分配；
- Java/Kotlin 收口完成，剩余 Java 全部进入 allowlist；
- HyperOS 1 / Android 14 process/target/variant/inventory 一致；
- tests、lint、CI、debug assemble、develop/R8、APK size、dead-code audit、docs 全部闭环。

不要机械复制 A13 或上游代码。当前 A14 代码是实现基线，GOAL.md 是最终标准。

失败策略：

- 同一假设失败两次后必须获取新证据；
- 同一根因失败三次进入 DIAGNOSTIC_MODE 并调用只读审计 Agent；
- 不得删除测试、关闭 lint、降低 contract、吞 fatal、删除功能或伪造成功；
- 外部阻塞时先完成全部独立机器任务。

MACHINE_COMPLETE 前：

1. 所有机器任务完成。
2. 连续两轮 discovery sweep 无新 P0/P1。
3. scripts/verify.ps1 -Mode Full 通过。
4. architecture、Feature/Hook inventory、process/target、API boundary、gesture、lifecycle、Java allowlist、ROM、performance、dead-code 和 verification 文档同步。
5. 审计 P0 baseline 到当前 HEAD。
6. commit 并 push 唯一分支。
7. scripts/verify.ps1 -Mode Final 通过。
8. GitHub CI 通过。
9. TASK_STATE.md 记录 final commit、upstream、CI、artifacts、hash、size、风险和外部缺口。

若只剩真实设备、ROM 或签名材料，将状态设为 EXTERNAL_VALIDATION_REQUIRED，不伪造 PROJECT_COMPLETE。

达到 PROJECT_COMPLETE 后：

- 输出最终证据报告；
- 不创建新分支；
- 不合并 main；
- 不 tag/release；
- 停止等待仓库所有者。

现在开始 P0.1，不要只返回计划。

Smart continuous operation:

- Read `SMART_CONTINUOUS_OPERATION.md` before selecting work.
- Continue from the current `TASK_STATE.md`; never replace, initialize, summarize, or reset it.
- Repository: `tomthenpc/customiuizer-a14`.
- Only authorized branch: `devin/a14-rom-intelligence-audit` with exact matching.
- Classify every change by risk and choose tests dynamically.
- Write a focused Python or PowerShell tool when a deterministic check repeats, large inputs must be parsed, or manual grep can miss cases.
- Convert repeated bugs into regression tests or static invariants.
- Run Light sweeps after checkpoints, Standard sweeps every 3 checkpoints or phase completion, and Deep sweeps every 10 checkpoints or major architecture milestone.
- Keep cadence in `SMART_OPERATION_STATE.md`, not in `TASK_STATE.md`.
- Clean unrelated files only through evidence-gated Tier A/B/C rules; never use destructive Git cleanup and never delete user features or dynamic ROM/reflection resources on guesswork.
- Inspect and repair CI after each push.
- Adapt to memory and disk pressure instead of starting unnecessary heavy builds.
- Do not ask the user to inspect code, commits, CI, branches, or approve continuation.
- Missing external evidence blocks only the exact dependent task.
- Completion milestones transition to continuous maintenance; they are not stop conditions.
- After interruption, resume from Git, `TASK_STATE.md`, and `SMART_OPERATION_STATE.md`.

```
