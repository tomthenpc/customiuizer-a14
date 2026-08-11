# C3-A0 — Battery Style Architecture C Preflight

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**Base / Freeze SHA:** `b37596d7a9841c2980c3a38d93c4f8e190a2a92c`  
**Scope:** `SystemUIBatteryHooks.StatusBarStyleBatteryIconHook` / `MiuiBatteryMeterView.updateAll` / Battery style  
**Type:** docs-only A0 preflight — no production, no test, no Resolver/ABI/Effect classes created.

---

## 0. START GATE

| Check | Result |
|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` |
| Local HEAD | `b37596d7a9841c2980c3a38d93c4f8e190a2a92c` |
| Remote HEAD | `b37596d7a9841c2980c3a38d93c4f8e190a2a92c` |
| Worktree | clean (`git status --short` = empty) |
| HEAD == frozen base | `true` |
| C1 production changed | `false` (no diff against `7fb1ca5d...` in `statusbarheight/` or `SystemStatusBarInsetsHooks.kt`) |
| C2 production changed | `false` (no diff against `92d717ec...` in `SystemClockHooks.kt` or `clock/`) |
| C3 production started | `false` (no `BatteryStyleResolver` / `BatteryStyleAbi` / `BatteryStyleEffect` files; no `mods/batterystyle/` package) |

START PASS.

---

## 1. LEGACY CALLBACK / FAILURE ORACLE

### 1.1 Current hook shape

```kotlin
// app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt:198-209
fun StatusBarStyleBatteryIconHook(lpparam: PackageReadyParam) {
    installBatteryStyleSnapshot()
    ModuleHelper.findAndHookMethod(
        "com.android.systemui.statusbar.views.MiuiBatteryMeterView",
        lpparam.classLoader,
        "updateAll",
        object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val style = batteryStyle ?: return
                val owner = param.getThisObject() ?: return
                val batteryView = owner as? ViewGroup ?: return
                val state = getOrCreateBatteryViewState(owner)
                reconcileBatteryView(batteryView, style, state)
            }
        })
}
```

**Frozen fact:** this is an `after(AfterHookCallback)` override.

**Frozen fact:** the Battery hook body itself **does not call `chain.proceed()`**.

### 1.2 MethodHook adapter exact graph

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt:166-201` and `:217-229`.

```text
MethodHook.intercept
  ├─ if (mIsReturnConstant) return constant  // not used here
  ├─ val before = BeforeHookCallback(chain)
  ├─ beforeHook(before)                      // Battery: before() is empty
  ├─ if (!before.skipped) {
  │      try {
  │          result = chain.proceed()        // exactly one call
  │      } catch (t: Throwable) {
  │          throwable = t
  │      }
  │  }
  ├─ if (hasAfterCallback()) {
  │      val after = AfterHookCallback(before, result, throwable)
  │      afterHook(after)                    // calls Battery after()
  │      if (after.throwable != null) throw after.throwable
  │      return after.result
  │  }
  ├─ if (throwable != null) throw throwable
  └─ return result
```

### 1.3 Throwable precedence oracle

`beforeHook()` / `afterHook()` catch block (`HookerClassHelper.kt:217-229`):

```text
catch (oom: OutOfMemoryError) { throw oom }
catch (td: ThreadDeath)     { throw td }
catch (vme: VirtualMachineError) { throw vme }
catch (t: Throwable)        { XposedHelpers.log(t) /* non-fatal: logged and swallowed */ }
```

**Fatal categories exactly:** `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`.

**Precedence table:**

| Scenario | Result |
|---|---|
| Original `updateAll` succeeds, Battery `after` succeeds | Adapter returns `after.result` (which is `result` unless `setResult` called). |
| Original succeeds, Battery `after` throws ordinary `Throwable` | `afterHook` logs and swallows it. **Original result is still returned.** |
| Original succeeds, Battery `after` throws fatal `Error`/`ThreadDeath` | `afterHook` rethrows it immediately. |
| Original succeeds, Battery `after` calls `setThrowable(t)` | `after.throwable` becomes `t`; `t` is thrown after `afterHook`. |
| Original throws, Battery `after` still executes | `AfterHookCallback` is constructed with original `throwable`; `afterHook` runs. |
| Original throws, Battery `after` does nothing to throwable | Original `throwable` is rethrown by adapter. |
| Original throws, Battery `after` calls `setThrowable(t)` | `t` is thrown, replacing original. |
| Original throws, Battery `after` throws ordinary `Throwable` | `afterHook` logs it and swallows. **Original throwable is still rethrown.** |
| Original throws, Battery `after` throws fatal | Callback fatal rethrown immediately, original throwable lost. |

### 1.4 A0 decision

