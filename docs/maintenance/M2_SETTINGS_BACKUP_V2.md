# M2 Settings Backup / Maintenance Hardening — V2 Format & Restricted Legacy Reader

## 1. Scope

M2 = **V2 backup format + restricted legacy reader**.

- New backups use V2.
- Restore accepts V2 and historical legacy Java-serialized backups.
- Legacy reader is fail-closed and graph-bounded.
- M1 transaction / rollback / side-effect reconciliation is reused unchanged.

## 2. V2 Wire Format

```
Offset  Length  Field                 Type
0       4       MAGIC                 "CUI2" (0x43 0x55 0x49 0x32)
4       4       FORMAT_VERSION        Int32 big-endian = 1
8       4       APP_REVISION          Int32 big-endian = BuildConfig.VERSION_CODE
12      4       ENTRY_COUNT           Int32 big-endian
16      var     TYPED_ENTRIES
var     4       CRC32                 UInt32 big-endian
```

CRC32 covers bytes `[0 .. 16+TYPED_ENTRIES.length-1]`.

Type tags (frozen constants, not enum ordinals):

| Tag | Meaning      |
|-----|--------------|
| 1   | BOOLEAN      |
| 2   | INT          |
| 3   | LONG         |
| 4   | FLOAT        |
| 5   | STRING       |
| 6   | STRING_SET   |

### 2.1 Key / Value Encoding

- **Key**: unsigned 16-bit big-endian length + UTF-8 bytes.
- **BOOLEAN**: 1 byte, only `0` or `1`.
- **INT**: 4-byte signed big-endian.
- **LONG**: 8-byte signed big-endian.
- **FLOAT**: IEEE-754 32-bit big-endian.
- **STRING**: 4-byte signed length + UTF-8 bytes.
- **STRING_SET**: 4-byte item count + each item as STRING.

Negative length / count → `BackupFormatException`.  
Unknown type tag → `BackupFormatException`.  
Malformed / unmappable UTF-8 → `BackupFormatException`.

## 3. Final Bounds

Offline corpus validation:

```text
30 preference XML files scanned
623 distinct keys
max UTF-8 key length: 66 bytes
```

Frozen constants:

```text
MAX_FILE_SIZE    = 2_097_152  (2 MiB)
MAX_ENTRY_COUNT  = 4_096
MAX_KEY_BYTES    = 128
MAX_STRING_BYTES = 65_535
MAX_SET_ITEMS    = 1_024
```

## 4. Determinism

Pre-filtered entries are sorted by key. `STRING_SET` elements are sorted.  
Same preference state → same payload → same CRC32.

## 5. Backup Writer

`BackupRestore.performBackup`:

1. Copies current preferences.
2. Removes `DROPPED_KEYS` and `NON_EXPORTABLE_KEYS`.
3. Validates all values are supported types.
4. Encodes to a `ByteArray` (full bounds / CRC check).
5. Writes to SAF output stream.

Unsupported value → `BackupFormatException`, backup FAILS (no partial file).

Dropped / non-exportable keys:

```text
pref_key_system_notif_disable_strong_toast
pref_key_system_notif_disable_strong_toast_always
pref_key_system_notif_disable_strong_toast_dnd
pref_key_system_notif_strong_toast_width
pref_key_miuizer_locale_applied
pref_key_miuizer_synced_from_lsposed
```

## 6. Format Detection

First 4 bytes:

- `43 55 49 32` (`CUI2`) → V2
- `AC ED 00 05` → legacy Java serialization
- anything else → `FAILURE`

`PreferenceFragmentBase` does **not** perform format detection; it calls
`BackupRestore.performBackup` / `BackupRestore.performRestore`.

## 7. V2 Decode Order

1. File length within `MAX_FILE_SIZE`.
2. Minimum header/footer length (`MIN_FILE_LENGTH = 20`).
3. Stored CRC32 footer.
4. Recompute CRC32 over payload.
5. CRC mismatch → `BackupFormatException`.
6. Magic, version, entry count.
7. Per-entry key length / type tag / value length.
8. No trailing bytes after `ENTRY_COUNT` entries.

## 8. Restricted Legacy Reader

### 8.1 Mechanism

`BackupRestore.RestrictedObjectInputStream` with two layers:

