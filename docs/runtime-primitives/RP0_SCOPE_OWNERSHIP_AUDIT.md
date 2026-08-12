# RP0 — A14 Runtime Primitives Scope / Duplication / Ownership Audit

## Gate status

| Item | Value |
|------|-------|
| Source freeze | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| Target branch | `devin/a14-runtime-primitives-r14.20.0` |
| Branch created from | exact SHA (not from a branch name) |
| Reset / rebase / amend / cherry-pick / merge / force-push | none |
| Local HEAD | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| Remote HEAD | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| Merge-base with source freeze | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| Worktree | clean at gate check |
| RP0_RESULT | `NO_SHARED_PRIMITIVE_JUSTIFIED` |
| FIRST_PROOF_TARGET | `NONE` |

## What was audited

This audit inspected production control flow at the following sites, classified by the actual executable semantics, not by keyword similarity.

| # | Site | File | Classification |
|---|------|------|----------------|
| 1 | `StatusBarDisplayRegistry` + `OwnedRegistrations` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt` | `OWNER_GENERATION` + `MULTI_OWNER_REGISTRATION` + `REATTACHABLE_VIEW` |
| 2 | `ReceiverRegistry` owned receiver path | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReceiverRegistry.kt` | `MULTI_OWNER_REGISTRATION` |
| 3 | `ReceiverRegistry` process-key replacement path | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReceiverRegistry.kt` | `PROCESS_KEY_REPLACEMENT` |
| 4 | `DeviceInfoMonitor` / `DeviceInfoMonitorState` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt`, `DeviceInfoMonitorState.kt` | `WORK_GENERATION` |
| 5 | `AudioVisualizer` lifecycle / generation | `app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt` | `WORK_GENERATION` + `SIMPLE_WEAK_OWNER` |
| 6 | `SystemClockHooks.SecondTicker` | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | `SIMPLE_WEAK_OWNER` |
| 7 | `CustomTextIconTintRoute` + `DarkTintRegistrationState` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/CustomTextIconTintRoute.kt`, `DarkTintRegistrationState.kt` | `REATTACHABLE_VIEW` |
| 8 | `LockScreenAlbumArtController` request generation | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt` | `WORK_GENERATION` |
| 9 | `SystemUIMonitorAndTileHooks` ContentObserver replacement | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | `PROCESS_KEY_REPLACEMENT` |
| 10 | `WeatherDataController` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt` | `SIMPLE_WEAK_OWNER` |

No code, tests, Architecture C documentation, or runtime primitive were created or modified. Only this document is added.

## Site-by-site evidence

### 1. `StatusBarDisplayRegistry` + `OwnedRegistrations`

`StatusBarDisplayRegistry` is a per-(status-bar) display registry. It has two containers:

- `byDisplay: MutableMap<Int, StatusBarDisplayState<O, R>>` — strong map keyed by display id.
- `pendingByOwner: WeakIdentityMap<O, StatusBarDisplayState<O, R>>` — weak-key identity map for views whose display is not yet known.

`StatusBarDisplayState` holds a `WeakReference<O>` called `generation` (the status-bar view), an optional `WeakReference<R>` for the second-row container, and an `OwnedRegistrations<O>` list for per-generation cleanup.

- `getOrCreatePending(owner)` refreshes the `WeakReference` for the same owner instance and returns the existing state (`StatusBarDisplayRegistry.kt:59-70`).
- `bind(owner, displayId)` migrates pending state, but if the display already has a state owned by a different (dead) generation it calls `existing.registrations.cleanupAll()` (`StatusBarDisplayRegistry.kt:88-91`). The same view re-attaching keeps the same state (`StatusBarDisplayRegistry.kt:84-87`).
- `detach(owner)` removes the owner by identity from pending and from every display it owns, then releases the state (`StatusBarDisplayRegistry.kt:114-133`).
- `prune()` removes states whose generation is gone and whose registration list is empty, after calling `cleanupAll()` (`StatusBarDisplayRegistry.kt:163-178`).

`OwnedRegistrations` is a list of `(owner, cleanup)` entries, each with a `WeakReference` to the owner and a `consumed` atomic flag. It supports:

- `register(owner, cleanup)` — adds a handle.
- `cleanupWhere(isStale)` — snapshots, removes stale entries, runs each cleanup once.
- `cleanupAll()` — snapshots, clears, runs each cleanup once.
- `RegistrationHandle.cleanupNow()` — idempotent single cleanup.

The cleanup runner (`OwnedRegistrations.kt:126-137`) catches `Throwable`, calls `FatalErrors.unwrapAndRethrowIfFatal(t)`, then logs. `FatalErrors.unwrapAndRethrowIfFatal` rethrows `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`, and unwrapped fatal causes from `InvocationTargetException` / `ExecutionException` / `XposedHelpers.InvocationTargetError` (`FatalErrors.kt:22-52`). Non-fatal throwables are logged via `XposedHelpers.log` and the loop continues.

**Classification:** `OWNER_GENERATION` + `MULTI_OWNER_REGISTRATION` + `REATTACHABLE_VIEW`. The `generation` field is the owner identity, not a work token. Multiple displays (and therefore multiple owners) coexist legitimately. The same owner can re-attach and keep its display state. The combination of `WeakIdentityMap`, `OwnedRegistrations`, and the per-display map already solves this site's problem.

### 2. `ReceiverRegistry` owned receiver path

`ReceiverRegistry.registerOwnedReceiver` creates a `WeakOwnerReceiver` and an `OwnedReceiver(ownerRef, contextRef, receiver)`, then atomically updates `ownedReceivers: ConcurrentHashMap<String, CopyOnWriteArrayList<OwnedReceiver>>` (`ReceiverRegistry.kt:396-469`).

For a given key it:

- keeps live registrations belonging to a *different* owner,
- removes stale owners (GC-ed) and the previous registration for the *same* owner,
- unregisters the displaced receivers outside the compute block,
- self-unregisters if it loses a race.

`WeakOwnerReceiver.onReceive` (`ReceiverRegistry.kt:316-347`) checks `ownerRef.get()` and, if the owner is gone, unregisters itself.

**Classification:** `MULTI_OWNER_REGISTRATION`. Several owners may legitimately coexist under the same key (the KDoc explicitly mentions two clock controllers / one status bar per display). Same-owner replacement is supported. This is already an existing, tested primitive; a new `OwnedSlot` would duplicate it or weaken its multi-owner semantics.

### 3. `ReceiverRegistry` process-key replacement path

`ReceiverRegistry.registerModuleReceiver` uses `moduleReceivers: ConcurrentHashMap<String, ModuleReceiverRegistration>` with a monotonic `moduleReceiverGeneration` (`ReceiverRegistry.kt:70-158`). For a single process key:

- it atomically installs a new registration,
- unregisters the previous receiver,
- self-unregisters if replaced concurrently,
- queues failed unregisters in a bounded stale queue.

`ReceiverRegistry.replaceModuleRegistration` is the non-receiver form for content observers and listeners (`ReceiverRegistry.kt:525-553`). It records a cleanup under a process key and runs the previous cleanup.

**Classification:** `PROCESS_KEY_REPLACEMENT`. The generation here is a monotonic token used for self-race detection, not an owner identity. `SystemUIMonitorAndTileHooks` and `Various.kt` are the main consumers (see below).

### 4. `DeviceInfoMonitor` / `DeviceInfoMonitorState`

`DeviceInfoMonitor` hooks the `NetworkSpeedController` constructor and captures a background `Looper` and `Context`. For each controller it creates an `activeMainHandler` and `activeBgHandler` that capture a `myId` from `DeviceInfoMonitorState.startNewGeneration()` (`DeviceInfoMonitor.kt:435-448` and `DeviceInfoMonitorState.kt:45-52`).

`DeviceInfoMonitorState` stores `activeBgHandlerId` and `activeMainHandlerId` and provides `isActiveBg`, `isActiveMain`. Each `IconUpdate` message carries its `generation` (`DeviceInfoMonitor.kt:95-99`). Handlers and the main-thread update path drop messages whose id does not match the current generation (`DeviceInfoMonitor.kt:453-456` and `DeviceInfoMonitorState.kt:57-69`).

**Classification:** `WORK_GENERATION`. The `generation` is a handler/work token, not an owner identity. There is a single `DeviceInfoMonitor` singleton; new controllers do not start a new generation in `onConfigMayHaveChanged`, so the generation is primarily used to ignore in-flight messages from a previous handler pair.

### 5. `AudioVisualizer` lifecycle / generation

`AudioVisualizer` is a `View` that owns a `Visualizer`, a `viewScope`, and a `visualizerGeneration` counter (`AudioVisualizer.kt:65`).

- `checkStateChanged()` starts a new visualizer session by incrementing `visualizerGeneration` and launching `linkVisualizer(generation)` (`AudioVisualizer.kt:817-820`).
- `linkVisualizer` checks the generation under a mutex and drops the candidate if `generation != visualizerGeneration` (`AudioVisualizer.kt:669-684`).
- `onDetachedFromWindow()` calls `dispose()`, which cancels jobs, releases the visualizer, cancels the view scope, and calls `onDisposed` (`AudioVisualizer.kt:557-589`).
- The preference observer holds a `WeakReference<AudioVisualizer>` (`AudioVisualizer.kt:264-270`).

**Classification:** `WORK_GENERATION` + `SIMPLE_WEAK_OWNER`. The `visualizerGeneration` is a per-start work token used to discard stale sessions when the view re-enters the playing state. The View itself is the owner; it uses `View` lifecycle (`onDetachedFromWindow`) for disposal. `OwnedRegistrations` / `ReceiverRegistry` are not applicable because the binding is to an `AudioManager` session and a `Visualizer` object, not a keyed registration.

### 6. `SystemClockHooks.SecondTicker`

`SecondTicker` (`SystemClockHooks.kt:748-844`) is created per `MiuiStatusBarClockController` instance. It holds:

- a `WeakReference<Any>` to the clock controller (`SecondTicker.kt:755`),
- its own `running` flag and `screenStateRegistered` flag,
- a `Handler` built from `context.mainLooper`.

`initSecondTicker` compares the effective seconds flags and, if they changed, disposes the previous ticker and stores a new one as an additional instance field on the controller (`SystemClockHooks.kt:712-733`). The ticker also uses `ModuleHelper.registerOwnedReceiver` for the `TIME_SET` receiver, with the controller as the owner (`SystemClockHooks.kt:912-920`).

**Classification:** `SIMPLE_WEAK_OWNER`. The ticker is an owner-bound observer. It uses a `WeakReference` to the controller so a GC-ed controller does not keep the ticker and its screen-state listener alive. Same-owner replacement is explicit. It is not a multi-owner site and it is not a generic slot candidate because the ticker is a `Runnable` + `ScreenStateListener`, not a keyed framework registration.

### 7. `CustomTextIconTintRoute` + `DarkTintRegistrationState`

`CustomTextIconTintRoute` registers module-created text icons (type 91/92) as dark-receiver owners with the ROM `DarkIconDispatcher`. It is a per-View registry with a three-phase lifecycle:

- `attach/register` — add the View as a dark receiver,
- `detach/unregister` — remove the receiver, but keep the `OnAttachStateChangeListener` so re-attach can re-register,
- `terminal dispose` — remove the listener and tracking.

`DarkTintRegistrationState` enforces the state machine (`DarkTintRegistrationState.kt:17-118`). It has:

- `registered`, `released`, `disposed` booleans,
- `canRegister()` only if `!registered && !disposed` (so a released-but-not-disposed state can re-register),
- `register(registerFn, applyInitialTint)` exact-once,
- `release(releaseFn)` exact-once, idempotent,
- `dispose(disposeFn)` exact-once, idempotent, passes `wasRegistered` to the dispose function.

`CustomTextIconTintRoute.register` finds any existing registration for the same view and, if it is not active, disposes it before creating a new one (`CustomTextIconTintRoute.kt:55-73`). The returned handle can terminal-dispose.

**Classification:** `REATTACHABLE_VIEW`. This is a single-owner, view-attached registration with a re-attach path. There is no second production site with the same `attach/release/dispose` + re-attach semantics. Dark-tint is view-specific because it mirrors `View.OnAttachStateChangeListener`; forcing other sites into this model would add indirection and listener retention.

### 8. `LockScreenAlbumArtController` request generation

`LockScreenAlbumArtController` is an `object` with one worker at a time. It uses `requestGeneration: AtomicLong` (`LockScreenAlbumArtController.kt:95`). Each call to `generate` increments the generation, cancels the previous coroutine job, and starts a new one (`LockScreenAlbumArtController.kt:262-285). The worker checks `isCurrent(generation)` between expensive stages and before publishing (`LockScreenAlbumArtController.kt:288-289`).

