# C5-B1 — VolumeDialogAutohideDelay Architecture C Implementation

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**C5-A0 freeze SHA:** `222af8f2cf2012a76e82427279db5954a7fbaf7c`  
**C5 target-selection freeze SHA:** `3c5cb8cca3cd08799097e534ffef2366a6504b59`  
**Scope:** `SystemUIControlCenterHooks.VolumeDialogAutohideDelayHook` → `com.android.systemui.miui.volume.MiuiVolumeDialogImpl.computeTimeoutH`  
**Type:** B1 production implementation.

---

## 1. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `222af8f2cf2012a76e82427279db5954a7fbaf7c` | `git rev-parse HEAD` |
| Remote HEAD | `222af8f2cf2012a76e82427279db5954a7fbaf7c` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Merge-base against `222af8f2...` | `222af8f2cf2012a76e82427279db5954a7fbaf7c` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` empty |
| C1/C2/C3/C4 production changed | `false` | no modifications in those phases |

START PASS.

---

## 2. IMPLEMENTATION SCOPE

### 2.1 Production files

| File | Purpose |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelaySnapshot.kt` | Immutable `expanded`/`collapsed` preference snapshot. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayAbi.kt` | Frozen cold-resolved ABI. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayResolver.kt` | Cold resolver. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayRuntimeState.kt` | Process-scoped runtime state and snapshot publication. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayEffect.kt` | FAST / LEGACY effect. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayHook.kt` | Thin hook installer. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | Wired to call `VolumeDialogAutohideDelayHook.install(classLoader)`. |

### 2.2 Test files

| File | Purpose |
|---|---|
| `app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayFixtures.java` | Structural test fixtures. |
| `app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayResolverTest.kt` | Resolver component tests. |
| `app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayRuntimeStateTest.kt` | Runtime state / publication component tests. |
| `app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayEffectTest.kt` | Effect / oracle / safety alias / failure component tests. |

### 2.3 Changed-file summary

```text
app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayAbi.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayEffect.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayHook.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayResolver.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayRuntimeState.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelaySnapshot.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayFixtures.java
app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayResolverTest.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayRuntimeStateTest.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/volumedialogautohide/VolumeDialogAutohideDelayEffectTest.kt
docs/architecture-c/C5_VOLUME_DIALOG_AUTOHIDE_B1.md
```

### 2.4 Scope freeze

- C1/C2/C3/C4 are not reopened.
- No changes to `BlurVolumeDialogBackgroundHook`, `DrawerBlurRatioHook`, `VolumeTimerValuesRes`, or other volume features.
- No shared preference infrastructure changes.

---

## 3. IMPLEMENTATION CHAIN

```text
Cold Resolve
    → VolumeDialogAutohideDelayResolver.resolve(classLoader)
        → VolumeDialogAutohideDelayAbi?

Typed Config
    → VolumeDialogAutohideDelayRuntimeState()
        → AtomicReference<VolumeDialogAutohideDelaySnapshot?> (initial null)
        → private val refreshLock = Any()
        → PreferenceObserver
        → initial refresh outside computeTimeoutH

Immutable Effect
    → VolumeDialogAutohideDelayEffect(abi, snapshotRef)

Thin Hook
    → VolumeDialogAutohideDelayHook.install(classLoader)
        → ModuleHelper.findAndHookMethod(
               "com.android.systemui.miui.volume.MiuiVolumeDialogImpl",
               classLoader,
               "computeTimeoutH",
               hook
           )

Mode Select (before any FAST field access)
    → abi != null
    → thisObject != null
    → thisObject.javaClass === abi.resolutionRootClass
    → snapshotRef.get() != null

Hot Execute
    → mHovering = mHoveringField.getBoolean(thisObject)
    → if mHovering: returnAndSkip(16000)
    → mSafetyWarning = LEGACY_SAFETY_ALIAS_READ(thisObject)
        (Strategy A: exact existing XposedHelpers try/catch block)
    → if mSafetyWarning:
        opt = snapshot.expanded
        returnAndSkip(opt > 0 ? opt : 5000)
    → mExpanded = mExpandedField.getBoolean(thisObject)
    → opt = if (mExpanded) snapshot.expanded else snapshot.collapsed
    → if opt > 0: returnAndSkip(opt)
    → otherwise fall through to original computeTimeoutH

