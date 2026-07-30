# Build and Release Guide

> Branch: `hardening/a14-lts-foundation`  
> Application ID: `tv.withaibuild.customiuizer.r14`

This document explains how the repository builds Debug, unsigned CI, and officially signed APKs. It also explains why the CI pipeline never touches official signing materials.

---

## 1. Build Types and File Names

The build produces **three** distinct artifacts with unambiguous names:

| Build command | Variant | Signing | Output name (version `r14.13.8`) |
|---|---|---|---|
| `./gradlew assembleDebug` | `debug` | Android debug key | `CustoMIUIzer-A14-r14.13.8-debug.apk` |
| `./gradlew assembleRelease` | `release` | **unsigned** | `CustoMIUIzer-A14-r14.13.8-unsigned-ci.apk` |
| `./gradlew assembleRelease -PofficialRelease=true` | `release` | Your `../keystore.properties` | `CustoMIUIzer-A14-r14.13.8.apk` |

* `./gradlew assembleDevelop` will produce `CustoMIUIzer-A14-r14.13.8-develop-unsigned-ci.apk`.
* `./gradlew assembleDevelop -PofficialRelease=true` will produce `CustoMIUIzer-A14-r14.13.8-develop.apk`.

You must **not** rename an unsigned CI APK to remove `-unsigned-ci`. You must **not** run `assembleRelease` on a machine that contains `../keystore.properties` unless you explicitly pass `-PofficialRelease=true`.

---

## 2. Why Signing Is Explicit

Previously, `app/build.gradle.kts` automatically enabled official signing whenever `../keystore.properties` existed. This caused two problems:

1. A developer could accidentally produce a formally signed APK by running a normal build on a machine that happened to have the release keystore.
2. CI could not guarantee that the uploaded "unsigned" release artifact was actually unsigned.

The new behavior makes the signing decision explicit:

* `assembleRelease` is **always** unsigned.
* Official signing is only enabled when the command-line property `officialRelease` is `true`.
* The build fails immediately if the property is set but the configuration or keystore file is missing.

---

## 3. Official Signing Configuration

Official signing is configured **outside** the repository:

```text
<repo-root>/../keystore.properties
```

Expected contents:

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=<keystore-password>
keyAlias=<key-alias>
keyPassword=<key-password>
```

Requirements:

* `storeFile` must point to an existing file.
* All four fields must be present and non-empty.
* The build script does **not** write any of these values to logs.
* The build script does **not** generate or overwrite keystores.

Example official build:

```bash
./gradlew assembleRelease -PofficialRelease=true --no-daemon
```

If the file is missing, you will see:

```text
officialRelease=true but ../keystore.properties was not found
```

If a required key is missing, the build fails with the missing key name, not its expected value.

---

## 4. Verifying APK Signatures

Use the included cross-platform helper:

```bash
# CI unsigned verification
python tools/verify-apk-signatures.py \
  --debug-apk app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.13.8-debug.apk \
  --release-apk app/build/outputs/apk/release/CustoMIUIzer-A14-r14.13.8-unsigned-ci.apk \
  --release-kind ci

# Official build verification
python tools/verify-apk-signatures.py \
  --debug-apk app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.13.8-debug.apk \
  --release-apk app/build/outputs/apk/release/CustoMIUIzer-A14-r14.13.8.apk \
  --release-kind official \
  --expected-sha256 <your-certificate-sha-256>
```

The helper finds the Android SDK from `local.properties`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME`, then runs `apksigner verify --print-certs -v`.

---

## 5. CI Behavior

The CI pipeline in `.github/workflows/ci.yml`:

* Builds `assembleDebug` and `assembleRelease`.
* Verifies that the Debug APK is signed and the Release APK is **not** signed.
* Verifies that no file named `CustoMIUIzer-A14-r14.13.8.apk` is produced by a normal CI build.
* Uploads `debug-apk`, `unsigned-release-apk`, and `broadcast-probe-debug-apk` artifacts.
* Does **not** read or write `../keystore.properties`.
* Does **not** upload official named APKs.

The CI artifact retention is set to a short, reasonable period to avoid keeping unsigned artifacts indefinitely.

---

## 6. What Must Not Be Published

* `CustoMIUIzer-A14-r14.13.8-unsigned-ci.apk` is for CI and local testing only. It is unsigned and must not be distributed as a release.
* `CustoMIUIzer-A14-r14.13.8-debug.apk` is signed with the local Android debug key and is not suitable for release.
* `CustoMIUIzer-A14-r14.13.8.apk` (no suffix) is the only official release filename. It must be produced with `officialRelease=true` and verified with `apksigner` before distribution.

---

## 7. Manual Release Gate

Before distributing an official APK, verify:

1. The build command was `./gradlew assembleRelease -PofficialRelease=true`.
2. `apksigner verify --print-certs -v` passes on `CustoMIUIzer-A14-r14.13.8.apk`.
3. The certificate SHA-256 matches your expected release certificate.
4. `zipalign -c -v 4 <apk>` confirms alignment.
5. The `applicationId`, `versionName`, `versionCode`, ABI, and Xposed metadata match the release notes.
6. The APK has been installed and smoke-tested on the target ROM/Android version.
