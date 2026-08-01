# A14 Runtime Hardening

This document covers the runtime hardening work on `devin/a14-runtime-hardening` at HEAD `fdc9ad3b`.
Scope is the LSPosed module's runtime inside `system_server`, `com.android.systemui`,
`com.miui.home` and other scoped packages on HyperOS 1 / Android 14 (SDK 34).
Target API 102, minimum API 101, `applicationId tv.withaibuild.customiuizer.r14`.

## Summary

- **MainModule package-installer split** — `MainModule.java` no longer installs every hook itself.
  Package-specific `*Installer` classes were extracted in `9a0938b3` (SystemUI), `5846c746` (Launcher),
  `0b9a9ab7` (input method), `4c615c00` (settings), `141a1c51` (security center),
  `0c2545c3` (remaining packages) and `a9f293f1` (finalize split).
- **Owner receiver active/stale state** — `WeakOwnerReceiver` gained an `AtomicBoolean active` flag and
  `ReceiverRegistry` bounded stale queues for failed unregisters. See `53de4a25`.
- **ModuleHelper split** — `ReceiverRegistry`, `PreferenceObserverRegistry`, `ContextResolver`,
  `CallbackGuard` and `HookInstallerFacade` were extracted from `ModuleHelper.kt` in `40e9af87`,
  `d4051de7`, `a0502c91`, `2ef0a28b` and `1701cf7a`.
- **Callback-guard audit** — every framework-invoked callback in `mods/` and `mods/utils/` was wrapped
  with `ModuleHelper.guarded`. Audited in `1570792d` (`mods/utils/`), `11bd74a5` (Controls/GlobalActions),
  `039879df` (System/Launcher), `e5417d00` (SystemUI/GlobalActionSystemServerHooks) and
  `697d7565` (remaining `mods/`).
- **Hot-path / memory audit** — `ReflectionCache` bounded per-loader state in `35a35c33`,
  and `ResourceHooks` replaced boxed `HashMap<Int, *>` with `SparseArray`/`SparseIntArray` in `71ff6e9f`.

## Architecture

The module is split into package-specific installers under `app/src/main/java/tv/withaibuild/customiuizer/installers/`.
Each installer is a stateless class that builds a `FeatureInstallRegistry`, registers `FeatureDefinition`
instances and calls `installAll` for the matching `FeatureTarget` and `InstallPhase`.

- `FeatureDefinition.kt` (`app/.../mods/utils/FeatureDefinition.kt:13`) declares a feature as a typed
  `id: FeatureId`, `name`, `preferenceKey`, `target`, `phase`, `lateInstallPolicy`, `restartRequirement`,
  `isEnabled(prefs)`, `install()` and `onPreferenceChanged(key, prefs)`.
- `FeatureInstallRegistry.kt` (`:14`) installs each `FeatureId` at most once per process, tracks
  `FeatureState` (`:67-91`) and handles preference changes without resetting an installed hook
  to uninstalled (`:101-118`).
- `FeatureTarget.kt` (`:9-27`) and `InstallPhase.kt` (`:10-31`) give the registry the process and
  lifecycle filters it needs.
- Base classes such as `BasePackageReadyFeature` (`feature/BasePackageFeatures.kt:13`) and
  `BaseSystemServerFeature` (`feature/SystemServerFeatures.kt:24`) implement `FeatureDefinition` and
  keep hook installation and preference checks separate.

`MainModule.java` is now a routing layer:

- `onSystemServerStarting` (`:111`) sets the package name to `android` and calls `SystemServerInstaller`.
- There is no `MainModule.loadPackage()`; package hooks enter through `onPackageReady` (`:131`).
  It filters non-first packages and excluded packages, then delegates to the installers:
  `AndroidPackageInstaller.install` (`:152`), `InputMethodInstaller.install` (`:165`),
  a `CommonPackageFeatures` registry for `ANY` / `PACKAGE_READY` (`:170-174`),
  `MediaInstaller.install` (`:179`), `SystemUiInstaller.install` (`:266`),
  `GuardProviderInstaller.install` (`:270`), `PhoneInstaller.install` (`:274`),
  `SecurityCenterInstaller.install` (`:278`), `PowerKeeperInstaller.install` (`:282`),
  `SettingsInstaller.install` (`:286`), `PackageInstallerRouter.install` (`:290`),
  `LauncherInstaller.install` (`:297`) and `GenericAppInstaller.installPostAttach` (`:306`).
