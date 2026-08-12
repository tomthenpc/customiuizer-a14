# M0 — Settings / Backup / Maintenance Hardening Preflight

```text
AUTHORITATIVE_BASE = b0c28b0dbcd70a24b877fbc274f454c531ef606b
WORKING_BRANCH     = devin/a14-settings-maintenance-r14.20.0
M0_PRODUCTION_CHANGE = NO
```

---

## 1. Scope / authority

This document is the **M0 preflight / audit-only** deliverable for the
`A14 Settings / Backup / Maintenance Hardening` task.

M0 is explicitly authorized; **M1–M4 are NOT authorized**.
The only engineering artifact produced in M0 is this document.
No production code, resources, feature-semantics metadata, tools, tests or build
configuration have been modified.

---

## 2. Repository / exact base evidence

- Repository: `tomthenpc/customiuizer-a14`
- Local remote: `origin  https://github.com/tomthenpc/customiuizer-a14 (fetch/push)`
- Authoritative base SHA: `b0c28b0dbcd70a24b877fbc274f454c531ef606b`
- `git cat-file -t b0c28b0dbcd70a24b877fbc274f454c531ef606b` → `commit`
- `git show --no-patch --format=fuller b0c28b0dbcd70a24b877fbc274f454c531ef606b` confirms
  author `tomthenpc <oktelevision771@gmail.com>`, date `Wed Aug 12 16:56:25 2026 +0800`,
  message: `ST1-L1 CORRECTIVE 1: preserve audited feature semantics`.
- `origin/devin/a14-settings-maintenance-r14.20.0` did **not** exist before branch creation
  (`git rev-parse --verify origin/devin/a14-settings-maintenance-r14.20.0` → fatal).
- New branch created with:
  `git checkout -b devin/a14-settings-maintenance-r14.20.0 b0c28b0dbcd70a24b877fbc274f454c531ef606b`.

---

## 3. Current backup control flow

The backup/restore UI is owned by `PreferenceFragmentBase` and exposed only from
`MainFragment` (the home screen):

1. `MainFragment` sets `toolbarMenu = true` and `activeMenus = "all"`,
   so `R.id.backuprestore` in `res/menu/menu_mods.xml` is visible.
2. `PreferenceFragmentBase.onOptionsItemSelected` (line 127–129)
   calls `showBackupRestoreDialog()`.
3. `showBackupRestoreDialog()` (line 336–346) shows a dialog with
   *Restore* (`restoreSettings`) and *Backup* (`backupSettings`) buttons.
4. **Backup:**
   - `backupSettings(act)` (line 569–578) fires
     `Intent.ACTION_CREATE_DOCUMENT` with MIME `application/octet-stream`
     and default title `pengeek_backup_<MMddHHmmss>`.
   - `onActivityResult` (line 580–607), request `SAVE_BACKFILE`, writes
     `ObjectOutputStream(outputStream).use { output.writeObject(AppHelper.appPrefs!!.all) }`.
5. **Restore:**
   - `restoreSettings(act)` (line 609–615) fires `Intent.ACTION_OPEN_DOCUMENT`.
   - `onActivityResult`, request `PICK_BACKFILE`, calls `doRestoreSettings(data.data)`.
   - `doRestoreSettings(uri)` (line 617–668) launches `Dispatchers.IO`:
     - opens `InputStream`;
     - `ObjectInputStream(inputStream).use { input.readObject() as Map<String, Any?> }`;
     - `AppSelectionSanitizer.queryInstalledPackageNames(pm)`;
     - `AppSelectionSanitizer.sanitizeRestoredEntries(entries, installedPackages)`;
     - `AppHelper.syncPrefsToAnother(sanitizedEntries, prefs, 1, null, true)`
       (`clearType=1` clears, `commitAction=true` uses `commit()`);
     - `AppLocaleController.invalidateFastPath(prefs)`;
     - on main thread, shows `R.string.restore_ok` and does
       `validAct.finish(); validAct.startActivity(validAct.intent)`.

No other fragment overrides `backupSettings`, `restoreSettings` or `doRestoreSettings`.

---

## 4. BACKUP_CURRENT_FORMAT

| Item | Actual production value | Source |
|------|-------------------------|--------|
| **Writer type** | `java.io.ObjectOutputStream` | `PreferenceFragmentBase.kt:589` |
| **Reader type** | `java.io.ObjectInputStream` | `PreferenceFragmentBase.kt:625` |
| **Root object type** | `Map<String, Any?>` (specifically a copy of `SharedPreferences.getAll()`) | `PreferenceFragmentBase.kt:590,626` |
| **Supported value types** | Boolean, Int, Long, Float, String, Set<String> | `AppHelper.syncPrefsToAnother:278–287` |
| **Filename generation** | `pengeek_backup_` + `SimpleDateFormat("MMddHHmmss", Locale.US).format(Date())` | `PreferenceFragmentBase.kt:574–575,689–691` |
| **Timestamp semantics** | Local device wall-clock, month/day/hour/minute/second, US locale digits, no timezone, no milliseconds | `PreferenceFragmentBase.kt:574–575` |
| **Stream ownership** | `contentResolver.openOutputStream/openInputStream` is handed to `ObjectOutputStream`/`ObjectInputStream`; `.use { }` closes the wrapper, which closes the underlying stream | `PreferenceFragmentBase.kt:588,625` |
| **Close semantics** | `use` closes on success; if `ObjectOutputStream` constructor throws, the underlying `outputStream` may be leaked | `PreferenceFragmentBase.kt:588,625` |
| **Exception handling (backup)** | `catch (e: Throwable) { e.printStackTrace(); show warning }`; **no `FatalErrors.rethrowIfFatal`** | `PreferenceFragmentBase.kt:598–604` |
| **Exception handling (restore)** | `catch (t: Throwable) { FatalErrors.rethrowIfFatal(t); t.printStackTrace(); show warning }` | `PreferenceFragmentBase.kt:655–665` |
| **Restore dispatch** | `AppHelper.syncPrefsToAnother(sanitizedEntries, prefs, 1, null, true)` | `PreferenceFragmentBase.kt:638` |
| **Commit/apply semantics** | Restore uses `commitAction=true` → `SharedPreferences.Editor.commit()`; result is **not** returned or checked | `AppHelper.syncPrefsToAnother:290–294` |

