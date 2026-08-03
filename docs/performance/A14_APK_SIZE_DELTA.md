# A14 APK Size Delta Report

## 1. Measurement scope

This report compares two builds of the same A14 source tree:

- Baseline commit: `55fc2a21d0e96f9ef643f53fcc9b74374bd959db`
- Current source commit: `1856c4e229213dfae47ff575aee446ce6a7b5f22`

Variants measured:

- **Debug**: `assembleDebug`, uncompressed, non-R8, diagnostic build.
- **Develop**: `assembleDevelop`, unsigned, R8 + resource-shrinking; closer to a release APK but **not** a signed release.

## 2. Baseline provenance

| Variant | Baseline commit | SHA-256 | APK bytes |
| --- | --- | --- | --- |
| debug | `55fc2a21d0e96f9ef643f53fcc9b74374bd959db` | `0f0bc2b03af1c341da411a59591289d322138836c0a19c2a0f797571cb87cb66` | 14605299 |
| develop | `55fc2a21d0e96f9ef643f53fcc9b74374bd959db` | `8f25e11a86efc4e5a6150f3ff246ea17ea9214667090abeb9ee864867b90d155` | 3381782 |

## 3. Current build provenance

| Variant | Current commit | Version | Version code | SHA-256 | APK bytes |
| --- | --- | --- | --- | --- | --- |
| debug | `1856c4e229213dfae47ff575aee446ce6a7b5f22` | r14.16.1 | 192 | `02217ad434e9d8ae2850102589835a31e5ed729fb708ee41a23fdc712485881f` | 14484755 |
| develop | `1856c4e229213dfae47ff575aee446ce6a7b5f22` | r14.16.1 | 192 | `573fea9ad94098c4d0b265fafd542fbefa815ce8f02f46332e853cef1f44a7e2` | 3398166 |

## 4. Build configuration

| applicationId | minSdk | targetSdk | ABI |
| --- | --- | --- | --- |
| tv.withaibuild.customiuizer.r14 | 34 | 34 | arm64-v8a |

## 5. Debug comparison

| Metric | Baseline | Current | Delta | Delta % | Trend |
| --- | --- | --- | --- | --- | --- |
| apkFileBytes | 14605299 | 14484755 | -120544 | -0.8253% | decrease |
| zipEntriesUncompressedBytes | 14676808 | 14702544 | 25736 | 0.1754% | increase |
| zipEntriesCompressedBytes | 14337690 | 14363426 | 25736 | 0.1795% | increase |
| fileCount | 637 | 637 | 0 | 0.0000% | unchanged |
| dexUncompressedBytes | 12726644 | 12752380 | 25736 | 0.2022% | increase |
| dexCompressedBytes | 12726644 | 12752380 | 25736 | 0.2022% | increase |
| resourcesArscUncompressedBytes | 836568 | 836568 | 0 | 0.0000% | unchanged |
| resourcesArscCompressedBytes | 836568 | 836568 | 0 | 0.0000% | unchanged |
| libUncompressedBytes | 381024 | 381024 | 0 | 0.0000% | unchanged |
| libCompressedBytes | 381024 | 381024 | 0 | 0.0000% | unchanged |
| resUncompressedBytes | 678747 | 678747 | 0 | 0.0000% | unchanged |
| resCompressedBytes | 352095 | 352095 | 0 | 0.0000% | unchanged |
| assetsUncompressedBytes | 36765 | 36765 | 0 | 0.0000% | unchanged |
| assetsCompressedBytes | 36712 | 36712 | 0 | 0.0000% | unchanged |
| metaUncompressedBytes | 512 | 512 | 0 | 0.0000% | unchanged |
| metaCompressedBytes | 361 | 361 | 0 | 0.0000% | unchanged |
| manifestUncompressedBytes | 14820 | 14820 | 0 | 0.0000% | unchanged |
| manifestCompressedBytes | 3512 | 3512 | 0 | 0.0000% | unchanged |

## 6. Develop/R8 comparison

