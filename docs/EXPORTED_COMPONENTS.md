# Exported Component Security Audit

> Branch: `hardening/a14-lts-foundation`  
> Scope: components with `android:exported="true"` in `app/src/main/AndroidManifest.xml`.

This document records why every exported component is exported, who is expected to call it, what extra/data/URI it accepts, what side effects it has, and what verification measures are in place. It is the authoritative source for the static invariant that **every exported component in the manifest must be listed here**.

---

## `tv.withaibuild.customiuizer.MainActivity` (Activity)

| Field | Value |
|---|---|
| Exported reason | Xposed module settings entry + Quick Settings tile preferences. The `de.robv.android.xposed.category.MODULE_SETTINGS` category requires it to be exported so Xposed/LSPosed launchers can open the module settings. `android.intent.action.MAIN` is also required for a visible entry. |
| Intent filter | `android.intent.action.MAIN`, `android.service.quicksettings.action.QS_TILE_PREFERENCES`, category `de.robv.android.xposed.category.MODULE_SETTINGS` |
| Expected callers | LSPosed / Xposed framework, launcher, Quick Settings tile (system), user. |
| Permission | None on the Activity; `XposedBridge` only calls if the module is enabled by the user. QS tile uses `android.permission.BIND_QUICK_SETTINGS_TILE` on the tile service, not on this Activity. |
| Controllable extras/data | Launcher extras are ignored. QS tile `QS_TILE_PREFERENCES` has no extra handling. `onCreate`/`onNewIntent` only operate on internal preference state. |
| Side effects | Opens module settings UI. No privileged actions triggered directly from the Intent. |
| Verification | Code review; `MainActivity` does not perform privileged operations based on external extras. |
| Risk | Low. Standard module settings entry. |
| Compatibility exception | Yes. Required by Xposed/LSPosed and Android launcher conventions. |
| Test | Build + manifest check; no active fuzzing required for a settings launcher. |

---

## `tv.withaibuild.customiuizer.GateWayLauncher` (Activity-Alias)

| Field | Value |
|---|---|
| Exported reason | Provides an icon in the system launcher. `targetActivity` is `MainActivity`. |
| Intent filter | `android.intent.action.MAIN`, category `android.intent.category.LAUNCHER` |
| Expected callers | System launcher. |
| Permission | None. |
| Controllable extras/data | None. Alias forwards to `MainActivity` unchanged. |
| Side effects | Same as `MainActivity`. |
| Verification | `targetActivity` is the already-audited `MainActivity`; `android:allowTaskReparenting="true"` is set. |
| Risk | Low. Pure launcher alias. |
| Compatibility exception | Yes. Required for launcher icon. |
| Test | Manifest check that `targetActivity` exists and is exported/audited. |

---

## `tv.withaibuild.customiuizer.Credentials` (Activity)

| Field | Value |
|---|---|
| Exported reason | Implements the "Secure lockscreen password" credential screen. Must be callable from system/launchers as a shortcut target. |
| Intent filter | None (direct component launch). |
| Expected callers | User via launcher/shortcut, system. |
| Permission | None. |
| Controllable extras/data | Does not act on external `Intent` extras; it only renders the credential UI and, on user input, grants a local `BiometricPrompt` result. |
| Side effects | Displays a full-screen credential UI; does not change system security state except through user confirmed biometric/lock credentials. |
| Verification | Code review confirmed no privileged action triggered by `Intent` extras. |
| Risk | Low. User-interactive credential UI. |
| Compatibility exception | Yes. Required for lockscreen shortcut. |
| Test | Build + manual interaction test. |

---

## `tv.withaibuild.customiuizer.CredentialsLauncher` (Activity-Alias)

| Field | Value |
|---|---|
| Exported reason | Enabled selectively to provide a launcher entry for `Credentials`. Disabled by default (`android:enabled="false"`). |
| Intent filter | `android.intent.action.MAIN`, category `android.intent.category.LAUNCHER` |
| Expected callers | System launcher when enabled. |
| Permission | None. |
| Controllable extras/data | None; forwards to `Credentials`. |
| Side effects | Same as `Credentials`. |
| Verification | `targetActivity` is `Credentials`; disabled by default. |
| Risk | Low when disabled. If enabled, same risk as `Credentials`. |
| Compatibility exception | Yes. Optional launcher alias. |
| Test | Manifest check. |

---

## `tv.withaibuild.customiuizer.CredentialsShortcut` (Activity)

| Field | Value |
|---|---|
| Exported reason | Allows users to add a home-screen shortcut for the credential screen. |
| Intent filter | `android.intent.action.CREATE_SHORTCUT`, category `android.intent.category.DEFAULT` |
| Expected callers | Launcher when creating a shortcut. |
| Permission | None. |
| Controllable extras/data | Responds to `CREATE_SHORTCUT` to return a shortcut `Intent`; no `data` or extras are trusted. |
| Side effects | Returns a shortcut result; the shortcut itself later launches `Credentials`. |
| Verification | No user input accepted from the `Intent`; only returns a pre-constructed shortcut. |
| Risk | Low. Shortcut creation helper. |
| Compatibility exception | Yes. Required for shortcut support. |
| Test | Build + manifest check. |

---

## `tv.withaibuild.customiuizer.tasker.UnlockSettings` (Activity)