1. `ObjectInputFilter` (set via reflection) for graph limits and class allowlist.
2. `resolveClass` / `resolveProxyClass` override for fail-closed class loading.

If `ObjectInputStream.setObjectInputFilter` is unavailable at runtime, the
reader fails closed rather than falling back to unrestricted deserialization.

### 8.2 Exact Wire Allowlist

Evidence from a JDK 17 `ObjectOutputStream` fixture containing a `HashMap`
with all supported value types and a `HashSet<String>`:

```text
java.util.HashMap
java.util.HashSet
java.util.Map
java.util.Map$Entry
[Ljava.util.Map$Entry;
java.util.HashMap$Node
[Ljava.util.HashMap$Node;
java.util.HashMap$TreeNode
[Ljava.util.HashMap$TreeNode;
java.lang.Boolean
java.lang.Integer
java.lang.Long
java.lang.Float
java.lang.String
[Ljava.lang.String;
java.lang.Number
java.lang.Object
```

Everything else, including `LinkedHashMap`, `LinkedHashSet`, `ArrayList`,
`Date`, `Double`, custom `Serializable`, and application classes, is rejected.

### 8.3 Graph Limits

Fixture evidence:

```text
maxDepth       = 3
maxReferences  = 23
maxArrayLength = 16
```

Headroom limits:

```text
LEGACY_MAX_DEPTH       = 16
LEGACY_MAX_REFERENCES  = 100_000
LEGACY_MAX_ARRAY_LENGTH = 4_096
```

`serialClass == null`: only graph limits checked, then `ALLOWED`.  
`serialClass != null`: exact allowlist match → `ALLOWED`, otherwise `REJECTED`.  
No `UNDECIDED` for unknown concrete classes.

### 8.4 Proxy Policy

Always rejected by `resolveProxyClass`.

## 9. Post-Decode Bounds

After V2 or legacy decode, `postDecodeBoundsCheck` verifies:

- map entry count <= `MAX_ENTRY_COUNT`
- key UTF-8 bytes <= `MAX_KEY_BYTES`
- String bytes <= `MAX_STRING_BYTES`
- StringSet items <= `MAX_SET_ITEMS`
- StringSet item bytes <= `MAX_STRING_BYTES`

`validateAndNormalizeEntries` then continues M1-style filtering.

## 10. Unified Restore Pipeline

```text
bounded input read (MAX_FILE_SIZE)
→ format detection
→ V2 decode OR restricted legacy decode
→ post-decode bounds
→ validateAndNormalizeEntries
→ AppSelectionSanitizer
→ PRE_RESTORE_SNAPSHOT
→ clear + puts + commit
→ rollback on commit(false)
→ locale reconcile
→ launcher reconcile
→ RestoreResult
```

No separate `performV2Restore` / `performLegacyRestore`.

## 11. Security Statement

- CRC32 is for **accidental corruption detection only**.
- No authentication, tamper protection, encryption, protobuf, or database.
- Legacy filter rejects custom `readObject` classes before instantiation.

## 12. Test Matrix

V2:
- header (magic / version / app revision / entry count)
- round-trip all 6 types
- deterministic keys and sets
- dropped / non-exportable filter
- CRC payload / header / footer mutations
- unsupported version
- unknown type / invalid boolean
- negative / oversized lengths
- exact and +1 boundaries
- malformed UTF-8

Legacy:
- `HashMap` root with Boolean, Int, Long, Float, String, `HashSet<String>`
- reject `LinkedHashMap`, `LinkedHashSet`, `ArrayList`, `Double`
- reject custom `Serializable` with `readObject`
- reject proxy if constructible
- wrong root
- oversized input

## 13. M1 Regression Coverage

M2 changes do not alter:
- `commit(false)` in-memory mutation semantics
- `PRE_RESTORE_SNAPSHOT`
- best-effort rollback
- restored count after sanitizer
- tombstone / device-derived filtering
- locale partial failure
- launcher partial failure
- fatal propagation

## 14. Files

- `app/src/main/java/tv/withaibuild/customiuizer/utils/BackupFormatV2.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/utils/BackupRestore.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupFormatV2Test.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupRestoreTest.kt`
- `docs/maintenance/M2_SETTINGS_BACKUP_V2.md`

## 15. Authorization

- M1 = PASS
- M2 = PASS
- M3_AUTHORIZATION = NO
