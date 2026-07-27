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
| Stable version | `r14.13.4` |
| Supported system | HyperOS 1 / Android 14 (SDK 34) |
| ABI | `arm64-v8a` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| libxposed API | min 101 / target 102 |
| Hot Reload | Disabled |
| Build | Kotlin DSL / version catalog / R8 |
| Download | [Source Release](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.4) · [LSPosed listing](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/182-r14.13.4) |

## r14.13.4 Highlights

- **Settings and locale cleanup**: moves the in-app language entry to About and fixes locale,
  theme, system-bar, and Fragment recreation behavior.
- **SystemUI lifecycle fix**: status-bar temperature/current text icons no longer retain stale
  Views indefinitely.
- **Resource-hook hot-path cleanup**: reduces boxing, reflection, and unnecessary parsing on
  resource lookup misses, with safely published caches.
- **Kotlin migration regressions fixed**: restores first-match thermal-zone behavior and removes
  repeated Regex compilation from pair parsing.
- **Preference-chain reliability**: empty RemotePreferences snapshots can be retried, and listener
  state is set only after successful registration.
- **One API 101/102 APK**: retains HyperOS 1 / Android 14 support, R8, resource shrinking,
  zipalign, and APK Signature Scheme v2.

## Download and Verification

- APK: `CustoMIUIzer-A14-r14.13.4.apk`
- Size: 3,032,173 bytes
- SHA-256: `E8A2BD362C0540972441B8D1DE0BCACE8FE85FEF71F31406F3B4DA1A4027D26C`
- Signing certificate SHA-256:
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`

Download only from this project's Releases or its corresponding LSPosed module repository.
A differently signed build may not install over the existing app. Back up your settings before
removing an older installation.

## Important: Back Up and Reinstall

The private signing key used by the public `r14.12.0` release and earlier releases has been lost.
`r14.13.4` is signed with a new official certificate and cannot be installed as an in-place update
over those older public builds.

Upgrade steps:

1. Back up the module settings in the old installation.
2. Record the current LSPosed/Vector scope.
3. Uninstall the old version.
4. Install `CustoMIUIzer-A14-r14.13.4.apk`.
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

- [CHANGELOG](CHANGELOG.md)
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

The public Releases page keeps four key versions:

| Version | Purpose |
| --- | --- |
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
