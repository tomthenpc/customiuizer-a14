# A14 Task Slice

```text
TaskId:
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
BaseCommit:
RiskTier: R0 | R1 | R2 | R3 | R4
ImplementerSession:
ReviewerSession:
```

## One objective

```text
Exactly one independently verifiable feature, defect, lifecycle owner, ABI boundary, or tooling invariant.
```

## Scope

```text
Production files:
Test files:
Tool files:
Documentation files:
Maximum production LOC:
```

## Explicit exclusions

```text
Do not modify:
Do not migrate:
Do not rewrite:
Do not update:
```

## Original behavior

```text
Inputs:
Outputs:
Side effects:
Process:
Install phase:
Preference defaults:
Fatal behavior:
JVM ABI:
Lifecycle/ClassLoader owner:
ROM assumptions:
```

## Invariants

```text
Behavior that must remain unchanged:
Behavior that must change:
Forbidden behavior:
```

## Independent oracle

```text
Main/release differential:
Focused behavior test:
Callback/integration test:
Contract test:
ABI test:
Mutation cases:
Device evidence:
```

## Verification

```powershell
# Focused

# Static/tool gates

# Risk-tier verification

git diff --check
```

## Stop conditions

```text
red focused test after two corrected attempts
red Full/Final/CI
unresolved ROM or lifecycle assumption
diff exceeds approved budget
context near 70%
test cannot be made independent of implementation
```

## Completion evidence

```text
QualifyingCommit:
VerifiedTree:
VerificationMode:
CIRun:
CIJob:
CICommit:
ReviewerDecision:
RemainingExternalBlock:
```
