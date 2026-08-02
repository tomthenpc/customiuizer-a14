# A14 Gesture Lifecycle Owner Inventory

```text
DocumentKind: CURRENT
Product: CustoMIUIzer A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: cd152365a1b258a7b36e978d1050db71f427fa83
EvidenceState: MIXED
GeneratedBy: tools/check-invariants.py + tools/tests/test_gesture_lifecycle_inventory.py + manual call chain audit
SourceOfTruth: app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture
```

## 1. Owners and lifecycle boundaries

| Owner | Creation / attach | Destruction / detach | Cleanup invoked | Verified by |
|---|---|---|---|---|
| `PhoneStatusBarView` (status bar) | `onAttachedToWindow` | `onDetachedFromWindow` | `statusBarMachine.clear(ownerId)` | `tools/check-invariants.py:check_gesture_detach_cleanup` |
| `ControlCenterWindowViewImpl` (control center window) | `onAttachedToWindow` | `onDetachedFromWindow` | `controlCenterMachine.clear(ownerId)` | `tools/check-invariants.py:check_gesture_detach_cleanup` |
| `ControlCenterGestureRuntimeHolder` active runtime | `PluginInstance$PluginFactory.createPlugin` (new ClassLoader) | New `createPlugin` binds a different ClassLoader and calls `existing?.machine?.clear()`; explicit `unbind()` is not yet wired to a plugin destroy hook | `machine.clear()` | `ControlCenterGestureRuntimeHolderTest` |
| `ControlCenterWindowViewImpl` raw motion events | `handleMotionEvent` DOWN | `handleMotionEvent` UP/CANCEL or `onDetachedFromWindow` | `arbiter.release(ownerId, event)`, `arbiter.releaseOwner(ownerId)`, `machine.clear(ownerId)` | `PhysicalGestureArbiterTest`, `GestureStateMachineTest`, `GestureMachineTest` |

## 2. What is released at each boundary

- `GestureMachine.clear(ownerId)`: snapshot, dependencies, config, side-effect gate owner fingerprints, arbiter owner tokens.
- `GestureMachine.clear()`: all snapshots/dependencies/configs, side-effect gate, all arbiter tokens.
- `ControlCenterGestureRuntimeHolder.unbind()`: calls `machine.clear()` and drops the `activeRuntime` reference.
- `PhysicalGestureArbiter.release/releaseOwner/releaseAll`: removes owner tokens and reaps stale tokens.
- `GestureSideEffectGate.clearOwner/clear`: removes owner fingerprints / all fingerprints.

## 3. Known gaps

```text
DEVICE_LIFECYCLE_ENTRY_BLOCKED: no reliable plugin/ClassLoader destruction hook found.
```

- `PluginInstance$PluginFactory` has a `createPlugin` method that is already hooked for `ControlCenterGestureRuntimeHolder.bind()`.
- No `destroyPlugin`, `onPluginUnloaded`, `unload` or equivalent method name was found in the repository, in the framework stub, or in the ROM intelligence docs.
- `ControlCenterWindowViewImpl.onDetachedFromWindow` is the narrowest, already-hooked View lifecycle boundary and is used to clear per-owner state.
- `ControlCenterGestureRuntimeHolder.unbind()` is available for a future `destroyPlugin` hook if one is discovered on-device.

## 4. Evidence

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` lines 842-857 contain the `PhoneStatusBarView` `onAttachedToWindow` / `onDetachedFromWindow` hooks, calling `statusBarMachine.prepare(ownerId)` and `statusBarMachine.clear(ownerId)`.
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt` lines 267-287 install the `ControlCenterWindowViewImpl` `onAttachedToWindow` / `onDetachedFromWindow` hooks, calling `controlCenterMachine.prepare(ownerId)` and `controlCenterMachine.clear(ownerId)`.
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt` lines 32-71 implement `bind`, `unbind` and `activeRuntime`.
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt` lines 197-207 implement `clear(ownerId: Int)` and `clear()`.
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolderTest.kt` lines 1-282 cover `bind`, `unbind`, `activeRuntime`, `sameLoader`, `newLoader`, `repeatedLoader` and `oldLoaderDetach` behavior.
