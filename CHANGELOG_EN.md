# Changelog

[简体中文](CHANGELOG.md) | English

This file records user-visible changes, compatibility boundaries, verification conclusions, and
rollback value for public releases. Internal migration batches, agent work logs, temporary APKs,
and performance figures without same-condition measurements are not release changelog entries.

## Public Releases

| Version | Date | Purpose |
| --- | --- | --- |
| `r14.13.5` | 2026-07-28 | Current stable release; fixes r14.13.4 home search navigation regression |
| `r14.13.4` | 2026-07-28 | Withdrawn; home search navigation regression, superseded by `r14.13.5` |
| `r14.12.0` | 2026-07-26 | Previous stable release; old signature, so back up and reinstall before upgrading |
| `r14.8.0` | 2026-07-25 | Kotlin infrastructure rollback point |
| `r14.7.4` | 2026-07-25 | Consolidated r14.7.x Kotlin/coroutine migration release |
| `r14.5.0` | 2026-07-24 | Independent package, signing, and release-path baseline |

Release titles contain only the version number. Asset names, sizes, and SHA-256 digests for
removed releases are in the [historical Release archive](docs/RELEASE_ARCHIVE.md); the
corresponding source remains available through Git tags.

## [r14.13.5] - 2026-07-28

### Release scope

Emergency hotfix for `r14.13.4`. Fixes the home search navigation regression affecting `Various`
results and sub-category jumps, restores the `0/1/2` search state machine, unifies empty/blank
`sub` semantics, and corrects the return value of `openModCat()`. Everything else remains identical
to `r14.13.4`.

This release supports only HyperOS 1 / Android 14 and `arm64-v8a`, while retaining the one-APK
libxposed API 101/102 compatibility boundary. It is signed with the same new official certificate as
`r14.13.4` and can be installed over `r14.13.4`.

### Fixed

- Fixed search results belonging to `Various` or sub-categories of System/Launcher/Controls jumping
  back to the home page immediately and not highlighting the target Preference.
- Restored the explicit search navigation state machine:
  - `0 = normal home`;
  - `1 = showing search results`;
  - `2 = navigated into a search result, clear search UI on returning home`.
- Made `ModData.sub` nullable so the search index no longer stores missing sub-categories as empty
  strings.
- `MainFragment.openModCat()` now consistently returns the navigation success/failure status for
  System, Launcher, Controls, and Various, instead of mixing transaction results with category types.
- Added blank `sub` protection in `SubFragment` to avoid casting a non-category Preference to
  `PreferenceCategoryEx`.
- Added `SearchRouteResolver` and `SearchStateMachine` unit tests.

### Build and compatibility

- Continues to use the verified JDK 17, Gradle 9.6.1, AGP 9.2.1, and Kotlin 2.3.21 toolchain.
- Release builds retain R8, resource shrinking, zipalign, and APK Signature Scheme v2.
- Signing certificate SHA-256:
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`.

### Verification

- Unit tests: 68 tests, 0 failures, 0 skipped.
- Lint / `lintRelease` / `lintVitalRelease`: passed; 107 deprecation warnings, 0 errors.
- Debug / Release, R8, and resource shrinking: passed (`BUILD SUCCESSFUL in 2m 8s`).
- APK: `CustoMIUIzer-A14-r14.13.5.apk`.
- APK size: 3,032,173 bytes.
- APK SHA-256: `89AE5046564F69D491DC44F7B853443113FEC7100FE997ABA9984181C4983EA5`.
- Signing: APK Signature Scheme v2; certificate SHA-256
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`.
- versionCode/versionName: `183 / r14.13.5`.
- `minSdk/targetSdk`: `34 / 34`.
- Xposed metadata: `minApiVersion=101`, `targetApiVersion=102`, `staticScope=false`.

### Important: r14.13.4 is withdrawn

- `r14.13.4` has a home search navigation regression and is superseded by `r14.13.5`.
- The `r14.13.4` GitHub Release and tag have been removed; historical asset information is in
  [RELEASE_ARCHIVE.md](docs/RELEASE_ARCHIVE.md).
- If you already have `r14.13.4` installed, you can install `r14.13.5` over it without uninstalling.

## [r14.13.4] - 2026-07-28

### Release scope

> Withdrawn; superseded by `r14.13.5`.

Built on the stable r14.12.0 baseline, this release completes focused work on the settings app,
locale and theme behavior, lifecycle management, and frequent Hook paths. It also closes the
r14.13 architecture audit and fixes Kotlin-migration regressions.

This release supports only HyperOS 1 / Android 14 and `arm64-v8a`, while retaining the one-APK
libxposed API 101/102 compatibility boundary.

### Settings and UI

- Moved the in-app language entry to the About page, retaining follow-system and the project's
  existing languages.