```text
CALLBACK_SHAPE = PRESERVE_AFTER
```

Architecture C C3 migration must **preserve** the `after(AfterHookCallback)` shape. It must not be rewritten into a C2-style `intercept` that manually calls `chain.proceed()`.

---

## 2. BATTERYSTYLE CONFIG / PUBLICATION FREEZE

### 2.1 `BatteryStyle` is a plain immutable class

```kotlin
// SystemUIBatteryHooks.kt:52-62
internal class BatteryStyle(
    val swap: Boolean,
    val fontSizeDp: Float,
    val markFontSizeDp: Float,
    val bold: Boolean,
    val leftMarginDp: Float,
    val rightMarginDp: Float,
    val verticalOffset: Int,
    val markVerticalOffset: Int,
    val battery4: Boolean,
)
```

- Not a `data class`.
- No `equals()` / `hashCode()` override.
- All fields are `val` and immutable (primitives + one `Boolean`).

### 2.2 `state.appliedStyle != style` uses identity equality

```kotlin
// SystemUIBatteryHooks.kt:232
state.appliedStyle != style || !matchesTarget(parent, newBaseline, style)
```

Because `BatteryStyle` does not override `equals()`, `!=` is reference (identity) inequality.

**Legacy behavior oracle:** `reconcileBatteryView` uses identity of the `BatteryStyle` instance to short-circuit `matchesTarget(...)`. When the preference observer calls `batteryStyle = readBatteryStyle()`, a **new** `BatteryStyle` instance is created; this makes `state.appliedStyle != style` true and forces re-apply even if the field values are the same. This is intentional and observable.

### 2.3 Current publication

```kotlin
// SystemUIBatteryHooks.kt:64-65
@Volatile
internal var batteryStyle: BatteryStyle? = null
```

```kotlin
// SystemUIBatteryHooks.kt:70-97
internal fun readBatteryStyle(): BatteryStyle { ... }

internal fun installBatteryStyleSnapshot() {
    batteryStyle = readBatteryStyle()
    ...
    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) = ModuleHelper.guarded {
            if (key == null || key in BATTERY_STYLE_KEYS) {
                batteryStyle = readBatteryStyle()
            }
        }
    })
}
```

- `batteryStyle` is `@Volatile`.
- `BatteryStyle` object is immutable.
- Writer (observer) creates a new instance and publishes the reference.
- Reader (after callback) reads the reference and sees a fully-constructed instance.

### 2.4 A0 decision

```text
BATTERY_STYLE_PUBLICATION = STRUCTURALLY_SAFE_VOLATILE_IMMUTABLE
STYLE_EQUALITY = IDENTITY
CONFIG_MODEL = REUSE_EXISTING_VOLATILE_IMMUTABLE_BATTERY_STYLE
```

A0 default conclusion: **do not** add `BatteryStyleConfig`, `AtomicReference`, `generation` counter, `State` wrapper, or custom `equals`/`hashCode`. The existing volatile reference to an immutable instance is structurally safe and the identity-equality short-circuit is an observable legacy behavior that must be preserved.

---

## 3. BATTERY VIEW STATE / OWNERSHIP

### 3.1 `BatteryViewState`

```kotlin
// SystemUIBatteryHooks.kt:270-273
internal data class BatteryViewState(
    var baseline: BatteryBaseline? = null,
    var appliedStyle: BatteryStyle? = null
)
```

```kotlin
// SystemUIBatteryHooks.kt:275-282
internal fun getOrCreateBatteryViewState(owner: Any): BatteryViewState {
    var state = XposedHelpers.getAdditionalInstanceField(owner, "customiuizer_battery_view_state") as? BatteryViewState
    if (state == null) {
        state = BatteryViewState()
        XposedHelpers.setAdditionalInstanceField(owner, "customiuizer_battery_view_state", state)
    }
    return state
}
```

- `BatteryViewState` is **per-view mutable state**.
- Bound to the `MiuiBatteryMeterView` owner via `XposedHelpers.setAdditionalInstanceField`.

### 3.2 additional-instance implementation evidence

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:159-260`.

```text
- WeakInstanceKey extends WeakReference<Object> implements InstanceKey
  - hash = System.identityHashCode(target)
  - equals by identity: mine != null && mine == other.target()
