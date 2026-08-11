# C4-A0 — HideIconsSignal Architecture C Preflight

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**Base / Freeze SHA:** `e559df4d3381a8627641072eeed8f4dec1036aee`  
**Scope:** `SystemUIStatusBarHooks.HideIconsSignalHook` / `StatusBarMobileView.applyMobileState` / `StatusBarMobileView.updateState`  
**Type:** docs-only A0 preflight — no production, no test, no Resolver/ABI/Effect classes created.

---

## 0. START GATE

| Check | Result |
|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` |
| Local HEAD | `e559df4d3381a8627641072eeed8f4dec1036aee` |
| Remote HEAD | `e559df4d3381a8627641072eeed8f4dec1036aee` |
| Merge-base against `e559df4d...` | `e559df4d3381a8627641072eeed8f4dec1036aee` (HEAD itself) |
| Worktree | clean (`git status --short` = empty) |
| C1/C2/C3 production changed | `false` |
| C4 production started | `false` (no `StatusBarIconVisibilityResolver` / `Abi` / `Effect`; no `mods/statusbariconvisibility/` package) |

START PASS.

---

## 1. EXACT HOOK SURFACE

### 1.1 Current installation

`HideIconsSignalHook` uses `ModuleHelper.hookAllMethods` for both target methods:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt:2530-2565`

```kotlin
ModuleHelper.hookAllMethods(
    "com.android.systemui.statusbar.StatusBarMobileView",
    lpparam.classLoader,
    "applyMobileState",
    stateHook
)
ModuleHelper.hookAllMethods(
    "com.android.systemui.statusbar.StatusBarMobileView",
    lpparam.classLoader,
    "updateState",
    stateHook
)
```

`ModuleHelper.hookAllMethods(String, ClassLoader?, String, MethodHook)` delegates to `XposedHelpers.hookAllMethods(Class<?>, String, MethodHook)`, which uses `hookClass.getDeclaredMethods()` and matches by method name only:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt:301-316`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:894-900`

### 1.2 Frozen hook-surface contract

- `hookAllMethods` only considers methods **declared in the named class**. Inherited methods with the same name are **not** hooked by this call.
- All declared overloads named `applyMobileState` are hooked with the same `stateHook` callback.
- All declared overloads named `updateState` are hooked with the same `stateHook` callback.
- The single `before` callback assumes **arg0 exists** and is the `mobileIconState` object.
- If a hooked overload has no parameters, `param.getArg(0)` returns `null` and the subsequent field access throws; the `MethodHook` adapter catches the non-fatal exception and the original method continues.

### 1.3 Overload evidence

| Item | Evidence |
|---|---|
| `REAL_METHOD_OVERLOAD_SET` | **NOT_PROVEN**. `StatusBarIconVisibilityHotPathTest` calls `HideIconsSignalHook(fakePackageReadyParam())` in `setUp`, but the fake `PackageReadyParam` uses the system classloader, so `StatusBarMobileView` class resolution fails and no real methods are hooked. The test does not verify overload count or signatures. |

---

## 2. EXACT LEGACY CALLBACK ORACLE

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt:2530-2565`.

### 2.1 Frozen execution order

```text
1. mobileIconState = arg0

2. shouldUpdate = (member.name == "updateState")

3. if !shouldUpdate:
       mState = thisObject.mState  (XposedHelpers.getObjectField)
       shouldUpdate = (mState == null)

4. if !shouldUpdate:
       return  // adapter will call chain.proceed() with original args

5. snapshot = currentOrBuildStatusBarIconVisibilitySnapshot()

6. wifiAvailable = mobileIconState.wifiAvailable  (getBooleanField)

7. subId = mobileIconState.subId  (getObjectField, cast to Int)

8. dataSubId = SubscriptionManager.getActiveDataSubscriptionId()

9. slotId = SubscriptionManager.getSlotIndex(subId)

10. result = computeSignalIconHiding(wifiAvailable, subId, dataSubId, slotId, snapshot)

11. if result.visible == false:
        mobileIconState.visible = false
        return

12. if result.roaming != null:
        mobileIconState.roaming = false

13. if result.volte != null:
        mobileIconState.volte = false
        mobileIconState.speechHd = false