- Fixed Activity, system-bar, and settings-page recreation after locale or day/night-mode changes.
- Made the About page show separate maintainer, upstream-source, and current-version entries.
- Fixed returning from a search result and restoring state during Fragment recreation.
- Moved Launcher, SystemUI, and Security Center restarts to background Root commands, with
  feedback for no Root access, a missing target process, and command failures.
- Refined Preference titles, summaries, dialogs, spacing, corners, and localized resources.

### Reliability and performance

- Fixed SystemUI status-bar text icons, including temperature and current, retaining stale Views;
  discarded Views can now be collected after theme, density, rotation, or status-bar recreation.
- Optimized resource-replacement Hook misses by reducing integer boxing, JNI method-name lookup,
  and unnecessary resource-name parsing, with safe publication for sparse containers.
- Restored first-match exit behavior in CPU thermal-zone scanning after the Java-to-Kotlin
  migration, avoiding periodic reads of irrelevant sysfs files.
- Removed repeated Regex compilation from `first|second` preference parsing and added PrefPair
  regression tests.
- Cached the application ClassLoader fallback, avoiding repeated reflection probes when a ROM
  legitimately lacks a class.
- Fixed RemotePreferences snapshots that were empty early in startup being treated as permanently
  loaded.
- Set the preference-listener registration state only after successful registration.
- Prevented repeated DexKitBridge construction.

### Build and compatibility

- Continues to use the verified JDK 17, Gradle 9.6.1, AGP 9.2.1, and Kotlin 2.3.21 toolchain.
- Does not include AGP 9.3.1 or other toolchain upgrades.
- Built with libxposed API/service 102, `minApiVersion=101`, `targetApiVersion=102`, and
  `staticScope=false`.
- Public loading and Hook paths remain available on API 101; Hot Reload, hook IDs, and atomic
  replacement are not enabled.
- Release builds retain R8, resource shrinking, zipalign, and APK Signature Scheme v2.

### Important: signing-key change

- The private key used by public r14.12.0 and earlier releases has been lost and cannot be used
  for future builds.
- r14.13.4 uses a new official signing certificate, so it cannot be installed over older public
  builds.
- Before upgrading, back up module settings in the old build; then uninstall it, install r14.13.4,
  re-enable the LSPosed/Vector scope, restore settings, and fully reboot.
- New signing-certificate SHA-256:
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`

### Verification

- Unit tests: 45 tests, 0 failures, 0 skipped.
- Lint / `lintRelease` / `lintVitalRelease`: passed; 107 deprecation warnings, 0 errors.
- Debug / Develop / Release, R8, and resource shrinking: passed (`BUILD SUCCESSFUL in 3m 32s`).
- APK: `CustoMIUIzer-A14-r14.13.4.apk`.
- APK size: 3,032,173 bytes.
- APK SHA-256: `E8A2BD362C0540972441B8D1DE0BCACE8FE85FEF71F31406F3B4DA1A4027D26C`.
- Signing: APK Signature Scheme v2; certificate SHA-256
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`.
- applicationId: `tv.withaibuild.customiuizer.r14`.
- versionCode/versionName: `182 / r14.13.4`.
- `minSdk/targetSdk`: `34 / 34`.
- Xposed metadata: `minApiVersion=101`, `targetApiVersion=102`, and `staticScope=false`.

### Known limitations

- Supports only HyperOS 1 / Android 14 and `arm64-v8a`.
- API 102 still requires independent device coverage on a compatible framework.
- Vendor system-app updates can change Hook targets.
- Performance and power effects depend on the ROM, enabled features, and usage; no fixed result is
  claimed without same-device controlled measurements.

## [r14.13.3] - 2026-07-27

### Release scope

> Non-public candidate; its changes were incorporated into the public r14.13.4 release.

Candidate maintenance work for UI, locale, and About pages; theme recreation; LSPosed log review;
DexKitBridge initialization; and documentation synchronization.

### Fixed

- Removed the duplicate language entry on the settings home page, kept the About-page entry, and
  enabled `valueAsSummary`.
- Split About information into maintainer, based-on, and version rows.
- Removed `uiMode` from `MainActivity` `configChanges` so the system recreates the activity for
  day/night theme changes.
- Added a non-null guard to `XposedHelpers.createBridge` to avoid repeated DexKitBridge creation.
- Added the missing `xmlns:miuizer` namespace in `prefs_about.xml` to fix Release resource merging.

### Verification

- Unit tests, Lint, `lintRelease`, `lintVitalRelease`, Debug, and Release builds passed.
- APK: `CustoMIUIzer-A14-r14.13.3.apk`, 3,039,311 bytes, SHA-256
  `FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`, signed with APK
  Signature Scheme v2.