Legacy
    → complete original callback oracle using XposedHelpers and MainModule.mPrefs
```

---

## 4. RESOLVER

- Resolution root: `com.android.systemui.miui.volume.MiuiVolumeDialogImpl`.
- Verifies the zero-explicit-parameter `computeTimeoutH` method surface with `XposedHelpers.findMethodExactIfExists`, without inspecting or filtering `Method.returnType`.
- Resolves `mHovering` and `mExpanded` recursively from the root with `XposedHelpers.findField` and requires `field.type == java.lang.Boolean.TYPE`.
- Inherited primitive-boolean fields are allowed.
- Wrapper `java.lang.Boolean` fields reject the ABI.
- Missing method or field returns `null` ABI.
- Ordinary resolver failure returns `null` and logs.
- Fatal errors (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) propagate via `FatalErrors.unwrapAndRethrowIfFatal`.

---

## 5. ABI

```kotlin
internal class VolumeDialogAutohideDelayAbi(
    val resolutionRootClass: Class<*>,
    val mHoveringField: Field,
    val mExpandedField: Field,
)
```

- Immutable and install-time only.
- Contains no `View`, `Window`, `Context`, `Activity`, `MiuiVolumeDialogImpl` instance, `ClassLoader`, or runtime instance cache.
- `mHoveringField` and `mExpandedField` are set accessible on the cold path.
- Safety alias fields (`mIsSafetyShowing`, `mSafetyWarning`) are intentionally **not** in the ABI.

---

## 6. SNAPSHOT AND PUBLICATION

```kotlin
data class VolumeDialogAutohideDelaySnapshot(
    val expanded: Int,
    val collapsed: Int,
)
```

- Publication: `AtomicReference<VolumeDialogAutohideDelaySnapshot?>`.
- Initial value: `null`.
- Built from a single captured `MainModule.mPrefs.getAll()` generation:
  ```kotlin
  val source = MainModule.mPrefs.getAll()
  val expanded = source[EXPANDED_KEY] as? Int ?: 0
  val collapsed = source[COLLAPSED_KEY] as? Int ?: 0
  ```
- No two independent `MainModule.mPrefs.getInt` calls.

---

## 7. RUNTIME STATE / OWNERSHIP

- `VolumeDialogAutohideDelayRuntimeState` is process-scoped.
- It owns:
  - `AtomicReference<VolumeDialogAutohideDelaySnapshot?>`
  - `private val refreshLock = Any()`
  - `PreferenceObserver`
- It does **not** own `View`, `Window`, `Context`, `Activity`, `MiuiVolumeDialogImpl`, or per-dialog cache.
- Installation protocol:
  1. `snapshotRef` starts `null`.
  2. Register process-scoped `PreferenceObserver`.
  3. Perform initial refresh **outside** `computeTimeoutH`.
  4. Initial refresh and observer refresh both synchronize on `refreshLock`.
- The `computeTimeoutH` callback never acquires `refreshLock`; it only calls `snapshotRef.get()`.
- `install()` is idempotent: repeated calls return the same instance and do not register duplicate observers.

---

## 8. OBSERVER

- Relevant keys: `system_volumedialogdelay_expanded`, `system_volumedialogdelay_collapsed`.
- `key == null` → rebuild.
- Relevant non-null key → rebuild.
- Irrelevant non-null key → return without rebuild.
- Uses `ModuleHelper.observePreferenceChange` (process-scoped).
- `PreferenceObserver.onChange` body is wrapped in `ModuleHelper.guarded`.
- Shared preference infrastructure (`PreferenceBootstrap`, `PreferenceObserverRegistry`, `PrefMap`, `ModuleHelper`) is not modified.

---

## 9. REFRESH FAILURE

```kotlin
private fun refreshSnapshotLocked() {
    try {
        val source = refreshSource()
        val snapshot = VolumeDialogAutohideDelaySnapshot(
            expanded = source[EXPANDED_KEY] as? Int ?: 0,
            collapsed = source[COLLAPSED_KEY] as? Int ?: 0,
        )
        snapshotRef.set(snapshot)
    } catch (t: Throwable) {
        snapshotRef.set(null)
        FatalErrors.rethrowIfFatal(t)
        XposedHelpers.log(t)
    }
}
```

- Existing snapshot is cleared **before** returning on any `Throwable`.
- Ordinary failure: snapshot becomes `null`; next eligible callback uses `COMPLETE_LEGACY`.
- Fatal failure: snapshot is cleared, then `OutOfMemoryError`/`ThreadDeath`/`VirtualMachineError` is rethrown from the C5 refresh boundary.
- `refreshSource()` is captured inside `refreshLock` so a waiting observer refresh reads the current `PrefMap` generation.

---

## 10. EFFECT MODE SELECTION AND FAST EXECUTION

- Callback entry: `thisObject = param.getThisObject()`.
- Mode selected before any FAST `Field` operation.
- FAST eligibility:
  ```text
  abi != null
  thisObject != null
  thisObject.javaClass === abi.resolutionRootClass
  snapshotRef.get() != null
  ```
- If any requirement fails: `COMPLETE_LEGACY`.
- FAST execution order:
  1. `mHovering = mHoveringField.getBoolean(thisObject)`
  2. if `mHovering`: `returnAndSkip(16000)`
  3. `mSafetyWarning = readSafetyWarning(thisObject)` — exact legacy XposedHelpers block
  4. if `mSafetyWarning`: `opt = snapshot.expanded`; `returnAndSkip(if (opt > 0) opt else 5000)`
  5. `mExpanded = mExpandedField.getBoolean(thisObject)`
  6. `opt = if (mExpanded) snapshot.expanded else snapshot.collapsed`
  7. if `opt > 0`: `returnAndSkip(opt)`
  8. otherwise fall through
- No `MainModule.mPrefs` access inside FAST.

---

## 11. COMPLETE LEGACY

The complete legacy oracle is preserved in `VolumeDialogAutohideDelayEffect.processLegacy`:

```kotlin
val mHovering = XposedHelpers.getBooleanField(thisObject, "mHovering")
if (mHovering) { param.returnAndSkip(16000); return }