The file is a raw Java object serialization of the entire `SharedPreferences` map.
There is **no magic, no format version, no checksum, no size limit, no schema
annotation** inside the file.

---

## 5. Actual legacy serialized object graph

`SharedPreferences.getAll()` on Android 14 / API 34 returns
`new HashMap<String, Object>(mMap)` (Android `SharedPreferencesImpl` source).
`mMap` itself is a `HashMap<String, Object>`.
`Editor.putStringSet()` stores `new HashSet(values)`.
Therefore the object graph written by the current backup is:

- **Root:** `java.util.HashMap<String, Object>`
- **Keys:** `java.lang.String`
- **Values:**
  - `java.lang.Boolean`
  - `java.lang.Integer` (stored by `putInt`)
  - `java.lang.Long`
  - `java.lang.Float`
  - `java.lang.String`
  - `java.util.HashSet<String>` (string sets)
- **Order:** not guaranteed; `HashMap` iteration order is hash-based.
- **Null values:** `SharedPreferences.getAll()` normally does not contain `null`
  values; `Editor` treats a `null` string as a remove. The current `syncPrefsToAnother`
  explicitly skips `null`.

`AppSelectionSanitizer.sanitizeRestoredEntries` wraps the deserialized map in a
`LinkedHashMap` and creates `LinkedHashSet<String>` copies for set values, but that
is a **post-deserialization transformation**, not the legacy wire format.

---

## 6. LEGACY_RESTORE_RISK

### A. Deleted preference keys in an old backup
`doRestoreSettings` calls `syncPrefsToAnother(..., clearType=1, ...)`.
`clearType=1` executes `prefEdit.clear()`, then writes every key from the backup.
Consequently, **any stale/removed key present in the backup is re-inserted into
live `SharedPreferences`.**

### B. Do removed/deprecated keys re-enter SharedPreferences?
Yes, unless the key also appears in a future `ignoreKeys` set.
There is currently no deprecated-key filter in the restore path.

### C. ClassCastException / type-mismatch / semantic resurrection risk
- `PrefMap` hot-path readers (`getInt`, `getLong`, `getBoolean`, `getStringSet`)
  use safe casts (`as?`) and defaults, so most stale values are ignored.
- **But:** `PrefMap.getStringAsInt` deliberately parses any `String` or `Number`.
  If a stale key with the same name now has a different semantic and the value is
  an unexpected type, a future feature may receive a wrong numeric value.
- `AppHelper.syncPrefsToAnother` stores `Set<*>` values via an unchecked cast to
  `Set<String>` (`:287`) and `XposedServiceManager.putValue` does the same
  (`XposedServiceManager.kt:283`). A malicious or corrupted legacy backup with a
  `HashSet` containing non-`String` objects can be written; later `getStringSet`
  will fail at read time with `ClassCastException`.
- `MainModule.mPrefs.getStringSet` also uses an unchecked cast
  (`PrefMap.kt:163`) and does not verify elements.

### D. Same-key reuse / type-change / stale-value survival
If a future release reuses a previously-dropped key for a new feature, or changes
its type, a legacy restore will silently inject the old value. This is the primary
justification for an explicit **tombstone policy**: the restore pipeline must know
that a dropped key must never be written into live prefs, and future features must
be blocked from reusing dropped key names.

### E. Is restore atomic?
No. The single `SharedPreferences.Editor.commit()` is atomic for the on-disk XML,
but the whole restore is a multi-step sequence:

1. parse (OIS read)
2. normalize/sanitize (`AppSelectionSanitizer`)
3. `SharedPreferences.Editor` mutation (clear + N puts)
4. `commit()` (on-disk atomic)
5. `AppLocaleController.invalidateFastPath(prefs)`
6. UI restart (`finish + startActivity`)

Only step 4 is atomic. Steps 5 and 6 (and any future side-effect reconcile) are not
protected by the `SharedPreferences` transaction.

### F. `SharedPreferences.commit() == false` still shows success
`AppHelper.syncPrefsToAnother` calls `prefEdit.commit()` and ignores its return
value. `doRestoreSettings` always shows `R.string.restore_ok` after the call returns.
Therefore a disk write failure (`commit() == false`) will still be reported as a
successful restore.

