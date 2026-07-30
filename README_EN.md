# CustoMIUIzer A14

[简体中文](README.md) | English

An independently maintained CustoMIUIzer build for **HyperOS 1 / Android 14**.

The project uses
[MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
as its Android 14 functional reference, with a separate package name, release line, signing
identity, and modern libxposed API integration. It is not an official upstream release and does
not support Android 15, Android 16, or other major MIUI / HyperOS versions.

## Current Release

**r14.13.8 is the only public release currently maintained.**

- [Source repository Release](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.8)

Current production APK:

| Item | Value |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.8.apk` |
| versionCode / versionName | `186 / r14.13.8` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| System | HyperOS 1 / Android 14 (SDK 34) |
| ABI | `arm64-v8a` |
| libxposed | minimum API 101 / target API 102 |
| Hot Reload | Disabled |
| SHA-256 | `B0E7D4A3CB50E39748531D5B0FD3CB95F81C1F777DDAC9E346B8C8D67B8CBE62` |
| Signing certificate SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

Download only from the official location above. If the LSPosed module repository mirror has not
yet caught up, use the source repository Release as the authority.

## What r14.13.8 Fixes

- Tightens the boundary between hook-process utilities and settings-app utilities, reducing
  unrelated class loading.
- Removes six obsolete GlobalActions forwarding stubs and calls their implementations directly.
- Fixes in-app "Reboot system" when no custom actions are configured.
- Distinguishes an unclaimed soft-reboot broadcast from a receiver-side failure, so the latter is
  no longer reported as "LSPosed service not connected".
- Completed on-device acceptance on Android 14 / HyperOS 1 with LSPosed 2.1.1 (7790).

Known issue: system Toast suppression may still be ineffective; this release does not change that
logic.

See the [CHANGELOG](CHANGELOG_EN.md) for the complete history.

## Before Installing

The old signing key used by public releases up to and including `r14.12.0` has been lost. Those
builds cannot be updated in place to r14.13.8. If you are using one of them:

1. Back up the module settings from the old installation.
2. Record the current LSPosed / Vector scope.
3. Uninstall the old version.
4. Install `CustoMIUIzer-A14-r14.13.8.apk`.
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

r14.13.8 passed the repository invariant checks, unit tests, all three lint variants, and
Debug / Release builds. The APK was checked for R8, resource shrinking, zipalign, v2 signing,
package metadata, SHA-256, and the actual signing certificate.

The soft-reboot fix completed on-device acceptance on Android 14 / HyperOS 1 with LSPosed 2.1.1
(7790): the module loaded in SystemUI and Launcher, both reboot cycles completed, and no P0/P1,
target-process crash, hook exception, or duplicate receiver registration was found. Other ROM and
system-app versions still require separate verification. See [Verification](docs/VERIFICATION.md)
for the evidence and remaining boundaries.

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
