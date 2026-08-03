# A14 Gesture Event Contract

```text
DocumentKind: CURRENT
Product: CustoMIUIzer A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: d189ad12fc50522ada4772fcb6e5afb510469e01
EvidenceState: STATIC
GeneratedBy: test_gesture_event_contract.py
SourceOfTruth: gesture package source
```

## 1. Event Sources

- Touch/pointer input: `GestureEvent` is constructed from `MotionEvent` and is the entry to `GestureMachine.process`.
- View attach/detach: `PhoneStatusBarView` and `ControlCenterWindowViewImpl` lifecycle hooks call `GestureMachine.prepare(ownerId)` and `GestureMachine.clear(ownerId)`.
- Bind/unbind: `ControlCenterGestureRuntimeHolder.bind(classLoader)` creates or reuses a runtime; `unbind()` clears the machine and drops `activeRuntime`.
- Preference/configuration change: `GestureConfigPublisher.publish()` resolves a new `GestureConfig`; `GestureMachine.dispatch()` snapshots config at the first DOWN.
- Lifecycle cleanup: `GestureMachine.clear(ownerId)` and `GestureMachine.clear()` remove per-owner or all state, including snapshots, dependencies, config, side-effect gate fingerprints, and arbiter tokens.

## 2. Event Data

- `ownerId: Int` scopes all per-View state in `GestureMachine` maps.
- `actionMasked` carries `DOWN`, `UP`, `MOVE`, `CANCEL`, `POINTER_DOWN`, and `POINTER_UP`.
- `pointerCount` and `activePointerCount` describe the number of pointers involved and the count normalized after the action.
- `x` and `y` are the event coordinates; direction is inferred from delta in `GestureStateMachine.handleMove`.
- `downTime` and `eventTime` are used for double-tap and long-press timing in `GestureStateMachine`.
- Gesture side is inferred by `GestureStateMachine` from screen-width ratios (LEFT, RIGHT, CENTER).
- `CANCEL` action triggers unconditional reset; there is no separate explicit cancellation reason field in `GestureEvent`.

## 3. State Machine

- Initial state is `GestureState.IDLE` with a default `GestureSession`.
- `GestureMachine.prepare(ownerId, context)` calls `ensureDependencies` and resolves `GestureDependencies` for the owner.
- A `DOWN` event with valid sliding start transitions the session to `GestureState.TRACKING` and emits a `BeginTracking` command.
- `MOVE` events that cross a threshold transition to `SLIDING_BRIGHTNESS` or `SLIDING_VOLUME`; `UP` events can emit `TriggerDoubleTap` or `TriggerLongPress`.
- `CANCEL` and `MOVE` beyond the status bar height transition back to `IDLE` with a `Reset` command.
- `GestureMachine.clear(ownerId)` removes per-owner state; `GestureMachine.clear()` resets the whole machine.
- `GestureStateMachine` silently ignores non-`DOWN` events while in `IDLE`, preventing illegal transitions by returning an empty command list.

## 4. Event Ordering

- `down → move → up` is the normal sequence: `DOWN` snapshots config, `MOVE` computes brightness/volume, `UP` commits the change or triggers double-tap/long-press.
- `down → cancel` resets to `IDLE` with a `Reset` command and clears temporary brightness.
- View `onDetachedFromWindow` calls `GestureMachine.clear(ownerId)`; `ControlCenterGestureRuntimeHolder.unbind()` calls `GestureMachine.clear()`.
- A new target `ClassLoader` in `ControlCenterGestureRuntimeHolder.bind()` calls `existing?.machine?.clear()` before rebinding.
- `GestureSideEffectGate` deduplicates by `OwnerFingerprint` (owner + physical identity); `PhysicalGestureArbiter` enforces a single active owner per physical token.

## 5. Ownership

- A physical gesture is represented by `PhysicalGestureArbiter.Token` with `downTime`, `deviceId`, and `source`.
- `PhysicalGestureArbiter.tryAcquireOnDown(ownerId, event)` claims the token; `isOwner(ownerId, event)` validates it for non-`DOWN` events.
- `PhysicalGestureArbiter.release(ownerId, event)` removes a specific token; `releaseOwner(ownerId)` removes all tokens for an owner; `releaseAll()` clears all tokens.
- `GestureMachine` keeps per-owner maps for snapshots, dependencies, and configs, preventing cross-View or cross-ClassLoader leakage.
- `GestureMachine.ensureDependencies` checks `classLoaderIdentity`; a different `ClassLoader` triggers `clear()` and re-preparation.

## 6. Side Effects

- `GestureSideEffectGate` filters commands by entry (`STATUS_BAR_TOUCH`, `CONTROL_CENTER_TOUCH`) and deduplicates by `OwnerFingerprint`.
- Business effects (`ApplyTemporaryBrightness`, `AdjustVolume`, `CommitBrightness`, `TriggerDoubleTap`, `TriggerLongPress`) are blocked if their fingerprint has already been seen; 一次手势最多只能触发一次真实副作用。
- `CANCEL`, cancelled, duplicate, and out-of-order events for the same fingerprint return an empty command list, so side effects are not re-triggered.
- `GestureEffectExecutor` is a functional interface executed after gate filtering; neither the gate nor the executor holds a lock during the callback.

## 7. Cleanup Contract

- `onDetachedFromWindow` invokes `GestureMachine.clear(ownerId)` to drop the owner snapshot, dependencies, and config.
- `ControlCenterGestureRuntimeHolder.unbind()` invokes `GestureMachine.clear()` and drops `activeRuntime`.
- A new `ClassLoader` in `bind()` first calls `existing?.machine?.clear()` before constructing a new `GestureMachine`.
- A failed or cancelled gesture emits `Reset` from `GestureStateMachine`; `GestureMachine` releases the arbiter token on `UP`, `CANCEL`, or `Reset`.
- `DEVICE_LIFECYCLE_ENTRY_BLOCKED` remains: no plugin `destroyPlugin`/`onPluginUnloaded` hook was found, so `unbind()` is available but not wired to a plugin lifecycle callback.

## 8. Hot-Path Constraints

- `GestureStateMachine.process` is a pure function with no Android service calls or I/O inside the state machine.
- `GestureDependencies` resolves `Method` handles once during `prepare()`; `invokePrepared` is used for brightness reads without repeated reflection.
- `GestureSnapshot` and `GestureSession` are immutable data classes; the config snapshot is taken once at `DOWN`.
- `GestureEffectExecutor` is registered once per machine; `GestureSideEffectGate` and `PhysicalGestureArbiter` prevent duplicate side effects and duplicate ownership.

## 9. Known Limitations

- No real device touch event log has been collected on ROM hardware; the tests are unit/integration/stress tests in `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/gesture/`.
- Plugin destroy entry is still missing: no `destroyPlugin`, `onPluginUnloaded`, or equivalent hook was found in the repository, framework stub, or ROM intelligence.
- This document is a `STATIC` code contract; it does not claim that every ROM-specific gesture sequence has been exercised on device.
- `P12.4 APK delta` is still `TODO` and is not treated as a final task here.

## 10. Evidence

The following source files exist at `EvidenceCommit` and contain the key symbols named in this document:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureEvent.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureState.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureStateMachine.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSession.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSnapshot.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureEntry.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureEffectExecutor.kt`
- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`