### G. Malformed / truncated / oversized / wrong-file behavior

| Input condition | Current behavior |
|-----------------|------------------|
| Truncated file / `StreamCorruptedException` | Caught by `catch (t: Throwable)`, `t.printStackTrace()`, warning dialog (`storage_cannot_restore`) |
| Wrong/non-backup file | `ObjectInputStream` throws; same catch path |
| Oversized file | No size limit; can OOM the process. `OutOfMemoryError` is rethrown by `FatalErrors.rethrowIfFatal` |
| Unsupported serialized class | `ClassNotFoundException` / `ClassCastException` caught and treated as “backup corrupt” |
| Malformed `StringSet` | If the set itself deserializes, it is stored unchecked; element-type failures surface later |
| Unsupported value type | `syncPrefsToAnother` silently skips any value that is not `Boolean/Float/Int/Long/String/Set` |

`FatalErrors.rethrowIfFatal` currently rethrows only `OutOfMemoryError`,
`ThreadDeath` and `VirtualMachineError`. `LinkageError` is **not** rethrown and is
treated as an ordinary restore failure.

### H. Device-specific / derived state carried by a backup
A backup created on another device may carry:

- **App selection values** (`*_apps`, `*_apps_black`, `*_app`, `*_activity`):
  package names that may not exist on the restoring device.
  `AppSelectionSanitizer` drops missing packages on restore.
- **`pref_key_miuizer_locale`:** a user language choice. This is portable.
- **`pref_key_miuizer_locale_applied`:** a derived marker of the last framework
  locale pushed by *this* app on the source device. The restore code explicitly
  calls `AppLocaleController.invalidateFastPath(prefs)` to discard its meaning.
- **`pref_key_miuizer_launchericon`:** a user toggle, but the actual `PackageManager`
  component state is not carried in the backup.
- No cached `installedAppsList`, icon cache, or runtime-derived state is persisted.

### I. `pref_key_miuizer_launchericon` — side effect not restored

- Storage key: `pref_key_miuizer_launchericon`
- Listener: `MainFragment.onActivityCreated`, lines 249–258.
- On manual change, it calls `PackageManager.setComponentEnabledSetting` for
  `ComponentName(act, GateWayLauncher::class.java)`.
- **Restore behavior:** `doRestoreSettings` writes the boolean to `SharedPreferences`
  but **does not** call `PackageManager` or any other reconcile logic.
  The launcher component enabled state is therefore out of sync after a restore.
- **M1 blocker/design requirement:** any new restore pipeline must reconcile the
  `GateWayLauncher` activity-alias enabled state with the restored preference value.

---

## 7. Deprecated key evidence

### Proven dropped keys
The following keys are **not present** in production code or XML as of base
`b0c28b0dbcd70a24b877fbc274f454c531ef606b`:

- `pref_key_system_notif_disable_strong_toast`
- `pref_key_system_notif_disable_strong_toast_always`
- `pref_key_system_notif_disable_strong_toast_dnd`
- short-form equivalents `system_notif_disable_strong_toast`,
  `system_notif_disable_strong_toast_always`, `system_notif_disable_strong_toast_dnd`

Evidence:
- `app/src/main/res/xml/prefs_system.xml` contains no match for these keys.
- `app/src/main/java/**/*.kt` and `**/*.java` contain no production reference.
- `SystemUiFeatures` does not register a feature for them
  (`StrongToastLegacyRemovalTest.kt` asserts this).
- `docs/strong-toast/ST1_L1_NOTIFICATION_LEGACY_REMOVAL.md` documents their removal.

### `pref_key_system_notif_strong_toast_width` / `system_notif_strong_toast_width`

- `app/src/main/res/xml/prefs_system.xml` does **not** contain this key.
- `SystemUI.kt:111` still references `system_notif_strong_toast_width` inside
  `TweakStrongToastHook`, but **that hook is never installed**: `MainModule.java` and
  `SystemUiFeatures` have no reference to `TweakStrongToastHook`.
- `feature-semantics/a14.json` still lists the key with `xmlSource: prefs_system.xml`,
  but the XML has been changed; the metadata is stale.
- The related `docs/strong-toast/` documents classify it as `DEAD_LEGACY`.

M0 classification: **UNRESOLVED_LEGACY_KEY** — code still holds a dead reference,
so it is not proven safe to tombstone as `DROPPED` without an explicit removal
decision.

### Proven current keys (not dropped)
- `pref_key_system_strong_toast_mode` / `system_strong_toast_mode` is live in
  `prefs_system.xml` and `SystemUiFeatures` (`StrongToastPresentationFeature`).

---

## 8. DEPRECATED_KEY_POLICY

The compatibility policy must be **cold-path only** and avoid any steady-state cost:

- **No** startup registry scan.
- **No** XML-wide discovery.
- **No** periodic cleanup.
- **No** background worker.
- **No** observer.
- **No** global mutable compatibility cache.

All migration/tombstone work happens inside `backup` and `restore`.

### Policy model

```text
DROPPED_KEYS       = removed preference keys that must never be written back
RENAMED_KEYS       = old key -> new key mapping
TYPE_MIGRATIONS    = same key, value must be converted to the new type
NON_EXPORTABLE_KEYS = keys that must not appear in V2 backup output
```