**Classification:** `WORK_GENERATION`. The generation identifies the latest work request; there is no owner object or key. This is a one-consumer, latest-wins dispatcher. An `OwnedSlot` keyed by an owner would be unnecessary and would add allocation and indirection.

### 9. `SystemUIMonitorAndTileHooks` ContentObserver replacement

The 5G and floating-time tiles create `ContentObserver`s inside `handleSetListening`. On `mListening == true` they register the observer and then call `ModuleHelper.replaceModuleRegistration("custom_5G_tile_listener") { resolver.unregisterContentObserver(contentObserver) }` (`SystemUIMonitorAndTileHooks.kt:117` and `:140`). On `mListening == false` they call `replaceModuleRegistration` with an empty cleanup, which runs the previous cleanup.

**Classification:** `PROCESS_KEY_REPLACEMENT`. There is a single process-scoped slot per key. The cleanup replaces the previous cleanup. `ReceiverRegistry.replaceModuleRegistration` already implements this pattern.

### 10. `WeatherDataController`

`WeatherDataController` is an `object` that:

- keeps a `WeakReference<Any>` to the current clock controller (`WeatherDataController.kt:142`),
- holds the `Context` only as `context.applicationContext`,
- registers one `TIME_TICK` receiver while the screen is on,
- is a `ScreenStateController.ScreenStateListener` (`WeatherDataController.kt:40`).