| Field | Value |
|---|---|
| Exported reason | Tasker / Locale plugin configuration Activity. The Locale plugin contract requires `com.twofortyfouram.locale.intent.action.EDIT_SETTING` receiver/activity to be exported. |
| Intent filter | `com.twofortyfouram.locale.intent.action.EDIT_SETTING` |
| Expected callers | Tasker, Locale, or any compatible automation host. |
| Permission | None (required by the Tasker/Locale plugin contract). |
| Controllable extras/data | Receives an incoming `Bundle` in `Constants.EXTRA_BUNDLE` for pre-population. The `Bundle` is only used to restore the `system_noscreenlock_force` radio selection; it does not grant access to the token. The token is only written back by this Activity after the user taps OK and `UnlockTokenProvider.bind()` succeeds. |
| Side effects | Calls `PackageManager` to read the caller's package, label, and signing certificates. Writes a per-host token to private `SharedPreferences` only after user confirmation. Returns a `Bundle` with the newly-issued/reused token and host package to the caller. |
| Verification | Two-stage flow: `prepare()` is read-only, `bind()` only runs on OK. `callingPackage` is required. Certificate mismatch cancels. `UnlockTokenContractTest` enforces the no-write-before-OK invariant. |
| Risk | Medium. Exported Activity that returns a secret token. Bound to `getCallingPackage()` and per-host certificate lineage; token not leaked to logs/Toast/export. |
| Compatibility exception | Yes. Required by Tasker/Locale plugin protocol. |
| Test | `UnlockTokenLogicTest`, `UnlockTokenContractTest`, manual with Tasker/Locale. |

---

## `tv.withaibuild.customiuizer.PrefsProvider` (ContentProvider)

| Field | Value |
|---|---|
| Exported reason | Exposes shared preferences and/or internal data to other app components or hosts. `android:grantUriPermissions="true"` and `tools:ignore="ExportedContentProvider"` are present. |
| Authority | `${applicationId}.provider.sharedprefs` |
| Expected callers | Module's own processes, possibly external apps granted URI permission. |
| Permission | None on the provider; `tools:ignore="ExportedContentProvider"` suppresses the lint warning. |
| Controllable URI | Any `ContentProvider` path. The implementation must be audited for path traversal, illegal file access, and write amplification. |
| Side effects | Read/write access to the provider's backing data. |
| Verification | Requires runtime source audit: `PrefsProvider` implementation should reject unexpected paths, not expose files outside its sandbox, and not leak the `unlock_hosts` SharedPreferences. |
| Risk | High if the provider exposes arbitrary files or the token storage. `tools:ignore` is a lint suppression, not a security justification. |
| Compatibility exception | No. Should be restricted if possible. |
| Test | Source audit of `PrefsProvider.kt`; add a contract test. |

---

## `tv.withaibuild.customiuizer.qs.AutoRotateService` (Service)

| Field | Value |
|---|---|
| Exported reason | Quick Settings tile service. The framework binds to it when the tile is added to the control center. |
| Intent filter | `android.service.quicksettings.action.QS_TILE` |
| Expected callers | System `com.android.systemui` (binds via `BIND_QUICK_SETTINGS_TILE`). |
| Permission | `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"` |
| Controllable extras/data | QS tile service lifecycle is controlled by the framework; no incoming `Intent` extras are trusted. |
| Side effects | Toggles system auto-rotate setting when the user taps the QS tile; protected by the QS tile permission. |
| Verification | `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"` restricts binding to the system or apps holding this signature/privileged permission. |
| Risk | Low. Permission-protected system tile. |
| Compatibility exception | Yes. Required for QS tile. |
| Test | Manifest check + permission check. |

---

## `tv.withaibuild.customiuizer.tasker.UnlockReceiver` (BroadcastReceiver)

| Field | Value |
|---|---|
| Exported reason | Tasker / Locale plugin execution entry. The `FIRE_SETTING` action must be exported for the host to trigger the saved task. |
| Intent filter | `com.twofortyfouram.locale.intent.action.FIRE_SETTING` |
| Expected callers | Tasker, Locale, or compatible automation host. |
| Permission | None (required by the Locale plugin contract). |
| Controllable extras/data | `Constants.EXTRA_BUNDLE` contains the saved host package and per-host token. The Bundle is not trusted by itself; `getSentFromPackage()` is required to be non-null and match the Bundle host. |
| Side effects | On successful `getSentFromPackage()` + token verification, forwards an `UnlockSetForced` broadcast to `com.android.systemui`. |
| Verification | `verifyBundle(context, bundle, sender)` checks sender, host, and token. No `getSentFromPackage()` means immediate rejection. Rate-limited `Log.w` for rejections. `UnlockTokenContractTest` and `BroadcastSecurityContractTest` cover the receiver. |
| Risk | High if sender identity can be bypassed. Currently no fallback; Tasker/Locale identity-sharing behavior must be verified on a real device. |
| Compatibility exception | Yes. Required by Tasker/Locale plugin protocol. |
| Test | `UnlockTokenContractTest`, `BroadcastSecurityContractTest`, real-device with Tasker/Locale. |

---

## Invariant

Every `android:exported="true"` component in `app/src/main/AndroidManifest.xml` must have a matching entry in this file. If a component is added or removed, update this audit and the `check-invariants.py` manifest check.