- ReferenceQueue<Object> additionalFieldsQueue
- ConcurrentHashMap<InstanceKey, ConcurrentHashMap<String, Object>> additionalFields
- ThreadLocal<LookupInstanceKey> additionalFieldProbe (reused per thread, released after lookup)
- expungeStaleAdditionalFields() called on write paths
```

### 3.3 Evidence classification

| Evidence | Classification |
|---|---|
| `WeakInstanceKey` / `ReferenceQueue` / identity-key / `ConcurrentHashMap` / `ThreadLocal` probe implementation | **STRUCTURAL** |
| `XposedHelpersAbiTest.weakIdentityKeyIsClearedByGc` (`app/src/test/.../XposedHelpersAbiTest.kt:145-162`) | **RUNTIME_TESTED_COMPONENT** |
| Real HyperOS / ART `MiuiBatteryMeterView` lifecycle and GC timing | **NOT_PROVEN** |
| Real `updateAll` callback thread | **NOT_PROVEN** |
| `BatteryViewState` single-thread confinement | **NOT_PROVEN** |
| Concurrent access to `BatteryViewState` | **NOT_PROVEN** |

### 3.4 A0 decision

```text
OWNER_MODEL = EXISTING_WEAK_IDENTITY_ADDITIONAL_INSTANCE
VIEW_STATE = PER_VIEW_MUTABLE_STATE
CALLBACK_THREAD = NOT_PROVEN
SINGLE_THREAD_CONFINEMENT = NOT_PROVEN
CONCURRENT_ACCESS = NOT_PROVEN
```

A0 does **not** claim the view state is thread-safe or that the callback is single-threaded. The existing weak-identity additional-instance map is structurally sound and has component-level runtime evidence, but real SystemUI lifecycle remains unproven.

---

## 4. EXACT LEGACY FIELD ABI

### 4.1 Three fields only

The current production code references exactly these three fields:

```text
mBatteryTextDigitView
mBatteryPercentView
mBatteryPercentMarkView
```

All in `SystemUIBatteryHooks.kt:296-434` (`captureBatteryBaseline`, `matchesBaseline`, `matchesTarget`, `restoreBatteryBaseline`, `applyBatteryStyle`).

### 4.2 Declaring class expectation

```kotlin
ModuleHelper.findAndHookMethod(
    "com.android.systemui.statusbar.views.MiuiBatteryMeterView",
    ...
)
```

So the declaring class is expected to be `com.android.systemui.statusbar.views.MiuiBatteryMeterView` or a subclass. `XposedHelpers.findField` will search the class hierarchy (`getDeclaredField` on each superclass up to but not including `Object.java`) if the field is not declared directly.

### 4.3 Field type expectation

All three are expected to be assignable to `TextView`:

```kotlin
val digitView = XposedHelpers.getObjectField(owner, "mBatteryTextDigitView") as? TextView ?: return null
```

### 4.4 Missing / wrong / null behavior

| Case | Legacy behavior |
|---|---|
| Field does not exist | `XposedHelpers.getObjectField` -> `XposedHelpers.findField` -> `findFieldRecursiveImpl` -> throws `NoSuchFieldError`. This `Error` is not `OOM`/`ThreadDeath`/`VirtualMachineError`, so `MethodHook.afterHook` catches it as ordinary `Throwable`, `XposedHelpers.log(t)` records it, and the after callback returns. The view is not modified. |
| Field exists but value is `null` | `as? TextView` gives `null`; `?: return null` / `?: return false` / `?: return` short-circuits the helper. No exception. View not modified. |
| Field exists but not `TextView` | `as? TextView` gives `null`; same short-circuit. No exception. View not modified. |
| Field exists but inaccessible | `findField` calls `setAccessible(true)`. If that or `Field.get` fails with `IllegalAccessException`, `getObjectField` throws `IllegalAccessError`; `afterHook` catches and logs. View not modified. |

### 4.5 `findField` cache

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:515-533`.

```text
findField(clazz, fieldName):
  - fieldCache lookup per (class, fieldName)
  - first miss: findFieldRecursiveImpl scans class hierarchy and caches the Field
  - steady state: ConcurrentHashMap lookup + cached Field
```

Therefore the legacy debt is:

```text
CACHED_GENERIC_XPOSED_FIELD_LOOKUP + Field.get
```

It is **not** a full hierarchy scan per callback after the first resolution.

### 4.6 A0 decision

```text
FIELD_ABI = EXACT_3_FIELDS_ONLY
FIELD_ALIASES = NONE_WITHOUT_EVIDENCE
HOT_PATH_GENERIC_FIELD_LOOKUP = REMOVE_IN_B1
```

No extra aliases such as `batteryTextDigitView`, `mPercentView`, `batteryPercentView` may be added unless repository/ROM evidence is provided.

---

## 5. CURRENT FAILURE SEMANTICS OF FIELD ACCESS

### 5.1 Failure oracle

