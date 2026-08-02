# A14 Control-Plane Documentation Migration

## Why this update is required

The new Local Skills enforce an atomic session boundary:

```text
one Task Slice
one qualifying engineering checkpoint
one independent review
handoff
end session
```

The current control plane still contains older same-session instructions such as:

```text
automatically enter the next task
no voluntary stop
one-stop engineering loop -> next objective
HumanReviewRequired: false
```

Those statements conflict with the explicit Skill workflow and increase context degradation and self-review errors.

## Documents that must be updated

### 1. `AGENTS.md` — required

Change session behavior, not the technical goal.

Replace same-context continuation with:

```text
Project continuity spans multiple sessions.
An explicitly invoked repository Skill limits the current session to one approved Task Slice.
After the qualifying checkpoint, exact CI inspection, and handoff, the current session ends.
The next Task Slice starts in a fresh Implementer context.
R2/R3/R4 changes require a separate Reviewer context.
```

Keep repository lock, platform boundaries, fatal rules, code style, product behavior, and verification rules.

Update the protected-control-layer section to allow owner-approved Skill/control-plane migrations.

### 2. `SMART_CONTINUOUS_OPERATION.md` — required

Replace:

```text
HumanReviewRequired: false
AutoResume: true
No voluntary stop
One-stop engineering loop -> next objective
```

with:

```text
SessionMode: ATOMIC_TASK_SLICE
IndependentReviewRequired: R2_R3_R4
AutoResumeWithinSlice: true
AutoStartNextSlice: false
ProjectContinuity: MULTI_SESSION
ContextHandoffThreshold: 70_PERCENT
```

Clarify that “continuous operation” means evidence-driven continuity across fresh sessions, not unlimited work in one context.

A session must stop after handoff. That is a successful slice boundary, not a project stop.

### 3. `DEVIN_START_PROMPT.md` — required

Retire it as a giant autonomous prompt.

Convert it into a short Local Skill launcher:

```text
For implementation:
@skills:a14-safe-implementation docs/process/tasks/<task-file>.md

For independent review:
@skills:a14-independent-review <base-sha> <head-sha> docs/process/tasks/<task-file>.md
```

Do not repeat all repository rules in this file; `AGENTS.md` and the invoked Skill are the sources.

### 4. `INSTALL_A14_CONTROL_PLANE.md` — recommended

Add the Skill locations and invocation examples.

Document that files under `.agents/skills/` are version-controlled repository workflow rules.

### 5. `TASK_STATE.md` — do not update solely for Skill installation

Do not append a bookkeeping checkpoint merely because Skill files were copied.

Record Skill adoption only when bundled with a real control-plane migration checkpoint that includes updated rules, checker coverage, and verification.

### 6. `SMART_OPERATION_STATE.md` and `docs/progress/*` — do not manually chase Skill installation

Do not create another state-only commit.

Only update them from the same qualifying control-plane checkpoint and its real verification transaction.

### 7. `GOAL.md` — normally no change

The product and engineering completion target remains valid. Update it only when it contains session-execution wording that conflicts with atomic Task Slices.

## New files

Commit:

```text
.agents/skills/a14-safe-implementation/SKILL.md
.agents/skills/a14-independent-review/SKILL.md
docs/process/A14_RISK_GATE_MATRIX.md
docs/process/templates/A14_TASK_SLICE_TEMPLATE.md
docs/process/templates/A14_SESSION_HANDOFF_TEMPLATE.md
docs/process/A14_DEVIN_LOCAL_SKILLS.md
```

## Migration acceptance

```text
no conflicting same-session auto-continue rule
both Skills have triggers: ["user"]
implementation and review Skill names are repository-specific
R2+ requires independent review
Task Slice and handoff paths are documented
no A13/A14 cross-reference in the repository-specific package
no state-only follow-up commit
control-state checker covers the new session model
Fast verification passes
CI belongs to the qualifying control-plane commit
```