`initContext` cancels the old coroutine scope, unregisters the old tick receiver, and re-registers with the new context (`WeatherDataController.kt:132-164`).

**Classification:** `SIMPLE_WEAK_OWNER`. The controller is the owner, but it is a process singleton and there is no replace/re-attach semantics. The weak reference only avoids pinning a dead controller when the singleton outlives it.

## Negative evidence and compatibility analysis

The table below compares each candidate against the properties a hypothetical shared `OwnedSlot` primitive would require.

| Property | 1 StatusBarDisplay | 2 OwnedReceivers | 3 ModuleReceiver/Reg | 4 DeviceInfoMonitor | 5 AudioVisualizer | 6 SecondTicker | 7 DarkTint | 8 AlbumArt | 9 TileObserver | 10 Weather |
|---|---|---|---|---|---|---|---|---|---|---|
| Owner identity is the key | yes | yes | no (process key) | no (work token) | yes (View) | yes (controller) | yes (View) | no | no | yes (controller) |
| Multiple owners may coexist under the same key | yes (per display) | yes (per key list) | no | — | no | no | no | no | no | no |
| Same-owner replacement | yes (display, pending) | yes (same owner/key) | yes | no | no | yes | yes (re-register triggers dispose of old) | no (work token) | yes (cleanup) | no (singleton) |
| Re-attach without full dispose | yes (display) | no | no | no | no | no | yes (view attach/detach) | no | no | no |
| Release is exact-once | yes (OwnedRegistrations) | yes (active flag) | yes (atomic state) | — | yes (dispose) | yes (dispose) | yes (state machine) | yes (cancel job) | yes (cleanup) | yes (unregister) |
| Double-release is harmless | yes | yes | yes | — | yes | yes | yes | yes | yes | yes |
| Failure/fatal semantics | OwnedRegistrations logs non-fatal, rethrows OOM/VME/ThreadDeath via `FatalErrors` | WeakOwnerReceiver logs; registry stale queue is bounded | bounded stale queue; catch `Throwable` but OOM rethrown | `CallbackGuard.guarded` / `FatalErrors.unwrapAndRethrowIfFatal` | `XposedHelpers.log` / `FatalErrors.rethrowIfFatal` | `CallbackGuard.guarded` | `FatalErrors.unwrapAndRethrowIfFatal` | `XposedHelpers.log`; OOM rethrown | `CallbackGuard.guarded` | `XposedHelpers.log`; OOM rethrown |
| Thread model | main thread only | concurrent (CHM/COW) | concurrent (CHM/AtomicReference) | main/bg handlers | main + IO coroutines | main handler | main thread (View callbacks) | Default dispatcher (limited parallelism 1) | main thread | main/IO coroutines |
| Existing primitive already covers | `WeakIdentityMap` + `OwnedRegistrations` + `StatusBarDisplayRegistry` | `ReceiverRegistry` owned path | `ReceiverRegistry` process-key path | `DeviceInfoMonitorState` | View scope + `Visualzer` | `SecondTicker` + `ScreenStateController` | `CustomTextIconTintRoute` + `DarkTintRegistrationState` | single coroutine + atomic generation | `ReceiverRegistry.replaceModuleRegistration` | `WeakReference` + `ScreenStateController` |

