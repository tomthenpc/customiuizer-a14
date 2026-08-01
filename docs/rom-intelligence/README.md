# ROM Intelligence

This directory holds offline ROM intelligence documentation and tooling for
CustoMIUIzer A14.

## Tools

- `tools/rom_inventory.py` — scan a directory of local ROM samples (APK / JAR /
  zip / dex), compute SHA-256, extract package / version / class / method /
  field information, and emit a JSON/CSV catalog. It safely degrades when
  `apkanalyzer`, `jadx`, or `javap` are not installed and it never downloads
  ROMs.
- `tools/rom_target_diff.py` — compare two ROM inventory catalogs, sample
  manifests, or target class/method/field matrices and emit a change report
  with types: `UNCHANGED`, `RENAMED`, `MOVED`, `SIGNATURE_CHANGED`,
  `PROCESS_CHANGED`, `REMOVED`, `DEXKIT_REQUIRED`, `UNKNOWN`.

## Documents

- `A14_ROM_SAMPLE_CATALOG.md` — local ROM sample catalog with SHA-256,
  package/process, sample type, and verification status.
- `A14_TARGET_MATRIX.md` — mapping between CustoMIUIzer feature IDs and the
  HyperOS 1 / Android 14 classes, methods, and fields they hook. It also
  reserves the upstream HyperOS 2 / Android 15 target column for future
  samples.
- `A14_SAMPLE_ACQUISITION.md` — how to obtain ROM samples safely, what must
  never be committed, and the schema for sample metadata.

## Important rules

- Never download ROMs from the network using these tools.
- Do not commit real ROM copies, signing materials, or device fingerprints.
- `local-rom-samples/` and `build/rom-intelligence/` are gitignored.
- `app/lib/framework.jar` is an SDK compile stub, not a real ROM sample.