For each dropped/renamed/migrated key:
- legacy restore still **accepts** the container (the backup is not rejected);
- the migration pipeline **recognizes** the key;
- the (deprecated) key is **not written** into live `SharedPreferences`;
- V2 backup does **not export** the deprecated key;
- an ignored/migrated count is **recorded** in the restore result;
- future semantic reuse of the same key is **prohibited**.

### M0 frozen lists

| Category | Keys |
|----------|------|
| **PROVEN_DROPPED_KEYS** | `pref_key_system_notif_disable_strong_toast`, `pref_key_system_notif_disable_strong_toast_always`, `pref_key_system_notif_disable_strong_toast_dnd` (and short forms) |
| **PROVEN_RENAMED_KEYS** | *(none identified)* |
| **PROVEN_TYPE_MIGRATIONS** | *(none identified)* |
| **PROVEN_NON_EXPORTABLE_KEYS** | `pref_key_miuizer_locale_applied`, `pref_key_miuizer_synced_from_lsposed` (device/runtime derived) |
| **UNRESOLVED_KEYS** | `pref_key_system_notif_strong_toast_width` / `system_notif_strong_toast_width` |

---

## 9. SIDE_EFFECT_PREFS

| Key | Classification | Production consumer | Side effect / derivation | Export policy | Restore policy | Reconcile requirement | Evidence |
|-----|----------------|---------------------|--------------------------|---------------|----------------|----------------------|----------|
| `pref_key_miuizer_launchericon` | `PREF_PLUS_SIDE_EFFECT` | `MainFragment.kt:249` | Toggles `PackageManager.COMPONENT_ENABLED_STATE_ENABLED/DISABLED` for `GateWayLauncher` | Include in V2 | After commit, reconcile `PackageManager` state | `setComponentEnabledSetting` must match restored value | `MainFragment.kt:249–258`, `AndroidManifest.xml:67–82` |
| `pref_key_miuizer_locale` | `PREF_PLUS_SIDE_EFFECT` (deferred) | `AboutFragment.kt:35`, `MainApplication.kt:34`, `AppLocaleController` | Persists locale choice; `AppLocaleController.apply` sets framework locale on next cold start | Include in V2 | Restore value; invalidate applied marker; next start applies | None in current process; process must eventually cold-start for effect | `AppLocaleController.kt:31,104,130`, `AboutFragment.kt:49–96`, `MainApplication.kt:34` |
| `pref_key_miuizer_locale_applied` | `DEVICE_DERIVED_STATE` | `AppLocaleController` | Records last locale actually pushed into `LocaleManager` (or `RECONCILE_MARKER`) | **Do not export** in V2; treat as derived | `AppLocaleController.invalidateFastPath` already overwrites after restore | Keep marker in sync with `LocaleManager` | `AppLocaleController.kt:44–45,184–199` |
| `pref_key_miuizer_settingsiconpos` | `PREF_VALUE_ONLY` | `SettingsFeatures` / `GlobalActions.kt:524` | Adds icon into com.android.settings (module side, not settings app) | Include in V2 | Restore value | None in settings app; module reads via mirror | `GlobalActions.kt:524`, `SettingsFeatures.kt:20,69` |
| `pref_key_miuizer_synced_from_lsposed` | `DEVICE_DERIVED_STATE` (dead/unwritten) | `XposedServiceManager.kt:199` (only in `IGNORE_KEYS`) | No known writer; appears only in mirror ignore list | **Do not export** | Ignore if present | None | `XposedServiceManager.kt:195–200` |

---

## 10. DEVICE_DERIVED_KEYS

Keys whose value is derived from the runtime environment, not a direct user choice:

1. `pref_key_miuizer_locale_applied` — derived from last `LocaleManager` write.
2. `pref_key_miuizer_synced_from_lsposed` — runtime/mirror marker (no known writer).
3. App-selection values (`*_apps`, `*_apps_black`, `*_app`, `*_activity`) are
   **package-derived** on the source device; the sanitizer drops entries whose
   package is not installed on the restoring device. They are user selections, but
   their validity is device-specific.

These keys should not be exported in V2, or if present in a legacy backup, should
be treated as derived and overwritten/invalidated on restore.

---

## 11. AppSelectionSanitizer interaction

- **Input:** `Map<String, Any?>` (the decoded backup entries).
- **Output:** a new `Map<String, Any?>` (`LinkedHashMap`) with filtered values.
- **Mutation behavior:**
  - Multi-app selection keys (`*_apps`, `*_apps_black`) whose value is a `Set<*>`:
    items not in `installedPackages` are removed; the set is replaced with a
    `LinkedHashSet<String>`.
  - Single-app selection keys (`*_app`, `*_activity`) whose value is a `String`:
    if the package part is missing/not installed, the key is removed **and** the
    sibling `<key>_user` key is also removed.
  - All other keys are passed through unchanged.
- **Invalid package handling:** silently dropped from sets; single-app key removed.
- **Missing package handling:** same as invalid.
- **Set type assumptions:** `value is Set<*>`; creates `LinkedHashSet<String>`,
  adding only elements where `item is String`.
