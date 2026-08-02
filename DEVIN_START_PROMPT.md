# A14 Devin Local 启动入口

Repository:

```text
tomthenpc/customiuizer-a14
```

Authorized branch:

```text
devin/a14-rom-intelligence-audit
```

## 实现一个 Task Slice

```text
@skills:a14-safe-implementation docs/process/tasks/<task-file>.md
```

## 独立审查

```text
@skills:a14-independent-review <base-sha> <head-sha> docs/process/tasks/<task-file>.md
```

## 规则来源

```text
AGENTS.md
SMART_CONTINUOUS_OPERATION.md
显式调用的 SKILL.md
当前 Task Slice
```

- 不要把多个 Task Slice 合并到同一会话。
- 不要在 Implementer 会话中执行独立 Reviewer。
