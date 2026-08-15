# A14 fuxi ROM compatibility audit (RC-A0)

> Scope: static ROM-only evidence. No production changes. No APK generated.

## 1. ROM versions and extraction

| Region | Version | Root path |
|---|---|---|
| CN | OS1.0.19.0.UMCCNXM | `C:/Home/xiaomi/rom/A14/fuxi_images_OS1.0.19.0.UMCCNXM_14.0` |
| Global | OS1.0.11.0.UMCMIXM | `C:/Home/xiaomi/rom/A14/fuxi_global_images_OS1.0.11.0.UMCMIXM_14.0` |
| TW | OS1.0.8.0.UMCTWXM | `C:/Home/xiaomi/rom/A14/fuxi_tw_global_images_OS1.0.8.0.UMCTWXM_14.0` |

- Extraction cache: `C:/Home/xiaomi/rom/A14/_compat_work` (outside repository).
- Relevant logical partitions: `system_a`, `system_ext_a`, `product_a`, `vendor_a`, `odm_a`, `mi_ext_a`, `cust.img`.
- ROM images were not modified, repacked or deleted.
- Full ROM decompile was not performed; only APKs referenced by current production source were parsed.

## 2. Artifact inventory

- Total indexed APK/JAR artifacts across three ROMs: 4783.
- Relevant host packages parsed for DEX evidence (per build): `com.android.systemui`, `com.miui.home`, `com.miui.securitycenter`, `com.android.settings`, `com.miui.powerkeeper`, `com.android.incallui`, package-installer variants, `com.miui.miwallpaper`, `com.miui.screenshot`, `com.miui.gallery`, input-method variants, and others.

### Package / version matrix

| Package | CN | Global | TW |
|---|---|---|---|
| `com.android.systemui` | 20230316.0 | 20230316.0 | 20230316.0 |
| `com.miui.home` | RELEASE-4.39.30.8604-08262248 | RELEASE-4.39.30.8604-08262248 | RELEASE-4.39.30.8604-08262248 |
| `com.miui.securitycenter` | 8.8.3-240129.0.1 | 8.9.6-241105.1.1 | 8.9.6-241105.1.1 |
| `com.android.settings` | 14 | 14 | 14 |
| `com.miui.powerkeeper` | 4.2.00 | 4.2.00 | 4.2.00 |
| `com.android.incallui` | 3.0.74 | — | 3.3.55 |
| `com.miui.packageinstaller` | 5.2.8.0.0-20240913 | — | — |
| `com.miui.global.packageinstaller` | — | 2.9.6 | 2.9.6 |
| `com.google.android.packageinstaller` | — | 14-11704966 | 14-11704966 |

- **Package installer**: CN uses `com.miui.packageinstaller` (5.2.8.0.0-20240913). Global/TW use `com.google.android.packageinstaller` (14-11704966) and a secondary `com.miui.global.packageinstaller` (2.9.6) APK.
- **Security Center**: CN is `8.8.3-240129.0.1`; Global/TW are `8.9.6-241105.1.1`.
- **InCallUI**: Present in CN (`3.0.74`) and TW (`3.3.55`); Global does not ship `com.android.incallui`; it ships `com.google.android.dialer` and an AOSP `Dialer.apk`.

## 3. Contract inventory methodology

- Scanned `app/src/main/java/tv/withaibuild/customiuizer/mods/**` for `XposedHelpers` / `ModuleHelper` / `XposedBridge` calls (`findClass*`, `findAndHookMethod*`, `hookAllMethods*`, `getObjectField*`, `callMethod*`, `getIdentifier`, `Class.forName`, package-name checks).
- Deduplicated by `target_package + target_class + member_type + target_member + parameter_types` to obtain unique hook contracts.
- Total unique hook contracts extracted from source: **966**.
- Of these, **442** had a resolvable target class; **524** could not be resolved by static regex (local variables, `thisObject.javaClass`, reflection, or parser limitations such as escaped `\$`).
- Each resolved contract was checked against the DEX indices of the three ROMs for class, method/constructor and field presence.

## 4. Three-ROM comparison matrix