```

### 2.2 Frozen ordering rules

| Rule | Contract |
|---|---|
| `visible=false` early return | If `result.visible == false`, write `visible` and **immediately return**; no `roaming`/`volte`/`speechHd` writes. |
| `roaming` before `volte` | `roaming` is written before `volte` and `speechHd`. |
| `speechHd` only with `volte` | `speechHd` is written **only** when `result.volte != null`. |
| `applyMobileState` eligibility | For `applyMobileState`, the callback reads `thisObject.mState`. Mutation happens **only if `mState == null`**. If `mState` is non-null, the callback returns before any field writes. |
| `updateState` eligibility | For `updateState`, `shouldUpdate` is `true` from the start; the callback always mutates (subject to the visibility short-circuit). |
| Snapshot read timing | Snapshot is read **after** the eligibility (`shouldUpdate`) check. If the callback returns early, no snapshot is built/retrieved. |
| Chain proceed | `MethodHook` adapter calls `chain.proceed()` after `before` returns unless `returnAndSkip` or `throwAndSkip` is used. `HideIconsSignalHook` uses neither. |

### 2.3 Data-flow contract

- `mobileIconState` is **not** replaced; only its primitive fields are written.
- The original SystemUI method sees the mutated values after `chain.proceed()`.
- No new `MobileIconState` instance is allocated or substituted by the hook.

---

## 3. LEGACY FIELD ABI

`XposedHelpers.findField` starts at the **runtime class** of the object and recurses upward, so subclass-declared fields take precedence over superclass fields.

### 3.1 `StatusBarMobileView.mState` — READ

| Attribute | Contract / Evidence |
|---|---|
| Access site | `SystemUIStatusBarHooks.kt:2538` |
| Lookup starting runtime class | `thisObject.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` |
| Expected type | `Object` (current mobile-state instance, same type as arg0, or `null`) |
| Access semantics | Object read; compared with `null` |
| Null behavior | `mState == null` → `shouldUpdate = true`; `mState != null` → `shouldUpdate = false`, return early |
| Missing-field behavior | `NoSuchFieldError` → caught by `MethodHook.beforeHook`, no mutation |

### 3.2 `MobileIconState.wifiAvailable` — READ

| Attribute | Contract / Evidence |
|---|---|
| Access site | `SystemUIStatusBarHooks.kt:2544` |
| Lookup starting runtime class | `mobileIconState.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` |
| Expected type | `boolean` or `Boolean` |
| Access semantics | `XposedHelpers.getBooleanField` → `Field.getBoolean(obj)` |
| Null behavior | `mobileIconState == null` causes NPE in `findField`; caught |
| Wrong-type behavior | `IllegalArgumentException` if field not boolean-compatible; caught |
| Missing-field behavior | `NoSuchFieldError`; caught |

### 3.3 `MobileIconState.subId` — READ

| Attribute | Contract / Evidence |
|---|---|
| Access site | `SystemUIStatusBarHooks.kt:2545` |
| Lookup starting runtime class | `mobileIconState.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` |
| Expected type | `int` (autoboxed to `Integer` by `Field.get`) or `Integer` |
| Access semantics | `getObjectField` + `as Int` |
| Wrong-type behavior | `ClassCastException` from `as Int` if value is not `Integer`; caught |
| Missing-field behavior | `NoSuchFieldError`; caught |

### 3.4 `MobileIconState.visible` / `roaming` / `volte` / `speechHd` — WRITE

| Attribute | Contract / Evidence |
|---|---|
| Access sites | `SystemUIStatusBarHooks.kt:2551, 2555, 2558, 2559` |
| Lookup starting runtime class | `mobileIconState.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` for each |
| Expected type | `boolean` or `Boolean` |
| Access semantics | `XposedHelpers.setObjectField(obj, name, Boolean.FALSE)`; `Boolean.valueOf(false)` returns `Boolean.FALSE` singleton (no allocation) |
| Partial-mutation | If a write succeeds and a later write throws, earlier writes remain. |
| Missing-field / wrong-type | `NoSuchFieldError` / `IllegalArgumentException`; caught |

### 3.5 Field ABI summary

| Field | RW | Expected type |
|---|---|---|
| `StatusBarMobileView.mState` | R | `Object` (state or `null`) |
| `MobileIconState.wifiAvailable` | R | `boolean` / `Boolean` |
| `MobileIconState.subId` | R | `int` / `Integer` |
| `MobileIconState.visible` | W | `boolean` / `Boolean` |
| `MobileIconState.roaming` | W | `boolean` / `Boolean` |
| `MobileIconState.volte` | W | `boolean` / `Boolean` |
| `MobileIconState.speechHd` | W | `boolean` / `Boolean` |

---

## 4. SUBCLASS / SHADOWING CONTRACT

### 4.1 Legacy lookup

`XposedHelpers.findFieldRecursiveImpl` starts at `obj.getClass()` and walks upward:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:556-572`

This means a subclass-declared field hides a same-named superclass field for the same instance.

### 4.2 Fast-path eligibility

```text
FAST_PATH_ELIGIBILITY = exact runtime class match

For any receiver R and frozen Field F:
    if (R.javaClass === F.declaringClass) {
        use F directly
    } else {
        use legacy XposedHelpers get/set helpers
    }
```

