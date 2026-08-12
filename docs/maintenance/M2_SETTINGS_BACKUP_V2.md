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
4. `BackupFormatV2.encode` pre-computes the exact total encoded size,
   validates every bound, then allocates a bounded `ByteArrayOutputStream` and
   writes. No temporary payload larger than `MAX_FILE_SIZE` is created.
5. Writes to SAF output stream.

Unsupported value → `BackupFormatException`, backup FAILS (no partial file).

`BackupFormatV2.encode` enforces:
- `MAX_ENTRY_COUNT`
- `MAX_KEY_BYTES`
- `MAX_STRING_BYTES`
- `MAX_SET_ITEMS`
- total encoded size <= `MAX_FILE_SIZE` (preflight, before buffer allocation)
- final encoded size == pre-computed size

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

`LegacyBackupDecoder` — a focused restricted Java-serialization parser that
replaces `ObjectInputStream` / `ObjectInputFilter`:

- Parses only the proven historical ObjectOutputStream wire subset.
- Enforces graph, reference, and allocation bounds before any object is fully
  instantiated or any custom `readObject` can run.
- No reflection, no `setObjectInputFilter`, no per-stream hidden-API calls.
- Runs on API 34 without depending on JDK 17 `ObjectInputFilter` availability.

`BackupRestore.decodeLegacyBackup` delegates to `LegacyBackupDecoder.decode` and
re-throws fatal / format errors as `BackupRestoreException`.

### 8.2 Exact Wire Allowlist

Historical fixture evidence (host JDK 17 `ObjectOutputStream`):

```text
java.util.HashMap
java.util.HashSet
java.lang.Boolean
java.lang.Integer
java.lang.Long
java.lang.Float
java.lang.String
java.lang.Number   (super class only)
java.lang.Object   (super class only)
```

The decoder explicitly rejects `LinkedHashMap`, `LinkedHashSet`, `ArrayList`,
`Date`, `Double`, `Enum`, arrays, proxies, custom `Serializable`, and any
application class before it can be instantiated.

Android API 34 runtime evidence for the above wire graph is **not available**
in this environment; the allowlist is based on the historical host fixture.

### 8.3 Graph / Allocation Limits

Historical host fixture evidence (5 entries + 3-item `HashSet`):

```text
maxDepth       = 3
maxReferences  = 23
maxArrayLength = 16
```

Decoder-enforced headroom limits:

```text
LEGACY_MAX_DEPTH        = 16
LEGACY_MAX_REFERENCES   = 100_000
LEGACY_MAX_ARRAY_LENGTH = 16_384
```

`LEGACY_MAX_ARRAY_LENGTH` is set to the next power-of-two headroom for a
full 4 096-entry `HashMap` / `HashSet` table (`4 096 / 0.75 ≈ 8 192`;
`16_384` provides margin). The bound is checked on the declared `HashMap` /
`HashSet` capacity before `ObjectInputStream` would allocate a backing table.

Fail-closed class policy:
- `serialClass == null` or interface-only: only graph / allocation limits apply.
- Unknown concrete class → `InvalidClassException` / `BackupRestoreException`.
- Proxy / enum → rejected.
- `TC_ARRAY` → rejected (the historical wire graph contains no serialized arrays;
  internal `Node[]` allocation is bounded by `LEGACY_MAX_ARRAY_LENGTH`).

### 8.4 Proxy / Enum / Array Policy

- `TC_PROXYCLASSDESC` → `BackupRestoreException`.
- `TC_ENUM` → `BackupRestoreException`.
- `TC_ARRAY` → `BackupRestoreException`.
- `TC_CLASS` / `TC_LONGSTRING` / unknown type code → `BackupRestoreException`.

### 8.5 API 34 Runtime Evidence

Per-stream `ObjectInputFilter` capability on Android 14 / API 34 was **not
independently verified** in this environment. The corrective removes all
`ObjectInputStream` / `ObjectInputFilter` dependence and uses a focused parser
that is compatible with any Java 8+ runtime.

The final legacy mechanism is `LegacyBackupDecoder` (no `ObjectInputStream`, no
reflection, no hidden APIs). Host JDK 17 unit tests cover the normal, evil
`readObject`, graph-limit, and array-capacity paths. An Android instrumentation
test for API 34 is not available in this environment and is not included.

### 8.6 Residual Limitations

- The legacy parser supports only the proven historical wire graph. If an
  Android device emits a different `ObjectOutputStream` layout (e.g. old-format
  `HashMap` block with only `size`, or a `String[]` in a `HashSet`), the parser
  will reject it until the layout is added to the allowed subset.
- `TC_ARRAY` is rejected entirely because the historical wire graph contains no
  serialized arrays. If Android evidence shows a required array, the policy must
  be revisited.
- Android API 34 runtime evidence is pending.

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
- exact `MAX_FILE_SIZE` and `MAX_FILE_SIZE + 1` writer boundaries
- malformed UTF-8

Legacy (host JDK 17 unit tests; API 34 instrumentation unavailable):
- `HashMap` root with Boolean, Int, Long, Float, String, `HashSet<String>`
- reject `LinkedHashMap`, `LinkedHashSet`, `ArrayList`, `Double`
- reject custom `Serializable` with `readObject` before execution
- reject `TC_PROXYCLASSDESC` / `TC_ENUM` / `TC_ARRAY`
- reject over `LEGACY_MAX_ARRAY_LENGTH` capacity
- reject over `LEGACY_MAX_DEPTH` nested graph
- wrong root
- oversized input
- `MAX_FILE_SIZE` bounds

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
- `app/src/main/java/tv/withaibuild/customiuizer/utils/LegacyBackupDecoder.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupFormatV2Test.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupRestoreTest.kt`
- `docs/maintenance/M2_SETTINGS_BACKUP_V2.md`

## 15. Authorization

- M1 = PASS
- M2_SELF_ASSESSMENT = PASS_CANDIDATE
- M3_AUTHORIZATION = NO

`M2` has not been independently audited; only an independent auditor may freeze
it as `PASS`.
