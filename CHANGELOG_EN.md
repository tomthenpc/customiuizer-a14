# Changelog

[简体中文](CHANGELOG.md) | English

## r14.16.1 — 2026-08-01

`versionCode 192`, targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Split SystemUI, Launcher, `system_server`, and regular-app entry points into process-routed installers. Stable feature IDs, explicit install states, and lazy definitions ensure one installation per process, while disabled features skip unrelated registration and business-object creation.
- Fixed concurrent early-preference bootstrap and empty-snapshot semantics. Failed installs no longer leave active definitions behind, and preference updates cannot reset installed Hooks to an uninstalled state.
- Made ReflectionCache bounded and isolated per ClassLoader. ResourceHooks now preserve real Hook results and concurrent installation semantics while reducing boxing, arrays, and name parsing on hit paths.
- Unified owner, replacement, active/stale, and release lifecycles for Receiver and Observer registrations. Stale weather, step counter, album-art, battery-indicator, and percentage-overlay state is released instead of retaining Contexts, Views, or intermediate Bitmaps.
- Ordinary failures remain isolated across shared Hook, Java/Kotlin, and logging boundaries, while `OutOfMemoryError` is always rethrown instead of being disguised as a compatibility failure.
- Reduced temporary objects and repeated work in network-speed sampling and formatting, charging hints, navigation-icon reloads, battery-indicator updates, and pass-through Hook argument paths.
- Preference switches now show the target checked state immediately before the existing persistence, disabled-state, and restart-requirement logic runs, improving feedback for rapid taps without changing final preference semantics.
- Module-load logs now include the version and short Git SHA. API 102 stable Hook ID support remains isolated as `READY_NOT_WIRED` and is not connected to production paths.

### Verification Boundary

- The release commit passes `python tools/verify.py full`, covering runtime invariants, Debug Kotlin/Java compilation, unit tests, and `lintDebug`.
- The formal Release APK uses the A14-specific certificate; version, SHA-256, signature, zip alignment, `debuggable=false`, and Xposed metadata are recorded in the GitHub Release.
- The existing Xiaomi 13 / HyperOS 1 baseline found no module-attributable P0/P1, duplicate-install, or stuck-installing issue. The new runtime and UI changes in this release have not completed per-feature device behavior verification and are not claimed as fully `DEVICE_VERIFIED`.

### Historical Core Implementation Summary

The r14 line established an independent package, signing identity, and HyperOS 1 / Android 14 maintenance path; completed staged Kotlin migration of settings and core Hooks; delivered one-APK libxposed API 101/102 compatibility; restored the `system` scope; fixed preference delivery and quick restart; governed Receiver, Observer, and View lifecycles; hardened reflection and resource caches; optimized status-bar and Launcher hot paths; and continuously refined network speed, lock screen, control center, and settings UI behavior. Fine-grained history remains in Git commits and historical tags, while obsolete APKs are no longer retained as Release assets.
