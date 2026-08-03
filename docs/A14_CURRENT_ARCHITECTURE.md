# A14 CURRENT Architecture

```text
DocumentKind: CURRENT
Product: CustoMIUIzer A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: d189ad12fc50522ada4772fcb6e5afb510469e01
EvidenceState: STATIC
GeneratedBy: test_current_architecture.py
SourceOfTruth: app and tools source trees
```

## 1. Startup and installation entry points

- `MainModule` (`app/src/main/java/tv/withaibuild/customiuizer/MainModule.java`) is the sole Xposed entry point. It extends `XposedModule` and implements `onModuleLoaded`, `onSystemServerStarting`, and `onPackageReady`.
- `onPackageReady` receives `PackageReadyParam` and calls `PreferenceBootstrap.bootstrap()` to load the remote-preference snapshot into the static `PrefMap`.
- `initPrefs()` only proceeds when `PreferenceBootstrap.isReady()` returns `true` (states `LOADED` or `VALID_EMPTY`).
- `MainModule` does not install hooks itself; it routes to dedicated installers via `ProcessRouter.resolve()`.
- Installer methods are exposed to Java through the Kotlin `@JvmStatic` ABI: each takes `PackageReadyParam` and `PrefMap`.

## 2. Process and package routing

- `ProcessRouter` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt`) maps a package/process pair to a `ProcessScope`.
- `system_server` is routed to `ProcessScope.SYSTEM_SERVER` and handled by `SystemServerInstaller` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt`).
- `SystemUI` is routed to `ProcessScope.SYSTEM_UI` main; `SYSTEM_UI_PLUGIN` is rejected. `SystemUiBootstrapCoordinator` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt`) installs the `SystemUIInitializer` bootstrap before `SystemUiInstaller` runs.
- `Launcher` (`com.miui.home`) is routed to `ProcessScope.LAUNCHER` and handled by `LauncherInstaller` (`app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.kt`).
- `SecurityCenter` is routed to `SECURITY_CENTER_MAIN`; `SECURITY_CENTER_BOOTAWARE` and `SECURITY_CENTER_REMOTE` are rejected. The main process is handled by `SecurityCenterInstaller` (`app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.kt`).
- Generic and package-specific app processes are handled by `GenericAppInstaller` and `PackageInstallerRouter` (`app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.kt`, `app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.kt`).
- Each installer creates a `FeatureInstallRegistry`, registers `FeatureSpec` entries from its package catalog, and calls `installAll(target, phase, mPrefs)`.

## 3. Feature architecture

- `FeatureIds` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt`) declare 245 stable `data object` identities with integer IDs 0..244. Each implements the `FeatureId` interface.
- `LazyFeatureSpec` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/LazyFeatureSpec.kt`) is the internal implementation of `FeatureSpec`. It carries metadata plus `enabled` and `factory` lambdas, so disabled features do not allocate their `FeatureDefinition`.
- `FeatureInstallRegistry` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt`) accepts `register()`, de-duplicates by `FeatureId`, and runs `installAll()` across enabled specs.
- Per-package catalog files in `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/` provide `all(lpparam, mPrefs): List<FeatureSpec>` for each installable process.
- Hook ownership is enforced by `tools/audit_hook_ownership.py`: 720 REGISTRY_FEATURE hooks, 25 INSTALLER_INFRASTRUCTURE hooks, 9 API_BRIDGE hooks, and 2 RESOURCE_INFRASTRUCTURE hooks.

## 4. Hook ownership

- `INSTALLER_INFRASTRUCTURE` hooks live in installers and bootstrappers such as `SystemUiBootstrapCoordinator` and `SystemServerInstaller`.
- `REGISTRY_FEATURE` hooks are installed by `FeatureDefinition` implementations through `ModuleHelper.findAndHookMethod()` wrappers; `HookDiagnostics` records each call.
- `API_BRIDGE` hooks are in `Api102HookBridge` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt`) and provide API 102 capability detection with API 101 fallback.
- `RESOURCE_INFRASTRUCTURE` hooks are in `ResourceHooks` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ResourceHooks.kt`) for hot-path `getText` / `getString` / `getLayout` / `getDrawableForDensity` replacement.
- Duplicate installation is prevented by `FeatureInstallRegistry.register()` using `putIfAbsent` and `FeatureInstallState.beginInstall()` atomically claiming `INSTALLING`/`INSTALLED`.

## 5. ClassLoader and lifecycle