Key findings:

1. **No two sites share the full set of required properties.** The most superficially similar pair — `CustomTextIconTintRoute` (`REATTACHABLE_VIEW`) and `ReceiverRegistry.registerOwnedReceiver` (`MULTI_OWNER_REGISTRATION`) — differ on re-attach, multi-owner coexistence, and owner type (View vs arbitrary).
2. **Several sites represent work, not owner identity.** `DeviceInfoMonitorState`, `AudioVisualizer.visualizerGeneration`, and `LockScreenAlbumArtController.requestGeneration` use monotonic tokens to discard stale work. Conflating these with owner-keyed slots would be a category error.
3. **Plain `WeakReference` + explicit lifecycle is already simpler in several cases.** `SecondTicker`, `WeatherDataController`, and `AudioVisualizer` only need a weak owner reference and an explicit `dispose` / `onDetachedFromWindow`. A generalized slot would add indirection without reducing code.
4. **Existing registries already solve the problem.** `ReceiverRegistry` covers both multi-owner weak receivers and process-key replacement. `StatusBarDisplayRegistry` + `OwnedRegistrations` + `WeakIdentityMap` covers per-display / pending owner state. `CustomTextIconTintRoute` + `DarkTintRegistrationState` covers the attach/detach/terminal-dispose view lifecycle. `PreferenceObserverRegistry` covers owner-bound observers. Introducing a new shared primitive would duplicate these tested abstractions.

