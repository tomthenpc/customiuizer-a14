# M2 Settings Backup / Maintenance Hardening — Format & Restricted Legacy Reader

## Scope

M2 = **V2 BACKUP FORMAT + RESTRICTED LEGACY READER**.

- New backups are written in the V2 binary format.
- Restore accepts both V2 and historical legacy Java-serialized backups.
- Legacy reader is restricted by a runtime class allowlist.
- M1 transaction / rollback / side-effect reconciliation is reused unchanged.

## V2 Format

```text
MAGIC            4 bytes   "CUI2"  (0x43 0x55 0x49 0x32)
FORMAT_VERSION   4 bytes   Int32 big-endian = 1
APP_REVISION     4 bytes   Int32 big-endian = BuildConfig.VERSION_CODE
ENTRY_COUNT      4 bytes   Int32 big-endian
TYPED_ENTRIES    variable
CRC32            4 bytes   big-endian over [MAGIC .. TYPED_ENTRIES end]
```

Type tags (frozen constants):
- `BOOLEAN = 1`
- `INT = 2`
- `LONG = 3`
- `FLOAT = 4`
- `STRING = 5`
- `STRING_SET = 6`

Encoding:
- Key: unsigned 16-bit big-endian length + UTF-8 bytes
- Boolean: 1 byte, only `0` or `1`
- Int: 4-byte signed big-endian
- Long: 8-byte signed big-endian
- Float: IEEE-754 32-bit big-endian
- String: 4-byte signed length + UTF-8 bytes
- StringSet: 4-byte item count + each item as String

Negative length/count: `BackupFormatException`.
Unknown type tag: `BackupFormatException`.
Malformed/unmappable UTF-8: `BackupFormatException`.

## Final Bounds

Validated against the current preference corpus:
- 30 XML files scanned
- 623 distinct preference keys
- maximum UTF-8 key length: 66 bytes

Frozen:
- `MAX_FILE_SIZE = 2 MiB (2_097_152)`
- `MAX_ENTRY_COUNT = 4096`
- `MAX_KEY_BYTES = 128`
- `MAX_STRING_BYTES = 65535`
- `MAX_SET_ITEMS = 1024`

## Determinism

Pre-filtered entries are sorted by key. StringSet elements are sorted.
Identical preference state produces identical payload and CRC32.

## Backup Filter

Dropped / non-exportable keys are removed before encoding:
- `pref_key_system_notif_disable_strong_toast`
- `pref_key_system_notif_disable_strong_toast_always`
- `pref_key_system_notif_disable_strong_toast_dnd`
- `pref_key_system_notif_strong_toast_width`
- `pref_key_miuizer_locale_applied`
- `pref_key_miuizer_synced_from_lsposed`

Unsupported value types cause `BackupFormatException`; no partial backup is written.

## Restore Detection

First 4 bytes:
- `CUI2` → V2
- `AC ED 00 05` → legacy Java serialization
- anything else → `FAILURE`

V2 decode checks, in order:
- file length within bounds
- minimum header/footer length
- stored CRC32 footer against computed CRC32
- magic, version, entry count
- every entry length and type tag
- no trailing bytes after `ENTRY_COUNT` entries

## Restricted Legacy Reader

`RestrictedObjectInputStream` overrides `resolveClass` to allow only:
- `java.util.HashMap`
- `java.util.HashSet`
- `java.lang.Boolean`
- `java.lang.Integer`
- `java.lang.Long`
- `java.lang.Float`
- `java.lang.String`
- `java.lang.Object`
- `java.lang.String[]`
- inner classes of `HashMap` / `HashSet` as needed by serialization

Proxy classes are always rejected. `LinkedHashMap`, `LinkedHashSet`, `ArrayList`,
`Date`, `Double`, arbitrary `Serializable`, and application classes are rejected.

## Pipeline

The V2 / legacy decode layer feeds the existing M1 pipeline:

```text
bounded input read (MAX_FILE_SIZE)
→ format detection
→ V2 decode (CRC/bounds) OR restricted legacy decode
→ validateAndNormalizeEntries
→ AppSelectionSanitizer
→ PRE_RESTORE_SNAPSHOT
→ clear + puts + commit
→ rollback on commit(false)
→ locale reconcile
→ launcher reconcile
→ RestoreResult
```

## Files

- `app/src/main/java/tv/withaibuild/customiuizer/utils/BackupFormatV2.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/utils/BackupRestore.kt` (M2 final)
- `app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt` (V2 backup writer)
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupFormatV2Test.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupRestoreTest.kt` (V2 + legacy tests)

## Validation

- `python tools/verify.py full`
- `gradlew :app:assembleDebug`
- All 1790+ unit tests passed

## Authorization State

- M1 = PASS
- M2 = PASS
- M3_AUTHORIZATION = NO
