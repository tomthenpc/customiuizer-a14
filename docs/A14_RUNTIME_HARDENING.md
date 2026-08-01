# A14 Runtime Hardening

This document describes the active A14 runtime architecture. The code is the source of truth; this file only records what cannot be automatically inferred.

Scope: HyperOS 1 / Android 14 (SDK 34), `applicationId tv.withaibuild.customiuizer.r14`, libxposed target API 102, minimum API 101, `staticScope=false`.

## Current architecture

`MainModule.java` is a routing layer. It does not install hooks itself. It delegates each package/process to a dedicated installer in `app/src/main/java/tv/withaibuild/customiuizer/installers/`.

- `FeatureDefinition` (`mods/utils/FeatureDefinition.kt`) describes one feature: `id`, `name`, `preferenceKey`, `target`, `phase`, `isEnabled(prefs)`, `install()`.
- `FeatureId` (`mods/utils/FeatureId.kt`) carries a stable integer `id` plus a human name. `FeatureIds.kt` is the single registry of all feature identities.
- `FeatureInstallState` (`mods/utils/FeatureInstallState.kt`) is a process-scoped object keyed by the stable feature `id`. It replaces per-installer install-state maps.
- `FeatureInstallRegistry` (`mods/utils/FeatureInstallRegistry.kt`) is a short-lived install transaction: it filters by `FeatureTarget` and `InstallPhase`, checks `isEnabled` once, installs enabled definitions, and records process-scoped state without retaining definitions after the transaction.
- `FeatureInstallResult` is an `enum` with singleton values. There are no `data class` allocations for skipped or failed features.
- `InstallPhase` (`mods/utils/InstallPhase.kt`) and `FeatureTarget` (`mods/utils/FeatureTarget.kt`) give the registry its process and lifecycle filters.
- Base classes in `mods/utils/feature/` implement `FeatureDefinition` for each process/phase and keep hook installation separate from preference checks.

`ReceiverRegistry`, `PreferenceObserverRegistry`, `ContextResolver`, `CallbackGuard`, `HookInstallerFacade`, `ReflectionCache` and `ResourceHooks` are dedicated boundaries extracted from the historical `ModuleHelper`. Do not merge them back.

## Process routing

| Process / package | Installer | Phase | Notes |
|---|---|---|---|
| `system` / `android` | `SystemServerInstaller` | `SYSTEM_SERVER_STARTING` | Also sets up global actions when configured. |
| `com.android.systemui` | `SystemUiInstaller` | `PACKAGE_READY` / `SYSTEM_UI_INITIALIZED` | Calls `ReflectionCache.onSafeLifecycle` first. |
| `com.miui.home` | `LauncherInstaller` | `PACKAGE_READY` / `APPLICATION_ATTACHED` | Calls `ReflectionCache.onSafeLifecycle` first. |
| `com.android.settings` | `SettingsInstaller` | `PACKAGE_READY` | |
| `com.miui.securitycenter` | `SecurityCenterInstaller` | `PACKAGE_READY` | |
| `com.android.packageinstaller` / `com.miui.packageinstaller` | `PackageInstallerRouter` | `PACKAGE_READY` | Routes to package installer features. |
| `com.android.incallui` | `PhoneInstaller` | `PACKAGE_READY` | |
| `com.miui.powerkeeper` | `PowerKeeperInstaller` | `PACKAGE_READY` | |
| `com.miui.guardprovider` | `GuardProviderInstaller` | `PACKAGE_READY` | Loads DexKit on demand. |
| media / download providers | `MediaInstaller` | `PACKAGE_READY` | Loads DexKit on demand. |
| input method packages | `InputMethodInstaller` | `PACKAGE_READY` | |
| generic apps with post-attach hooks | `GenericAppInstaller` | `APPLICATION_ATTACHED` | Uses `LauncherPostAttachFeatures` for selected packages. |
| remaining packages (ANY target) | `CommonPackageFeatures` via `MainModule` | `PACKAGE_READY` | For hooks that do not need a dedicated installer. |

Phase rules:
- `MODULE_LOADED` and `SYSTEM_SERVER_STARTING` run when the process itself is created.
- `PACKAGE_READY` runs at `IXposedHookZygoteInit` / `onPackageReady` boundary.
- `SYSTEM_UI_INITIALIZED` and `APPLICATION_ATTACHED` run after the target app class loader is attached.

## Runtime invariants

The static gate `tools/check-invariants.py` enforces these. See `docs/RUNTIME_INVARIANTS.md` for the real defect behind each rule.

- Every framework callback (`onReceive`, `onChange`, `run`, listener lambdas, `Handler.handleMessage`, animation/observer callbacks) is wrapped with `ModuleHelper.guarded`.
- Every receiver/observer has a tracked owner and a bounded stale-registration queue.
- Hook callbacks do not use `XposedHelpers.getArgsArray()` for read-only parameters.
- `Handler()` always receives an explicit `Looper`.
- No Legacy `de.robv.android.xposed` API is used in runtime paths.
- API 102-only symbols do not enter API 101 cold paths:
  `setId`, `replaceHook`, `HotReloadingParam`, `HotReloadedParam` and `getApiVersion()`
  in callbacks are blocked by `tools/check-invariants.py`.