- The module ClassLoader loads `MainModule`, Kotlin runtime, and all feature code. Static state such as `MainModule.mPrefs` and `ResourceHooks` lives here.
- The target process ClassLoader arrives in `PackageReadyParam.classLoader` and is used by `XposedHelpers.findClassIfExists()` and `ReflectionCache` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt`).
- `ReflectionCache` maintains per-`ClassLoader` `LoaderState` instances. `ControlCenterGestureRuntimeHolder` detects a new ClassLoader on `bind()` and calls `machine.clear()` before rebinding.
- `FeatureInstallState` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallState.kt`) tracks `NOT_INSTALLED`, `INSTALLING`, `INSTALLED`, `FAILED_TRANSIENT`, and `FAILED_PERMANENT` per `FeatureId` in a process-scoped map.
- Gesture runtime uses `ControlCenterGestureRuntimeHolder.bind()` / `unbind()`. View attach/detach calls `GestureMachine.prepare(ownerId)` and `GestureMachine.clear(ownerId)`.
- `PhysicalGestureArbiter.release`, `releaseOwner`, and `releaseAll` remove owner tokens when views detach or runtime unbinds.

## 6. Java/Kotlin boundary

- `MainModule.java` is the only Java entry point. It calls Kotlin installer methods annotated with `@JvmStatic` and exact JVM signatures (`install(PackageReadyParam, PrefMap)`).
- All feature logic, installers, and utilities are implemented in Kotlin. `FeatureSpec`, `LazyFeatureSpec`, and `FeatureDefinition` are Kotlin types.
- `ModuleHelper` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt`) exposes helpers via `@JvmStatic`. `ProcessRouter.resolve()` uses `@JvmStatic @JvmOverloads` for the optional `processName` parameter.
- The architecture keeps Java/Kotlin boundaries at the installer-ABI surface; features do not use Java.

## 7. Configuration and event flow

- `PrefMap` (`app/src/main/java/tv/withaibuild/customiuizer/utils/PrefMap.kt`) is a process-local `AtomicReference<Map<String, Any>>` snapshot. It supports full-snapshot replacement and single-key CAS updates.
- `PreferenceBootstrap` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt`) registers one `OnSharedPreferenceChangeListener` on remote SharedPreferences. `onPreferenceChanged(key)` updates `PrefMap` and is wrapped in `ModuleHelper.guarded` for failure isolation.
- `PreferenceObserverRegistry` dispatches preference-changed events to registered `ModuleHelper.PreferenceObserver` instances.
- Hot paths avoid I/O, reflection, and allocation: `ResourceHooks` uses fixed `ResourceGetterKind` mappings, `PrefMap` caches parsed ints in `ConcurrentHashMap`, and gesture callbacks pass through `GestureSideEffectGate` to prevent duplicate side effects.

## 8. Verification architecture

- `tools/verify.py` runs targeted and full machine verification modes: `fast`, `full`, and `final`. `scripts/verify.ps1` orchestrates the same gates on Windows.
- `tools/check-invariants.py` audits source for invariant violations such as unguarded `catch(Throwable)`, duplicate feature registries, and lifecycle cleanup gaps.
- `tools/check_document_contracts.py` enforces `DocumentKind`, `EvidenceCommit` ancestry, and required metadata on `docs/**/*.md` and `docs/**/*.json`.
- `tools/check_automation_state.py` reconciles repository, branch, upstream, and control-plane files.
- `tools/audit_hook_ownership.py` classifies every hook call and produces the hook-ownership report.
- `tools/progress_snapshot.py` generates `docs/progress/A14_PROGRESS_CURRENT.json` and `docs/progress/A14_PROGRESS_CURRENT.md` from `TASK_STATE.md` using evidence-gated scoring.
- `tools/tests/` contains 175+ unit tests. Python tests run with `python -m unittest discover -s tools/tests -p "test_*.py"`.
- Device evidence is deliberately `NOT_EXERCISED` until real ROM/device testing is completed; no synthetic device evidence is generated.

## 9. Current known limitations

- `DEVICE_LIFECYCLE_ENTRY_BLOCKED`: no reliable plugin/ClassLoader destruction hook was found in the repository, framework stub, or ROM intelligence. `ControlCenterGestureRuntimeHolder.unbind()` is available but not yet wired to a plugin destroy entry.
- `DEVICE-001` in `TASK_STATE.md` is `BLOCKED_EXTERNAL`; real device validation remains pending.
- Many `COMPLETE`/`VERIFIED_*` tasks are currently scored in the `evidence_pending` bucket because they lack a canonical `EvidenceCommit`, paths, and commands. This is a provenance gap, not an engineering-quality claim.
- `P12.3 Gesture event contract` and `P12.4 APK delta` are still `TODO` and are not treated as finalized architecture in this document.

## 10. Evidence

The following source files exist at `EvidenceCommit` and contain the key symbols named in this document:

- `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/utils/PrefMap.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/LazyFeatureSpec.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ResourceHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt`
- `tools/verify.py`
- `scripts/verify.ps1`
- `tools/check-invariants.py`
- `tools/check_document_contracts.py`
- `tools/check_automation_state.py`
- `tools/audit_hook_ownership.py`
- `tools/progress_snapshot.py`
- `TASK_STATE.md`