val mSafetyWarning = try {
    XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
} catch (e: Throwable) {
    FatalErrors.rethrowIfFatal(e)
    XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
}

if (mSafetyWarning) {
    val opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
    param.returnAndSkip(if (opt > 0) opt else 5000)
    return
}

val mExpanded = XposedHelpers.getBooleanField(thisObject, "mExpanded")
val opt = MainModule.mPrefs.getInt(
    if (mExpanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed",
    0,
)
if (opt > 0) param.returnAndSkip(opt)
```

---

## 12. SAFETY ALIAS

- `mIsSafetyShowing` and `mSafetyWarning` are **not** resolved into the ABI.
- The exact legacy block is retained:
  ```kotlin
  try {
      XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
  } catch (e: Throwable) {
      FatalErrors.rethrowIfFatal(e)
      XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
  }
  ```
- Primary success → no fallback.
- Primary ordinary failure → fallback.
- Primary fatal failure → no fallback, propagate.
- Fallback ordinary failure → propagates to `MethodHook.beforeHook` boundary.
- Fallback fatal → propagate.

---

## 13. FAST FIELD FAILURE

- `IllegalAccessException` from `Field.getBoolean` is mapped to `IllegalAccessError` and rethrown.
- `IllegalArgumentException` from an invalid receiver propagates; no legacy retry is attempted.
- Any other ordinary FAST failure propagates; `MethodHook.beforeHook` logs and lets the original method proceed if `returnAndSkip` has not been called.
- Fatal errors propagate.
- No retry through `XposedHelpers` after FAST begins.

---

## 14. HOT-PATH PROHIBITIONS

Eligible FAST callback must not perform:

- `MainModule.mPrefs.getInt`
- `MainModule.mPrefs.getAll`
- field-name resolution for `mHovering` or `mExpanded`
- `findField` / `findFieldIfExists`
- `ClassLoader` lookup
- resolver invocation
- `refreshLock` acquisition
- snapshot rebuild
- coroutine / `Flow` / per-instance cache

Allowed:

- `AtomicReference.get`
- `Field.getBoolean`
- safety alias `XposedHelpers` calls

---

## 15. TEST COVERAGE

### 15.1 Resolver

- exact root resolves primitive `mHovering`/`mExpanded`
- inherited primitive fields resolve
- wrapper `Boolean` `mHovering` rejects
- wrapper `Boolean` `mExpanded` rejects
- missing `mHovering` rejects
- missing `mExpanded` rejects
- missing `computeTimeoutH` rejects
- no return-type filtering (string return accepted)
- ordinary resolver failure returns `null`
- fatal resolver failure propagates

### 15.2 Runtime state / snapshot

- initial snapshot `null` before initialization
- one `getAll()` source builds both values
- type mismatch → `0` for that value
- relevant expanded key refresh
- relevant collapsed key refresh
- irrelevant key ignored
- `null` key refresh
- no callback-time lazy build
- ordinary refresh failure clears previous snapshot
- fatal refresh clears then rethrows
- initial/observer refresh serialized
- `install()` returns same process singleton

### 15.3 Effect FAST oracle

- hovering `true` → `16000`
- hovering `true` does not touch safety
- safety `true` / expanded `> 0`
- safety `true` / expanded `<= 0` → `5000`
- safety `true` does not read `mExpanded`
- safety `false` / expanded `true` `> 0`
- safety `false` / collapsed `> 0`
- `opt <= 0` → original fallthrough

### 15.4 Safety alias

- primary `true`
- primary `false`
- primary `null` cast failure → fallback
- primary missing field → fallback
- fallback success

### 15.5 Mode selection

- `abi == null` → complete legacy
- `thisObject == null` → complete legacy
- subclass runtime object → complete legacy
- snapshot `null` → complete legacy

### 15.6 Failure

- `IllegalAccessException` mapped to `IllegalAccessError`
- `IllegalArgumentException` propagates and `returnAndSkip` does not happen
- no legacy retry after FAST begins

---

## 16. EVIDENCE MATRIX

| Item | Classification | Source / note |
|---|---|---|
| `VolumeDialogAutohideDelayResolver.resolve` exact root | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver.resolve` inherited fields | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver` wrapper/primitive enforcement | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver` no return-type filtering | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver` fatal propagation | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayRuntimeState` initial `null` snapshot | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` one `getAll` source | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` refresh serialization | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` refresh failure clears snapshot | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` install singleton | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayEffect` FAST oracle | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` LEGACY oracle | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` mode selection | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` safety alias | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` `IllegalAccessException` mapping | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` `IllegalArgumentException` propagation | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| Real `computeTimeoutH` return type | `NOT_PROVEN` | no real ROM/device/binary evidence |
| Real `computeTimeoutH` overload set | `NOT_PROVEN` | only zero parameter types supplied to hook |
| Real `computeTimeoutH` callback thread | `NOT_PROVEN` | source does not prove the invoking thread |
| Real `computeTimeoutH` callback frequency | `NOT_PROVEN` | no real-device timing |
| Real HyperOS field type for `mHovering` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss |
| Real HyperOS field type for `mExpanded` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss |
| Real safety-field prevalence (`mIsSafetyShowing` vs `mSafetyWarning`) | `NOT_PROVEN` | both field names are candidates |
| Real preference observer callback execution | `NOT_RUNTIME_TESTED_CALLBACK` | no real callback timing/thread evidence |
| Start gate / validation commands | `LOCAL_EXECUTION_EVIDENCE_ONLY` | executed locally during B1 |
| Focused unit test results | `LOCAL_EXECUTION_EVIDENCE_ONLY` | executed with `python tools/verify.py` locally |

---

## 17. VALIDATION

| Command | Result | Evidence |
|---|---|---|
| `git diff --check` | pass | exit `0` |
| `python tools/check_document_contracts.py` | pass | exit `0` |
| `python tools/verify.py full` | pass | exit `0` |
| Focused unit tests | pass | see section 15 |

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 18. SUBMISSION FIELDS

| Field | Value |
|---|---|
| Base SHA | `222af8f2cf2012a76e82427279db5954a7fbaf7c` |
| Final SHA | `5a9c61c26f6fa1eb69cba47b23d9039a26264021` |
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Production changed | `true` |
| Tests changed | `true` |
| Docs changed | `true` |

---

C5_B1_READY_FOR_INDEPENDENT_AUDIT