- Out-of-memory errors are rethrown at the boundaries of `HookInstallerFacade`,
  `ModuleHelper`, `FeatureInstallRegistry`, `ReflectionCache` and `ReceiverRegistry`.

## libxposed API boundary

- Compiled against `io.github.libxposed:api:102.0.0` and `service:102.0.0`.
- Public hook paths only use API 101 symbols: `XposedModule` lifecycle, `HookBuilder`, `Hooker.intercept`, `Chain.proceed`, `HookHandle.unhook`.
- API 102 `HotReloadingParam`, `onHotReloading`, `HookBuilder.setId`, `HookHandle.getId` and `replaceHook` are not used.
- `MainModule.java` remains a stable, API 101-compatible entry point. `staticScope=false`; Hot Reload is off.

## Hot-path and memory boundaries

`ResourceHooks` keeps resource replacement on the hot path fast:
- `fakes` is a `SparseIntArray`; `resourceIdReplacements` is a `SparseArray` published copy-on-write.
- Theme staging uses local `SparseIntArray` / `SparseArray` once, then writes back to the framework map once.

`ReflectionCache` keeps reflection on the cold path:
- Bounded `LoaderState` per class loader (max 4 loaders, 64 classes each).
- `onSafeLifecycle` resets dependency-method cache at process/package boundaries.

`FeatureInstallState` stores install state keyed by the stable feature `id`. There is one state map per process, not one per installer.

`FeatureInstallResult` is an `enum`; common results are singletons.

## Project lineage

- Original upstream: `Mikanoshi/CustoMIUIzer`.
- Android 14 functional upstream (read-only reference): `MonwF/customiuizer v24.10.12`.
- Current independent project: `tomthenpc/customiuizer-a14`.

The current repo is the source of truth. Do not reset, rebase or merge to upstream; do not copy upstream files over the current Kotlin implementation.

## Current component status

| Component | Status |
|---|---|
| Installer split | Done. `MainModule` only routes. |
| Feature registry | Done. `FeatureInstallRegistry` plus `FeatureInstallState`. |
| Stable feature IDs | Done. 245 compact integer IDs. |
| Enum install results | Done. `FeatureInstallResult` no longer allocates common results. |
| Callback guarding | Done. `ModuleHelper.guarded` and `CallbackGuard`. |
| Receiver/Observer registry | Done. `ReceiverRegistry` and `PreferenceObserverRegistry`. |
| ReflectionCache | Done. Bounded per-loader state; OOM does not write negative caches. |
| ResourceHooks sparse | Done. `SparseArray`/`SparseIntArray` hot path; real hook results honored. |
| Feature spec / lazy definition | Done. All package features return `LazyFeatureSpec`. A disabled feature costs only the fixed metadata and a lightweight lambda; zero `FeatureDefinition`, zero business installer object, zero Hook object. |
| API 102 stable hook ID | READY_NOT_WIRED. `XposedApiCapabilities` + `Api102HookBridge` isolated; `setId` not wired to production path yet. |
| Bitmap / View lifecycle, periodic SystemUI work | Not started. Deferred to A14-6G. |

## A14-6G final correctness status

| Item | Status |
|---|---|
| Feature lazy construction | VERIFIED_STATIC |
| Install OOM cleanup | VERIFIED_STATIC |
| Early preference restart semantics | VERIFIED_STATIC |
| ReflectionCache fatal boundary | VERIFIED_STATIC |
| API102 stable hook ID | READY_NOT_WIRED |
| Device validation | DEFERRED_EXTERNAL |

## Remaining static tasks

- Add static `isEnabled` predicates and filter disabled features before instantiating them in `XxxFeatures.all()`.
- Make installers share `FeatureInstallState` and remove per-installer `FeatureInstallRegistry` where possible.
- Consolidate small feature definitions that share the same target, phase and lifecycle domain.
- Remove confirmed pass-through helpers/facades that are pure single-call delegation and have no state, no lock and no independent test value.
- Audit dead source, tests and resources that have no production or test caller.

## Device-only validation

These cannot be proven by static checks:

- `WeakOwnerReceiver` cleanup under real GC and concurrent framework registration.
- `ReflectionCache.onSafeLifecycle` trigger timing on different ROMs.
- Resource-replacement latency and memory profile across SystemUI and Launcher theme changes.
- Whether any `guarded` fallback hides a real failure inside `system_server`.
- Fast-reboot receiver setup and the 10-second restart guard.

## Verification entry points

```bash
python tools/verify.py full
python tools/check-invariants.py
python -m unittest discover -s tools/tests -p "test_*.py"
```

At the current `devin/a14-runtime-hardening` HEAD:

- `check-invariants.py` reports 157 files, no violations.
- `compileDebugKotlin`, `compileDebugJavaWithJavac`, `testDebugUnitTest` and `lintDebug` pass.
- A14-6G correctness cleanup is complete; see `docs/VERIFICATION.md` for the final status matrix.

See `docs/VERIFICATION.md` for the current verification record and `docs/LSPOSED_LOG_ANALYSIS.md` for log triage.
