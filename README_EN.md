# CustoMIUIzer A14

[简体中文](README.md) | English

An independently maintained CustoMIUIzer build for HyperOS 1 / Android 14.

This project uses
[MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
as the functional reference for Android 14, but has its own package name, version line, signing
identity, modern libxposed API integration, and release process. It is not an official upstream
release.

## Current Version

| Item | Status |
| --- | --- |
| Stable version | `r14.13.6` |
| Supported system | HyperOS 1 / Android 14 (SDK 34) |
| ABI | `arm64-v8a` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| libxposed API | min 101 / target 102 |
| Hot Reload | Disabled |
| Build | Kotlin DSL / version catalog / R8 |
| Download | [Source Release](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.6) · [LSPosed listing](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/184-r14.13.6) |

## r14.13.6 Highlights

- **Changing the interface language finally works.** `AppCompatDelegate.setApplicationLocales()`
  is a silent no-op during application start-up — on API 33+ it needs a live Activity to resolve
  `LocaleManager` — so the saved choice was never applied. It now calls the framework
  `LocaleManager` directly.
- **The language row no longer breaks the settings screen** and no longer overwrites the saved
  language with the XML placeholder: binding is read-only with respect to preference state.
- **No more spurious "module not active" warning**: "we stopped waiting" is now distinct from
  "proven disconnected", and a timeout gets one further wait before anything is reported.
- **A toggle opened from search updates immediately**: the search highlight is one-shot again and
  no longer permanently replaces the row's background.
- **System-process robustness**: every callback the module registers from a hook is now isolated,
  including two that run inside `system_server`; coroutine scopes carry a failure handler.
- **Registrations and memory**: receiver and observer cleanup is bound to an owner, fixing several
  cleanup paths that never ran; additional instance fields are stored by identity.
- **Performance**: hook arguments are no longer copied and re-marshalled per invocation;
  reflection cache hits do not allocate; the main-screen search is a single allocation-free scan.
- **Structure**: the three oversized hook files are split into 18 domain files. Every moved member
  was verified byte-identical; hook registration order and the R8 method set are unchanged.

## r14.13.5 Highlights

- **Fixes search navigation regression**: `Various` search results and sub-category items no longer
  return to the home page immediately after being tapped; the target Preference is highlighted and
  scrolled into view.
- **Restores the search state machine**: three states `0/1/2`, automatically collapsing the
  SearchView, clearing the query, and hiding the result list when returning to the home page.
- **Unifies empty/blank `sub` semantics**: `ModData.sub` is now nullable, and `SubFragment` no longer
  treats an empty string as a valid sub-category.
- **Corrects `openModCat()` return value**: System / Launcher / Controls / Various now return `true`
  on successful navigation and `false` for unknown categories.
- **Adds unit tests**: `SearchRouteResolver` and `SearchStateMachine` cover route parsing and state
  transitions.
- **One API 101/102 APK**: retains HyperOS 1 / Android 14 support, R8, resource shrinking,
  zipalign, and APK Signature Scheme v2.

## Verification status of this release

`r14.13.6` passes check-invariants (113 files, 8 rules, no violations), 122 unit tests,
lint / lintRelease / lintVitalRelease with 0 errors, and both debug and release builds — but it
has **not completed on-device acceptance**. It is signed with the same certificate as `r14.13.5`,
so it installs in place, and `r14.13.5` remains available as a source tag to roll back to.

## Download and Verification

- APK: `CustoMIUIzer-A14-r14.13.6.apk`
- Size: 3,082,129 bytes
- SHA-256: `35AEE1FEA1D7B38D967267210B7C272340B56B580ED49BEF4945AA9FC6F2ED96`
- Signing certificate SHA-256:
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`

Download only from this project's Releases or its corresponding LSPosed module repository.
A differently signed build may not install over the existing app. Back up your settings before
removing an older installation.

## Important: Back Up and Reinstall

The private signing key used by the public `r14.12.0` release and earlier releases has been lost.
`r14.13.6` is signed with the new official certificate and cannot be installed as an in-place
update over those older public builds, but it can be installed over `r14.13.5`.

Upgrade steps:

1. Back up the module settings in the old installation.
2. Record the current LSPosed/Vector scope.
3. Uninstall the old version.
4. Install `CustoMIUIzer-A14-r14.13.6.apk`.
5. Re-enable the module and restore the original scope.
6. Restore the settings.
7. Fully reboot the device.

Do not uninstall the old version before backing up its settings.

## Feature Scope

- Status bar, icons, battery, signal, network speed, date, and temperature;
- Control center, volume panel, brightness, and notification behavior;
- Lock screen, charging information, media UI, and shortcuts;
- Launcher, recents, folders, icons, and home-screen gestures;
- Navigation bar, buttons, custom actions, power menu, and system animations;
- App, permission, installer, sharing, privacy-app, and app-lock behavior.

Feature availability still depends on the ROM and system-app versions. Vendor updates may change
hook targets.

## Theoretical Performance and Power Assessment

The module runs inside long-lived processes such as SystemUI, Launcher, and `system_server`.
Its additional cost is mainly determined by:

> Trigger frequency × cost per invocation × process count × lifetime

The following compares code paths with upstream or earlier r14 implementations. It is a
**theoretical engineering assessment**, not a laboratory battery benchmark:

| Scenario | Earlier implementation risk | Current handling | Expected effect |
| --- | --- | --- | --- |
| Feature disabled | Hooks or listeners may still receive callbacks | Registration depends on feature state and process | Less unnecessary hook dispatch and resident overhead |
| SystemUI recreation | Receivers, observers, or tasks may be duplicated | Idempotent registration; resources detach with their owner | Fewer duplicate callbacks, leaks, and background tasks |
| Drawing and animation | Repeated resource lookup, formatting, and temporary objects | Cold-path caching and hot-path state reuse | Lower CPU, allocation, and GC pressure |
| Audio and periodic events | Unclear task ownership or duplicate scheduling | Lifecycle cancellation and system-event-driven work | Lower chance of idle wakeups and orphaned tasks |
| Settings lists | Repeated traversal, filtering, and deduplication | Stable caches and constant-time deduplication | Less main-thread work during page loading |
| Compatibility failure | Repeated reflection probes or log flooding | Cold-path probing and per-feature safe disablement | Fewer retries and lower restart-storm risk |

Theoretical gains should be most visible when many features are disabled, after repeated SystemUI
recreation, during long standby periods, and with frequent status-bar or control-center drawing.
Potential power savings come from reducing unnecessary callbacks, scheduling, polling, duplicate
registration, and exception retries—not from reducing line count.

The project does not claim a fixed improvement in battery life, CPU usage, or memory usage.
Actual results depend on enabled features, ROM, framework, usage patterns, and system-app versions.
Reliable numbers require same-device, same-configuration comparisons with Perfetto, Batterystats,
and memory profiling tools.

## Compatibility

- Supports only HyperOS 1 / Android 14 (SDK 34) on `arm64-v8a`.
- The framework must implement modern libxposed API 101 or API 102.
- Android 15, Android 16, and other MIUI/HyperOS versions are not supported.
- Do not enable this module together with upstream or another CustoMIUIzer-derived module.
- An API 101 manager may warn that `targetApiVersion=102` targets a newer API. Treat actual module
  loading logs and behavior as authoritative; the warning alone does not mean loading failed.
- API 102 Hot Reload, hook IDs, and atomic replacement are not enabled.

See the
[libxposed API 101/102 compatibility document](docs/LIBXPOSED_API_101_102_COMPATIBILITY.md)
for the complete boundary.

## Installation

1. Download and install the APK.
2. Enable the module in LSPosed/Vector and confirm the recommended scope.
3. Open module settings once.
4. Fully reboot the device.
5. Check `system_server`, SystemUI, Launcher, and the features you use.

Versions using an older package name do not migrate automatically to the current independent
package. Back up your configuration before uninstalling an older module.

## Verification Status

- Unit tests, Debug/Release builds, Lint, `lintRelease`, and `lintVitalRelease` passed;
- R8, resource shrinking, zipalign, APK v2 signing, and APK metadata checks passed;
- API 101 dependency back-compilation and the official API 102 build passed;
- Installation, a full device reboot, and complete `full.log` review were completed on API 101;
- No module-attributable crash, ANR, entry-point failure, hook failure, or API linkage error was
  found;
- API 102 still requires independent runtime validation on a framework that implements API 102.

A successful build proves only the static and artifact boundaries. It does not replace runtime
testing across ROMs, frameworks, and feature combinations. See
[Verification](docs/VERIFICATION.md) for evidence, artifact hashes, and unverified boundaries.

## Development and Build

JDK 17 and the Android SDK are required:

```powershell
.\gradlew.bat --no-daemon test assembleDebug
.\gradlew.bat --no-daemon clean test lint lintRelease lintVitalRelease assembleRelease
```

Signing configuration is stored outside the repository in `../keystore.properties`. Never commit
keystores, passwords, logs, caches, or local build state.

Engineering and provenance documents:

- [CHANGELOG](CHANGELOG_EN.md)
- [Project lineage](docs/PROJECT_LINEAGE.md)
- [libxposed API 101/102 compatibility](docs/LIBXPOSED_API_101_102_COMPATIBILITY.md)
- [Verification](docs/VERIFICATION.md)
- [Engineering method](docs/ENGINEERING_METHOD.md)
- [Historical Release archive](docs/RELEASE_ARCHIVE.md)

The linked engineering documents are currently maintained in Chinese.

## Differences from Upstream

| Dimension | This project | Upstream reference |
| --- | --- | --- |
| Positioning | Independent HyperOS 1 / Android 14 maintenance line | Android 14 functional reference |
| Package | `tv.withaibuild.customiuizer.r14` | Upstream package |
| Xposed API | One modern API 101/102 APK | v24.10.12 uses API 100 |
| Implementation | Kotlin-first with stable JVM boundaries retained | Primarily Java |
| Lifecycle | Explicit owners, cleanup, and duplicate prevention | Upstream implementation retained |
| Build | Kotlin DSL, version catalog, and R8 | Upstream build flow |

Upstream is used only to confirm feature intent and historical hook behavior. Older upstream code
will not replace the current Kotlin/API 101/102 implementation.

## Release Policy

The public Releases page keeps the following key versions:

| Version | Purpose |
| --- | --- |
| `r14.13.6` | Current stable release |
| `r14.13.5` | Previous release; source tag kept, Release removed |
| `r14.12.0` | Previous stable release; old signature, so back up and reinstall when upgrading |
| `r14.8.0` | Kotlin infrastructure fallback |
| `r14.7.4` | Consolidated r14.7.x Kotlin/coroutine migration |
| `r14.5.0` | Independent package, signing, and release baseline |

Release titles contain only the version number. Other versions remain documented in the
CHANGELOG, historical archive, and Git tags.

## License and Credits

This project is derived from Mikanoshi/CustoMIUIzer and references the Android 14 work in
[MonwF/customiuizer](https://github.com/MonwF/customiuizer).
Thanks to the maintainers of LSPosed/libxposed, DexKit, and the related open-source projects.

Distributed under [GPL-3.0](LICENSE). See [NOTICE.md](NOTICE.md) for provenance and independent
maintenance details.