```text
CURRENT_MISSING_FIELD_FAILURE =
  NoSuchFieldError thrown by XposedHelpers.findField
  -> MethodHook.afterHook catches as ordinary Throwable
  -> XposedHelpers.log(t) records it
  -> after callback aborts
  -> adapter returns original result / rethrows original throwable
  -> feature does not crash SystemUI; view not modified

CURRENT_WRONG_FIELD_TYPE_FAILURE =
  getObjectField returns non-TextView object
  -> as? TextView becomes null
  -> ?: return null / ?: return false / ?: return
  -> helper short-circuits silently
  -> no exception, no view change

CURRENT_NULL_FIELD_VALUE_FAILURE =
  getObjectField returns null
  -> same as wrong type: as? TextView null, short-circuit

CURRENT_AFTER_CALLBACK_FAILURE_POLICY =
  - Ordinary Throwable in after callback: logged and swallowed; original result/throwable preserved.
  - Fatal Error (OOM / ThreadDeath / VirtualMachineError) in after callback: rethrown immediately.
```

### 5.2 Compatibility requirement for cold resolver

The B1 cold resolver must not change the observable failure semantics:

- Missing field **must not** become a SystemUI startup crash or process fatal.
- Missing field **must not** cause the `StatusBarStyleBatteryIconHook` to throw at install time in a way that the legacy would not.
- Wrong type / null value **must remain** a silent no-op for the affected view.

### 5.3 Recommended resolver failure policy

```text
RESOLVER_FAILURE_POLICY = FEATURE_LOCAL_DISABLE
```

Rationale:

- `ModuleHelper.findAndHookMethod` already returns `null` and logs once when the target class is missing (`HookInstallerFacade.kt:27-40`).
- For field-level failure (class exists but one or more child fields missing), the B1 resolver should resolve the three fields at install time. If any field is missing or of the wrong type, it should record the failure once and **not install the after hook** (or install an empty no-op). This preserves the legacy end-user observable behavior: Battery style is silently disabled for that ROM, no crash, no repeated exception logs on every `updateAll`.
- `STRICT` fail-fast is rejected because it would turn a legacy swallowed `NoSuchFieldError` into an install-time failure.
- `DEFERRED/LAZY` resolve is unnecessary because `MiuiBatteryMeterView` is known at `findAndHookMethod` time; deferred resolution would re-introduce per-call reflection on the first `updateAll`.

---

## 6. RESOLVER DESIGN — PREFLIGHT ONLY

### 6.1 Concept names (proposal only, no files created)

- `BatteryStyleResolver` — cold class/field metadata resolution.
- `BatteryStyleAbi` — frozen `Class<*>`, three `Field` references.
- `BatteryStyleEffect` — hot helper methods using the frozen `Field`s.

These are **only** conceptual placeholders for B1.

### 6.2 Resolver responsibilities

```text
BatteryStyleResolver.resolve(classLoader) -> BatteryStyleAbi?
  1. XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiBatteryMeterView", classLoader)
  2. For each of [mBatteryTextDigitView, mBatteryPercentView, mBatteryPercentMarkView]:
       - resolve Field via findFieldIfExists or guarded findField
       - verify type is assignable to TextView
  3. If any step fails, return null and log once.
  4. Return BatteryStyleAbi with three frozen Field references.
```

The resolver must **not** hold:
- `View`
- `Context`
- `MiuiBatteryMeterView` instance
- `BatteryViewState`
- preference state

It may hold `ClassLoader` only during resolution and then only the resolved metadata.

### 6.3 Failure strategy (reiterated)

```text
RESOLVER_FAILURE_POLICY = FEATURE_LOCAL_DISABLE
```

If resolution fails, `StatusBarStyleBatteryIconHook` should skip installing the after hook (equivalent to legacy disabled feature), preserving the no-crash observable semantics.

---

## 7. EFFECT BOUNDARY

### 7.1 Minimal scope

The effect is **not** a Battery feature rewrite. It only replaces the three recurring `XposedHelpers.getObjectField(...)` calls with frozen `Field.get(...)` calls.

### 7.2 Proposed effect boundary

```text
BatteryStyleEffect(abi: BatteryStyleAbi) {
    - readDigitView(parent)
    - readPercentView(parent)
    - readMarkView(parent)
    - captureBatteryBaseline(parent)      // uses frozen Fields
    - matchesBaseline(parent, baseline)
    - matchesTarget(parent, baseline, style)
    - applyBatteryStyle(parent, baseline, style)
    - restoreBatteryBaseline(parent, baseline)
}
```

Each method internally uses `abi.digitField.get(parent) as? TextView` etc.

### 7.3 Allocation policy

- **No hot-path `Pair` / `Triple` / `List` / `Map` / `Sequence` / `Flow` / coroutine.**
- **No per-`updateAll` child-view holder allocation.**
- Each helper call may call `Field.get` up to 3 times, matching the legacy `getObjectField` count but removing the `ConcurrentHashMap` cache lookup.
- The existing `BatteryBaseline` (capture on child change) and `BatteryViewState` are retained.