- **Null handling:** if `installedPackages.isEmpty()` the map is returned unchanged.
  `null` values are never produced by `SharedPreferences.getAll()` in practice.
- **Exception semantics:** no explicit exception thrown; unchecked casts are safe at
  the raw-type level.
- **Allocations:** one `LinkedHashMap(entries)`, one `LinkedHashSet` per multi-app
  key, plus one `LinkedHashSet<String>` for selection.
- **Current restore reporting:** the sanitizer does not return any count; restore
  shows only a generic success dialog.

### Unit for `appSelectionsSanitized`
M0 freezes the unit as **the number of primary app-selection preference keys whose
value was mutated or removed** (`*_apps`, `*_apps_black`, `*_app`, `*_activity`).
The companion `<key>_user` removal is a side effect of a single selection and must
not be counted separately.

---

## 12. Locale restore interaction

- **Storage key:** `pref_key_miuizer_locale`
- **Applied-marker key:** `pref_key_miuizer_locale_applied`
- **Stored representation:** a supported locale tag (`"auto"`, `"en"`, `"zh-CN"`,
  `"zh-TW"`, `"ru-RU"`, `"ja-JP"`, `"vi-VN"`, `"cs-CZ"`, `"pt-BR"`, `"tr-TR"`,
  `"es-ES"`) or legacy `"1"` normalized to `"auto"`.
- **Auto representation:** `"auto"`.
- **Supported locales list:** `AppLocaleController.SUPPORTED_LOCALE_TAGS`
  (`AppLocaleController.kt:58`).
- **Manual UI change flow:**
  1. `AboutFragment` installs `OnPreferenceChangeListener` (`AboutFragment.kt:49`).
  2. Listener blocks automatic persistence and shows confirmation dialog.
  3. On confirm, `AppLocaleController.setUserLocale(prefs, newTag)` does a
     synchronous `commit()` (`AppLocaleController.kt:104–114`).
  4. On success, `AppLocaleController.exitApplicationAfterLocaleSave(activity)`
     calls `finishAffinity()` and `Process.killProcess(Process.myPid())`
     (`AppLocaleController.kt:398–400`).
- **Persistence timing:** `commit()` at confirmation time.
- **Application:** `MainApplication.onCreate` calls `AppLocaleController.apply(it, this)`
  (`MainApplication.kt:34`). `apply` reads the stored tag and, if needed, pushes
  the locale to `LocaleManager`.
- **Fast path:** if `tag == "auto"` and `pref_key_miuizer_locale_applied` is absent,
  `apply` returns immediately without touching `LocaleManager`
  (`AppLocaleController.kt:138`).

### Restore behavior
- `doRestoreSettings` writes `pref_key_miuizer_locale` (and any old
  `pref_key_miuizer_locale_applied`) through `syncPrefsToAnother`, then calls
  `AppLocaleController.invalidateFastPath(prefs)` which writes the
  `RECONCILE_MARKER` (`AppLocaleController.kt:197–198`).
- It **does not** call `AppLocaleController.apply` or exit the process.
- After the activity `finish + startActivity`, the new `MainActivity` runs in the
  same process; `MainApplication.onCreate` will not run again, so the new locale
  is **not applied until the next cold start**.

### Classification
- `pref_key_miuizer_locale` → `PREF_PLUS_SIDE_EFFECT` (deferred application).
- `pref_key_miuizer_locale_applied` → `DEVICE_DERIVED_STATE`.

---

## 13. Restore atomicity / commit semantics

- The only atomic step is `SharedPreferences.Editor.commit()` in
  `AppHelper.syncPrefsToAnother`.
- `commit()` clears the entire file, writes all restored entries, and flushes to disk.
- The return value is ignored.
- Steps after `commit()` (`AppLocaleController.invalidateFastPath`, UI restart) are
  not inside the `SharedPreferences` transaction.
- Cross-pref synchronization: the `OnSharedPreferenceChangeListener` in
  `XposedServiceManager` sees `key == null` for bulk changes and schedules a full
  mirror pass (`XposedServiceManager.kt:221–225`), but this also is not atomic with
  the commit.

Conclusion: **whole restore is not atomic**. A future pipeline should treat
`SharedPreferences` commit as one step in a larger result, and side-effect reconcile
must be explicitly accounted for.

---

## 14. Restore failure / fatal semantics

`doRestoreSettings` uses:

```kotlin
catch (t: Throwable) {
    FatalErrors.rethrowIfFatal(t)
    t.printStackTrace()
    ... show dialog ...
}
```

`FatalErrors.rethrowIfFatal` rethrows:
- `OutOfMemoryError`
- `ThreadDeath`
- `VirtualMachineError`

It **does not** rethrow `LinkageError`.

| Exception type | Current handling |
|----------------|------------------|
| Malformed input / `IOException` / `ClassNotFoundException` | caught, `printStackTrace`, warning dialog |
| `ClassCastException` (root not a Map) | caught, warning dialog |
| `RuntimeException` | caught, warning dialog |
| `OutOfMemoryError` / `VirtualMachineError` / `ThreadDeath` | rethrown |
| `LinkageError` (e.g. `NoClassDefFoundError`) | **caught**, not propagated |

