# Changelog

[简体中文](CHANGELOG.md) | English

This file records user-visible changes, compatibility boundaries, verification conclusions, and
rollback value for public releases. Internal migration batches, agent work logs, temporary APKs,
and performance figures without same-condition measurements are not release changelog entries.

## Public Releases

| Version | Date | Purpose |
| --- | --- | --- |
| `r14.15.0` | 2026-07-31 | Same runtime code as `r14.13.9`; manual smoke test deferred |
| `r14.13.9` | 2026-07-31 | Current stable release; restores upstream A14 `system` scope, fixes `system_server` hook loading |
| `r14.13.8` | 2026-07-30 | Structure tidy-up, soft-reboot receiver fix, LSPosed 2.1.1 acceptance |
| `r14.13.7` | 2026-07-29 | Current stable release; settings survive an unbound service, soft reboot un-gated, hot-path robustness |
| `r14.13.6` | 2026-07-29 | Runtime hardening, language fix, hook files split by domain |
| `r14.8.0` | 2026-07-25 | Old-signature rollback point; back up and reinstall before upgrading |
| `r14.7.4` | 2026-07-25 | Consolidated r14.7.x Kotlin/coroutine migration release |

Release titles contain only the version number. Asset names, sizes, and SHA-256 digests for
removed releases are in the [historical Release archive](docs/RELEASE_ARCHIVE.md); the
corresponding source remains available through Git tags.

## [r14.15.0] - 2026-07-31

### Purpose

A release-only bump from `r14.13.9` with identical runtime code. Only `versionCode`, `versionName`,
`CHANGELOG`, and maintenance documents were updated. No hook logic, preference handling, `HookDiagnostics`,
R8 rules, or Xposed scope were changed.

### Changes

- `versionCode` from `187` to `188`.
- `versionName` from `r14.13.9` to `r14.15.0`.
- Added `docs/SYSTEM_SCOPE_AUDIT.md` covering every branch in `MainModule.onSystemServerStarting`.
- Added `docs/MAINTENANCE.md` with the deferred manual smoke test, release code freeze, and offline gate.

### Verification

- `python tools/check-invariants.py` passes.
- `python -m unittest discover -s tools/tests -p "test_*.py"` passes.
- `gradlew test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease` passes.
- Real-device LSPosed log for `r14.13.9` confirms `system` (`system_server`), `SystemUI`, and `Launcher`
  all loaded with zero hook-install errors; the Toast block feature is working.

### Known boundary

The full manual smoke test (power/volume/nav keys; AppLock/lock screen/strong auth; freeform/orientation/
window; audio/vibration/calls; security/install/wallpaper/Global Actions) is deferred and is not a blocker
for this release. `r14.15.0` does not claim all 40 `system_server` hooks have been individually verified.

### Artifacts

- APK: `CustoMIUIzer-A14-r14.15.0.apk` (production signed) / `CustoMIUIzer-A14-r14.15.0-unsigned-ci.apk` (CI).
- versionCode / versionName: `188 / r14.15.0`

## [r14.13.9] - 2026-07-31

### Purpose

Restores the upstream A14 `system` scope so `system_server` is loaded again and system-service hooks
are not silently skipped. No business hook logic was changed.

### Changes

- Restores `system` in `app/src/main/resources/META-INF/xposed/scope.list` while keeping the existing `android` scope.
- Improves ADB regression normalization of `system` / `system_server`, preserving `rawProcess`.
- Adds a scope static regression test requiring `system` in `scope.list` whenever `MainModule.onSystemServerStarting` is present.

### Verification

- `python tools/check-invariants.py`, `python tools/audit-feature-semantics.py --validate`, and the full Python test suite pass.
- `gradlew test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease` passes.
- GitHub Actions CI passes.
- `META-INF/xposed/scope.list` contains `system`, `android`, `com.android.systemui`, and `com.miui.home`.

### Known boundary

The `r14.13.9` build and CI have passed, but the fixed `system_server` real-device loading, full `a14-smoke`, Broadcast negative probe, and Tasker manual checkpoint have not yet been executed.

### Artifacts

- APK: `CustoMIUIzer-A14-r14.13.9.apk`
- versionCode / versionName: `187 / r14.13.9`

## [r14.13.8] - 2026-07-30

### Purpose

Closes the structure-tidy round without changing existing feature semantics, then fixes the
independent registration and result handling of the soft-reboot receiver. Toast suppression,
`AnimationScale`, and unrelated features are unchanged.