### 7.4 Helpers to migrate

| Helper | Action in B1 |
|---|---|
| `captureBatteryBaseline` | Migrate 3 `getObjectField` to frozen `Field.get`. |
| `matchesBaseline` | Migrate 3 `getObjectField` to frozen `Field.get`. |
| `matchesTarget` | Migrate 3 `getObjectField` to frozen `Field.get`. |
| `applyBatteryStyle` | Migrate 3 `getObjectField` to frozen `Field.get`. |
| `restoreBatteryBaseline` | Migrate 3 `getObjectField` to frozen `Field.get`. |
| `reconcileBatteryView` | Keep control flow; call `effect` methods. |
| `applyBatteryChildSwapIfNeeded` / `moveChildTo` / `restoreChildOrder` | Keep unchanged. |
| `isBatteryStyleDefault` / `expectedTextSize` / `dipToPx` | Keep unchanged. |
| `setTextSizeIfChanged` / `setTextSizePxIfChanged` / `setTypefaceIfChanged` / `setPaddingRelativeIfChanged` | Keep unchanged. |

---

## 8. EXACT HOT-PATH BEHAVIOR ORACLE

Source: `SystemUIBatteryHooks.kt:211-237`.

```text
reconcileBatteryView(parent, style, state):
  1. baseline = state.baseline
  2. childrenChanged = baseline == null || childIdentitiesChanged(parent, baseline.childIds)
  3. newBaseline = when {
       baseline == null -> capture(parent)
       childrenChanged -> capture(parent)
       state.appliedStyle == null && !matchesBaseline(parent, baseline) -> capture(parent)
       else -> baseline
     }
  4. if (newBaseline == null) return
  5. state.baseline = newBaseline
  6. defaultStyle = isBatteryStyleDefault(style)
  7. when {
       defaultStyle -> {
           if (state.appliedStyle != null || !matchesBaseline(parent, newBaseline)) {
               restore(parent, newBaseline)
           }
           state.appliedStyle = null
       }
       state.appliedStyle != style || !matchesTarget(parent, newBaseline, style) -> {
           apply(parent, newBaseline, style)
           state.appliedStyle = style
       }
     }
```

### 8.1 Exact `getObjectField` count per control-flow path

| Scenario | Legacy `getObjectField` count | Breakdown |
|---|---|---|
| A. first custom / baseline null | **6** | `captureBatteryBaseline`(3) + `applyBatteryStyle`(3). `state.appliedStyle != style` short-circuits `matchesTarget`. |
| B. first default / baseline null | **6** | `captureBatteryBaseline`(3) + `matchesBaseline`(3). If baseline matches, no `restore`. |
| C. steady same custom / target matches | **3** | `matchesTarget`(3) only. |
| D. steady same custom / target drifted | **6** | `matchesTarget`(3) + `applyBatteryStyle`(3). |
| E. preference refresh creates new `BatteryStyle` identity, baseline unchanged | **3** | `applyBatteryStyle`(3). `state.appliedStyle != style` short-circuits `matchesTarget`. |
| F. custom → default | **3** | `restoreBatteryBaseline`(3). `state.appliedStyle != null` short-circuits `matchesBaseline`. |
| G. steady default / unchanged | **6** | `matchesBaseline`(3) in baseline-resolution branch + `matchesBaseline`(3) in default branch. |
| H. steady default / same children but OEM property drift | up to **9** | `matchesBaseline`(3) → false → `captureBatteryBaseline`(3) → then `matchesBaseline`(3) in default branch. |
| I. child replacement + same custom style | up to **9** | `captureBatteryBaseline`(3) + `matchesTarget`(3) + `applyBatteryStyle`(3). |
| J. swap order restoration | included in `applyBatteryStyle`/`restoreBatteryBaseline` counts; `restoreChildOrder` and `applyBatteryChildSwapIfNeeded` do not call `getObjectField`. |
| K. `battery4` right-margin routing | logic inside `matchesTarget`/`applyBatteryStyle`; `battery4` value is already in `BatteryStyle`. |

### 8.2 Identity short-circuit is observable

`state.appliedStyle != style` must remain **identity** (reference) comparison. It must not be changed to structural equality. This short-circuit is a real observable control-flow behavior, because the preference observer creates a new `BatteryStyle` instance on every relevant key change.

---

## 9. EXACT REFLECTION-COST ORACLE

### 9.1 `findField` cache

`XposedHelpers.findField` uses `fieldCache: ConcurrentHashMap<MemberCacheKey, ConcurrentHashMap<String, Object>>` (`XposedHelpers.java:515-533`).

