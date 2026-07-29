# CustoMIUIzer A14

[简体中文](README.md) | English

An independently maintained CustoMIUIzer build for **HyperOS 1 / Android 14**.

The project uses
[MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
as its Android 14 functional reference, with a separate package name, release line, signing
identity, and modern libxposed API integration. It is not an official upstream release and does
not support Android 15, Android 16, or other major MIUI / HyperOS versions.

## Current Release

**r14.13.7 is the only public release currently maintained.**

- [Source repository Release](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.7)
- [LSPosed module repository Release](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/185-r14.13.7)

Both locations provide the same APK:

| Item | Value |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.7.apk` |
| versionCode / versionName | `185 / r14.13.7` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| System | HyperOS 1 / Android 14 (SDK 34) |
| ABI | `arm64-v8a` |
| libxposed | minimum API 101 / target API 102 |
| Hot Reload | Disabled |
| SHA-256 | `11D01A737BED25C3C4D31153DE22CB918A651D0DD043D0374E2C0E41D32492CC` |
| Signing certificate SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

Download only from these two official locations.

## What r14.13.7 Fixes

- Settings changed while the LSPosed service is temporarily unavailable are no longer silently
  discarded; a full reconciliation runs after the connection is restored.
- Soft reboot now sends an ordered broadcast directly to SystemUI instead of depending on the
  settings application's binder state.
- Damaged or type-mismatched list preferences fall back to safe defaults instead of propagating
  exceptions into SystemUI or `system_server`.
- Status-bar battery and temperature formatting updates immediately; options that genuinely need
  a restart are identified in the UI.
- Lock-screen album art uses generation checks, a byte-bounded cache, and correct cache keys,
  preventing parallel full-screen processing and ineffective caching.
- A saturated icon-loading queue now releases in-flight state correctly, so icons do not remain
  permanently blank.

See the [CHANGELOG](CHANGELOG_EN.md) for the complete history.

## Before Installing

The old signing key used by public releases up to and including `r14.12.0` has been lost. Those
builds cannot be updated in place to r14.13.7. If you are using one of them:

1. Back up the module settings from the old installation.
2. Record the current LSPosed / Vector scope.
3. Uninstall the old version.
4. Install `CustoMIUIzer-A14-r14.13.7.apk`.
5. Restore the scope and settings.
6. Fully reboot the device.

Do not uninstall the old version before backing up. APKs from other sources may use a different
certificate and may not install over this build.

## Feature Scope

- Status bar, icons, battery, signal, network speed, date, and temperature;
- Control center, volume panel, brightness, and notification behavior;
- Lock screen, charging information, media UI, and shortcuts;
- Launcher, recents, folders, icons, and home-screen gestures;
- Navigation bar, buttons, custom actions, power menu, and system animations;
- App, permission, installer, sharing, privacy-app, and app-lock behavior.

Availability depends on the ROM and system-app versions. Vendor updates may change hook targets.
Do not enable this module together with upstream or another CustoMIUIzer-derived module.

## Installation

1. Download and install the APK.
2. Enable the module in LSPosed / Vector and confirm the recommended scope.
3. Open the module settings once.
4. Fully reboot the device.
5. Check `system_server`, SystemUI, Launcher, and the features you use.

## Verification Boundary

r14.13.7 passed the repository invariant checks, 171 unit tests, all three lint variants, and
Debug / Release builds. The APK was checked for R8, resource shrinking, zipalign, v2 signing,
package metadata, SHA-256, and the actual signing certificate.

**This r14.13.7 build has not completed its full on-device acceptance cycle, so it is described as
the current public release rather than an on-device-verified stable release.** Behavior involving
SystemUI, Launcher, `system_server`, ROM reflection targets, or an API 102 framework still needs to
be confirmed through loading logs and real-device behavior. See
[Verification](docs/VERIFICATION.md) for the evidence and remaining boundaries.

## Development and Build

JDK 17 and the Android SDK are required:

```powershell
python tools/check-invariants.py
.\gradlew.bat --no-daemon test lintVitalRelease assembleDebug assembleRelease
```

Production signing configuration is stored outside the repository. Never commit keystores,
passwords, tokens, private logs, caches, or local build state.

Engineering documents:

- [Project lineage](docs/PROJECT_LINEAGE.md)
- [libxposed API 101 / 102 compatibility](docs/LIBXPOSED_API_101_102_COMPATIBILITY.md)
- [LSPosed service binder delivery and failure mode](docs/LSPOSED_BINDER_DELIVERY.md)
- [Engineering method](docs/ENGINEERING_METHOD.md)
- [Maintenance checkpoint](docs/MAINTENANCE_CHECKPOINT.md)

The engineering documents are maintained primarily in Chinese.

## License and Credits

This project is derived from Mikanoshi/CustoMIUIzer and references the Android 14 work in
[MonwF/customiuizer](https://github.com/MonwF/customiuizer).
Thanks to the maintainers of LSPosed / libxposed, DexKit, and related open-source projects.

Distributed under [GPL-3.0](LICENSE). See [NOTICE.md](NOTICE.md) for provenance and independent
maintenance details.
