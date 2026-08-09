# Changelog

English | [简体中文](CHANGELOG_CN.md)

## r14.18.6 — 2026-08-09

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Rebuilt settings navigation around lazily generated category pages and a generated search index, reducing initial Preference creation while removing transition residue and reorganizing the Various section.
- Removed repeated caller stack scans from Launcher gestures, charging hints, and Security Center dock handling; added bounded process-local routing and setup metrics without moving work into hot paths.
- Fixed AudioVisualizer and battery-indicator observer ownership, startup activation-state races, and stale app selections restored from backups.
- Fixed custom status-bar height in apps where only the icons moved while the actual status-bar/content area kept the stock height; WindowInsets and app-window geometry now follow the configured height.
- Clarified input-method naming: the category is generic while Gboard-only padding controls remain explicitly labeled.
- Added **in-development** HyperOS status-capsule controls for charging, silent mode, and Do Not Disturb; status-bar height matching remains under compatibility work.

### Verification Boundary

- Unified static rules, invariants, Python tests, Android JVM tests, compilation, and lint passed; the official APK is additionally checked during release publication.
- Charging-scenario integration is complete; status-capsule height matching remains under compatibility work.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.18.6.apk`
- versionCode / versionName: `196 / r14.18.6`

---

## r14.18.2 — 2026-08-08

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Improved compatibility and fail-closed behavior for heads-up notification and lockscreen hooks on HyperOS / Android 14.
- Fixed charging-info font size being reset after lockscreen indication updates.
- Simplified several SystemUI / system_server lifecycle hooks by using native after callbacks and removing redundant state handling.
- Strengthened fatal-error propagation during hook installation so fatal failures are not treated as ordinary install failures.

### Verification Status

- Feature-semantics validation, source-hazard scanning, invariant checks, Python tool tests, Android JVM unit tests, lint, and R8 analysis passed.
- The signed Release APK passed version, certificate, v2 signing, zip alignment, SDK, ABI, debuggable, and Xposed entry checks.
- The release candidate was installed and passed device smoke validation.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.18.2.apk`
- Size: `3468849` bytes
- SHA-256: `77F868590C631271251991EDEBF066919460E2F1DA955EFDC10271207EAF3E77`
- versionCode / versionName: `195 / r14.18.2`

---

## r14.18.1 — 2026-08-07

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Build toolchain upgrade: JDK 25, Gradle 9.6.1, AGP 9.3.1; Gradle Daemon JVM criteria pinned to Java 25.
- Java source/target remain 17; Android Java compiler output remains 17 while Gradle and the compiler toolchain run on 25.
- `.idea` metadata hygiene: moved local/generated IDE state (Gradle IDE model, deployment targets, repository mirrors, migration state, inspection profile) out of Git tracking, preserving shared code styles, compiler target hint, encoding, and VCS mapping.
- Fixed a fail-open in `SystemLockScreenHooks` where a failed `handleIncomingUser` resolution fell back to user 0; the hook now returns the original method result instead of continuing CustoMIUIzer wallpaper post-processing.

### Verification Boundary

- This Release-only build did not run `python tools/verify.py full`, because that mode triggers `assembleDebug`.
- Feature-semantics validation, source-hazard scanning, invariant checks, `git diff --check`, and JVM unit tests were run and passed.
- Release APK is R8-minified, resource-shrunk, and officially signed.
- This release is not claimed as fully `DEVICE_VERIFIED`; the no-reboot status-bar-height sequence awaits device verification.

### Known Major

- `SystemNotificationHooks` notification menu falls back to user 0 when `UserHandle.getUserId` resolution fails, which may open app info or force stop the wrong user.
- `Various.kt` AppInfo app launch falls back to user 0 when `UserHandle.getUserId` resolution fails, which may launch the app for the wrong user.

---

## r14.18.0 — 2026-08-06

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Added adjustable lock-screen charging text size; the default keeps the system text size and changes apply after restarting SystemUI.
- Hardened charging-info initialization and hot paths. Disabled details skip unnecessary work, reducing duplicate installation, invalid reads, and fallback overhead.
- Fixed a possible SystemUI crash when status-bar battery or temperature information is enabled, and hardened stale Handlers, detached Views, ROM field fallbacks, and custom-icon creation.
- Fixed left-side custom status-bar text icons becoming invisible on dark backgrounds by completing tint registration, initial synchronization, recreation, and release lifecycles.
- Added status-bar height synchronization with WindowInsets and the SystemUI window, including runtime application and restoration of the system height when disabled; no-reboot fuxi switching still awaits device verification.
- Hardened status-bar and control-center gesture, View, callback, and ClassLoader lifecycles to reduce duplicate effects, state conflicts, and stale-object retention.
- Optimized process routing, feature install deduplication, and disabled-feature initialization. Ordinary failures remain isolated while fatal errors continue to propagate.
- Added Git revision and APK provenance records, with feature semantics, Python gates, unit tests, and lint integrated into the unified verification flow.

### Verification Status

- `python tools/verify.py full`, feature-semantics validation, source-hazard scanning, CI portability checks, and the full Python suite pass.
- All 405 Python tool tests pass, together with Android JVM unit tests and `lintDebug`.
- The no-reboot `44 → 40 → 12 → 44 → disabled` status-bar-height sequence has not yet been run on fuxi, so this release is not claimed as fully `DEVICE_VERIFIED`.

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
