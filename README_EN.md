# CustoMIUIzer A14 Kotlin Refactor

[简体中文](README.md) | English

A Kotlin-refactored, independently maintained CustoMIUIzer build for
**HyperOS 1 / Android 14**.

The project uses [MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
as its Android 14 functional reference, with a separate package name, release line, signing
identity, and modern libxposed API. It is not an official upstream release and does not support
Android 15, Android 16, or other major MIUI / HyperOS versions.

## Current Release

| Item | Value |
| --- | --- |
| Version | `r14.15.3` |
| versionCode | `191` |
| System | HyperOS 1 / Android 14 (SDK 34) |
| ABI | `arm64-v8a` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| libxposed | `minApiVersion=101`, `targetApiVersion=102` |
| staticScope | `false` |
| APK | `CustoMIUIzer-A14-r14.15.3.apk` |
| APK SHA-256 | `F7AB34722B0193DD8C97DF0146C968E5A6064655AD497061E902CD1545375E7E` |
| Signing certificate SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

The previous public release is `r14.13.8`.

The LSPosed user download page is at:

`Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14`

> This Releases page only keeps the current formal release. Changelog for older versions has been merged into the current Release and CHANGELOG. Older APKs are no longer available for download; the historical source tags remain.

## r14.15.3 Highlights

* Restores the previously-removed `system` scope, fixing `system_server` hook loading and the silent
  failure of related system-level features.
* Hardens the `system_server` BroadcastReceiver in Global Actions with exception boundaries, trust
  checks, and ordered-broadcast result handling.
* Improves owner binding, replacement, cleanup, and concurrent registration handling for Receivers
  and Observers, avoiding duplicate registration and registered-but-untracked receivers.
* Improves hook-load diagnostics, compatibility logging, and runtime error recording.
* Keeps network-speed bold text in the current SystemUI font family, with a fallback for when no
  valid bold glyph is available.
* Adds dual-row network speed line spacing from `70%` to `130%`, with related localization notes.
* Fixes text-style inheritance for settings controls and attribution/version text wrapping on the
  About page.
* Merges the runtime-safety, scope, network-speed, and UI fixes after `r14.13.8`.

Full history is in [CHANGELOG](CHANGELOG_EN.md).

## Compatibility

| Item | Value |
| --- | --- |
| System | HyperOS 1 / Android 14 |
| Android SDK | 34 |
| ABI | `arm64-v8a` |
| Framework | LSPosed / Vector implementing libxposed API 101 or 102 |
| Android 15/16 | Not supported |

Feature availability depends on the device ROM and system-app versions. Vendor updates may change
hook targets. Do not enable this module together with the upstream version or another
CustoMIUIzer-derived module.

## Main Features

* Status bar icons, battery, signal, network speed, date, and temperature;
* Control center, volume panel, brightness, and notification behavior;
* Lock screen, charging info, media UI, and shortcuts;
* Launcher, recents, folders, icons, and home-screen gestures;
* Navigation bar, buttons, custom actions, power menu, and system animations;
* App, permission, installer, sharing, privacy app, and app-lock behavior.

## Important Upgrade Notes

Builds from `r14.13.5` and later using the new signing key can be installed as updates.

The old signing key used by public releases up to and including `r14.12.0` has been lost. Those
builds cannot be updated in place. Before upgrading:

1. Back up module settings.
2. Record the current LSPosed / Vector scope.
3. Uninstall the old version.
4. Install the new version.
5. Re-enable the scope.
6. Restore settings.
7. Fully reboot the device.

Do not uninstall the old version before completing the backup.

## Installation

1. Download the official APK from the LSPosed release repository.
2. Verify the APK SHA-256.
3. Install the APK.
4. Enable the module in LSPosed / Vector.
5. Make sure the recommended scope includes `system`.
6. Open the module settings once and fully reboot the device.

## Build

JDK 17 and the matching Android SDK are required.

```bash
./gradlew :app:assembleRelease -PofficialRelease=true
```

Production signing configuration is stored outside the repository. Never commit keystores,
passwords, tokens, real `keystore.properties`, APKs, signing backups, private logs, caches, or local
build state.

## Verification Notes

`r14.15.3` has completed the official Release APK build and the following basic checks:

* APK v2 signing;
* zipalign;
* applicationId, versionCode, versionName;
* libxposed `module.prop`, `scope.list`, and `java_init.list`;
* `system` and `android` scopes;
* APK SHA-256 and the production signing certificate.

This release did not run the full unit test suite, Lint, project Audit, ADB regression, or full
real-device smoke tests. APK build and metadata checks do not prove that every hook works on every
HyperOS 1 ROM.

## Feedback

When reporting issues, please provide:

* Module version and APK source;
* Device, ROM, and system-app versions;
* Framework name and actual libxposed API;
* Currently enabled scope;
* Logs from `system_server`, SystemUI, or Launcher after a full reboot;
* Repeatable feature toggles and steps.

## License and Acknowledgements

The project is derived from Mikanoshi/CustoMIUIzer, with Android 14 work referenced from
MonwF/customiuizer. It is distributed under GPL-3.0.
