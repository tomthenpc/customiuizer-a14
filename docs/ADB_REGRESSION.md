# ADB Regression Operator Guide

This guide covers `tools/adb-regression.py`, the local Android Debug Bridge
regression harness for CustoMIUIzer A14.  It is designed for **read-only,
non-destructive** regression on a real HyperOS 1 / Android 14 device.  When no
device is available, the framework is exercised by the `tools/tests/fixtures/fake_adb.py`
fixture.

The repository CI never connects to real hardware; it only validates plans,
fixtures and schemas.

---

## 1. What this tool does

- Collects a lightweight preflight baseline (`preflight`) without copying large logs.
- Validates ADB regression plan files (`validate-plan`).
- Runs a plan against one device (`run`).
- Proposes device evidence bundles from a report (`propose-evidence`).
- Redacts serials, tokens, base64, SharedPreferences and Bundle dumps before any
  output is written.

The tool refuses destructive commands such as `rm`, `reboot`, `settings put`,
`input`, `su`, `chmod`, `mount`, etc.  This denylist is enforced even with
`--allow-dangerous`.

---

## 2. Setting up Platform Tools on Windows

1. Download **Android SDK Platform Tools** from the official Android developer site.
2. Extract to a path such as `C:\platform-tools`.
3. Either add that directory to your user `PATH`, or pass the absolute path to the
   executable with `--adb`.

The executable may be `adb.exe`, `adb`, a `*.cmd` wrapper or a symlink.  The
harness resolves it through `PATH` or the `--adb` override.

---

## 3. USB debugging and RSA authorization

1. Enable **Developer options** on the device.
2. Turn on **USB debugging**.
3. Connect the device and authorize the RSA fingerprint when prompted.
4. Verify with `adb devices`.

A device in `unauthorized` or `offline` state is rejected.  Always confirm the
state before a run; an unauthorized device cannot be auto-authorized by the tool.

---

## 4. CLI overview

Global options appear before the subcommand:

```cmd
python tools/adb-regression.py [global] <command> [command-options]
```

Global options:

- `--adb <path>`   Absolute or `PATH`-resolvable adb executable.
- `--serial <id>`  Target one device when several are attached.
- `--timeout <s>`  Default 30 seconds per adb invocation.
- `--output <dir>` Output directory, default `build/adb-regression`.

Subcommands:

- `preflight`                  Collect a baseline report.
- `validate-plan --plan <>`    Validate a regression plan file.
- `run --plan <>`              Execute a plan.
- `propose-evidence --report <>`  Build an evidence proposal JSON.

### Multi-device (`--serial`)

If more than one device is connected, the tool exits unless `--serial` is given.
Specify the full device serial shown by `adb devices -l`.

```powershell
python tools/adb-regression.py --serial ABCD1234 preflight
```

### Absolute adb path (`--adb`)

Use this when the platform tools are not on `PATH` or when you want a specific
build:

```powershell
python tools/adb-regression.py --adb C:\platform-tools\adb.exe preflight
```

---

## 5. `preflight`

Collects device state and writes `preflight.json` / `preflight.md` under the
output directory.  It gathers:

- Build properties (manufacturer, model, SDK, fingerprint, MIUI version).
- Root / `su` availability.
- Installed module version and certificate.
- PIDs for `system_server`, `com.android.systemui`, `com.miui.home`.
- Recent module-load markers from logcat.

Device serials are hashed; no raw serial is stored.  Well-known environment
variables containing tokens or secrets are stripped from the child process.

---

## 6. `validate-plan`

Validate a plan without touching the device.  Useful in CI and before a real run.

```cmd
python tools/adb-regression.py validate-plan --plan adb-regression/a14-smoke.json
```

The validator checks JSON, required fields, step types, timeout limits, shell
command safety, path traversal in `evidenceFiles`, and feature/preference link
consistency.  Exit codes are documented below.

---

## 7. `run`

Run a validated plan against the selected device.

```powershell
python tools/adb-regression.py run --plan adb-regression/a14-smoke.json --serial ABCD1234
```

### `run` options

- `--apk <path>`            APK to inspect or install.
- `--install`               Allow installation of the APK (user must still opt in).
- `--allow-dangerous`       Permit steps marked `dangerous: true`.
- `--verbose`               Extra logging.
- `--lsposed-log <file>`    Use a saved LSPosed verbose log as an evidence source.
- `--allow-unverified-log`  Accept a stale or unverifiable LSPosed log.
- `--manual-results <file>` Provide PASS/FAIL for `manual_checkpoint` steps.

