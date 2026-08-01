# A14 Framework Audit — Round 1

Scope: HyperOS 1 / Android 14 (SDK 34), branch `devin/a14-rom-intelligence-audit`.
Date: 2026-08-02.

## Framework call graph (P0 baseline)

```text
MainModule
  onModuleLoaded -> PreferenceBootstrap / XposedApiCapabilities
  onSystemServerStarting -> ProcessScope.SYSTEM_SERVER -> SystemServerInstaller
  onPackageReady -> ProcessRouter.resolve() -> ProcessScope
                      -> Early deny (isInstallable == false)
                      -> CommonPackageFeatures (ANY, PACKAGE_READY)
                      -> Installer per ProcessScope
                         SYSTEM_UI    -> ReflectionCache + SystemUIInitializer hook + SystemUiInstaller
                         LAUNCHER     -> ReflectionCache + LauncherInstaller
                         SETTINGS     -> SettingsInstaller
                         SECURITY     -> SecurityCenterInstaller
                         ...
                         GENERIC_APP  -> GenericAppInstaller.installPostAttach

FeatureSpec / LazyFeatureSpec / FeatureDefinition
  -> FeatureInstallRegistry.register / installAll
  -> FeatureInstallState (per process, per FeatureId)
  -> FeatureInstallResult (enum of INSTALLED / ALREADY_INSTALLED / SKIPPED / FAILED_*)

Installers
  -> Hook / Controller / Receiver / Observer / View additional fields
  -> HookDiagnostics per stage
```

## What was already unified

- `FeatureSpec` / `LazyFeatureSpec` / `FeatureDefinition` already form a single lazy feature pipeline.
- `FeatureInstallResult` is already a typed enum; no change needed.
- `FeatureInstallRegistry` already filters by `FeatureTarget` and `InstallPhase`, creates results only when `collectResults = true` in production, and never keeps a global per-feature instance.
- `FeatureTarget` and `InstallPhase` enums already cover the A14 surface.

## What was changed in this round

### 1. `ProcessScope` and `ProcessRouter` (P1)

- Added `ProcessScope` covering all requested A14 processes: `SYSTEM_SERVER`, `SYSTEM_UI`, `SYSTEM_UI_PLUGIN`, `LAUNCHER`, `SETTINGS_MAIN`/`_REMOTE`, `SECURITY_CENTER_MAIN`/`_REMOTE`/`_BOOTAWARE`, `POWER_KEEPER`, `WALLPAPER`, `MEDIA`, `PHONE`, `GUARD_PROVIDER`, `PACKAGE_INSTALLER`, `INPUT_METHOD`, `GENERIC_APP`, `NETWORK_STACK`, `UNSUPPORTED`.
- Added `ProcessRouter.resolve(packageName, processName)` as the single table-driven package/process mapping.
- `MainModule.onPackageReady` now resolves the scope once, rejects non-installable scopes (`UNSUPPORTED`, `*_REMOTE`, `*_BOOTAWARE`, `NETWORK_STACK`), and then uses `scope == ProcessScope.X` for installer dispatch instead of `pkg.equals("...")`.
- `MainModule` still owns SystemUI/Launcher context-dependent init because those need live `Context` / `ClassLoader` access; they were not split into separate coordinators in this round.

### 2. Tests (P0/P1)

- `ProcessRouterTest` covers all listed process variants, remote variants, media/wallpaper, input method exact/prefix, unsupported, network stack, and `isInstallable`.
- Updated `SystemUiInstallerTest` to use the new `if (scope == ProcessScope.SYSTEM_UI)` / `if (scope == ProcessScope.GUARD_PROVIDER)` section markers.

## Remaining work (deferred to avoid large rewrites in one round)

### P2: MainModule / Feature system further simplification

- `MainModule` still contains the full SystemUI post-init hook block. Extract a `SystemUiCoordinator` that owns `fastRebootReceiver`, `statusBarSetup`, `preferenceWatch`, and 10s restart window.
- `Launcher` post-attach logic can move to a `LauncherCoordinator`.
- `CommonPackageFeatures` / `GenericAppInstaller` generic-feature matching is still package string based; consider moving to a `GenericFeatureResolver` that consumes `ProcessScope.GENERIC_APP`.

### P3: Startup / hot path cost

- No new optimizations beyond existing lazy `FeatureDefinition` construction. Add per-install internal counters to measure `ProcessScope.resolve()` calls, `FeatureSpec` construction, and `CommonPackageFeatures` repeated builds.
- Introduce bounded `CompatibilityCache` keyed by `ClassLoader` once `ProcessScope` and `ClassLoader` sources are fully aligned.

### P4: Large file split

- `MainModule.java` still > 300 lines after this round.
- `SystemUiFeatures.kt`, `SystemUIStatusBarHooks.kt`, `SystemUIControlCenterHooks.kt` remain large; split by `statusbar view`, `gesture state machine`, `control center plugin`, `clock ticker` in later rounds.

### P5: Documentation / generated data

- `A14_PROCESS_MATRIX.md` and `A14_TARGET_MATRIX.md` still need a deterministic generator so Markdown/CSV/JSON counts cannot drift.
- `A14_FEATURE_RETIREMENT.md` and `.csv` already match (240); `A14_FEATURE_RETIREMENT_AUDIT.md` (245) remains a separate detailed appendix.

## Verification run

- `python tools/verify.py full` — passed
- `python tools/check-invariants.py` — 159 files, no violations
- `python -m compileall tools` — passed
- `python -m unittest discover -s tools/tests -p "test_*.py"` — 89 passed
- `./gradlew.bat :app:testDebugUnitTest` — passed
- `./gradlew.bat :app:lintDebug` — passed
- `git diff --check` — passed