| Classification | Count | Meaning |
|---|---|---|
| SAME_3_OF_3 | 310 | Class and member verified in all three ROMs with no cross-build descriptor change. |
| SIMPLE_ALIAS | 1 | Same functionality exists under a different package/class name in some ROMs; a simple alias/fallback likely fixes it. |
| SIGNATURE_VARIANT | 0 | Class exists in all ROMs, but the target member has a different descriptor in at least one ROM. |
| PACKAGE_MOVE | 2 | Target package/artifact or class package differs across ROMs (different host APK or partition path). |
| FEATURE_ABSENT | 129 | Target package or class/member is absent in at least one ROM. |
| SEMANTIC_DIVERGENCE | 0 | Same-signature member likely behaves differently; needs semantic review. |
| UNKNOWN | 524 | Target could not be resolved from source, or the host class is in framework/odex/vdex outside the parsed APK set. |

## 5. P0 compatibility shortlist

P0 = current hook fails on at least one ROM and the fix is very simple/safe.

### 5.1 Package installer routing / package split (Global/TW)

Evidence:
- `ProcessRouter.kt` routes package-installer hooks only for `com.miui.packageinstaller`. Global/TW do not ship this package.
- `com.miui.packageInstaller.InstallStart.getCallingPackage` exists in CN `MIUIPackageInstaller.apk`; the equivalent AOSP class `com.android.packageinstaller.InstallStart` exists in Global/TW `GooglePackageInstaller.apk`.
- Miui-specific classes `com.miui.packageInstaller.ui.listcomponets.AppInfoViewObject`, `com.miui.packageInstaller.ui.listcomponets.SafeModeTipViewObject\$ViewHolder` and `com.miui.packageInstaller.model.ApkInfo` are only in CN `MIUIPackageInstaller.apk` and absent in Global/TW.

Impact: `PackageInstallerMiuiPackageFeature`, `PackageInstallerAppInfoFeature` and `PackageInstallerPurifyFeature` fail to install on Global/TW (routed to `ProcessScope.GENERIC_APP` and/or classes missing).

Proposed safe fix (RC-B candidate): add `com.miui.global.packageinstaller` and `com.google.android.packageinstaller` to `ProcessRouter.packageInstallerPackages`; add `com.android.packageinstaller.InstallStart` as fallback for `InstallStart.getCallingPackage`; guard Miui-specific UI classes with `findClassIfExists` (already partially guarded).

### 5.2 `com.android.incallui` absent in Global

Evidence:
- `com.android.incallui.InCallPresenter.answerIncomingCall` and `com.android.incallui.InCallActivity.onCreate` are present in CN and TW, absent in Global.
- Global ships `com.google.android.dialer` and `Dialer.apk` instead of `com.android.incallui`.
- `ProcessRouter.kt` maps `com.android.incallui` to `ProcessScope.PHONE`; on Global the package does not exist.

Impact: InCallUI-related `Various.kt` hooks fail on Global.

Proposed safe fix: either guard InCallUI hooks with package-name checks and skip on Global, or add `com.google.android.dialer` / AOSP `Dialer` package to `ProcessRouter` after class verification (higher risk).

### 5.3 `RegionSamplingHelper` package/class move (SecurityCenter → SystemUI)

Evidence:
- `Various.kt:631` loads `com.android.systemui.navigationbar.gestural.RegionSamplingHelper` from the `com.miui.securitycenter` classloader (via `AddSideBarExpandReceiverHook`).
- CN `MIUISecurityCenter.apk` contains `com.android.systemui.navigationbar.gestural.RegionSamplingHelper`. Global/TW `MIUISecurityCenter.apk` do **not** contain this class.
- Global/TW `MiuiSystemUI.apk` contain `com.android.systemui.shared.navigationbar.RegionSamplingHelper` instead.

Impact: `AddSideBarExpandReceiverHook` fails to install on Global/TW (class not found in SecurityCenter classloader).

Proposed safe fix: add `com.android.systemui.shared.navigationbar.RegionSamplingHelper` as a fallback class, or move the hook to a SystemUI feature if the functionality belongs there; keep CN primary class unchanged.

## 6. P1 compatibility shortlist