### Simulation flag

A run is automatically marked as `simulation` when the selected serial starts
with `FAKE` or when the preflight reports a `FakePhone` model.  Simulated runs
produce a complete report, but `propose-evidence` rejects them and emits
`SIMULATION_ONLY` confidence.

---

## 8. LSPosed log handling

### Manual export

1. Open LSPosed Manager.
2. Enable verbose logging if it is not already on.
3. Reproduce the scenario.
4. Use the manager's export or `adb pull` to save `full.log`.

### `--lsposed-log` and `--allow-unverified-log`

The framework can use the saved log as a fallback when live `adb logcat` does not
contain module markers:

```powershell
python tools/adb-regression.py run --plan adb-regression/a14-smoke.json --lsposed-log full.log --allow-unverified-log
```

By default the log must contain recent CustoMIUIzer markers.  If the newest
marker is older than 5 minutes, the log is considered stale and the run fails.
`--allow-unverified-log` downgrades that to `UNVERIFIED` confidence instead of
stopping.

### Log freshness / stale rules

- `VERIFIED`: live `adb logcat` contains module markers.
- `VERIFIED`: LSPosed log contains module markers less than 5 minutes old.
- `UNVERIFIED`: LSPosed log is accepted with `--allow-unverified-log`.
- `STALE_OR_UNVERIFIED_LOG`: LSPosed log is stale and not allowed.
- `LOG_SOURCE_UNAVAILABLE`: no live or provided source contains markers.

`STALE` and `UNVERIFIED` evidence must not be promoted to `DEVICE_VERIFIED`.

---

## 9. `--manual-results`

Some steps are `manual_checkpoint`.  Their results cannot be determined by adb,
so an operator records them in a JSON file:

```json
{
  "checkpoints": [
    {
      "stepId": "broadcast-negative-placeholder",
      "status": "PASS",
      "notes": "No unexpected SENTINEL/FAILED replies observed."
    },
    {
      "stepId": "tasker-manual-placeholder",
      "status": "PASS",
      "notes": "Open/cancel/save/re-use cycle completed."
    }
  ]
}
```

```powershell
python tools/adb-regression.py run --plan adb-regression/a14-smoke.json --manual-results tasker-results.json
```

Notes are redacted for paths, emails, phone numbers, tokens, base64, bundles and
SharedPreferences.

---

## 10. `broadcast_probe` negative testing

A negative broadcast test sends an action that the module must **not** handle and
expects `FAILED` or `NOT_AVAILABLE`.  This proves the module rejects malformed or
untrusted intents instead of silently executing.  Fake adb has several negative
probes such as `WRONG_TARGET_PROBE` and `UNREGISTERED_PROBE` that always return
`HANDLED`; the framework treats `HANDLED` as a failure for these cases.

Run this only against the fake fixture or a controlled test build; never against
an untrusted action on a personal device.

---

## 11. `Tasker` manual checkpoint

The `Tasker/Locale UnlockSettings` interaction is exercised by a human:

1. Open the settings from Tasker / Locale.
2. Cancel without saving.
3. Re-open.
4. Change a value and save.
5. Re-open and confirm the previous value is restored.
6. Fire `FIRE_SETTING` and verify the action reaches the module.
7. Inspect the log for any `token`, `Bundle` or extra that was not redacted.

Record the result in `--manual-results`.

---

## 12. Interpreting `HookSummary`

The framework parses lines like:

```
[HookSummary] process=com.android.systemui stage=init installed=100 classMissing=0 memberMissing=0 failed=0 silentSkipped=0 dexkitFailed=0 dexkitNoMatch=0 prefsUnavailable=0
```

- `installed`          Number of hooks applied.
- `classMissing`       Hook target class not found in this ROM.
- `memberMissing`      Class exists, but the exact method/field is missing.
- `failed`             Hook threw an exception during install.
- `silentSkipped`      Hook skipped without an error.
- `dexkitFailed`       DexKit search failed.
- `dexkitNoMatch`      DexKit found no candidate.
- `prefsUnavailable`   Module preferences were not ready when the hook ran.

`classMissing` and `memberMissing` are often ROM-specific, but `failed > 0` is a
regression.  `prefsUnavailable > 0` is usually an order-of-initialization issue
rather than a hook crash.

---

## 13. PID and crash rules