- `loadDexKit()` (`:100`) is no longer called unconditionally. Features that need DexKit
  (`MediaFeatures.kt:51`, `GuardProviderFeatures.kt:33`) call it from their `install()` body.
- `ReflectionCache.onSafeLifecycle(classLoader)` is called before `SystemUiInstaller.install` (`:182`)
  and `LauncherInstaller.install` (`:296`) so cached misses can be retried at a safe boundary.

## Lifecycle & Receiver Safety

`ModuleHelper.kt` now delegates all registration to `ReceiverRegistry.kt`:

- `registerModuleReceiver` (`ReceiverRegistry.kt:72`) is for process-scoped, single-receiver registrations.
  It replaces any previous registration under the same key, retries stale unregisters first,
  performs the framework registration outside the map lock, and self-unregisters if a concurrent
  replacement won the race (`:127-142`). Failed unregisters are kept in a bounded stale queue
  (`MAX_STALE_MODULE_RECEIVERS = 3`, `:37`; `recordStaleModuleReceiver`, `:200-238`) and retried on
  the next same-key operation.
- `registerOwnedReceiver` (`:392`) is for multi-instance hook targets. It holds the owner and context
  through `WeakReference`, sweeps collected owners, and keeps only one registration per owner/key
  (`:407-427`). Framework registration is again outside the map lock, with a loser self-unregister
  race check (`:435-450`).
- `replaceModuleRegistration` (`:521`) is the non-receiver equivalent for content observers, listeners
  or other teardownable registrations. It records a `Runnable` cleanup under a key, runs the previous
  cleanup, and tracks failed cleanups in a bounded stale queue (`MAX_STALE_MODULE_REGISTRATIONS = 3`,
  `:505`; `recordStaleModuleRegistration`, `:560-595`).
- `WeakOwnerReceiver` is an internal class in `ReceiverRegistry.kt` (`:300`). It stores a
  `WeakReference<Any>` owner and an `AtomicBoolean active` (`:305-306`). `markInactive()` (`:308`)
  disables the receiver. `onReceive` (`:312`) wraps its body with `ModuleHelper.guarded`; if `active`
  is false or the owner was collected, it removes the registration and unregisters itself.

`OutOfMemoryError` is rethrown through all these paths because continuing after an OOM would leave
system_server, SystemUI or Launcher in a corrupt state. Non-OOM throwables are logged and swallowed.

## Callback Guarding

`ModuleHelper.guarded` is an `inline` wrapper that forwards to `CallbackGuard.kt` (`:23-30` and `:39-47`).
It catches `Throwable`, logs it through `XposedHelpers.log(t)`, and rethrows only `OutOfMemoryError`.
The value-returning overload returns a `fallback` on non-OOM failure.

Callbacks must be guarded because `Handler.handleMessage`, `BroadcastReceiver.onReceive`,
`ContentObserver.onChange`, `Runnable.run` and listener lambdas run outside the `MethodHook` try/catch.
A reflective miss, a `NumberFormatException` from a malformed preference, or a ROM-renamed field inside
one of those bodies can take down a system process. The fallback for value-returning callbacks must be
chosen so the host's default behavior is preserved.

The audit covered:

- `mods/utils/` (`1570792d`), including `ReceiverRegistry.kt`, `PreferenceObserverRegistry.kt` and
  `ResourceHooks.kt`.
- `mods/Controls.kt` and `mods/GlobalActions.kt` (`11bd74a5`).
- `mods/System*.kt` and `mods/Launcher*.kt` (`039879df`).
- `mods/SystemUI*.kt` and `mods/GlobalActionSystemServerHooks.kt` (`e5417d00`).
- the remainder of `mods/` (`697d7565`).

`PreferenceObserver.onChange` is not required to wrap itself because `PreferenceObserverRegistry.
handlePreferenceChanged` (`:92-99`) already isolates each observer and logs failures.

## Hot Path & Memory

`ResourceHooks.kt` replaced hot-path `Map<Int, *>` lookups with unboxed sparse collections:

- `fakes` is a `SparseIntArray` (`:54`) and `resourceIdReplacements` is a `SparseArray<ResourceValue>`
  (`:57`). Both are published copy-on-write under `replacementsLock` (`:51-58`) so the `mReplaceHook`
  `intercept` method (`:61-98`) only does fast `SparseArray` reads on every `Resources` call.
- `initThemeHook()` (`:105-178`) builds per-call `SparseIntArray`, `SparseArray<IntArray>` and
  `SparseArray<Array<String>>` for staged theme values, then writes them back to the framework
  `HashMap` only once. This removes `Integer` boxing from the local staging path.

