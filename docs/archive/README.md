# 归档文档

这里的文件是**写作当时的快照**，不是当前状态的描述：一次重构的计划与进度、几次一次性的
审计、某一天的日志复盘。保留它们是为了能回答「当时为什么这么改」，不是为了指导现在怎么改。

因此：

- 不要把这里的结论当作现状。当前状态以 `docs/` 顶层的文档、`AGENTS.md`、
  `tools/check-invariants.py` 和代码本身为准。
- 文中出现的文件路径、行号、HEAD、分支和待办都是当时的。归档时没有改写正文，
  改写会破坏记录本身的价值 —— 所以这些路径可能已经失效。
- 不要更新这里的文件。有新结论就写进 `docs/` 顶层的对应文档。

| 文件 | 记录的是 |
| --- | --- |
| `REFACTOR_PLAN_r14.13.md` | r14.13 Kotlin 化重构的计划 |
| `REFACTOR_PROGRESS.md` | 同一轮重构的逐批进度 |
| `ARCHITECTURE_AUDIT_r14.13.md` | r14.13 架构地图与问题清单 P1–P10 |
| `HOT_PATH_AUDIT_r14.13.md` | 热路径分配与反射审计 |
| `MEMORY_AUDIT_r14.13.md` | 内存与生命周期审计 |
| `STATE_REGRESSION_AUDIT_r14.13.md` | 状态回归审计 |
| `JAVA_BOUNDARY_ASSESSMENT_r14.13.md` | 保留 Java 的边界评估 |
| `RC1_LOG_AUDIT_r14.13.md` | r14.13 RC1 的实机日志审计 |
| `LOG_AUDIT_2026-07-28.md` | 2026-07-28 的日志复盘 |
