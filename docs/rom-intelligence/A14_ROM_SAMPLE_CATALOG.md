# A14 ROM Sample Catalog

This catalog tracks local ROM samples for CustoMIUIzer A14 intelligence work.
It is maintained by `tools/rom_inventory.py` and updated by hand when samples
are acquired.

## Important note

- `app/lib/framework.jar` is an **SDK compile stub** used at build time. It is
  **not a real ROM sample** and must not be treated as one.
- `local-rom-samples/` and `build/rom-intelligence/` are currently empty.
- `tmp-apk/` contains module builds of CustoMIUIzer itself and are **not ROM
  samples**.

## Sample fields

Every row uses the schema described in `A14_SAMPLE_ACQUISITION.md`:

| Field | Meaning |
| --- | --- |
| `sampleId` | Stable local identifier |
| `device` / `codename` | Device name and board codename |
| `Android` / `SDK` | Android version and API level |
| `HyperOS version` | HyperOS build version |
| `fingerprint` | Public build fingerprint (sanitized) |
| `package` / `process` | APK package or host process |
| `app version` | `versionName` or JAR implementation version |
| `file SHA-256` | SHA-256 of the file |
| `source` | Local origin |
| `sample type` | `APK`, `JAR`, `DEX`, `ZIP`, `COMPILE_STUB`, `UNKNOWN` |
| `verification status` | Catalog status |

## Catalog

| sampleId | device / codename | Android / SDK | HyperOS version | fingerprint | package / process | app version | file SHA-256 | source | sample type | verification status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `jar:f51134328a93e97f:framework.jar` | compile stub | 14 / 34 | N/A | N/A | `android.framework` | N/A | `f51134328a93e97f4dff9ffdc5deef664d38a38b8a2f0842fc4533f82db0bd79` | `app/lib` | `COMPILE_STUB` | `NOT_A_SAMPLE` |
| `local-rom-samples:empty` | waiting | — | — | — | — | — | — | `local-rom-samples/` | `WAITING_FOR_SAMPLE` | `WAITING_FOR_SAMPLE` |
| `build-rom-intelligence:empty` | waiting | — | — | — | — | — | — | `build/rom-intelligence/` | `WAITING_FOR_SAMPLE` | `WAITING_FOR_SAMPLE` |

## Catalog inventory details

The SDK compile stub `app/lib/framework.jar` contains the following internal
shape (from `tools/rom_inventory.py`):

- SHA-256: `f51134328a93e97f4dff9ffdc5deef664d38a38b8a2f0842fc4533f82db0bd79`
- Size: `2,168,774` bytes
- Classes: `1,671`
- Methods: `13,052`
- Fields: `10,044`
- Tool verdict: `COMPILE_STUB` / `NOT_A_SAMPLE`

No real ROM samples are present in `local-rom-samples/` or
`build/rom-intelligence/` at this time.
