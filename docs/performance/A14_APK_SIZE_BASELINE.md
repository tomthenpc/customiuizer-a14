# A14 APK Size Baseline

Generated for `devin/a14-rom-intelligence-audit` at build `r14.16.1`.

APK size is reported at three levels:

- `apkFileBytes`: the real on-disk APK file size (what users download).
- `zipEntriesCompressedBytes`: the sum of compressed entry sizes inside the APK.
- `zipEntriesUncompressedBytes`: the sum of decompressed entry sizes (how much data the APK contains before compression).

## Variant summary

| Variant | APK file | ZIP compressed | ZIP uncompressed | Files | classes*.dex (c/u) | resources.arsc (c/u) | lib/ (c/u) | res/ (c/u) | assets/ (c/u) | SHA-256 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `debug` | 13.8 MB | 13.6 MB | 13.9 MB | 636 | 12.0 MB / 12.0 MB | 817 KB / 817 KB | 372 KB / 372 KB | 344 KB / 663 KB | 36 KB / 36 KB | `44dda7dfc46e930937ead329ca7afc007505003256847e8fd8a6ddcab456175c` |
| `develop` (minified) | 3.2 MB | 3.2 MB | 3.5 MB | 537 | 1.7 MB / 1.7 MB | 764 KB / 764 KB | 372 KB / 372 KB | 297 KB / 599 KB | 38 KB / 38 KB | `bd2f0fc688be0211067a0548e3414f09de7fd831fd531d407e1ad4a3d51ba261` |

Key observations:

- Real APK file size drops from 13.8 MB (debug) to 3.2 MB (develop) after R8 shrinking.
- DEX entries are stored without compression in both variants (`compressed == uncompressed` for `.dex`).
- `lib/arm64-v8a/libdexkit.so` is 381 KB in both variants and survives R8.
- `resources.arsc` is not compressed (stored) and remains the second largest single entry.
- `res/xml/prefs_system.xml` compresses from ~40 KB to ~7.8 KB in the minified build (name `res/yi.xml` after R8).

## Debug top 20 files (compressed)

| File | Compressed | Uncompressed |
|---|---|---:|
| `classes.dex` | 9,823,456 | 9,823,456 |
| `resources.arsc` | 836,568 | 836,568 |
| `classes3.dex` | 809,116 | 809,116 |
| `classes7.dex` | 764,088 | 764,088 |
| `lib/arm64-v8a/libdexkit.so` | 381,024 | 381,024 |
| `classes4.dex` | 320,460 | 320,460 |
| `classes2.dex` | 270,456 | 270,456 |
| `classes8.dex` | 251,572 | 251,572 |
| `classes6.dex` | 162,152 | 162,152 |
| `classes9.dex` | 124,292 | 124,292 |
| `classes10.dex` | 42,472 | 42,472 |
| `classes11.dex` | 31,008 | 31,008 |
| `classes5.dex` | 30,100 | 30,100 |
| `assets/test1.mp3` | 22,569 | 22,569 |
| `assets/test0.png` | 12,499 | 12,499 |
| `res/mipmap-xxhdpi-v4/ic_launcher.png` | 11,799 | 11,799 |
| `res/xml/prefs_system.xml` | 7,855 | 40,400 |
| `res/mipmap-xxxhdpi-v4/ic_launcher_foreground.png` | 7,277 | 7,277 |
| `res/mipmap-xxhdpi-v4/ic_launcher_foreground.png` | 4,708 | 4,708 |
| `res/drawable-xxhdpi-v4/ic_credentials.png` | 4,343 | 4,343 |

## Develop (minified) top 20 files (compressed)

| File | Compressed | Uncompressed |
|---|---|---:|
| `classes.dex` | 1,792,108 | 1,792,108 |
| `resources.arsc` | 782,132 | 782,132 |
| `lib/arm64-v8a/libdexkit.so` | 381,024 | 381,024 |
| `assets/test1.mp3` | 22,569 | 22,569 |
| `assets/test0.png` | 12,499 | 12,499 |
| `res/RJ.png` | 11,799 | 11,799 |
| `res/yi.xml` | 7,855 | 40,400 |
| `res/as.png` | 7,277 | 7,277 |
| `res/Lf.png` | 4,708 | 4,708 |
| `res/Bz.png` | 4,343 | 4,343 |
| `res/Em.png` | 3,843 | 3,843 |
| `AndroidManifest.xml` | 3,494 | 14,756 |
| `res/qD.9.png` | 2,834 | 2,834 |
| `res/MF.9.png` | 2,816 | 2,816 |
| `res/fe.png` | 2,789 | 2,789 |
| `res/C4.png` | 2,715 | 2,715 |
| `res/dh.xml` | 2,709 | 11,888 |
| `res/NA.9.png` | 2,505 | 2,505 |
| `res/WV.9.png` | 2,480 | 2,480 |
| `res/zV.9.png` | 2,463 | 2,463 |

## Volume budget

Recommended gate for future changes against the `develop` baseline:

- Real APK file delta > **100 KB**: require per-file attribution.
- DEX (compressed) delta > **50 KB**: list changed packages/classes.
- `lib/arm64-v8a/libdexkit.so` any change: explain DexKit/native update.
- `resources.arsc` delta > **50 KB**: list added resource types.
- New `implementation` dependency: explain APK/DEX contribution.