### Changes

- Tightens the boundary between hook-process utilities and settings-app utilities by splitting
  `HookUtils`, reducing unrelated class loading inside system processes.
- Removes six obsolete GlobalActions forwarding stubs and calls their implementations directly.
- Registers the soft-reboot receiver independently of custom actions. In-app "Reboot system" is
  received and executed by SystemUI even when no custom action is configured.
- Distinguishes an unclaimed broadcast from receiver-side execution failure. A failed soft reboot
  is no longer misreported as "LSPosed service not connected"; custom-action behavior is unchanged.

### On-device and static verification

- Android 14 / HyperOS 1 with LSPosed 2.1.1 (7790): P0 and P1 were both zero; the module loaded in
  SystemUI and Launcher, both reboot cycles completed, and no target-process crash, hook exception,
  or duplicate receiver registration was found.
- Full invariant checks, unit tests, `lintDebug`, `lintRelease`, `lintVitalRelease`, Debug /
  Release builds, and production-signature verification all passed.

### Known issue

- System Toast suppression may still be ineffective; this release does not address that existing
  issue.

### Artifact

- APK: `CustoMIUIzer-A14-r14.13.8.apk`
- Size: 3,085,209 bytes
- SHA-256: `B0E7D4A3CB50E39748531D5B0FD3CB95F81C1F777DDAC9E346B8C8D67B8CBE62`
- Signing certificate SHA-256: `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- versionCode / versionName: `186 / r14.13.8`

## [r14.13.7] - 2026-07-29

### Purpose

A reliability round after `r14.13.6`. Its centre is a long-standing defect that the previous
release happened to expose: while the settings app is not bound to the LSPosed service, every
setting the user changes is dropped and never resent. Four system-process defects found while
auditing the same call chains are fixed alongside it. Nothing else about user-visible behaviour
changes.

### Root cause: the LSPosed service binder push

A captured on-device log shows that after the settings process restarts rapidly four times, the
LSPosed/Vector daemon **stops pushing the module binder to this module's `XposedProvider`**. Every
later start still asks system_server's bridge for it, but the daemon's `Sent module binder` never
appears again for the remaining 14 minutes and 15 process starts, with no error logged either.

Decompiling `libxposed-service` 102.0.0 confirms the shape: the binder is *pushed* by the daemon
(`XposedProvider.call("SendBinder")`), and `XposedServiceHelper.registerListener()` only parks a
listener and drains a static cache. There is **no request or retry path**. Longer timeouts,
re-registering and polling therefore cannot fix it; that part belongs to the framework and cannot
be solved inside the app.

What this release does is make that state stop costing data, stop breaking unrelated features, and
stop being silent.

### Fixed

- **A setting changed while unbound is no longer lost.** The preference listener returned early when
  `remotePrefs == null`, and `onServiceBind` only started listening from that point — it never
  caught up. Since the module reads its snapshot once per hooked process and installs hooks from
  it, a toggle flipped at the wrong moment stayed off permanently and silently. That is what
  "album art as wallpaper does nothing" was; **the art processor itself was not at fault.** The
  mirror now reconciles in full when the service binds, and the "not connected" dialog says so
  while anything is still undelivered.
- **Soft reboot is no longer gated on the settings app's bind state.** It broadcasts to the module
  inside SystemUI, which is unrelated to whether this process holds a service binder. It is now
  sent as an ordered broadcast addressed explicitly to `com.android.systemui`; SystemUI claims it
  only after its reflection resolves, and the user is told only if nobody claimed it.
- **`PrefMap.getStringAsInt()` no longer throws.** A changed stored type raised
  `ClassCastException` and a damaged string raised `NumberFormatException`, from hooks running in
  SystemUI and `system_server` — most of them while deciding which hooks to install at process
  start. Unreadable values now fall back to the caller's default, and a failed parse is cached like
  a successful one.
- **Status bar battery/temperature formats and units apply without a SystemUI restart.** The ticker
  used the config snapshot captured at hook time, so the `@Volatile` field that
  `onConfigMayHaveChanged()` refreshed was never read. Each tick now takes one current snapshot.
  What genuinely cannot hot-update is the icon slot itself (the master toggle and "on the right"),
  and those two now say so in the settings screen.
- **Lock-screen album art concurrency and cache.** The single-slot dispatcher opened
  `withContext(Dispatchers.Default)` as its first statement, handing the work back to the unbounded
  pool, so skipping tracks quickly could generate several full-screen ARGB_8888 frames at once. The
  cache was bounded at three *entries* (~31 MB at 1080x2400) and its key recorded the blur radius
  as a hard-coded 0 over the identity of the post-blur bitmap, so it could never hit. Now
  generation-checked (cancelling cannot stop a CPU blur that has no suspension point), bounded by
  `allocationByteCount`, and keyed on the source with the real parameters; the cache is released
  when playback stops, the theme is unsupported, or the target size changes. CENTER_CROP/fit
  geometry and output quality are unchanged.
- **A saturated icon queue no longer blanks an icon permanently.** `DiscardOldestPolicy` dropped
  queued tasks without releasing the in-flight marker, after which every loader for that key
  decided someone else was already loading it and returned. It now uses `AbortPolicy` and handles
  rejection explicitly at submission.

### Verification

- check-invariants: 116 files, 8 rules, no violations; 171 unit tests, 0 failures;
  lint / lintRelease / lintVitalRelease with 0 errors; debug and release builds pass.
- **Published without on-device acceptance.** This release changes the album art processor and the
  status bar ticker, both of which run inside SystemUI, so it sits closer to a system process than
  `r14.13.6` did. If SystemUI misbehaves, roll back to `r14.13.6`.

### Artifact

- APK: `CustoMIUIzer-A14-r14.13.7.apk`
- Size: 3,084,589 bytes
- SHA-256: `11D01A737BED25C3C4D31153DE22CB918A651D0DD043D0374E2C0E41D32492CC`
- Signing certificate SHA-256: `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- versionCode / versionName: `185 / r14.13.7`