`ReflectionCache.kt` keeps reflection on the cold path:

- `MAX_LOADERS = 4` and `MAX_CLASSES_PER_LOADER = 64` (`:60-61`).
- Each `ClassLoader` gets a bounded `LoaderState` (`:34-49`) with an LRU `LinkedHashMap` for
  `classResults` and a cached `Dependency` method.
- After a successful lookup, `getDepInstance()` (`:87-93`) returns the dependency with a single map
  read and a `when` branch; no reflection is re-run on the hot path.
- `onSafeLifecycle()` (`:105-117`) increments a global lifecycle and resets the dependency-method cache
  so `MethodMissing` / `DependencyNotReady` results can be retried at a safe boundary.

## Feature Install Result & State Slimming

In commits `c9852a1a` and `fdc9ad3b` the feature-install infrastructure was refactored to reduce object
allocations and share per-process state:

- `FeatureInstallResult` is now an `enum` (`FeatureInstallResult.kt:9`) with singleton values for
  `INSTALLED`, `ALREADY_INSTALLED`, `SKIPPED`, `FAILED_TRANSIENT`, `FAILED_PERMANENT` and `RESTART_LATER`.
  This removes the per-call `data class` allocations for skipped and failed features.
- `FeatureInstallRegistry` no longer forces a result list: `installAll()` takes an optional
  `collectResults` flag and returns `emptyList()` when the caller does not need the results.
- `FeatureId` now carries a stable integer `id` (`FeatureId.kt:11`) and all 245 feature identities in
  `FeatureIds.kt` were assigned compact, stable IDs.
- `FeatureInstallState` (`FeatureInstallState.kt`) is a process-scoped object that stores
  `FeatureState` keyed by the integer feature ID.  `FeatureInstallRegistry` now delegates to it,
  removing the per-installer `HashMap<FeatureId, FeatureState>` that each registry used to create.

This set the foundation for the next step: filtering disabled features before instantiating them in
`XxxFeatures.all()` and moving each installer to a shared, single-state installation path.

## Verification

Commands used for this baseline:

```bash
python tools/verify.py full
python tools/check-invariants.py
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon compileDebugKotlin compileDebugJavaWithJavac
./gradlew --no-daemon lintDebug
```

At HEAD `fdc9ad3b` we executed the equivalent of the `full` pipeline:

- `python tools/check-invariants.py` reports `153 files, no violations`.
- `compileDebugKotlin` and `compileDebugJavaWithJavac` succeed.
- `testDebugUnitTest` succeeds (330 tests).
- `lintDebug` succeeds.

What the static gate confirms:

- Every framework callback in `mods/` is guarded (`check_guard_framework_callbacks` and
  `check_guard_deferred_callbacks` in `tools/check-invariants.py:117-189`).
- No raw `Context.registerReceiver` in `mods/` except in `ModuleHelper.kt` and `ReceiverRegistry.kt`
  (`check_no_raw_register_receiver`, `:219-269`).
- Hook installation goes through `ModuleHelper`/`HookInstallerFacade`
  (`check_no_direct_hook_installation`, `:316-330`).
- No legacy `de.robv.android.xposed` references outside the three boundary files
  (`check_no_legacy_xposed`, `:333-345`).
- `Handler()` always has an explicit Looper; no redundant argument marshalling in hooks;
  no single-character `toRegex()` splits in hot paths (`:272-378`).

What still requires on-device validation:

- Whether `WeakOwnerReceiver` cleanup under real GC and framework broadcast timing is race-free.
- Whether the `ReflectionCache` `onSafeLifecycle` boundary is triggered late enough on every ROM.
- Whether the `SparseArray` resource-replacement path keeps the same latency and memory profile
  across SystemUI and Launcher theme changes.
- Whether any `guarded` fallback hides a real failure inside system_server.
- The 10-second restart guard behavior (`MainModule.java:250-264`) and fast-reboot receiver setup.

## Related Docs

- [Runtime invariants and the rules behind them](RUNTIME_INVARIANTS.md)
- [System server starting audit](SYSTEM_SERVER_STARTING_AUDIT.md)
- [System scope audit](SYSTEM_SCOPE_AUDIT.md)
- [Verification record](VERIFICATION.md)
- [LSPosed log analysis](LSPOSED_LOG_ANALYSIS.md)
- [Agent rules](../AGENTS.md)