P1 = compatibility improvement is evident, but actual current failure is not fully proven.

- **Security Center version delta**: CN `8.8.3` vs Global/TW `8.9.6`. `com.miui.appmanager.*` classes (used by `AppInfoHook`) are in `MIUISecurityCenter.apk` in all three ROMs and signatures match (`SAME_3_OF_3`), but the larger version difference warrants runtime/dynamic verification of subtle behavior changes.
- **Input method packages**: CN ships `com.miui.securityinputmethod` / `com.iflytek.inputmethod.miui` / `SogouInput`; Global/TW ship `com.google.android.inputmethod.latin`. Contracts referencing `com.miui.inputmethod.InputMethodUtil` and `android.inputmethodservice.InputMethodService` could not be located in the parsed host APKs (framework or missing class); needs targeted decompile of the actual input-method APK on device.
- **InCallUI version delta**: TW `3.3.55` vs CN `3.0.74`; the target methods have matching descriptors in the two ROMs, but the version gap is a risk for overload resolution and call semantics.

## 7. SKIP and UNKNOWN

- SKIP: ~524 low-value/ambiguous contracts (library `androidx.*`/`miuix.*`, `Settings.System` framework classes, and unresolved field-access-on-variable patterns) where the source does not provide a concrete class to verify.
- UNKNOWN: Framework/server classes (`com.android.server.*`, `android.*`, `com.android.internal.*`, `com.android.location.fused`, etc.) and any contract whose target class could not be resolved from source. These require a VDEX/ODEX parser or device testing to verify.
- No cross-build method-signature variants were detected in the resolved contract set (`SIGNATURE_VARIANT = 0`).

## 8. Region/version hardcoding assessment

- `REGION_HARDCODE_REQUIRED = YES` for package-installer routing (CN uses `com.miui.packageinstaller`, Global/TW use `com.miui.global.packageinstaller` and `com.google.android.packageinstaller`) and for InCallUI (Global uses `com.google.android.dialer`).
- `VERSION_HARDCODE_REQUIRED = NO`: no build-specific version strings are required in hooks; only package/artifact routing and class fallbacks differ.

## 9. Production change authorization

- This phase is **AUDIT / EXTRACTION / DOCS ONLY**.
- No production hook, preference, resource, Gradle or test files were modified.
- No APK was generated.
- ROM binaries and full decompile outputs are **not** in Git; they remain in `C:/Home/xiaomi/rom/A14/_compat_work`.

## 10. Verification status

- `DEVICE_VERIFIED_CN = NO`
- `DEVICE_VERIFIED_GLOBAL = NO`
- `DEVICE_VERIFIED_TW = NO`
- `STATIC_ROM_VERIFIED = YES` (three-ROM APK/JAR artifact index + DEX method/field presence extracted from the specified images).

## 11. Limitations

- DEX evidence is from APK `classes*.dex` only. Boot/framework classes living in VDEX/ODEX (`boot-framework.vdex`, `services.jar` stubs, etc.) are not parsed; those contracts are classified `UNKNOWN`.
- The source scanner is regex-based; some local-variable class references, `thisObject.javaClass` fields, and escaped `\$` inner-class names are unresolved and counted as `UNKNOWN`.
- The audit does not prove runtime timing, classloader visibility, or semantic behavior; it proves static class/method/field presence or absence in the three extracted ROM images.

## 12. Evidence files

All heavy/derived artifacts are outside the repository:
- `C:/Home/xiaomi/rom/A14/_compat_work/contract_inventory_v3.json` — source-level unique hook contracts.
- `C:/Home/xiaomi/rom/A14/_compat_work/contract_comparison.json` — per-build DEX comparison results.
- `C:/Home/xiaomi/rom/A14/_compat_work/dex_index/dex_index_*.json.gz` — per-build DEX class/method/field indices.
- `C:/Home/xiaomi/rom/A14/_compat_work/artifact_index.json` — 4,783 APK/JAR artifact inventory.

---
Generated from base `3084297afd0b414e4db103dbc119d96700abfe62` on `devin/a14-final-polish-r14.20.0`.
This is a documentation-only commit.