- First call for a `(class, fieldName)` scans the class hierarchy via `findFieldRecursiveImpl`.
- Steady state is a `ConcurrentHashMap` lookup plus `Field.get(obj)`.

### 9.2 Legacy debt description

```text
CACHED_GENERIC_XPOSED_FIELD_LOOKUP + Field.get
```

It is **not** `FULL_HIERARCHY_SCAN_PER_CALLBACK` after the first resolution.

### 9.3 B1 goal

B1 replaces the `ConcurrentHashMap` lookup and the field-name string lookup with a direct frozen `Field` reference held in `BatteryStyleAbi`:

```text
old: XposedHelpers.getObjectField(parent, "mBatteryTextDigitView") as? TextView
new: abi.digitField.get(parent) as? TextView
```

This removes the generic Xposed field-name lookup while keeping the `Field.get` semantics and behavior.

---

## 10. BASELINE / CHILD IDENTITY SEMANTICS

### 10.1 `BatteryBaseline`

```kotlin
// SystemUIBatteryHooks.kt:257-268
internal data class BatteryBaseline(
    val percentIndex: Int,
    val markIndex: Int,
    val digitTextSize: Float,
    val percentTextSize: Float,
    val markTextSize: Float,
    val digitTypeface: Typeface?,
    val percentTypeface: Typeface?,
    val percentPadding: Padding,
    val markPadding: Padding,
    val childIds: List<Int>
)
```

### 10.2 `childIds`

```kotlin
// SystemUIBatteryHooks.kt:310
childIds = (0 until owner.childCount).map { System.identityHashCode(owner.getChildAt(it)) }
```

### 10.3 `childIdentitiesChanged` algorithm

```kotlin
// SystemUIBatteryHooks.kt:284-294
internal fun childIdentitiesChanged(parent: ViewGroup, childIds: List<Int>): Boolean {
    if (parent.childCount != childIds.size) return true
    outer@ for (i in 0 until parent.childCount) {
        val currentId = System.identityHashCode(parent.getChildAt(i))
        for (baselineId in childIds) {
            if (currentId == baselineId) continue@outer
        }
        return true
    }
    return false
}
```

**Semantics:**
- If child count changes, return `true`.
- For each current child, check whether its `identityHashCode` appears anywhere in the baseline `childIds` list.
- Because it does not enforce one-to-one matching, a pure reordering of the same child instances would return `false` (set membership is unchanged). `matchesBaseline` / `matchesTarget` later detect and fix order via `restoreChildOrder` / `applyBatteryChildSwapIfNeeded`.
- Theoretical `identityHashCode` collision between two different `View` objects is a **theoretical debt**, not a concrete correctness issue.

### 10.4 A0 decision

- Keep `System.identityHashCode` semantics.
- Do not change to `View.id`, `equals()`, structural list comparison, `WeakReference` list, or object-reference list without concrete correctness evidence.
- The theoretical collision risk is recorded only; it is **not** fixed in C3.

---

## 11. LEGACY VIEW MUTATION ORACLE

### 11.1 Default style definition

```kotlin
// SystemUIBatteryHooks.kt:239-248
private fun isBatteryStyleDefault(style: BatteryStyle): Boolean {
    return !style.swap &&
        style.fontSizeDp == 7.5f &&
        style.markFontSizeDp == 7.5f &&
        !style.bold &&
        style.leftMarginDp == 0f &&
        style.rightMarginDp == 0f &&
        style.verticalOffset == 8 &&
        style.markVerticalOffset == 17
}
```

(Note: `battery4` is **not** part of the default check.)

### 11.2 Text size behavior

```kotlin
// SystemUIBatteryHooks.kt:405-407
internal fun expectedTextSize(view: TextView, sizeDp: Float, baselineSize: Float): Float {
    return if (sizeDp == 7.5f) baselineSize else sizeDp * view.resources.displayMetrics.density
}
```

- If `sizeDp == 7.5f`, restore baseline pixel size; otherwise set to `sizeDp * density`.

### 11.3 Typeface behavior

```kotlin
// SystemUIBatteryHooks.kt:510-512
private fun setTypefaceIfChanged(view: TextView, typeface: Typeface?) {
    if (view.typeface !== typeface) view.typeface = typeface
}
```

- `style.bold` -> `Typeface.DEFAULT_BOLD`.
- Otherwise -> baseline typeface.
- Identity comparison prevents unnecessary setter calls.

### 11.4 Padding behavior

```kotlin
// SystemUIBatteryHooks.kt:329-334
private fun paddingEquals(view: TextView, padding: Padding): Boolean { ... }

// SystemUIBatteryHooks.kt:514-517
private fun setPaddingRelativeIfChanged(view: TextView, padding: Padding) { ... }
```