```text
LEGACY_FALLBACK_REQUIRED = true
```

### 4.3 Behavior-preservation proof

Legacy `findField(R.javaClass, name)` returns the first matching field when walking from `R.javaClass` upward. If `R.javaClass === F.declaringClass`, the walk stops at `F.declaringClass` and returns `F`. If `R.javaClass` is a strict subclass, the walk first inspects the subclass. A frozen base-class `Field` used on a subclass instance would read/write the **base** field, while legacy `findField` would read/write the **subclass** field if it shadows the name. Therefore the fast path must require exact class equality, and any non-exact class must fall back to the legacy runtime-class-first lookup. This preserves shadowing semantics at the cost of conservative fast-path eligibility.

---

## 5. FAILURE / FATAL CONTRACT

### 5.1 Outer `MethodHook.beforeHook` contract

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt:203-215`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FatalErrors.kt:14-28`

Ordinary `Throwable` (including `Error` subtypes that are not `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError`) is logged and swallowed; the adapter calls `chain.proceed()`. Fatal errors are rethrown immediately.

### 5.2 Per-scenario contract

| Scenario | Legacy result | Frozen ABI contract |
|---|---|---|
| Missing `mState` | `NoSuchFieldError` caught, original method proceeds, no mutation | Fast-path ineligible → fallback; fast path with missing field must not occur |
| Missing `wifiAvailable` / `subId` / `visible` / `roaming` / `volte` / `speechHd` | `NoSuchFieldError` caught, no mutation (partial if earlier writes succeeded) | Same; fast path must write in same order to preserve partial mutation |
| `subId as Int` `ClassCastException` | Caught, no mutation | Fast path use `Field.get(obj) as Int` to keep identical exception type, or treat equivalent `IllegalArgumentException` as non-fatal |
| `Field.get/set IllegalAccessException` | `XposedHelpers` throws `IllegalAccessError` (non-fatal `Error`), caught | `Field` must be `setAccessible(true)` at resolve; any `IllegalAccessException` treated as ordinary, logged, fallback or continue |
| `Field.get/set IllegalArgumentException` | Caught as ordinary `Throwable` | Prevented by exact-class eligibility; if it occurs, treat as ordinary and fall back |
| Resolver expected miss | N/A | Return `null` ABI; effect uses legacy helpers |
| Resolver unexpected ordinary `Throwable` | N/A | Log once, return `null` ABI, use legacy |
| Fatal `Throwable` | Rethrown | Preserve fatal propagation; never catch `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError` |

### 5.3 Partial-mutation contract

The legacy callback may leave `visible` or `roaming` mutated if a later write throws. The Architecture C effect must not wrap writes in a transaction that rolls back earlier writes. Each write is an independent, non-atomic step.

---

## 6. CONFIG / PUBLICATION

The existing B3 snapshot/publication mechanism is reused.

### 6.1 Artifacts

- `StatusBarIconVisibilitySnapshot`: `SystemUIStatusBarHooks.kt:136-166`
- `StatusBarIconVisibilityRuntimeState`: `SystemUIStatusBarHooks.kt:262-310`
- `buildStatusBarIconVisibilitySnapshot` / `currentOrBuildStatusBarIconVisibilitySnapshot`: `SystemUIStatusBarHooks.kt:1974-2017`

### 6.2 Frozen contract

- **Relevant keys**: the 28 strings in `StatusBarIconVisibilityRuntimeState.relevantKeys`.
- **Rebuild behavior**: `observer.onChange(key)` returns if `key != null && key !in relevantKeys`; otherwise builds a new snapshot and `AtomicReference.set`s it.
- **Null-key behavior**: `null` triggers a full rebuild.
- **First snapshot**: lazy, on first `currentOrBuildStatusBarIconVisibilitySnapshot()` call.
- **Publication**: immutable `StatusBarIconVisibilitySnapshot` published through `AtomicReference<StatusBarIconVisibilitySnapshot?>`.
- **Invalidation**: in-flight callbacks continue with the snapshot they retrieved; only new callbacks see the updated snapshot.

### 6.3 Evidence

| Item | Evidence |
|---|---|
| `StatusBarIconVisibilitySnapshot` build | **RUNTIME_TESTED_COMPONENT** (`StatusBarIconVisibilityHotPathTest`) |
| `computeSignalIconHiding` | **RUNTIME_TESTED_COMPONENT** (`StatusBarIconVisibilityHotPathTest`) |
| `REAL preference observer auto-callback` | **NOT_RUNTIME_TESTED_CALLBACK** |

---

## 7. OWNERSHIP / LIFECYCLE