- Signing-certificate SHA-256:
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`.
- Review of the r14.13.3 LSPosed reboot logs found no module-attributable crash, ANR, Hook
  failure, or RemotePreferences exception; tombstones did not contain the module package.
- `apksigner verify -v` and `aapt2 dump badging` confirmed the applicationId,
  versionCode/versionName, `minSdk`/`targetSdk`, and `module.prop` metadata.
- Full device UI/locale/Hook regression coverage and independent API 102 runtime verification were
  not completed for this candidate.

### Signing

- The signing certificate changed from r14.13.0-rc1; r14.13.3 continued to use the new one.
- The private key used by r14.12.0 and earlier releases has been lost.
- A new-signature build cannot be installed over an old-signature build.
- Back up module settings, uninstall the old build, and then install the new build.
- New certificate SHA-256:
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`.

### Known limitations

- The settings app, day/night theme, locale switching, and Root-restart feedback still required
  device verification.
- An independent API 102 framework environment had not been verified.

## [r14.13.0-rc1] - non-public candidate

> This candidate was not publicly released; its work was incorporated into r14.13.4.

### Refactored

- Completed the first r14.13 cleanup of Kotlin and settings-layer code.
- Completed the Java/Kotlin-boundary and core hot-path audit.

### Fixed

- Fixed the Bitmap-cache thread-pool thread-count boundary calculation.
- Restored tolerant nullable-`Context` behavior in the vibration helper.
- Made network-speed formatting use `Locale.ROOT`.

### Performance

- Cached the `DisplayManager` and display ID in the status-bar-gesture path to reduce frequent
  reflection and repeated queries.

### Verification

- Unit tests, Lint, Debug, and Release builds passed.
- Long-running device regression testing and LSPosed/Vector log review remained pending.

## [r14.12.0] - 2026-07-26

### Release scope

Completed the core Kotlin migration, lifecycle management, and hot-path work while supporting
libxposed API 101 and API 102 in the same APK. Android support remains HyperOS 1 / Android 14.

### Main changes

- Compiled against API 102 with `minApiVersion=101`, `targetApiVersion=102`, and
  `staticScope=false`.
- Public Hook paths use only API 101 capabilities; Hot Reload, hook IDs, and atomic replacement
  are not enabled.
- Conservatively migrated core Hooks, the settings UI, and utilities to Kotlin, retaining
  `MainModule.java`, the libxposed compatibility layer, and necessary JVM/reflection boundaries.
- Fixed loading state in the app selector; deduplication for share/open-with; and repeated data in
  privacy-app and app-lock lists.
- Tightened screenshot DexKit target matching to avoid hooking methods with incompatible signatures.

### Lifecycle and performance

- Released AudioVisualizer observers, coroutines, animations, and native Visualizer instances with
  their owner.
- Unregistered BatteryIndicator receivers, observers, and drawing callbacks on detach.
- Prevented repeated registration after SystemUI recreation for volume blur, screenshot-bar hiding,
  and lock-screen album-art listeners.
- Reduced temporary objects, formatting, and resource reads in dual-row signal, timed-vibration,
  and Launcher-icon-scaling hot paths.
- Kept reflection, DexKit, and resource probing on initialization paths.
- Avoided registering matching Hooks and long-lived listeners when a feature is disabled.

### Build and dependencies

- Migrated Groovy build scripts to Kotlin DSL and consolidated direct dependencies in the version
  catalog.
- Uses Gradle Wrapper 9.6.1, Android Gradle Plugin 9.2.1, and Kotlin BOM 2.3.21.
- Uses kotlinx.coroutines 1.11.0 and libxposed API/service 102.0.0.
- Release enables R8, resource shrinking, zipalign, and APK Signature Scheme v2.

### Verification

- Unit tests, Debug, Release, Lint, `lintRelease`, and `lintVitalRelease` passed.
- API 101 dependency recompilation, the API 102 release build, and the Legacy Xposed API scan
  passed.
- Checked APK entry point, scope, `module.prop`, signing, and zip alignment.
- API 101 installation, full reboot, and complete `full.log` review found no module-related crash,
  ANR, entry-point, Hook, or API-linkage error.
- APK details, device environment, log scan items, and verification boundaries are in the
  [verification record](docs/VERIFICATION.md).

### Known limitations

- Supports only HyperOS 1 / Android 14 and `arm64-v8a`.
- API 102 still requires independent device verification on a compatible framework.
- Hot Reload is disabled.
- Vendor system-app updates can change Hook targets.

## [r14.8.0] - 2026-07-25

### Release scope

Established an infrastructure stability point before large-scale Kotlin migration of the core mods,
so later Hook-migration issues can be distinguished from utility-layer issues.

### Main changes