### 11.5 Swap behavior

```kotlin
// SystemUIBatteryHooks.kt:494-503
private fun restoreChildOrder(parent: ViewGroup, percentView: View, markView: View, percentIndex: Int, markIndex: Int) {
    if (markIndex > percentIndex) {
        moveChildTo(parent, markView, markIndex)
        moveChildTo(parent, percentView, percentIndex)
    } else {
        moveChildTo(parent, percentView, percentIndex)
        moveChildTo(parent, markView, markIndex)
    }
}
```

`applyBatteryChildSwapIfNeeded` targets `[percent, mark, digit]` order.

### 11.6 `battery4` right-margin routing

```kotlin
// SystemUIBatteryHooks.kt:365-366 (in matchesTarget) / 468-469 (in applyBatteryStyle)
val rightMarginOnPercent = if (style.battery4) rightMargin else 0
val rightMarginOnMark = if (style.battery4) 0 else rightMargin
```

When `battery4` is true, the right margin is applied to the percent view; otherwise to the mark view.

### 11.7 Vertical offset mapping

```kotlin
// matchesTarget / applyBatteryStyle
val topMargin = if (style.verticalOffset == 8) 0 else dipToPx((style.verticalOffset - 8) * 0.5f, metrics)
```

```kotlin
val markTopMargin = if (style.markVerticalOffset == 17 && style.verticalOffset == 8) {
    topMargin
} else {
    dipToPx((style.markVerticalOffset - 8) * 0.5f, metrics)
}
```

### 11.8 Idempotence expectations

- `applyBatteryChildSwapIfNeeded` and `moveChildTo` are idempotent: if the view is already at the target index, no `removeView`/`addView` is called.
- `setTextSizeIfChanged` / `setTypefaceIfChanged` / `setPaddingRelativeIfChanged` compare before writing.
- Repeated `updateAll` with the same `BatteryStyle` identity and matching view state should result in zero mutations.

### 11.9 A0 decision

```text
BEHAVIOR_CHANGE_ALLOWED = false
```

C3 is **not** a UI feature rewrite. All text-size, typeface, padding, swap, restore, baseline-relative, `battery4` margin, and idempotence semantics must be preserved exactly.

---

## 12. TEST EVIDENCE INVENTORY

### 12.1 Existing tests

| Test | What it actually covers | Classification |
|---|---|---|
| `BatteryStyleSnapshotTest` (`app/src/test/.../BatteryStyleSnapshotTest.kt`) | Direct calls to `SystemUIBatteryHooks.readBatteryStyle()` and manual `batteryStyle = readBatteryStyle()` | `readBatteryStyle()` mapping = **RUNTIME_TESTED_COMPONENT**; preference observer auto-callback = **NOT_RUNTIME_TESTED_CALLBACK** |
| `BatteryViewStateTest` (`app/src/test/.../BatteryViewStateTest.kt`) | Direct calls to `SystemUIBatteryHooks.captureBatteryBaseline`, `applyBatteryStyle`, `restoreBatteryBaseline`, `reconcileBatteryView` on `FakeBatteryView` | **RUNTIME_TESTED_COMPONENT** for helper execution |
| `BatteryChildReorderBehaviorTest` (`app/src/test/.../BatteryChildReorderBehaviorTest.kt`) | Direct calls to `SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded`, `moveChildTo` | **RUNTIME_TESTED_COMPONENT** for helper execution |
| `XposedHelpersAbiTest` (`app/src/test/.../XposedHelpersAbiTest.kt`) | `weakIdentityKeyIsClearedByGc`, `cacheHitReturnsSameMemberInstance`, `publicAbiSnapshotContainsExpectedMethods` | **RUNTIME_TESTED_COMPONENT** for additional-instance GC and reflection cache behavior; public API surface = **STRUCTURAL** |
| Real `MiuiBatteryMeterView.updateAll` after callback | Not covered | **NOT_RUNTIME_TESTED_CALLBACK** |
| Real HyperOS / SystemUI runtime | Not covered | **NOT_PROVEN** |

### 12.2 A0 interpretation

- `BatteryViewStateTest` / `BatteryChildReorderBehaviorTest` exercise production helpers but do **not** exercise the real `after` hook callback or the `XposedHelpers` install path.
- `BatteryStyleSnapshotTest` does **not** prove the preference observer callback; it only proves that `readBatteryStyle()` produces the expected object.
- `XposedHelpersAbiTest` provides component-level evidence that the additional-instance map uses weak keys and that field/method caches work, but it does not prove real SystemUI lifecycle.

---

## 13. PROPOSED B1 TEST MATRIX