Future design must keep fatal `Throwable` propagation and should not use a
bare `catch (Throwable)` that converts `VirtualMachineError` or `ThreadDeath` into
a “backup corrupt” message.

---

## 15. Restore result model direction

M1 should introduce a typed, immutable restore result. Minimum fields:

```text
restored               : Int   // number of preference keys actually written
                       //       (after sanitization, migration, tombstone)
deprecatedIgnored      : Int   // number of dropped/renamed/migrated keys
                       //       recognized and not written
invalidSkipped         : Int   // number of keys/values skipped due to invalid type
                       //       or malformed data
appSelectionsSanitized : Int   // number of primary app-selection keys changed/removed
migrated               : Int   // number of keys migrated (renamed or type-converted)
commitSucceeded        : Boolean
deviceReconciled       : Boolean  // true if all local side effects succeeded
```

### Semantics
- `commitSucceeded == false` → `RESTORE_RESULT = FAILURE`.
- `commitSucceeded == true && deviceReconciled == false` → `PARTIAL_FAILURE`
  (prefs are persisted but launcher/locale/etc. did not reconcile).
- `commitSucceeded == true && deviceReconciled == true` → `SUCCESS`.
- No “success toast” may appear unless `commitSucceeded` is true and required
  reconciles are true.

M0 does **not** implement this model.

---

## 16. BACKUP_V2_DIRECTION

### Evaluation of current Java serialization
- **Compatibility:** poor forward/backward schema detection; no version header.
- **Deserialization attack surface:** `ObjectInputStream.readObject()` can
  instantiate any `Serializable` class; no `ObjectInputFilter` is configured.
- **Malformed input behavior:** caught as generic `Throwable`.
- **Truncation / oversized input:** no size or count limits; OOM is the only guard.
- **Unsupported object types:** may be instantiated if on the classpath.
- **StringSet element typing:** no element-level validation; unchecked cast to
  `Set<String>`.
- **Schema/version detection:** none.

### Proposed R14 BACKUP V2 format
A compact binary format with the following header and footer:

```text
MAGIC            : 4 bytes  "CUI2" / 0x43554932
FORMAT_VERSION   : 4 bytes  (int, currently 1)
APP_REVISION     : 4 bytes  (BuildConfig.VERSION_CODE)
ENTRY_COUNT      : 4 bytes  (int)
TYPED_ENTRIES[]  : N bytes
INTEGRITY_FOOTER : 4 bytes  CRC32 of all preceding bytes
```

Each entry:

```text
KEY_LENGTH  : 2 bytes (unsigned short, big-endian)
KEY_BYTES   : KEY_LENGTH bytes (UTF-8)
TYPE_TAG    : 1 byte
VALUE       : type-specific encoding
```

Type tags (M0 proposal):

- `BOOLEAN`   : 1 byte (0 or 1)
- `INT`       : 4 bytes (big-endian)
- `LONG`      : 8 bytes (big-endian)
- `FLOAT`     : 4 bytes (IEEE-754, big-endian)
- `STRING`    : 4-byte length + UTF-8 bytes
- `STRING_SET`: 4-byte count + (4-byte length + UTF-8 bytes) per element

### Proposed bounds (all `PROPOSED`, not measured yet)

| Limit | Proposed value | Rationale |
|-------|----------------|-----------|
| `MAX_FILE_SIZE`     | 2 MiB | Observed backups are small text/maps; 2 MiB gives generous headroom for string-set backups while capping OOM risk. |
| `MAX_ENTRY_COUNT`   | 4096 | `feature-semantics/a14.json` has ~1000 feature records; active preference keys are far fewer. 4× headroom. |
| `MAX_KEY_BYTES`     | 128 | Longest observed keys are under 80 bytes (`pref_key_system_betterpopups_allowfloat_apps_black`). |
| `MAX_STRING_BYTES`  | 65535 | Longest strings are user-edited paths/labels; 64 KiB is a safe upper bound. |
| `MAX_SET_ITEMS`     | 1024 | App-selection sets are user-chosen subsets; this far exceeds realistic counts. |

- **CRC32** is for accidental corruption detection only; it is **not authentication**
  and **not tamper protection**.
- M0 constraints: **no encryption, no protobuf, no database, no large serialization
  framework**.

---

## 17. Restricted legacy reader direction

The legacy reader must remain available for M2, but it must be **restricted**:

- **New writer:** V2 (this section).
- **New reader:** V2 + restricted legacy.
- Legacy must not call unrestricted `ObjectInputStream.readObject()`.

### Required allowlist (M0 proposal)
- Root: `java.util.HashMap`, `java.util.LinkedHashMap`
- Key type: `java.lang.String`
- Value classes:
  - `java.lang.Boolean`
  - `java.lang.Integer`
  - `java.lang.Long`
  - `java.lang.Float`
  - `java.lang.String`
  - `java.util.HashSet`
  - `java.util.LinkedHashSet`
- Container interfaces `java.util.Map` and `java.util.Set` may be allowed only
  as supertypes of the concrete classes above.

### Max input size
Apply `MAX_FILE_SIZE` (proposed 2 MiB) before opening `ObjectInputStream`.

### Post-deserialization normalization
1. Reject if the root object is not a `Map`.
2. Reject if any key is not a `String`.
3. Reject if any value is not in the allowed value class list.
4. For `Set` values, create a new `LinkedHashSet<String>` and add only elements
   that are non-null `String`s; reject if any element is not a `String`.
