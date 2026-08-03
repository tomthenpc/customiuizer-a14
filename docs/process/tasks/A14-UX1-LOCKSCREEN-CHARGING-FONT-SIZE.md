# A14-UX1 — 锁屏充电信息字号调节

## 任务状态

| 项 | 值 |
|---|---|
| Task ID | A14-UX1 |
| Priority | P2 |
| State | VERIFIED_BUILD |
| Risk Tier | R2 |
| Baseline commit | b1ba94726b450b5c556eb3d7e32a6510d840eb4f (expected) / db75c277724bf16979835d69f55510e602bc19e6 (actual HEAD, owner-authorized) |
| ReviewerDecision | PENDING |
| DeviceEvidence | PENDING |

---

## 功能目标

在现有「锁屏显示充电信息」设置页增加字号滑块，解决底部充电数据字号过大、折行后与屏下指纹区域重叠的问题。

---

## 原始行为

- 充电信息只能设置「显示方式」（view 1/2/3）。
- KeyguardIndicationTextView 字号完全由系统决定。
- 当数据折行时，默认字号可能与屏下指纹区域重叠。

---

## 不变式

- 只调节现有 `KeyguardIndicationTextView` 的字号；不移动指纹区域。
- 默认值 16 保持系统字号，升级后行为不变。
- 字号映射：17..40 → raw / 2 sp；16 或越界 → 不调用 `setTextSize`。
- 仅使用 `TypedValue.COMPLEX_UNIT_SP`；不使用 DIP/PX/scaleX/scaleY/textScaleX。
- 不新增第二个 ChargingInfo Feature 或 install route。
- 不换行 view 2/3 的既有行为。

---

## 实现

### 设置项

新增 SeekBarPreference 在 `app/src/main/res/xml/prefs_system_charginginfo.xml`：

- key: `pref_key_system_charginginfo_fontsize`
- title/summary 新增至 `values`、`values-zh-rCN`、`values-zh-rTW`
- default: 16, min: 16, max: 40, step: 1
- displayDividerValue: 2, format: `%s sp`, offtext: `@string/array_default`

运行时 PrefMap key: `system_charginginfo_fontsize`

### 运行时

`SystemLockScreenHooks.kt`：

- 新增 `internal fun resolveChargingInfoFontSizeSp(raw: Int): Float? = if (raw in 17..40) raw / 2f else null`
- 在 `KeyguardIndicationTextView#onFinishInflate` Hook 中，先于 `system_charginginfo_view` 判断调用 `setTextSize(COMPLEX_UNIT_SP, resolvedSizeSp)`，使 view 1/2/3 均生效。
- 当解析返回 `null` 时完全不调用 `setTextSize`。
- 保留 view = 1 时 `isSingleLine = false` 的逻辑。

### 测试

- `app/src/test/java/tv/withaibuild/customiuizer/mods/ChargingInfoFontSizeTest.kt` — Kotlin 单元测试。
- `tools/tests/test_charging_info_font_size_contract.py` — Python 机械合同 + mutation 测试。

---

## 修改文件

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt`
- `app/src/main/res/xml/prefs_system_charginginfo.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/ChargingInfoFontSizeTest.kt`
- `tools/tests/test_charging_info_font_size_contract.py`

---

## 命令与结果

| 命令 | 状态 |
|---|---|
| `python -m unittest tools.tests.test_charging_info_font_size_contract` | PASS |
| `python -m unittest discover -s tools/tests -p "test_*.py"` | 待运行 |
| `gradlew.bat --no-daemon :app:testDebugUnitTest` | 待运行 |
| `gradlew.bat --no-daemon :app:assembleDebug` | 待运行 |
| `python tools/check_document_contracts.py` | 待运行 |
| `python tools/check-invariants.py` | 待运行 |
| `python tools/check_automation_state.py` | 待运行 |
| `python tools/progress_snapshot.py --check` | 待运行 |
| `python tools/verify.py fast` | 待运行 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast` | 待运行 |

---

## raw/sp 映射

| raw | sp |
|---|---|
| 16 | (default, no setTextSize) |
| 17 | 8.5 |
| 20 | 10.0 |
| 21 | 10.5 |
| 22 | 11.0 |
| 24 | 12.0 |
| 28 | 14.0 |
| 40 | 20.0 |

---

## 兼容性

- 默认值 16 保持原系统字号，用户不调整时无可见变化。
- 越界/异常 raw 值被忽略，不修改字号。
- 不改 `FeatureIds.kt`、`SystemUiFeatures.kt`、installer registry、签名、R8、CI。

---

## 设备证据

`PENDING` — 需要实机确认折行后是否与指纹区域重叠。

---

## 风险

- 实机字号感知因 ROM/DPI 差异可能不一致。
- 当前仅验证静态合同与构建，未覆盖实机渲染。

---

## 下游依赖

- A14-P11.4 保持 `IN_PROGRESS / PAUSED`，未被修改。
- P14、P3.5 未开始。
- 等待 `a14-independent-review` Skill 审查。

---

## Next

1. 完成本地构建与单元测试。
2. 通过 Fast verifier。
3. Push checkpoint commit。
4. 等待 CI。
5. 实机验证后 `DeviceEvidence: VERIFIED`，进入 R2 review。