- A `process_snapshot` step records PIDs.
- A `process_restart_observed` step compares before/after PIDs.
- A PID change for `system_server`, `SystemUI` or `Launcher` means the target
  restarted between snapshots.
- `logcat_assert` steps look for `FATAL EXCEPTION`, `WATCHDOG`, `system_server crash`,
  and `SystemUI` / `Launcher crash loop` markers.
- Any crash during a smoke run is treated as a failure.

---

## 14. Privacy redaction

The tool redacts before writing any report or evidence file:

- Raw device serials.
- Serial-like alphanumeric strings of 12+ characters.
- Tasker / Locale tokens and bundle keys.
- Long base64 strings.
- `SharedPreferences` and `Bundle` dumps.
- Keystore / password lines.
- User paths and account info in manual notes.

Do not paste unredacted logs into issues.  Always run `propose-evidence` and
review the generated proposal before sharing.

---

## 15. Exit codes

| Code | Meaning |
|------|---------|
| `0`  | Success.  All steps passed, or a `manual_checkpoint` is pending and the run was otherwise clean. |
| `1`  | Assertion / regression failure.  A step returned `FAIL` or a `manual_checkpoint` returned `FAIL`. |
| `2`  | Environment / input / safety error.  Invalid plan, missing adb, unsafe command, stale log, etc. |
| `3`  | Manual pending.  A `manual_checkpoint` step could not be completed automatically. |

A plan validation returns `2` for schema or safety errors, `1` for semantic
inconsistencies, `0` when valid.

---

## 16. APK signing boundary

Three signing classes are relevant to this workflow:

- **Debug**        Built locally with the Android debug keystore.  Useful for
  development and fake-adb runs.  Never distribute.
- **CI unsigned**  `assembleRelease` without a release keystore.  The build is
  zip-aligned and R8-shrunk, but the certificate is the debug / CI placeholder.
  Not installable over an official build.
- **Official**     Signed with the project release keystore held outside the
  repository.  This is the only APK that can replace a previous official release.

`verify-apk-signatures.py` checks the actual certificate.  A debug or unsigned
APK must not be reported as a candidate release.

---

## 17. CMD and PowerShell examples

### CMD

```cmd
python tools\adb-regression.py preflight --output build\adb-regression
python tools\adb-regression.py validate-plan --plan adb-regression\a14-smoke.json
python tools\adb-regression.py run --plan adb-regression\a14-smoke.json --serial %SERIAL%
python tools\adb-regression.py propose-evidence --report build\adb-regression\<run-id>\report.json --output build\adb-regression\<run-id>\proposal.json
```

### PowerShell

```powershell
$env:ADB_FAKE_STATE = "ok"
python tools/adb-regression.py run --plan adb-regression/a14-smoke.json --adb (Resolve-Path tools/tests/fixtures/fake_adb.py)
python tools/adb-regression.py validate-plan --plan adb-regression/a14-smoke.json
```

Use `Resolve-Path` or a full path on Windows.  Do not rely on shell quoting of
paths containing spaces.

---

## 18. Real device vs fake-adb

- **fake-adb** is a Python script under `tools/tests/fixtures/fake_adb.py`.  It
  uses `ADB_FAKE_STATE` and `FAKE_ADB_SCENARIO` to simulate devices, PIDs,
  packages, logcat and broadcast results.
- A fake run is always marked `simulation` and does not produce formal device
  evidence.
- A **real device** must be authorized, must be one of HyperOS 1 / MIUI 14 / AOSP
  on API 34, and must have the module package installed for the smoke plan.
- The same exit codes apply, but a real run can produce `VERIFIED` evidence
  proposals.

---

## 19. `DEVICE_VERIFIED` manual review rules

`propose-evidence` generates proposals with `reviewerStatus: PENDING_REVIEW` and a
current `evidenceConfidence`.  The framework **never** emits `DEVICE_VERIFIED` on
its own.

A reviewer may upgrade a proposal to `DEVICE_VERIFIED` only when **all** of these
hold:

1. The run used a real device, not `fake-adb`.
2. The `lsposed-log` was `VERIFIED` or live `adb logcat` contained markers.
3. All `manual_checkpoint` steps passed.
4. No `HookSummary` contains `failed > 0`.
5. No PID restart or crash markers are present.
6. The APK is an official signed build.
7. The build fingerprint, module version and feature scope match the proposal.

If any of these are missing, keep the original `evidenceConfidence` and add a
human review note instead of overriding it to `DEVICE_VERIFIED`.
