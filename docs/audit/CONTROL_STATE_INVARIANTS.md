# A14 Control-State Invariants

## Required files

```text
TASK_STATE.md
SMART_OPERATION_STATE.md
SMART_CONTINUOUS_OPERATION.md
```

## SMART_OPERATION_STATE unique keys

```text
Mode
CheckpointCount
CheckpointsSinceStandardSweep
CheckpointsSinceDeepSweep
LastQualifyingCheckpoint
LastLightSweepCommit
LastStandardSweepCommit
LastDeepSweepCommit
LastFullVerificationCommit
LastCIState
LastCleanupCommit
LastToolCreated
LastFailureClass
CurrentObjective
ResumeTask
```

每个 key 只能出现一次。

## Qualifying checkpoint

不计数：

- 只改 SMART state；
- 只改计数；
- 只改“当前正在工作”文字；
- 无测试/验证的格式化；
- 覆盖层安装 commit。

计数：

- 独立业务修复；
- 测试/门禁闭环；
- 工具闭环；
- 实际 sweep；
- 有源码一致性证据的文档/inventory。

## Parent/child state

- 所有 required child COMPLETE，parent 不得仍 IN_PROGRESS。
- 任一 required child TODO/IN_PROGRESS，parent 不得 COMPLETE。
- BLOCKED_EXTERNAL 只影响对应 child。
- 问题队列必须和 phase 正文同步。

## CI

```text
NOT_CONFIGURED
PENDING
PASS
FAIL
UNAVAILABLE
```

无 workflow = `NOT_CONFIGURED`。

## Sweep

只有实际执行并记录命令、退出码、证据时才能填写 LastStandard/Deep。

Standard 要求 Full 时，LastFullVerificationCommit 必须同步。

## Completion

旧的“PROJECT_COMPLETE 后停止/等待”必须替换为：

```text
record milestone
remain on exact branch
enter CONTINUOUS_MAINTENANCE
continue evidence-driven work
```

## Current project reconciliation

- 删除 SMART_OPERATION_STATE 中重复键；每个键必须唯一。
- 从 Git 历史重新计算 qualifying checkpoint；纯状态计数 commit 不计数。
- 只有真正执行 Standard sweep 并有命令/结果证据时，才能填写 `LastStandardSweepCommit`。
- Standard sweep 要求 Full 时，必须同步填写 `LastFullVerificationCommit`。
- 没有 workflow/status checks 时，`LastCIState` 设置为 `NOT_CONFIGURED`，并在 P11 建立 CI 任务。
- 若 P5 全部子项证据仍有效且测试通过，将 P5 父状态改为 `COMPLETE`。
- BASELINE-001、VERIFY-001、ARCH-001、API-001、GESTURE-001 根据正文证据同步；LIFECYCLE-001 保持未完成并关联 P6。
- Checkpoint 节记录最后一个真实 qualifying checkpoint，不记录纯状态 commit。
