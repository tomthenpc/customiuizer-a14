# A14 P6-A0 — Final Quality / Dead Code / Production Boundary Audit

> Scope: audit only. No production, resource, test, or build changes. No APK built or installed.

- Base SHA: `7f823eb709d798b21c8bbb0e7985595aba3fc771`
- Branch: `devin/a14-final-polish-r14.20.0`
- Audit temp: `C:\Users\tv\AppData\Local\Temp\a14-p6-final-audit`

## 1. Base / scope

- Target: `app/src/main/java`, `app/src/main/res`, `app/src/main/resources`, `app/src/main/assets`
- Build: `app/build.gradle.kts`, root Gradle, `gradle/libs.versions.toml`
- Tests: `app/src/test`, `tools/tests`
- Frozen items honored: USB architecture, P2 IA, P3 restart, P4 IME dismiss, Search/Lazy, Backup M2, AudioVisualizer, etc. No redesign performed.

## 2. Production / test inventory

| Metric | Value |
|---|---|
| PRODUCTION_SOURCE_FILE_COUNT | 267 |
| PRODUCTION_KOTLIN_COUNT | 264 |
| PRODUCTION_JAVA_COUNT | 3 |
| PRODUCTION_LOC_CODE | 59,423 |
| TEST_SOURCE_FILE_COUNT | 221 |
| APP_RESOURCE_FILE_COUNT | 1,432 |
| FORMAL_LOCALIZED_LOCALE_COUNT | 9 |

## 3. Dead-code audit

| Metric | Value |
|---|---|
| UNREFERENCED_CANDIDATE_COUNT | 0 |
| CONFIRMED_DEAD_COUNT | 8 |
| DYNAMIC_FALSE_POSITIVE_COUNT | 1 |
| UNKNOWN_DEAD_CANDIDATE_COUNT | 0 |

Confirmed dead:

| File | Symbol | Kind | Evidence |
|---|---|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/utils/LegacyBackupDecoder.kt` | `SC_EXTERNALIZABLE` | private const | never read/written |
| `app/src/main/java/tv/withaibuild/customiuizer/utils/LegacyBackupDecoder.kt` | `SC_BLOCK_DATA` | private const | never read/written |
| `app/src/main/java/tv/withaibuild/customiuizer/utils/LegacyBackupDecoder.kt` | `SC_ENUM` | private const | never read/written |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt` | `unavailableReported` | private var | never read/written |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt` | `WINDOW_STATE_CLASS` | private const | never read/written |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt` | `DISPLAY_POLICY_CLASS` | private const | never read/written |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt` | `STATUS_BARS_TYPE` | private const | never read/written |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | `StatusBarCls` | private val | never read/written |

Dynamic false positive:

| File | Symbol | Reason |
|---|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | `private XposedHelpers()` | Static utility class private constructor by design; not dead code. |

Methodology: per-file token counting after comment stripping, repository-wide grep for `R.*`, `@...`, and string-literal references, plus manual review of high-confidence candidates. No candidates remain unconfirmed.

## 4. Feature / preference / resource wiring

### Feature graph

| Metric | Value |
|---|---|
| Total feature records | 353 |
| ORPHAN_FEATURE_COUNT | 0 |
| ORPHAN_PREFERENCE_COUNT | 0 |
| Duplicate wiring | 5 |

Duplicate wiring (same preference key used by multiple `LazyFeatureSpec`s):

- `launcher_folder_cols` — `LauncherFolderColumnsResFeatureId` + `LauncherFolderColumnsFeatureId`
- `launcher_privacyapps_gest` — `LauncherPrivacyAppsGestFeatureId` + `LauncherPrivacyFolderFeatureId`
- `launcher_closefolders` — `LauncherCloseFolderOnLaunchFeatureId` + `LauncherCloseOnLaunchFeatureId`
- `various_restrictapp` — `PowerKeeperAppsRestrictFeatureId` + `SecurityCenterAppsRestrictFeatureId`
- `system_detailednetspeed_style` — `DetailedNetSpeedFeatureId` + `NetSpeedStyleFeatureId`

These are generally target/package differences or res-only vs. runtime specs; not necessarily wrong, but they are wiring surface that should be explicit.

### Resource orphan audit

| Metric | Value |
|---|---|
| Total resources | 1,432 |
| CONFIRMED_ORPHAN_RESOURCE_COUNT | 9 |
| DYNAMICALLY_REFERENCED | 96 |
| STATICALLY_UNUSED | 0 |
| UNKNOWN | 1 |

Confirmed orphans:

| Type | Name | File |
|---|---|---|
| strings | `system_recents_blur_summ` | `res/values/strings.xml` |
| strings | `system_fivegtile_summ` | `res/values/strings.xml` |
| strings | `system_recents_card_style_summ` | `res/values/strings.xml` |
| strings | `launcher_privacyapps_fail` | `res/values/strings.xml` |
| strings | `settings` | `res/values/strings.xml` |
| strings | `miuizer` | `res/values/strings.xml` |
| attrs | `DropDownPreferenceEx` | `res/values/attrs.xml` |
| attrs | `preferenceSecondaryTextColor` | `res/values/attrs.xml` |
| ids | `update_alert` | `res/values/ids.xml` |

Dynamically referenced resources (96) are primarily `statusbar_signal_*` drawables loaded by `getIdentifier` with the template `statusbar_signal_${slot}_${lvl}` and protected by `res/raw/keep.xml`. These are intentionally not static orphans.

## 5. Test / production boundary

| Metric | Value |
|---|---|
| MAIN_SOURCE_CLEANLINESS_VIOLATIONS | 0 |
| TEST_DEPENDENCY_IN_PRODUCTION_COUNT | 0 |
| TEST_IMPLEMENTATION_SEAM_COUNT | 9 |

`python tools/check_main_source_cleanliness.py`: `514 files, no violations`.

Production build dependencies in `app/build.gradle.kts`:
- `compileOnly(files("lib/framework.jar"))`
- `compileOnly(libs.libxposed.api)`
- `implementation(libs.libxposed.service)`, `commons.lang3`, `androidx.preference`, `androidx.palette`, `androidx.appcompat`, `dexkit`, `kotlin.bom`, `kotlinx.coroutines.android`

Test-only dependencies: `libxposed.api`, `kotlinx-coroutines.test`, `junit` — all in `testImplementation`. No leak.

Test implementation seams (9) are test-only overloads / diagnostic assertion methods in:
- `StatusBarIconVisibilityResolver.kt` (3 overloads)
- `VolumeDialogAutohideDelayResolver.kt` (1 overload)
- `NotificationAutoExpandResolver.kt` (1 overload)
- `PhysicalGestureArbiter.kt` (4 diagnostic methods)

These are not dead code; they are deliberate testability hooks. No production test/debug-only Activity, debug menu, or fake backend found.

## 6. Fatal semantics

| Metric | Value |
|---|---|
| GENERIC_THROWABLE_CATCH_COUNT | 945 |
| FATAL_SAFE_COUNT | 622 |
| FATAL_UNSAFE_COUNT | 323 |
| runCatching | 0 |
| InvocationTargetException sites | 21 |

Project has a `FatalErrors` boundary (`rethrowIfFatal` / `unwrapAndRethrowIfFatal`) and `XposedHelpers.throwOrReturn` rethrows captured throwables. 622 catch sites either rethrow via `throwOrReturn` or call `FatalErrors`.

323 sites swallow a generic `Throwable`/`Error` without fatal propagation. Representative examples:

- `Credentials.kt:34, 46` — `printStackTrace()`
- `MainApplication.kt:61` — empty catch (`catch (_: Throwable) {}`)
- `PreferenceFragmentBase.kt:434, 495` — `printStackTrace()` / empty catch
- `PrefsProvider.kt:50` — `t.printStackTrace()`
- `SubFragment.kt:272, 290` — `Log.e("miuizer", ...)`
- `Controls.kt` and other hook files — `XposedHelpers.log(t)` / `catch (ignore: Throwable)`

Most of these are likely ordinary hook error isolation, but they do not explicitly protect `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError`. The conservative minimal fix is to add `FatalErrors.rethrowIfFatal(t)` as the first statement in each.

## 7. Reflection / allocation hot-path

| Metric | Value |
|---|---|
| Reflection findings | 1,360 |
| — COLD_PATH | 239 |
| — INSTALL_TIME_ONLY | 176 |
| — ACCEPTABLE_CALLBACK_COST | 593 |
| — HOT_PATH_CANDIDATE | 316 |
| — CONFIRMED_HOT_PATH_WASTE (reflection) | 36 |
| Non-reflection hot-path findings | 426 |
| — COLD_PATH | 246 |
| — INSTALL_TIME_ONLY | 52 |
| — HOT_PATH_CANDIDATE | 87 |
| — CONFIRMED_HOT_PATH_WASTE (other) | 41 |
| **Total confirmed hot-path waste** | **77** |
| **Total hot-path candidates** | **403** |

Confirmed waste examples:

- `Controls.kt:310` — `findMethodExact(MediaPlayerCls, "getAudioStreamType", ...)` inside `MediaPlayer.pause` hook
- `Controls.kt:574` — `findClassIfExists("...MiuiKeyButtonRipple", ...)` inside nav button callback
- `GlobalActionSystemServerHooks.kt:597` — `findField(thisObject.javaClass.superclass, "mRequestShowMenu")` inside `onReceive`
- `GlobalActionSystemServerHooks.kt:632, 806, 858` — `findClass` / `findClassIfExists` inside broadcast/notification callbacks
- `SystemUIControlCenterHooks.kt` — repeated `getIdentifier` resource-name scans and `findViewWithTag` View tree traversal in control-center header callbacks

A dedicated `ReflectionCache` already exists; the finding is that some callbacks still perform per-invocation reflection or resource-name scans.

## 8. Lifecycle / retention / coroutine

| Metric | Value |
|---|---|
| LIFECYCLE_SUSPECT_COUNT | 4 |
| RETENTION_SUSPECT_COUNT | 0 |
| COROUTINE_SUSPECT_COUNT | 0 |

Lifecycle suspects (missing explicit unregister / remove / cancel for non-process-lifetime owner):

1. `SubFragmentWithSearch.kt:62` — `textInput?.addTextChangedListener(...)` with no matching `removeTextChangedListener`
2. `mods/LauncherIconHooks.kt:354` — `mMessage.addTextChangedListener(...)` with no matching remove
3. `mods/SystemUIScreenshotHooks.kt:76` — `view.addOnAttachStateChangeListener(this)` with no matching remove
4. `mods/utils/LockScreenAlbumArtController.kt:184` — `view.addOnAttachStateChangeListener(backgroundLifecycleListener)` with no matching remove

No static / global strong references to `Activity`, `View`, `Fragment`, `Context`, `Drawable`, or `Window` were found. No `GlobalScope` or uncancelled coroutine scopes. `StepCounterController`, `BatteryIndicator`, `AudioVisualizer`, `WiFiList`, `BTList` have explicit cancel / unregister / `WeakReference` cleanup.

## 9. TODO / FIXME audit

| Metric | Value |
|---|---|
| TODO_FIXME_TOTAL | 14 |
| ACTIONABLE_TODO_COUNT | 0 |
| STALE_TODO_COUNT | 0 |

All 14 are `temporary` / `workaround` comments:
- `TECH_DEBT_ONLY` (5): conflict workaround annotations in `GlobalActionSystemServerHooks.kt`
- `VALID_DOCUMENTATION` (8): design descriptions of temporary states / test docs
- `FALSE_POSITIVE` (1): `tools/SOURCE_HAZARD_BASELINE.json` repeated snippet

No `TODO`/`FIXME`/`HACK`/`XXX` markers found.

## 10. Lint findings

| Metric | Value |
|---|---|
| LINT_TOTAL | 321 |
| LINT_ERROR_COUNT | 0 |
| LINT_WARNING_COUNT | 321 |
| LINT_ACTIONABLE_COUNT | 83 |

Top categories:

| Rule | Count | Audit classification |
|---|---|---|
| UseKtx | 90 | SKIP — style, no runtime risk |
| DiscouragedApi | 64 | SKIP — API surface choice, frozen |
| MissingTranslation | 29 | P1 — real user-visible missing strings (see P1 findings) |
| IconLocation | 24 | P2 — resource organization |
| SetTextI18n | 14 | P2 — TextView uses `setText(Int)`; i18n design frozen |
| ObsoleteSdkInt | 13 | SKIP — version gating still used for ROM compatibility |
| UseCompatLoadingForDrawables | 11 | P2 — compat usage |
| RtlHardcoded | 9 | SKIP — RTL audit not in scope |
| UnusedResources | 6 | P1 — confirmed orphan strings/attrs/ids |
| ContentDescription | 6 | SKIP — accessibility |
| PluralsCandidate | 5 | P2 |
| IconDuplicates | 5 | P2 |
| KotlinNullnessAnnotation | 4 | SKIP — style |
| PrivateApi / PrivateResource | 4+3 | SKIP — Xposed by design |
| ClickableViewAccessibility | 4 | SKIP — accessibility |

`MissingTranslation` 29 is a P5 residual: the P5 contract test does not cover `arrays.xml` or all code-side `R.string` access patterns (e.g. `.text = getString(...)`). The missing keys include `unlock_host_*`, `system_epm_action_*`, `fast_reboot_*`, `restart_*done/failed`, `qs_toggle_floatingtime`, etc.

## 11. Top production files by LOC

| Rank | File | LOC (non-comment, non-blank) |
|---|---|---|
| 1 | `mods/utils/feature/SystemUiFeatures.kt` | 2,783 |
| 2 | `mods/SystemUIStatusBarHooks.kt` | 2,280 |
| 3 | `mods/SystemUIStrongToastHooks.kt` | 1,574 |
| 4 | `mods/Various.kt` | 1,559 |
| 5 | `mods/SystemUIControlCenterHooks.kt` | 1,517 |
| 6 | `mods/SystemLockScreenHooks.kt` | 1,513 |
| 7 | `mods/utils/feature/SystemServerFeatures.kt` | 1,335 |
| 8 | `mods/utils/XposedHelpers.java` | 1,301 |
| 9 | `mods/utils/feature/LauncherPostAttachFeatures.kt` | 1,213 |
| 10 | `mods/SystemClockHooks.kt` | 1,086 |

## 12. Modularization evaluation

| Metric | Value |
|---|---|
| MODULARIZATION_CANDIDATE_COUNT | 20 |
| MODULARIZATION_RECOMMENDED_COUNT | 2 |

Recommended splits (only if P6-B authorizes non-correctness maintenance):

1. `SystemUiFeatures.kt` (2,783 LOC) — split into `SystemUiStatusBarFeatures.kt`, `SystemUiControlCenterFeatures.kt`, `SystemUiLockScreenFeatures.kt`, `SystemUiNotificationFeatures.kt`. NET_VALUE = POSITIVE, RISK = MEDIUM.
2. `SystemUIStatusBarHooks.kt` (2,280 LOC) — extract net-speed snapshot/build/apply/hook family into `StatusBarNetSpeedHooks.kt`. NET_VALUE = POSITIVE, RISK = MEDIUM.

These are not correctness issues; they are maintainability candidates and should be delayed behind all correctness work.

## 13. P0 / P1 / P2 / SKIP findings

### P0

None.

### P1 (high value, correctness or quality defects)

1. **Fatal semantics — 323 generic `catch (Throwable/Error)` do not propagate fatal errors.** Add `FatalErrors.rethrowIfFatal(t)` at the start of each swallowing catch.
2. **User-visible missing translations — 29 lint `MissingTranslation` residuals.** The P5 contract test has scope gaps: it does not cover `arrays.xml` or all `R.string` code access patterns (e.g. `.text = getString(...)`). Affected keys: `unlock_host_*`, `system_epm_action_*`, `fast_reboot_*`, `restart_*_done/failed`, `qs_toggle_floatingtime`, etc.
3. **Hot-path reflection / allocation waste — 77 confirmed waste sites.** Move per-callback `findClass`/`findMethod`/`findField`/`getIdentifier`/`findViewWithTag` out of hot paths into install-time or lazy caches.
4. **Lifecycle listener leaks — 4 `addTextChangedListener` / `addOnAttachStateChangeListener` sites without matching remove.** Fix in `SubFragmentWithSearch`, `LauncherIconHooks`, `SystemUIScreenshotHooks`, `LockScreenAlbumArtController`.
5. **Confirmed orphan resources — 9 strings/attrs/ids.** Safe to delete after verifying no dynamic `getIdentifier` or `addFakeResource` path: `system_recents_blur_summ`, `system_fivegtile_summ`, `system_recents_card_style_summ`, `launcher_privacyapps_fail`, `settings`, `miuizer`, `DropDownPreferenceEx`, `preferenceSecondaryTextColor`, `update_alert`.

### P2 (maintenance / improvement, not correctness)

1. **Duplicate feature wiring — 5 preference keys used by multiple `LazyFeatureSpec`s.** Verify intent; consolidate or document.
2. **Module split candidates — `SystemUiFeatures.kt`, `SystemUIStatusBarHooks.kt`.**
3. **Lint `SetTextI18n` / `PluralsCandidate` / `IconLocation` / `IconDuplicates` / `UseCompatLoadingForDrawables` — style/organization only.**

### SKIP

- `UseKtx`, `DiscouragedApi`, `ObsoleteSdkInt`, `RtlHardcoded`, `ContentDescription`, `KotlinNullnessAnnotation`, `PrivateApi`/`PrivateResource` — style, frozen API/ROM compatibility, or accessibility.

## 14. P6-B shortlist

`P6_B_REQUIRED = YES` (P1 findings exist; P0 = 0, but high-value P1 > 0).

`P6_B_AUTHORIZATION = NO` at this moment; this is an audit-only phase.

Recommended minimal P6-B scope, in priority order:

1. Add `FatalErrors.rethrowIfFatal(t)` to the 323 swallowing `catch (Throwable/Error)` sites.
2. Extend `test_p5_localization_contract.py` to cover `arrays.xml` / `string-array` and additional code-side `R.string` patterns; then fill the 29 missing translations.
3. Move the 77 confirmed hot-path reflection/allocation sites to install-time or lazy caches.
4. Add matching `removeTextChangedListener` / `removeOnAttachStateChangeListener` for the 4 lifecycle suspects.
5. Delete the 9 confirmed orphan resources.

If P6-B budget is tight, drop #3 (largest effort) and #5 (low runtime risk) and keep #1, #2, #4.

## 15. Release preflight

| Item | Value |
|---|---|
| APPLICATION_ID | `tv.withaibuild.customiuizer.r14` |
| VERSION_NAME | `r14.20.0` |
| VERSION_CODE | `198` |
| minSdk | 34 |
| targetSdk | 34 |
| compileSdk | 37 |
| RELEASE_SIGNING_MATERIAL_PRESENT | YES (`C:\Users\tv\Documents\buildkey\r14` exists) |
| DEVICE_AVAILABLE | NO |
| APK_GENERATED | NO |

No `assembleRelease` / `bundle` / `package` / `install` / `sign` / `publish` / `officialRelease` executed.

## 16. Validation

| Command | Result |
|---|---|
| `python tools/verify.py fast --changed` | PASS |
| `python tools/verify.py full` | PASS |
| `python tools/audit-feature-semantics.py --validate` | PASS |
| `python tools/check_main_source_cleanliness.py` | PASS |
| `python -m compileall tools` | PASS |
| `python -m unittest discover -s tools/tests -p "test_*.py"` | PASS (486 tests, skipped=5) |
| `git diff --check` | PASS |
| `git status --short` | clean (only this doc uncommitted) |

## 17. Final gate

```text
P6_A0 = PASS_CANDIDATE
P6_B_AUTHORIZATION = NO
P6_C_AUTHORIZATION = NO

P0_FINDING_COUNT = 0
P1_FINDING_COUNT = 5
P2_FINDING_COUNT = 3
SKIP_FINDING_COUNT = 7

P6_B_REQUIRED = YES
P6_B_RECOMMENDED_SCOPE = fatal-safety, localization-test-gap, hot-path-waste, lifecycle-listeners, orphan-resources

APK_GENERATED = NO
P6_A0_SELF_ASSESSMENT = PASS_CANDIDATE
```

Stop. Waiting `P6_A0_GATE`.
