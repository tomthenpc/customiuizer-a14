# A14 Task Slice

```text
TaskId: P12.1
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
BaseCommit: cd152365a1b258a7b36e978d1050db71f427fa83
RemoteHead: cd152365a1b258a7b36e978d1050db71f427fa83
AheadBehind: 0 / 0
RiskTier: R0
ImplementerSession: a14-safe-implementation
ReviewerSession: a14-independent-review
```

## One objective

Verify and mark `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` as current against the current source tree, correcting stale source line references and binding the document to a valid `EvidenceCommit`.

## Scope

Production files:
- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` (update)

Test/tool files:
- `tools/tests/test_gesture_lifecycle_inventory.py` (new)

State files (only with evidence from this checkpoint):
- `TASK_STATE.md` (update P12.1 / DOC-001 lifecycle inventory evidence)
- `SMART_OPERATION_STATE.md` (update LastLightSweepCommit etc. only if required by control state)

Maximum production LOC: 0 (doc only; tool test limited to ~120 lines).

## Explicit exclusions

Do not modify:
- `GOAL.md`, `AGENTS.md`, `SMART_CONTINUOUS_OPERATION.md`, `DEVIN_START_PROMPT.md`, `scripts/verify.ps1`
- Any `app/src/main/**` production code
- Any existing `app/src/test/**` unit test
- `docs/process/P3.5-GenericAppEligibilityResolver.md` (untracked previous task slice; not this slice)
- `docs/A14_STASH_AUDIT.md`

Do not:
- Restore `stash@{0}`
- Start P14 MACHINE_COMPLETE
- Make a state-only commit

## Original behavior

`docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` is a `CURRENT` lifecycle owner inventory for gesture components. Its front matter currently has:

```text
EvidenceCommit: pending
EvidenceState: STATIC
GeneratedBy: tools/check-invariants.py + manual call chain audit
```

The evidence section currently cites stale line ranges (`SystemUIControlCenterHooks.kt` 874-889 and 131-146) that no longer contain the status-bar attach/detach hooks. The document cannot be mechanically verified and is not bound to a concrete commit.

## Invariants

Behavior that must remain unchanged:
- The document still describes the four owners: `PhoneStatusBarView`, `ControlCenterWindowViewImpl` (window), `ControlCenterGestureRuntimeHolder` active runtime, and `ControlCenterWindowViewImpl` raw motion events.
- The document still records the known gap `DEVICE_LIFECYCLE_ENTRY_BLOCKED`.
- No production code or public API changes.

Behavior that must change:
- `EvidenceCommit` must be a real commit SHA (the base commit or the qualifying commit, as long as the source files and line numbers are consistent with it).
- Source line references must point to the actual attach/detach hooks, `ControlCenterGestureRuntimeHolder.bind/unbind/activeRuntime`, `GestureMachine.clear()` and `clear(ownerId)`, and `ControlCenterGestureRuntimeHolderTest`.
- A new tool test in `tools/tests/` must verify the document against the source tree.

Forbidden behavior:
- Setting `EvidenceCommit` to a value that does not exist or is not an ancestor of `HEAD`.
- Citing line ranges that do not contain the claimed patterns.
- Updating `TASK_STATE.md` or `SMART_OPERATION_STATE.md` with claims not proven by the verification commands.

## Independent oracle

- `tools/tests/test_gesture_lifecycle_inventory.py` parses the markdown and fails if:
  - `EvidenceCommit` is not a valid 40-character Git object that is an ancestor of `HEAD`.
  - Any referenced source/test file does not exist.
  - Any cited line range does not contain the expected token (e.g. `statusBarMachine.clear`, `controlCenterMachine.clear`, `onAttachedToWindow`, `onDetachedFromWindow`, `fun clear()`, `fun clear(ownerId:`, `fun bind`, `fun unbind`, `fun activeRuntime`, test names covering `bind`/`unbind`/`idempotency`/`sameLoader`/`newLoader`/`repeatedLoader`/`oldLoaderDetach`/`runtimeIdentity`).
- `python tools/check-invariants.py` reports `gesture-detach-cleanup` as satisfied.
- `gradlew :app:testDebugUnitTest --tests ControlCenterGestureRuntimeHolderTest` passes.
- `gradlew :app:testDebugUnitTest --tests ControlCenterPluginRuntimeTest` passes.
- `gradlew :app:testDebugUnitTest --tests GestureStateMachineTest` passes.
- `gradlew :app:testDebugUnitTest --tests GestureMachineTest` passes.
- `gradlew :app:testDebugUnitTest --tests PhysicalGestureArbiterTest` passes.

## Mutation

- Intentionally corrupt a cited line range in the doc; the tool test must fail on the next run.
- Set `EvidenceCommit` to a non-existent SHA; the tool test must fail.
- Remove a required token from a source file (in a throwaway working tree or with a temporary mutation) and verify the test fails.

## Verification

```powershell
# Independent oracle
python -m unittest tools.tests.test_gesture_lifecycle_inventory

# Static gate
python tools/check-invariants.py

# Focused behavior tests
python tools/verify.py fast --tests ControlCenterGestureRuntimeHolderTest
python tools/verify.py fast --tests ControlCenterPluginRuntimeTest
python tools/verify.py fast --tests GestureStateMachineTest
python tools/verify.py fast --tests GestureMachineTest
python tools/verify.py fast --tests PhysicalGestureArbiterTest

# R0 risk-tier verification
python tools/verify.py fast
python tools/verify.py full

git diff --check
git status --short
```

## Stop conditions

- Tool test red after two corrected attempts.
- `python tools/verify.py full` red.
- Source code changes required to satisfy the doc (this slice is doc-only).
- Diff exceeds the doc + test budget.
- Context near 70%.

## Completion evidence

```text
QualifyingCommit: TBD
VerifiedTree: TBD
VerificationMode: Fast / Full
CIRun: TBD
CIJob: TBD
CICommit: TBD
ReviewerDecision: TBD
RemainingExternalBlock: DEVICE_LIFECYCLE_ENTRY_BLOCKED (known, external)
```