5. Convert the root map to `LinkedHashMap<String, Any?>` for deterministic order.

### Rejection / fatal behavior
- Malformed or disallowed classes: throw a dedicated `BackupFormatException`
  (ordinary failure, not fatal).
- `OutOfMemoryError`, `VirtualMachineError`, `ThreadDeath`, `LinkageError`: propagate.
- Do **not** catch these inside the “backup corrupt” handler.

### Unified pipeline
All decoded data (V2 or legacy) must flow through the same pipeline:

```text
decode
→ normalize
→ migrate
→ tombstone filter
→ validate/sanitize
→ prepare transaction
→ commit
→ side-effect reconcile
→ final result
```

The order is correct because side effects (launcher, locale) must happen **after**
the preference file is committed. If a side effect fails, the result must report
`PARTIAL_FAILURE` while preserving the already-committed prefs.

---

## 18. PENGEEK_REFERENCE_MATRIX

### Search command
`grep -R -i "Pengeek|pengeek"` over the repository.

### Production references (M3 target: 0)

| File | Symbol / location | String | Runtime purpose | Safe rename? | M3 action |
|------|-------------------|--------|-----------------|--------------|-----------|
| `PreferenceFragmentBase.kt:575` | Backup filename | `pengeek_backup_` | Default backup file name prefix | Yes | Rename to `customiuizer_backup_` |
| `AppHelper.kt:62,67,72,77` | Log methods | `[Pengeek]`, `[Pengeek][$mod]` | Logcat prefix for settings-app logs | Yes | Replace with `[CustoMIUIzer]` or module-specific prefix |
| `mods/utils/XposedHelpers.java:387,395,399,407` | Log methods | `[Pengeek]`, `[Pengeek][$mod]` | Logcat prefix for module logs | Yes | Replace with `[CustoMIUIzer]` |
| `utils/BitmapCachedLoader.kt:171` | `TAG` | `Pengeek.IconLoader` | Log tag for icon loader | Yes | Rename to `CustoMIUIzer.IconLoader` |
| `utils/BitmapCachedLoader.kt:188` | Thread factory | `Pengeek-IconLoader-N` | Icon loader thread name | Yes | Rename to `CustoMIUIzer-IconLoader-N` |

**Production `Pengeek` reference count:** 11 occurrences across 4 files.

### Tool / historical references

| File | Symbol / location | String | Purpose | M3 action |
|------|-------------------|--------|---------|-----------|
| `tools/analyze_lsposed_log.py:34` | `MODULE_PREFIX = "[Pengeek]"` | `[Pengeek]` | Log parser legacy marker | Keep as `LEGACY_LOG_MARKER`; add new markers if needed |
| `tools/analyze_lsposed_log.py:454` | Marker detection | `("CustoMIUIzer", ..., "[Pengeek]")` | Detect module lines in LSPosed logs | Keep; optionally extend for new markers |

**Tool / historical `Pengeek` reference count:** 2 occurrences in 1 file.

### Constraints
- `applicationId = tv.withaibuild.customiuizer.r14` — unchanged.
- Package/namespace `tv.withaibuild.customiuizer` — unchanged.
- `tools/analyze_lsposed_log.py` may keep `[Pengeek]` as a legacy parser marker.

---

## 19. LOCALE_UI_MOVE_SCOPE

### Current hierarchy

Home (`app/src/main/res/xml/prefs_main.xml`):

```text
米客 A14 设置 (category @string/miuizer, key pref_key_miuizer)
├─ 系统设置中入口位置   (pref_key_miuizer_settingsiconpos)
└─ 启动器图标          (pref_key_miuizer_launchericon)
```

About (`app/src/main/res/xml/prefs_about.xml`):

```text
米客 A14 设置 (category @string/miuizer, key pref_key_miuizer_settings)
└─ 界面语言            (pref_key_miuizer_locale)
```

### M4 target (UI_OWNER_MOVE_ONLY)

Home category should become:

```text
设置 (or @string/miuizer / @string/settings where suitable)
├─ 系统设置中入口位置
├─ 启动器图标
└─ 界面语言
```

- Same preference key `pref_key_miuizer_locale`.
- Same listener/controller: `AppLocaleController.setupLocalePreference` +
  `AboutFragment.installLocaleChangeListener` → will be moved to `MainFragment`.
- Same confirmation dialog and `exitApplicationAfterLocaleSave` behavior.
- Same `SUPPORTED_LOCALE_TAGS` and `auto` semantics.
- Same persistence (synchronous `commit()`) and apply/exit semantics.
- Same `AppLocaleController` fast path.
- `About` page should remove the locale row; `app_name = 米客 A14` stays
  `translatable=false`.

This is a **UI owner move**, not a locale architecture rewrite.

---

## 20. About layout findings

File: `app/src/main/res/layout/fragment_about_head.xml`

