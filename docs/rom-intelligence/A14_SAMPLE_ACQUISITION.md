# A14 ROM Sample Acquisition

All ROM intelligence for CustoMIUIzer A14 is built from **local, offline**
samples. The tools in `tools/rom_inventory.py` and `tools/rom_target_diff.py`
do not and must not download ROMs from the network.

## Safe sources

Acceptable ways to obtain a sample:

1. **Your own device backup** — extract `system/framework/`,
   `system/system_ext/`, `system/priv-app/`, `system/app/`, vendor APKs, and
   relevant OAT/VDEX/DEX files from a firmware package you already own.
2. **Official vendor firmware package** — a publicly released fastboot/recovery
   ROM for your device that you have downloaded manually and placed in
   `local-rom-samples/`.
3. **Existing local analysis dumps** — baksmali directories, DEX extractions,
   or APK copies already on disk from previous offline work.

## What must never be committed

- Actual ROM copies (APK, JAR, DEX, OAT, VDEX, zip firmware packages).
- Device fingerprints, IMEI/serial numbers, MAC addresses, or owner identity.
- Personal logs, crash dumps, or LSPosed archives that contain the above.
- Signing materials: keystore, `.jks`, `.p12`, `.pfx`, `keystore.properties`.

The repository already ignores these:

```gitignore
/local-rom-samples/
/build/
*.jks
*.keystore
*.p12
*.pfx
keystore.properties
```

## Sanitization

Before adding any sample metadata to the repo:

1. Replace unique device identifiers with the device **codename** (e.g.
   `garnet`, `ziyi`) and the build **fingerprint** only if it is already
   public.
2. Verify the SHA-256 of every sample and record it in the catalog.
3. Keep the original ROM copy outside the git tree or in
   `local-rom-samples/`.

## Sample metadata schema

Every cataloged sample must record these fields:

| Field | Meaning |
| --- | --- |
| `sampleId` | Stable local identifier, e.g. `apk:<sha16>:<file name>` |
| `device` / `codename` | Device marketing name and board codename |
| `Android` / `SDK` | Android version and API level, e.g. `14 / 34` |
| `HyperOS version` | HyperOS build version, e.g. `1.0.3.0.UMNCNXM` |
| `fingerprint` | Public build fingerprint (sanitized) |
| `package` / `process` | APK package name or the process the code runs in |
| `app version` | `versionName` for APKs, `Implementation-Version` for JARs |
| `file SHA-256` | SHA-256 of the sample file |
| `source` | Local origin (`local-rom-samples/`, `build/rom-intelligence/`, etc.) |
| `sample type` | `APK`, `JAR`, `DEX`, `ZIP`, `COMPILE_STUB`, `UNKNOWN` |
| `verification status` | `CATALOGUED`, `SOURCE_DERIVED`, `WAITING_FOR_SAMPLE`, `NOT_A_SAMPLE`, `PARTIAL`, `UNKNOWN` |

## Marking status

- `CATALOGUED` — the sample was scanned and its manifest extracted.
- `SOURCE_DERIVED` — the target was derived from module source/contract files
  but has not yet been verified against a real ROM.
- `WAITING_FOR_SAMPLE` — no matching real ROM sample is available yet.
- `NOT_A_SAMPLE` — the file is an SDK compile stub or module build, not a ROM.
- `PARTIAL` — the file was recognized but the manifest could not be fully
  extracted.
