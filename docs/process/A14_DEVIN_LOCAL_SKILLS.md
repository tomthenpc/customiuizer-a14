# A14 Devin Local Skills

## Install

Copy these repository-specific files into the root of `tomthenpc/customiuizer-a14`.

Do not copy the other product's package into this repository.

## Implementation

Create a filled Task Slice under a local or committed task path, then invoke:

```text
@skills:a14-safe-implementation docs/process/tasks/<task-file>.md
```

The skill uses `triggers: ["user"]`; it cannot be automatically selected by Devin.

## Review

Start a separate context:

```text
@skills:a14-independent-review <base-sha> <head-sha> docs/process/tasks/<task-file>.md
```

The Reviewer context must not modify production code.

## Current-state rule

Do not put current HEAD, current CI run, or a transient objective inside `SKILL.md`.

Skills contain stable process rules. Current work belongs in the Task Slice, `TASK_STATE.md`, and the handoff.

## Session lifecycle

```text
Planner/Task Slice
-> fresh Implementer session
-> qualifying engineering commit
-> exact CI
-> handoff
-> fresh Reviewer session
-> approve or return findings
-> next Task Slice in another fresh session
```
