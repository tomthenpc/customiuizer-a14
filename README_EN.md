# CustoMIUIzer A14

[简体中文](README.md) | English

A Kotlin-refactored, independently maintained CustoMIUIzer build for
**HyperOS 1 / Android 14**.

The project uses
[MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
as its Android 14 functional reference, with a separate package name, release line, signing
identity, and modern libxposed API integration. It is not an official upstream release and does
not support Android 15, Android 16, or other major MIUI / HyperOS versions.

## Current Release

The current working branch is `release/r14.15.3`, a locally-built, production-signed candidate.
This version is not a public Release yet; real-device validation on Android 14 / HyperOS and
LSPosed log triage are pending.

| Item | Value |
| --- | --- |
| Branch | `release/r14.15.3` |
| APK | `CustoMIUIzer-A14-r14.15.3.apk` |
| versionCode / versionName | `191 / r14.15.3` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| System | HyperOS 1 / Android 14 (SDK 34) |
| ABI | `arm64-v8a` |
| libxposed | minimum API 101 / target API 102 |
| Hot Reload | Disabled |
| Size | 3,107,273 bytes |
| SHA-256 | `2561BFA49CC8B32E931AFE2B7B520CC2A535B8D333EC8E9A8FF3D73EB19DE58D` |
| Signing certificate SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

The previous public stable release is `r14.13.8`. Historical assets and verification details are in
the [CHANGELOG](CHANGELOG_EN.md) and [RELEASE_ARCHIVE](docs/RELEASE_ARCHIVE.md).

## What r14.15.3 Changes (candidate)

- Consolidates the `hardening/a14-lts-foundation` hardening and the `integration/a14-r14.15.1`
  runtime code baseline.
- Brings in the dual-row network speed line spacing `70%–130%`, prerequisite note localization,
  and `feature-semantics` metadata from `devin/r14-netspeed-font-spacing-i18n`.
- Brings in the About attribution text wrapping and SeekBar system-text style inheritance fix
  from `fix/a14-ui-text-inheritance-and-about-wrap`.
- Version: `versionCode 191 / versionName r14.15.3`.

**Known boundary:** system Toast suppression logic is unchanged; network speed display, About
layout, and the full manual smoke test are still pending real-device validation.

See the [CHANGELOG](CHANGELOG_EN.md) for the complete history.

## Before Installing

The old signing key used by public releases up to and including `r14.12.0` has been lost. Those
builds cannot be updated in place to `r14.15.3`. If you are using one of them:

1. Back up the module settings from the old installation.
2. Record the current LSPosed / Vector scope.
3. Uninstall the old version.
4. Install `CustoMIUIzer-A14-r14.15.3.apk`.
5. Restore the scope and settings.
6. Fully reboot the device.

Do not uninstall the old version before backing up. Candidate APKs should be taken from the local
build output `../release-output/A14/` or from the maintainer's official channel.

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

`r14.15.3` is expected to pass the repository invariant checks, unit tests, all three lint
variants, and Debug / Release builds before the production-signed candidate APK is produced. The
APK must be checked for R8, resource shrinking, zipalign, v2 signing, package metadata, SHA-256,
and the actual signing certificate.

Current status: `r14.15.3` is a local production-signed candidate. Static checks and builds pass;
real-device validation on Android 14 / HyperOS and LSPosed log triage are pending. See
[Verification](docs/VERIFICATION.md) and `BUILD_INFO_R14_15_3.txt` for the evidence and remaining
boundaries.

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
