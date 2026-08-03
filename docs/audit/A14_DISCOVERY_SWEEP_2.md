# A14 Discovery Sweep 2 Audit Report

## 1. Scope

This is the second full discovery sweep over the `devin/a14-rom-intelligence-audit`
branch after the P12 lifecycle/architecture/gesture/APK work.
The sweep re-runs all non-device audit tools and re-examines the source changes
between the first sweep baseline and the second sweep baseline.

- **First sweep baseline**: `c46ffb1a`
- **Second sweep baseline**: `f0cb5173`
- **Audit commit**: `c4ab7e30d26f1357913a5705b9843c73cd9108d3`
- **Risk tier**: R1

The goal is to confirm that no new P0/P1 issues were introduced and that all
P12 artifacts remain consistent with the control plane.

## 2. First sweep baseline

- `docs/audit/SOURCE_HAZARD_BASELINE.json` was produced by the first sweep.
- It contains 415 reviewed, de-duplicated source hazards (down from 1,079 raw
  findings).
- The baseline is not overwritten in this sweep; it is only used as the diff
  reference.

## 3. Audited source commit

- `c4ab7e30d26f1357913a5705b9843c73cd9108d3` — `docs(sweep): record P13.2 second discovery sweep as VERIFIED_STATIC`
- This is the current HEAD at audit time.

## 4. Changed files since sweep 1

Between `c46ffb1a` and `f0cb5173` the repository introduced:

- Control-plane documentation and skills (`.agents/skills`, `AGENTS.md`,
  `SMART_CONTINUOUS_OPERATION.md`, `TASK_STATE.md`, `INSTALL_A14_CONTROL_PLANE.md`)
- Runtime architecture and gesture contracts
  - `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt`
  - `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt`
  - `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt`
- Unit tests for the new runtime
- `docs/audit/SOURCE_HAZARD_BASELINE.json` (new baseline)
- APK size/R8 measurement data and reports
  - `docs/performance/A14_APK_SIZE_*.json`
  - `docs/performance/A14_APK_SIZE_DELTA.md`
- New/updated tooling
  - `tools/apk_size_delta.py`
  - `tools/apk_size_report.py` (used in P12.4)
  - `tools/audit_hook_ownership.py`
  - `tools/check-invariants.py`
  - `tools/check_automation_state.py`
  - `tools/progress_snapshot.py`
  - `tools/source_hazard_scan.py`
  - `tools/tests/test_*.py`

No source files in `app/src/main/**` were removed; changes were additive or
contractual and are covered by tests.

## 5. Commands and exit codes

| Command | Exit code | Result |
| --- | --- | --- |
| `python tools/source_hazard_scan.py` | 0 | pass, 0 new findings |
| `python tools/audit_hook_ownership.py` | 0 | pass, 755 hook sites |
| `python tools/audit-feature-semantics.py --validate` | 0 | pass |
| `python tools/extract_process_matrix.py` | 0 | pass, 244 features |
| `python -m unittest discover -s tools/tests -p "test_*.py"` | 0 | 225 passed |
| `python tools/check_document_contracts.py` | 0 | pass |
| `python tools/check-invariants.py` | 0 | no violations |
| `python tools/check_automation_state.py` | 0 | pass |
| `python tools/progress_snapshot.py --check` | 0 | fresh |
| `python tools/verify.py full` | 0 | pass |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Full` | 0 | A14 VERIFICATION PASSED |
| `git diff --check` | 0 | clean |
| `git ls-files *.apk *.aab` | (empty) | no tracked APK/AAB |

## 6. Source hazard findings

- `python tools/source_hazard_scan.py` reports:
  `Source hazard scan passed: 1052 reviewed finding(s), 0 new`
- All reported findings are reviewed and baseline-tracked.
- No new hazard fingerprints were introduced by the P12 changes.

## 7. Runtime / fatal rethrow findings

- `CATCH_THROWABLE_NO_FATAL` and `EMPTY_CATCH` hazards are documented in the
  baseline; no new occurrences were introduced.
- The new `ControlCenterPluginRuntime.kt` and `ControlCenterGestureRuntimeHolder.kt`
  use explicit lifecycle methods and owner-cleanup; no bare `catch (Throwable)`
  without fatal rethrow was added.
- Unit tests (`ControlCenterGestureRuntimeHolderTest`,
  `ControlCenterPluginRuntimeTest`) validate lifecycle cleanup and exception paths.

## 8. Lifecycle findings

- Receiver, Observer, View, Handler and ClassLoader ownership is documented in
  `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`.
- `tools/audit_hook_ownership.py` produced an inventory of 755 hook sites with
  owner/routing information.
- No orphan hook sites or un-owned lifecycle sinks were identified in the delta.

## 9. Hook / Feature findings

- `tools/audit-feature-semantics.py --validate` passed.
- `tools/extract_process_matrix.py` produced 244 feature rows; no duplicate
  install routes or `UNKNOWN` feature mappings were flagged by the validators.
- The P12 runtime uses a single `Installer`/`PluginRuntime` pair per feature;
  no orphan Feature definitions were detected.

## 10. ROM / API findings

- `A14_GESTURE_EVENT_CONTRACT.md` and `A14_CURRENT_ARCHITECTURE.md` define the
  API 101 (caller) / 102 (callee) boundary and the ROM target classes for
  SystemUI/Launcher.
- The new hook sites reference the ROM target classes explicitly;
  `audit_hook_ownership.py` did not report missing ROM targets.

## 11. Gesture findings

- The gesture runtime uses `CANCEL`-aware state machines and explicit owner
  cleanup.
- `ControlCenterGestureRuntimeHolderTest` exercises the lifecycle and CANCEL paths.
- No duplicate side-effect registrations were detected by the semantic validator.

## 12. Performance / APK findings

- `A14_APK_SIZE_DELTA.md` explains the `MIXED_CHANGE` conclusion for the P12.4
  build.
- The develop build (R8 + resource shrinking) produced an unsigned APK;
  no signing material, keystore or password appears in tracked files.
- `git ls-files *.apk *.aab` returned empty; no APK/AAB artifacts are tracked.
- The `MIXED_CHANGE` is attributed to bucket-level dex/resource shifts and is
  documented, not a P0/P1 regression.

## 13. P0/P1 findings

- No new P0/P1 issues were introduced by the P12 work.
- All gated tools (`check-invariants.py`, `check_automation_state.py`,
  `source_hazard_scan.py`, `verify.py full`, `verify.ps1 -Mode Full`) passed.

## 14. P2/P3 findings

- `SOURCE_HAZARD_BASELINE.json` still contains 1,052 reviewed findings, but these
  are baseline-known and de-duplicated; none are escalated to P0/P1.
- Remaining P2/P3 items (deprecated Android API usages, lint warnings) are tracked
  in the baseline and are not new.

## 15. External evidence gaps

- Device validation remains `BLOCKED_EXTERNAL` (`DEVICE-001`).
- No device evidence was collected in this sweep.
- This is expected for a static discovery sweep.

## 16. Conclusion

`NO_NEW_P0_P1`

Two consecutive discovery sweeps (`c46ffb1a` and `f0cb5173`) completed without
new P0/P1 findings. The P12 lifecycle, gesture, architecture and APK work is
statically clean and the repository is ready for the next non-sweep phase.
P14 is not started by this audit.