| Metric | Baseline | Current | Delta | Delta % | Trend |
| --- | --- | --- | --- | --- | --- |
| apkFileBytes | 3381782 | 3398166 | 16384 | 0.4845% | increase |
| zipEntriesUncompressedBytes | 3647648 | 3652081 | 4433 | 0.1215% | increase |
| zipEntriesCompressedBytes | 3326196 | 3330629 | 4433 | 0.1333% | increase |
| fileCount | 537 | 537 | 0 | 0.0000% | unchanged |
| dexUncompressedBytes | 1815220 | 1819612 | 4392 | 0.2420% | increase |
| dexCompressedBytes | 1815220 | 1819612 | 4392 | 0.2420% | increase |
| resourcesArscUncompressedBytes | 782132 | 782132 | 0 | 0.0000% | unchanged |
| resourcesArscCompressedBytes | 782132 | 782132 | 0 | 0.0000% | unchanged |
| libUncompressedBytes | 381024 | 381024 | 0 | 0.0000% | unchanged |
| libCompressedBytes | 381024 | 381024 | 0 | 0.0000% | unchanged |
| resUncompressedBytes | 613605 | 613605 | 0 | 0.0000% | unchanged |
| resCompressedBytes | 304560 | 304560 | 0 | 0.0000% | unchanged |
| assetsUncompressedBytes | 38686 | 38727 | 41 | 0.1060% | increase |
| assetsCompressedBytes | 38632 | 38673 | 41 | 0.1061% | increase |
| metaUncompressedBytes | 497 | 497 | 0 | 0.0000% | unchanged |
| metaCompressedBytes | 360 | 360 | 0 | 0.0000% | unchanged |
| manifestUncompressedBytes | 14756 | 14756 | 0 | 0.0000% | unchanged |
| manifestCompressedBytes | 3494 | 3494 | 0 | 0.0000% | unchanged |

## 7. Bucket-level changes

### Debug

- Added: 0
- Removed: 0
- Changed: 10

### Develop

- Added: 1
- Removed: 1
- Changed: 3

## 8. Largest entry changes

### Debug top 20 compressed-size increases

| Name | Bucket | Baseline compressed | Current compressed | Delta |
| --- | --- | --- | --- | --- |
| `classes8.dex` | dex | 251572 | 764088 | 512516 |
| `classes5.dex` | dex | 30100 | 349692 | 319592 |
| `classes9.dex` | dex | 124292 | 251576 | 127284 |
| `classes10.dex` | dex | 42472 | 125536 | 83064 |
| `classes11.dex` | dex | 31008 | 42472 | 11464 |

### Debug top 20 compressed-size decreases

| Name | Bucket | Baseline compressed | Current compressed | Delta |
| --- | --- | --- | --- | --- |
| `classes7.dex` | dex | 764088 | 162152 | -601936 |
| `classes4.dex` | dex | 320576 | 90576 | -230000 |
| `classes6.dex` | dex | 162152 | 30100 | -132052 |
| `classes12.dex` | dex | 88168 | 31008 | -57160 |
| `classes3.dex` | dex | 818304 | 811268 | -7036 |

### Develop top 20 compressed-size increases

| Name | Bucket | Baseline compressed | Current compressed | Delta |
| --- | --- | --- | --- | --- |
| `classes.dex` | dex | 1815220 | 1819612 | 4392 |
| `assets/dexopt/baseline.prof` | assets | 1659 | 1698 | 39 |
| `assets/dexopt/baseline.profm` | assets | 262 | 264 | 2 |

### Develop top 20 compressed-size decreases

_No entries.

## 9. Interpretation

Conclusion: `MIXED_CHANGE`

The changes above reflect differences between the baseline build and the current build. Because the Debug build is uncompressed and does not run R8, it is primarily useful for diagnosing raw source growth. The Develop build applies R8 and resource shrinking, so bucket-level shifts there are closer to what a release artifact would experience, but it remains unsigned and is not equivalent to an official signed release.

A smaller APK is not automatically a performance improvement, and a larger APK is not automatically a regression; the interpretation must be anchored to dex, resource, library and asset bucket changes rather than the headline APK byte count.

## 10. Limitations

- This is a static build-size measurement, not a runtime or device performance measurement.
- No real device was exercised during this comparison.
- The Develop variant is unsigned and excludes official signing metadata; it is not a release-quality APK.
- APK size differences between builds may include nondeterministic build artifacts, timestamps, and generated auxiliary files.

## Reproduction commands

```text
./gradlew --no-daemon clean :app:assembleDebug :app:assembleDevelop
python tools/apk_size_report.py app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk --out docs/performance/A14_APK_SIZE_CURRENT.json
python tools/apk_size_report.py app/build/outputs/apk/develop/CustoMIUIzer-A14-r14.16.1-develop-unsigned.apk --out docs/performance/A14_APK_SIZE_CURRENT_DEVELOP.json
python tools/apk_size_delta.py --baseline-commit 55fc2a21d0e96f9ef643f53fcc9b74374bd959db --current-commit 1856c4e229213dfae47ff575aee446ce6a7b5f22
```
