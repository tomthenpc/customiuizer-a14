# A14 APK Size Baseline

Generated for `devin/a14-rom-intelligence-audit` at build `r14.16.1`.

## Variant summary

| Variant | Total | Files | classes*.dex | resources.arsc | lib/ | res/ | assets/ | META-INF/ | SHA-256 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| `debug` | 14.5 MB | 636 | 12.0 MB | 817 KB | 372 KB | 663 KB | 36 KB | 0.5 KB | `44dda7dfc46e930937ead329ca7afc007505003256847e8fd8a6ddcab456175c` |
| `develop` (minified) | 3.5 MB | 537 | 1.7 MB | 764 KB | 372 KB | 599 KB | 38 KB | 0.5 KB | `bd2f0fc688be0211067a0548e3414f09de7fd831fd531d407e1ad4a3d51ba261` |

Key observations:

- `develop` R8 shrink removes **~10.9 MB** of DEX (from 12.0 MB to 1.7 MB).
- `lib/arm64-v8a/libdexkit.so` is **381 KB** and survives R8 in both variants.
- `resources.arsc` is the second largest entry in both variants (~800 KB).
- The largest debug DEX is `classes.dex` at ~9.4 MB.

## Debug top 20 files

| File | Bytes |
|---|---|---:|
| `classes.dex` | 9,823,456 |
| `resources.arsc` | 836,568 |
| `classes3.dex` | 809,116 |
| `classes7.dex` | 764,088 |
| `lib/arm64-v8a/libdexkit.so` | 381,024 |
| `classes4.dex` | 320,460 |
| `classes2.dex` | 270,456 |
| `classes8.dex` | 251,572 |
| `classes6.dex` | 162,152 |
| `classes9.dex` | 124,292 |
| `classes10.dex` | 42,472 |
| `res/xml/prefs_system.xml` | 40,400 |
| `classes11.dex` | 31,008 |
| `classes5.dex` | 30,100 |
| `assets/test1.mp3` | 22,569 |
| `AndroidManifest.xml` | 14,820 |
| `assets/test0.png` | 12,499 |
| `res/xml/prefs_launcher.xml` | 11,888 |
| `res/mipmap-xxhdpi-v4/ic_launcher.png` | 11,799 |
| `res/xml/prefs_controls.xml` | 8,960 |

## Develop (minified) top 20 files

| File | Bytes |
|---|---|---:|
| `classes.dex` | 1,792,108 |
| `resources.arsc` | 782,132 |
| `lib/arm64-v8a/libdexkit.so` | 381,024 |
| `res/yi.xml` | 40,400 |
| `assets/test1.mp3` | 22,569 |
| `AndroidManifest.xml` | 14,756 |
| `assets/test0.png` | 12,499 |
| `res/dh.xml` | 11,888 |
| `res/RJ.png` | 11,799 |
| `res/VL.xml` | 8,960 |
| `res/ot.xml` | 8,044 |
| `res/as.png` | 7,277 |
| `res/BJ.xml` | 7,164 |
| `res/UU.xml` | 6,888 |
| `res/YK.xml` | 6,728 |
| `res/uT.xml` | 4,972 |
| `res/XJ.xml` | 4,872 |
| `res/Lf.png` | 4,708 |
| `res/93.xml` | 4,400 |
| `res/Dz.xml` | 4,392 |

## Volume budget

Recommended gate for future changes against the `develop` baseline:

- APK total delta > **100 KB**: require per-file attribution.
- DEX delta > **50 KB**: list changed packages/classes.
- `lib/arm64-v8a/libdexkit.so` any change: explain DexKit/native update.
- `resources.arsc` delta > **50 KB**: list added resource types.
- New `implementation` dependency: explain APK/DEX contribution.