- `StatusBarIconVisibilityRuntimeState` is a process-scoped singleton holder.
- The preference observer is registered with the `created` runtime-state instance as owner: `SystemUIStatusBarHooks.kt:330-336`.
- The observer must not hold a `View`, `Context`, `Activity`, or short-lived controller.
- The future `Effect` should receive an **immutable captured reference** to the snapshot source, not create a new mutable process-global owner.
- `StatusBarMobileView` and `MobileIconState` instances are callback-local and must not be stored in static or long-lived effect state.

---

## 8. SUBSCRIPTION MANAGER — OUT OF SCOPE

The two calls remain unchanged and out of scope:

- `SystemUIStatusBarHooks.kt:2546-2547`

```kotlin
val dataSubId = SubscriptionManager.getActiveDataSubscriptionId()
val slotId = SubscriptionManager.getSlotIndex(subId)
```

- Do not cache.
- Do not resolve at install.
- Do not move thread.
- Do not reorder.

| Item | Evidence |
|---|---|
| `SUBSCRIPTION_MANAGER_INTERNAL_COST` | **NOT_PROVEN**. Whether these calls perform synchronous Binder IPC depends on the runtime and cannot be proven from the repository. |

---

## 9. TEST / EVIDENCE MATRIX

| Item | Evidence |
|---|---|
| `XposedHelpers` field-cache / additional-instance-field cost model | **STRUCTURAL** (`XposedHelpers.java:70-96`, `159-245`, `515-538`, `1360-1379`, `2010-2020`) |
| `MethodHook.beforeHook` failure / fatal contract | **STRUCTURAL** (`HookerClassHelper.kt:203-215`, `FatalErrors.kt:14-28`) |
| `ModuleHelper.hookAllMethods` → `XposedHelpers.hookAllMethods` | **STRUCTURAL** (`ModuleHelper.kt:301-316`, `XposedHelpers.java:894-900`) |
| `StatusBarIconVisibilitySnapshot` build & keys | **RUNTIME_TESTED_COMPONENT** |
| `computeSignalIconHiding` | **RUNTIME_TESTED_COMPONENT** |
| `StatusBarIconVisibilityRuntimeState` ownership | **STRUCTURAL** |
| `REAL_METHOD_OVERLOAD_SET` | **NOT_PROVEN** |
| `REAL_applyMobileState callback` | **NOT_RUNTIME_TESTED_CALLBACK** |
| `REAL_updateState callback` | **NOT_RUNTIME_TESTED_CALLBACK** |
| `REAL_HYPEROS_FIELD_OWNER` | **NOT_PROVEN** |
| `REAL_HYPEROS_FIELD_TYPE` | **NOT_PROVEN** |
| `CALLBACK_THREAD` | **NOT_PROVEN** |
| `REAL_CALLBACK_FREQUENCY` | **NOT_PROVEN** |
| `SUBSCRIPTION_MANAGER_INTERNAL_COST` | **NOT_PROVEN** |

---

## 10. SCOPE FREEZE

### 10.1 IN SCOPE

- `HideIconsSignalHook` callback oracle.
- `StatusBarMobileView.applyMobileState` hook.
- `StatusBarMobileView.updateState` hook.
- Fields: `mState`, `wifiAvailable`, `subId`, `visible`, `roaming`, `volte`, `speechHd`.
- Existing `StatusBarIconVisibilitySnapshot` and `StatusBarIconVisibilityRuntimeState`.
- Existing `computeSignalIconHiding` / `SignalIconHidingResult`.

### 10.2 OUT OF SCOPE

- `HideIconsHook` (`StatusBarIconControllerImpl.setIconVisibility`).
- `HideIconsFromSystemManager` (`CommandQueue.setIcon`).
- `DualRowSignalHook`, `MobileType`, and other signal hooks.
- Drawer blur (`doFrame`, `applyBlur`, `ControlPanelWindowManager.setBlurRatio`).
- `DetailedNetSpeed` / `NetSpeedStyle`.
- `SubscriptionManager` semantics or implementation.
- Preference key semantics changes.
- Forced icon refresh.
- C1, C2, C3 production and tests.

---

## 11. A0 OUTCOME

The preflight contract can be fully frozen:

- Hook surface, callback oracle, field ABI, and failure semantics are documented.
- A behavior-preserving fast-path contract (`exact runtime class match` + mandatory legacy fallback) is defined and justified.
- Config/publication and ownership are already in a reusable frozen state.
- `SubscriptionManager` is explicitly out of scope.
- All real-device/runtime evidence gaps are labeled `NOT_PROVEN` or `NOT_RUNTIME_TESTED_CALLBACK`.

No production code, test code, Resolver, ABI, or Effect has been created or modified.

C4_A0_PREFLIGHT_READY_FOR_INDEPENDENT_AUDIT
