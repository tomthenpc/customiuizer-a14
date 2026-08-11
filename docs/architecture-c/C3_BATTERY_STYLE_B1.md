# C3-B1 — Battery Style Architecture C Production Migration

## Base / frozen input

- **Base SHA:** `bad4250394db3a478df4e6ff2ea6509d517d30f7`
- **Branch:** `devin/a14-architecture-c-r14.20.0`
- **A0 input:** `docs/architecture-c/C3_BATTERY_STYLE_A0_PREFLIGHT.md` (frozen; no factual errors found during B1)

## Implementation files

| Path | Role |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleAbi.kt` | Frozen metadata holder: `resolutionRootClass`, three `Field`s. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleResolver.kt` | Cold resolver; returns `BatteryStyleAbi?` or `null` for legacy fallback. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleEffect.kt` | Minimal effect; FAST vs LEGACY mode selection per helper invocation. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | Wiring + migrated `capture/matches/restore/apply` helpers. |

New test files:

| Path | Role |
|---|---|
| `app/src/test/java/tv/withaibuild/customiuizer/mods/battery/BatteryArchitectureCTest.kt` | Runtime component tests (A–P). |
| `app/src/test/java/tv/withaibuild/customiuizer/mods/battery/testfixtures/*.java` | Java test doubles for field shadowing and declared-type tests. |

## Legacy oracle preserved

- `BatteryStyle` remains a normal `internal class`; not a `data class`.
- `@Volatile internal var batteryStyle: BatteryStyle?` unchanged.
- `BatteryViewState.baseline` / `appliedStyle` unchanged.
- `state.appliedStyle != style` remains identity equality.
- `getOrCreateBatteryViewState` uses existing additional-instance key.
- `StatusBarStyleBatteryIconHook` still overrides `after(AfterHookCallback)` and does not call `chain.proceed()`.
- Hook body remains thin: read snapshot, get owner, get state, call `reconcileBatteryView`.
- `reconcileBatteryView` control-flow branches and short-circuit order unchanged.

## Resolver design

```text
Target class: com.android.systemui.statusbar.views.MiuiBatteryMeterView
Field names:  mBatteryTextDigitView, mBatteryPercentView, mBatteryPercentMarkView
No declared-type validation
No field aliases
Fatal boundary: FatalErrors.unwrapAndRethrowIfFatal(t) before logging
Ordinary failure: XposedHelpers.log(t); return null
```

`BatteryStyleResolver.resolve(ClassLoader?)` is the production entry point. A test-only overload `resolve(ClassLoader?, String)` is provided so the resolver logic can be exercised with test doubles without mocking the real class name.

## Effect design

```text
FAST eligibility: abi != null && parent.javaClass === abi.resolutionRootClass
FAST:            abi.xxxField.get(parent) as? TextView
LEGACY FALLBACK: XposedHelpers.getObjectField(parent, exactFieldName) as? TextView
Mode selection:  once per helper invocation; all three children same mode
No Map<Class, Abi>, no runtime cache, no per-owner ABI state
No Pair/Triple/List/Map/Flow/coroutine allocation
```

`BatteryStyleEffect` handles `IllegalAccessException` by logging and throwing `IllegalAccessError(e.message)`, matching `XposedHelpers.getObjectField`. `IllegalArgumentException` is rethrown unchanged.

## FAST eligibility / field shadowing

- `exact runtime target class` → FAST uses frozen `Field.get`.
- `runtime subclass` → legacy fallback, even when the target class also declares the same fields.
- `subclass shadows same-name field` → legacy fallback reads the subclass field, preserving Java runtime-owner precedence.
- `subclass-only field` → resolver returns `null` (missing on target), fallback reads from the subclass.

Evidence: `BatteryArchitectureCTest.runtimeSubclassShadowing_fallbackPreservesRuntimeOwnerPrecedence` and `runtimeSubclass_doesNotSelectFastPath`.

## Fallback semantics

- Resolver `null` or `BatteryStyleEffect(null)` selects legacy fallback.
- The after hook is always installed.
- Feature is never permanently disabled due to a cold target-class field miss.
- `XposedHelpers.getObjectField` performs the original `owner.getClass()` hierarchy lookup.

## Fatal boundary

- Resolver `catch (t: Throwable)` first calls `FatalErrors.unwrapAndRethrowIfFatal(t)`.
- Fatal categories: `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`.
- `OutOfMemoryError` thrown by a custom `ClassLoader.loadClass` propagates through the resolver without being swallowed.
- Ordinary `NoSuchFieldError` / missing class / missing field is logged once and returns `null`.

## Test classification

| Test | Classification |
|---|---|
| `BatteryArchitectureCTest` runtime assertions on resolver/effect/helper | `RUNTIME_TESTED_COMPONENT` |
| `BatteryViewStateTest` / `BatteryChildReorderBehaviorTest` existing helper tests | `RUNTIME_TESTED_COMPONENT` (still pass unchanged) |
| `BatteryStyleSnapshotTest` | `RUNTIME_TESTED_COMPONENT` for `readBatteryStyle()` mapping; preference observer callback = `NOT_RUNTIME_TESTED_CALLBACK` |
| Real `MiuiBatteryMeterView.updateAll` after callback | `NOT_RUNTIME_TESTED_CALLBACK` |
| Real HyperOS / SystemUI runtime | `NOT_PROVEN` |
| FAST path source absence of `XposedHelpers.getObjectField` in `BatteryStyleEffect.readFast` | `STRUCTURAL` (source inspection) |

## Remaining NOT_PROVEN evidence

- Real `MiuiBatteryMeterView.updateAll` after callback = `NOT_RUNTIME_TESTED_CALLBACK`
- Real HyperOS / SystemUI runtime = `NOT_PROVEN`
- Callback thread = `NOT_PROVEN`
- `BatteryViewState` single-thread confinement = `NOT_PROVEN`
- Concurrent access = `NOT_PROVEN`

## Validation commands and results

```text
git diff --check                              # passed
python tools/verify.py full                   # passed
  - static rules passed
  - observer-key-contract passed
  - check-invariants: 236 files, no violations
  - audit-feature-semantics: Validation passed
  - gradlew compileDebugKotlin compileDebugJavaWithJavac: ok
  - gradlew testDebugUnitTest: ok
  - gradlew lintDebug: ok
.\gradlew.bat :app:testDebugUnitTest          # 1627 tests completed, all passed
```

## Diff scope (commit audit)

Changed production files:
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleAbi.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleResolver.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleEffect.kt`

Changed test files:
- `app/src/test/java/tv/withaibuild/customiuizer/mods/battery/BatteryArchitectureCTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/battery/testfixtures/*.java`

Changed docs:
- `docs/architecture-c/C3_BATTERY_STYLE_B1.md`

Unchanged:
- C1 production
- C2 production
- DetailedNetSpeed
- Drawer blur
- Status bar icon visibility
- `XposedHelpers.java`
- preference architecture

## Final marker

C3_B1_BATTERY_STYLE_READY_FOR_INDEPENDENT_AUDIT

STOP. Do not enter B2. Do not modify DetailedNetSpeed / Drawer blur / Status bar icon visibility.