These tests are **planned** for the B1 implementation phase. No test files are created in A0.

1. Frozen Field ABI resolution success: all three fields resolve from `MiuiBatteryMeterView` class hierarchy.
2. Missing field compatibility: resolver returns null and the after hook is not installed / is no-op.
3. Frozen Field child access: `effect.readXxx(parent)` returns the same view as legacy `getObjectField`.
4. Baseline capture equivalence: `effect.captureBatteryBaseline` produces the same `BatteryBaseline` as legacy.
5. `matchesBaseline` equivalence.
6. `matchesTarget` equivalence.
7. Custom apply equivalence.
8. Restore equivalence.
9. Identity `BatteryStyle` refresh still forces re-apply (new instance → apply even with same values).
10. Child replacement recapture (new child identity → re-baseline).
11. Swap idempotence (already swapped order, second apply zero mutation).
12. `battery4` margin routing (right margin on percent vs mark).
13. Ordinary effect exception follows legacy `afterHook` behavior (logged/swallowed, original result preserved).
14. Fatal exception category preservation where feasible (OOM/ThreadDeath/VirtualMachineError propagate).
15. No `MainModule.mPrefs` read in the migrated `updateAll` hot callback.
16. No `XposedHelpers.getObjectField` calls for the three frozen fields in the migrated hot path (STRUCTURAL source test).

**Test classification reminder:**
- Source-substring / method-body absence test = **STRUCTURAL**.
- Production helper execution on fake/double = **RUNTIME_TESTED_COMPONENT**.
- Real hook callback in real SystemUI = **RUNTIME_TESTED_CALLBACK** / **NOT_PROVEN**.

---

## 14. NON-GOALS

The following are explicitly out of scope for C3:

- Modifying `DetailedNetSpeed`.
- Modifying Drawer blur.
- Modifying Status bar icon visibility.
- Modifying C1 (`SystemStatusBarInsetsHooks` / `mods/statusbarheight`).
- Modifying C2 (`SystemClockHooks` / `mods/clock`).
- Battery feature UI redesign.
- Preference architecture redesign.
- Generic Architecture-C manager / God object.
- `LateAbi` pattern.
- Generic reflection abstraction.
- `AtomicReference` / generation counter for `BatteryStyle`.
- `State` data class wrapper for `BatteryStyle`.
- Coroutines / Flow in the hot path.
- Lifecycle framework refactor.
- Additional-instance framework refactor.
- Opportunistic fixes in `XposedHelpers.java`.

---

## 15. A0 DECISION OUTPUT

| Decision | Value |
|---|---|
| `CALLBACK_SHAPE` | `PRESERVE_AFTER` |
| `CONFIG_MODEL` | `REUSE_EXISTING_VOLATILE_IMMUTABLE_BATTERY_STYLE` |
| `STYLE_EQUALITY` | `IDENTITY` |
| `OWNER_MODEL` | `EXISTING_WEAK_IDENTITY_ADDITIONAL_INSTANCE` |
| `VIEW_STATE` | `PER_VIEW_MUTABLE_STATE` |
| `CALLBACK_THREAD` | `NOT_PROVEN` |
| `SINGLE_THREAD_CONFINEMENT` | `NOT_PROVEN` |
| `CONCURRENT_ACCESS` | `NOT_PROVEN` |
| `FIELD_ABI` | `EXACT_3_FIELDS_ONLY` |
| `FIELD_ALIASES` | `NONE_WITHOUT_EVIDENCE` |
| `HOT_PATH_GENERIC_FIELD_LOOKUP` | `REMOVE_IN_B1` |
| `HOT_PATH_PREF_READ` | `NONE / PRESERVE_NONE` |
| `RESOLVER_FAILURE_POLICY` | `FEATURE_LOCAL_DISABLE` |
| `EFFECT_SCOPE` | `MINIMAL` |
| `BEHAVIOR_CHANGE_ALLOWED` | `false` |
| `C1_TOUCH` | `false` |
| `C2_TOUCH` | `false` |
| `NETSPEED_TOUCH` | `false` |

---

## 16. VALIDATION / COMMIT

A0 is a docs-only artifact. Validation performed:

```text
git diff --check
```

Expected final state:

- Changed files: `docs/architecture-c/C3_BATTERY_STYLE_A0_PREFLIGHT.md` only.
- Production changed: `false`
- Tests changed: `false`
- C1 changed: `false`
- C2 changed: `false`
- NetSpeed changed: `false`
- C3 production started: `false`

---

C3_A0_BATTERY_STYLE_PREFLIGHT_READY_FOR_INDEPENDENT_AUDIT

STOP. Do not start B1 implementation. Do not create Resolver / ABI / Effect production classes.