- Conservatively migrated `Helpers`, `AppHelper`, `ModuleHelper`, `HookerClassHelper`,
  `ResourceHooks`, `ShakeManager`, and `ResourceConstants` to Kotlin.
- Migrated `AppHelperTest`, `PrefMapTest`, and `XposedHelpersCacheTest` to Kotlin.
- Preserved Java/Kotlin static interop, reflective entry points, Hook priority, and exception
  propagation behavior.
- Fixed Lint issues in `MainFragment`, `SpinnerEx`, `SortableListView`, and Intent flags.
- Removed old APKs, temporary build logs, and unused artifacts.

### Verification

- versionCode 170 / versionName `r14.8.0`.
- Unit tests, compilation, Release, R8, Lint, and signing checks passed.
- A full reboot log confirmed module loading and found no module-related crash or ANR.

### Rollback value

Retained as an infrastructure reference before core Hook Kotlin migration, API 101/102 work, and
later lifecycle management.

## [r14.7.4] - 2026-07-25

### Release scope

Merged the coroutine, settings-subpage, UI-control, and small-utility migrations from r14.7.0
through r14.7.3 as the only public stable r14.7.x release.

### Main changes

- Migrated BitmapCachedLoader, weather, step counter, audio visualizer, and battery indicator to
  lifecycle-aware Kotlin coroutines.
- Used lifecycle scope for Activity/app selectors, search subpages, and settings Fragments.
- Introduced ViewHolder in list adapters and migrated preference controls and small settings pages
  to Kotlin.
- Used the public `Settings.Global` API for animation scaling while retaining necessary fallback.
- Removed obsolete build artifacts, old APKs, and temporary logs.

### Verification

- versionCode 169 / versionName `r14.7.4`.
- Release build and `lintVitalRelease` passed.
- A full reboot log confirmed the entry point loaded and found no module-related crash or ANR.
- APK SHA-256:
  `1B2026B6FFAEE33C3BE50E4695EE8BF19EAA6740124A199153D89C63251F2329`.

### Rollback value

Retained as the r14.7.x coroutine/UI migration consolidation point, providing a layered comparison
with the r14.8.0 utility-infrastructure release.

## [r14.5.0] - 2026-07-24

### Release scope

Established the current independent package, signing, and GitHub release path. It is the long-term
rollback baseline for subsequent Kotlin and API work.

### Main changes

- Moved source packages to `tv.withaibuild.customiuizer`.
- Set namespace to `tv.withaibuild.customiuizer` and applicationId to
  `tv.withaibuild.customiuizer.r14`.
- Updated the Manifest, XML, preferences, shortcuts, Tasker components, and R8 rules.
- Specified stable Locale behavior for number formatting and case comparison.
- Changed synchronous settings-reset `commit()` to `apply()`.
- Explicitly bound Handler to the main Looper and added required Android 14 export flags to dynamic
  receivers.
- Consolidated frequent `Resources.getIdentifier()` calls into a thread-safe resource-ID cache.

### Verification

- versionCode 150 / versionName `r14.5.0`.
- `assembleRelease`, `lintVitalRelease`, and signing checks passed.
- A full reboot found no module-related crash, ANR, or exception stack.
- APK SHA-256:
  `DCB9EBC4BBE7AEE721B58F83B5371E1030AD7CAB0C4FE6CC4EAD900C420E8C93`.

### Rollback value

The earliest public stable baseline for the current package name and signing line. Older versions
have a different package name or project structure and are not general-user rollback builds.

## Non-public engineering milestones

### r14.10.0

- Established the libxposed API 101/102 one-APK compatibility boundary.
- Migrated build scripts to Kotlin DSL and fixed direct dependencies through the version catalog.
- Completed API 101 dependency recompilation, API 102 Release, R8, resource shrinking, and a
  Legacy API scan.
- This version was not kept as a public rollback release; its work is included in r14.12.0.

## Historical phases

### r14.0-r14.3

- Established the independent HyperOS 1 / Android 14 maintenance line using modern libxposed API
  101.
- Completed early optimization of resources, reflection, status-bar drawing, and inactive Hooks.

### r14.5-r14.6

- Established the current independent package, signing, and release path.
- Advanced lifecycle, dual-row signal, resource lookup, R8, and test management.

### r14.7-r14.8

- Advanced Coroutine, settings UI, utility, and infrastructure Kotlin migration.
- Removed hidden APIs, Lint issues, dead code, and obsolete resources.

### r14.9-r14.12

- Completed conservative Kotlin migration of core Hooks and Kotlin/JVM-boundary review.
- Established API 101/102 one-APK support, Kotlin DSL, and the version catalog.
- Completed audits of lifecycle, duplicate registration, hot paths, settings UI, dependencies, and
  the build toolchain.

Fine-grained commit history remains available through Git tags and commits; no public Release is
created for each internal batch.