## [r14.13.6] - 2026-07-29

### Purpose

A round of runtime robustness and performance work after `r14.13.5`. It fixes defects that
affect real use and splits the three oversized hook files by functional domain. User-visible
behaviour is unchanged apart from the interface language.

### Fixed

- **Changing the interface language did nothing.** `AppCompatDelegate.setApplicationLocales()`
  is a silent no-op from `Application.onCreate`: on API 33+ it resolves `LocaleManager` through
  the set of *live AppCompat Activity delegates*, and no Activity exists that early. The choice
  was saved correctly and then ignored. It now calls `android.app.LocaleManager` directly.
- **The language row could break the settings screen and silently revert the language.** Writing
  a preference value during binding triggered `notifyItemChanged` while the RecyclerView was
  laying out, and persisted the XML placeholder over the user's language. Binding is now
  read-only with respect to preference state.
- **Spurious "module not active" warning.** "We stopped waiting" and "proven disconnected" were
  the same state value, and the UI waited less time than the service takes to decide. They are
  now distinct, and a timeout gets one further wait before anything is reported.
- **A toggle opened from search did not update until the screen was left and re-entered.** The
  search highlight was meant to play once but replayed on every bind, and the animation
  permanently replaced the row's background, including its pressed state.
- **Unguarded callbacks inside system processes.** Callbacks the module registers from hooks are
  outside `MethodHook`'s try/catch; two of them run on a `system_server` handler, where a throw
  reboots the device. 23 sites hardened.
- **Leaked registrations.** Cleanup was keyed on the hooked instance, which is new every time,
  so it never ran. One leaked receiver listens for `TIME_TICK` — a wasted wakeup every minute.
- **Additional instance fields were keyed by `equals`.** Two distinct-but-equal objects shared
  one field map, and mutating a field the hash derives from lost the entry permanently. Keys are
  now weak references compared by identity.

### Performance

- Hook arguments are no longer copied and re-marshalled per invocation (117 read-only sites).
- Reflection cache hits no longer allocate (616 field lookups, 137 no-argument method lookups).
- The main-screen search is a single allocation-free scan; sorting happens once at index build.

### Structure

- `mods/System.kt` 4898 -> 593 lines, `mods/SystemUI.kt` 3682 -> 205,
  `mods/Launcher.kt` 2960 -> 405, split into 18 domain files. Every moved member was verified
  byte-identical, MainModule's call sequence is unchanged, and R8 keeps the same method set.

### Verification

- check-invariants: 113 files, 8 rules, no violations. 122 unit tests, 0 failures.
  lint / lintRelease / lintVitalRelease: 0 errors. Debug and release builds green.
- **Published without on-device acceptance**: the changes in this release have not been run on
  a device.

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