| TextView | Height | singleLine | maxLines | ellipsize | Start/End padding | Notes |
|----------|--------|------------|----------|-----------|-------------------|-------|
| app name | `wrap_content` | not set | not set | not set | none | `textStyle="bold"` |
| about_maintainer | `wrap_content` | not set | not set | not set | `@dimen/preference_item_child_padding` | `gravity="center"` |
| about_based_on | `wrap_content` | not set | not set | not set | same | `gravity="center"` |
| about_version | `wrap_content` | not set | not set | not set | same | `gravity="center"`, uses `%1$s` |

- Root `LinearLayout` height = `wrap_content`.
- No fixed title height.
- No forced `singleLine`.
- No `ellipsize`.
- Padding is present on start/end.
- `about_version` text is set at runtime from `about_version` string and `PackageInfo.versionName`.
- `miuizer_icon` is hidden in landscape via `AboutFragment.updateHeadViews`.

Conclusion: the About head layout is already broadly tolerant of font scale and long
lines. M0 hardening scope is minimal; no layout rewrite is required.

---

## 21. Memory / startup / hot-path impact

Current and proposed design must respect the following constraints:

- **No** startup preference scan.
- **No** permanent observer for backup/restore.
- **No** periodic cleanup.
- **No** background worker.
- **No** hook hot-path backup cost.
- **No** application-start migration pass.
- **No** XML registry reflection.
- **No** unbounded parser.
- **No** unnecessary global cache.

| Phase | Current | Proposed V2 / restricted legacy |
|-------|---------|----------------------------------|
| Steady-state retained memory | 0 (no backup state) | 0 |
| Startup cost | 0 | 0 |
| Hook hot-path cost | 0 | 0 |
| Backup peak allocation | `SharedPreferences.getAll()` + OOS buffer; unbounded if app data huge | Bounded by `MAX_FILE_SIZE` / `MAX_ENTRY_COUNT` |
| Restore peak allocation | OIS + full map copy + sanitizer; unbounded | Bounded; V2 uses typed parser; legacy OIS limited by `MAX_FILE_SIZE` filter |

Compatibility and codec work are **cold-path only** (user-initiated backup/restore).

---

## 22. Feature-semantics safety

- `python tools/audit-feature-semantics.py --validate` was executed and **passed**.
- `tools/audit-feature-semantics.py --init` was **not** run.
- `feature-semantics/a14.json` was **not** modified.

```text
FEATURE_SEMANTICS_CHANGED = NO
AUDIT_FEATURE_SEMANTICS_INIT_USED = NO
```

---

## 23. M1 proposed implementation scope

If and only if `M1_AUTHORIZATION = YES` is granted, M1 should implement:

1. **Restore result model** (`RestoreResult`) and the typed pipeline from Section 17.
2. **Side-effect reconcile** for `pref_key_miuizer_launchericon`:
   - after a successful restore commit, call `PackageManager.setComponentEnabledSetting`
     with the restored value.
3. **Commit-failure handling:** check the return value of `SharedPreferences.commit()`
   and map `false` to `RESTORE_RESULT = FAILURE`.
4. **Tombstone filter** for `PROVEN_DROPPED_KEYS` and `NON_EXPORTABLE_KEYS`.
5. **V2 backup writer** (MAGIC, FORMAT_VERSION, APP_REVISION, ENTRY_COUNT,
   TYPED_ENTRIES, CRC32 footer).
6. **Restricted legacy reader** using an `ObjectInputFilter` allowlist and the
   post-deserialization validation from Section 17.
7. Unit tests covering malformed/truncated legacy files, V2 round-trip,
   commit-failure, launcher reconcile, and dropped-key tombstone.

M0 does **not** authorize these changes.

---

## 24. Open questions / blockers

1. **Legacy bounds:** the proposed `MAX_FILE_SIZE`, `MAX_ENTRY_COUNT`,
   `MAX_KEY_BYTES`, `MAX_STRING_BYTES`, and `MAX_SET_ITEMS` are not derived from
   real device measurements; they need empirical validation.
2. **`pref_key_system_notif_strong_toast_width` classification:** unresolved between
   `DEAD_LEGACY` and `DROPPED`. Requires a human decision before building a
   tombstone list.
3. **`pref_key_miuizer_synced_from_lsposed`:** no production writer is visible;
   confirm whether it is dead or written by an older build.
4. **Restore process semantics:** should a successful restore also force a process
   exit for locale, or is `finish + startActivity` the intended contract?
5. **Commit failure policy:** should `commit() == false` be treated as `FAILURE`
   (no partial result) or `PARTIAL_FAILURE`? M0 recommends `FAILURE`.
6. **About category title for M4:** decide whether to reuse `@string/miuizer` or
   switch to `@string/settings` when the locale row is moved.

---

## 25. M0 conclusion

- The existing backup/restore implementation has been fully traced.
- The legacy format is raw Java `ObjectOutputStream` of a `SharedPreferences.getAll()`
  `HashMap`, with `HashSet<String>` for string sets.
- Restore is not atomic, `commit()` result is ignored, and the launcher icon
  side effect is not reconciled.
- A minimal V2 format and restricted legacy reader have been proposed with
  explicit bounds and a unified pipeline.
- All `Pengeek` production references have been catalogued for M3 removal.
- No production code was changed.
- `audit-feature-semantics.py --validate` passes.

**M0_SELF_ASSESSMENT: PASS_CANDIDATE**

M1 is **not authorized** until an independent audit confirms `M0 = PASS`.