## Fatal cleanup semantics

The `OwnedRegistrations` cleanup loop catches `Throwable` and calls `FatalErrors.unwrapAndRethrowIfFatal(t)` before logging. The executable semantics are:

- `OutOfMemoryError`, `ThreadDeath`, and any `VirtualMachineError` are rethrown immediately.
- `InvocationTargetException`, `ExecutionException`, and `XposedHelpers.InvocationTargetError` are unwrapped up to depth 4; if a cause is fatal, it is rethrown.
- If no fatal cause is found, the original throwable is returned and logged via `XposedHelpers.log`.

`StatusBarDisplayRegistry.releaseState` and `StatusBarDisplayRegistry.detach` call `state.registrations.cleanupAll()`, so the same fatal propagation applies there: a fatal error during release can propagate out of the registry. This is the intended behavior for a fatal JVM condition.

`CallbackGuard.guarded` uses a simpler inline check: it rethrows `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError` and logs others. This is the boundary used by many listener callbacks (`View.OnAttachStateChangeListener`, `BroadcastReceiver.onReceive`, `ContentObserver.onChange`, `Runnable.run`).

## Conclusion

- **RP0_RESULT:** `NO_SHARED_PRIMITIVE_JUSTIFIED`.
- **FIRST_PROOF_TARGET:** `NONE`.
- No production code, tests, Architecture C documentation, or runtime primitive were created or modified.
- `RP1` was not started.
- The only file added by this RP0 gate is this audit document.
