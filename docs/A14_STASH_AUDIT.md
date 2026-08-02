# A14 Stash Audit — P3.5 GenericAppEligibilityResolver

```text
DocumentKind: CURRENT
Product: CustoMIUIzer A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: 45a7b48d082370f290e69e6139b4966bd7fda138
EvidenceState: STATIC
GeneratedBy: git stash show
SourceOfTruth: stash@{0} on devin/a14-rom-intelligence-audit
```

## Base

- Stash: `stash@{0}`
- Stash message: `WIP: P3.5 GenericAppEligibilityResolver`
- Base snapshot recorded by SMART at stash creation: `LastQualifyingCheckpoint: 8cb27bf2`
- Stash index state (`CheckpointsSinceDeepSweep: 14`) predates the current HEAD.

## Files touched by the stash

- `SMART_OPERATION_STATE.md` (old checkpoint counters, now stale)
- `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.java`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/GenericAppEligibilityResolver.kt` (new)
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/GenericAppSelection.kt` (new)

## Overlap with current HEAD

Current HEAD (`45a7b48d`) has reverted `MainModule.java` and `GenericAppInstaller.java` to the pre-stash version. The two new Kotlin files (`GenericAppEligibilityResolver.kt`, `GenericAppSelection.kt`) are not present in HEAD. The stash contains the same `MainModule.java`/`GenericAppInstaller.java` changes that were reverted, so `git stash apply` would conflict.

## Classification

```text
OBSOLETE
```

- The user explicitly reverted the P3.5 `GenericAppEligibilityResolver`/`GenericAppSelection` approach.
- The stash base (`CheckpointCount: 14`) is older than the current HEAD and its SMART counters are stale.
- Applying it would overwrite intentionally reverted `MainModule.java` and `GenericAppInstaller.java` and re-introduce removed files.

## Future recovery condition

If P3.5 is ever revisited, do not restore from this stash directly. Instead:

1. Re-read `GenericAppEligibilityResolver.kt` and `GenericAppSelection.kt` from the stash with `git show stash@{0}:<path>`.
2. Re-design the eligibility resolver against the current `MainModule.java` and `ProcessRouter` if needed.
3. Add new focused tests and run `python tools/verify.py full`.
4. Do not re-apply the old `SMART_OPERATION_STATE.md` fragment.

## Action

Keep as a read-only record. Do **not** `pop`, `apply` or `drop`.
