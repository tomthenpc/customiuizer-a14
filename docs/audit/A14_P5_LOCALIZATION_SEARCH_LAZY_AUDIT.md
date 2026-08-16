# A14 P5-A0 Localization / Search / Lazy Loading Audit

> Scope: documentation-only audit. No production source, resource, generator, or build changes. No APK built or installed.

Base: `06a5376097a8d534a4f09ef58cf37fbfe0628a25`

## 1. Base / scope

- Branch: `devin/a14-final-polish-r14.20.0`
- Authoritative canonical XML: `app/src/main/res/xml/prefs_{system,launcher,controls,various}.xml`
- Generator: `tools/generate_preference_artifacts.py`
- Generated output: build-time `app/build/generated/res/preference-artifacts/main` (not committed)
- Resolver: `app/src/main/java/tv/withaibuild/customiuizer/utils/PreferenceResourceResolver.kt`
- Search runtime: `app/src/main/java/tv/withaibuild/customiuizer/utils/SearchNavigation.kt`, `Helpers.parseModSearchIndex`
- Formal locales: `values`, `values-zh-rCN`, `values-zh-rTW`, `values-cs-rCZ`, `values-es-rES`, `values-ja-rJP`, `values-pt-rBR`, `values-ru-rRU`, `values-tr-rTR`, `values-vi-rVN` (9 localized, plus base)

## 2. Canonical / generated parity

Re-ran the official generator into a temp directory and executed `tools/tests/test_generate_preference_artifacts`.

| Metric | Value |
|---|---|
| Canonical functional preference count | 347 |
| Generated lazy page files | 36 (excl. `mod_search_index.xml`) |
| Generated total files | 37 (incl. `mod_search_index.xml`) |
| Deterministic / byte-for-byte | PASS |
| Content mismatch vs. canonical source | 0 |
| `test_generate_preference_artifacts` | 7/7 PASS |

Top-level category selectors (`prefs_*_cat.xml`) are derived directly from canonical categories and match one-to-one. Every generated lazy page contains exactly one category slice; no sibling-category leakage observed.

## 3. Lazy routing matrix

`PreferenceResourceResolver` covers all 32 generated category/sub routes (5 controls, 6 launcher, 13 system, 7 various + 4 fallback roots). No missing or stale route.

| Metric | Value |
|---|---|
| LAZY_ROUTE_COUNT | 32 |
| LAZY_ROUTE_MISSING | 0 |
| CATEGORY_SELECTOR_INFLATES_FULL_PAGE | NO |
| SUBFRAGMENT_LOADS_ONLY_RESOLVED_XML | YES |

## 4. Search inventory

| Metric | Value |
|---|---|
| SEARCH_EXPECTED_COUNT | 322 |
| SEARCH_INDEX_COUNT | 322 |
| SEARCH_MISSING_COUNT | 0 |
| SEARCH_EXTRA_COUNT | 0 |
| SEARCH_DUPLICATE_KEY_COUNT | 0 |
| SEARCH_WRONG_ROUTE_COUNT | 0 |
| SEARCH_REACHABLE_COUNT | 322 |
| SEARCH_UNREACHABLE_COUNT | 0 |
| SEARCH_HARDCODED_TITLE_COUNT | 0 |

All search titles, category titles, and breadcrumb titles in `mod_search_index.xml` are `@string/...` resource references. No literal text.

Reachability proof: for every `<mod>` entry, `PreferenceResourceResolver.resolve(category, routeSub)` maps to a generated lazy XML, and the generated XML contains the exact `key`. `SEARCH_UNREACHABLE = 0`.

`SearchRouteResolver` only accepts the four known root categories and normalizes blank sub to `null`; `SearchStateMachine` clears search state on return.

`SEARCH_RUNTIME_INFLATES_ALL_PAGES = NO` (uses compact index only).

## 5. Localization

| Metric | Value |
|---|---|
| FORMAL_LOCALE_COUNT | 9 |
| USER_VISIBLE_REQUIRED_STRING_COUNT | 756 (referenced by title/summary/text/hint/breadcrumb) |
| MISSING_TRANSLATION_COUNT | 414 key-locale pairs (117 unique keys) |
| FORMAT_MISMATCH_COUNT | 0 |
| HARD_CODED_VISIBLE_TEXT_COUNT | 3 |

### 5.1 Missing translations per locale

| Locale | Missing key-locale pairs |
|---|---|
| values-zh-rTW | 111 |
| values-tr-rTR | 99 |
| values-es-rES | 68 |
| values-ru-rRU | 34 |
| values-cs-rCZ | 30 |
| values-zh-rCN | 10 |
| values-ja-rJP | 16 |
| values-pt-rBR | 16 |
| values-vi-rVN | 16 |

The bulk are status-bar icon toggles and a small set of recently added summaries. Android fallback to `values` keeps functionality, so this is not a correctness failure, but it is a quality gap (P1).

### 5.2 Hard-coded visible text

1. `app/src/main/res/xml/prefs_system_hideicons.xml` `android:title="SIM 1"`
2. `app/src/main/res/xml/prefs_system_hideicons.xml` `android:title="SIM 2"`
3. `app/src/main/res/layout/fragment_selectcolor.xml` `android:text="HSV"`

These are P0/P1 localization defects (literal UI text not in `strings.xml`).

### 5.3 Translation quality — P4 IME dismiss

The P4 feature `controls_hide_ime_dismiss_button_title/summ` is present in all 10 string files.

- `values-ru-rRU`: `Скрыть кнопку сбора клавиатуры` is awkward (literally "collect/gather keyboard button"). Recommend rephrasing to `Скрыть кнопку сбора клавиатуры` → `Скрыть кнопку скрытия клавиатуры` in P5-B.
- Other locales are natural and short.

### 5.4 Format placeholder parity

All translated strings use the same `%s` / `%d` / `%1$d` placeholder set as the base strings. `FORMAT_MISMATCH_COUNT = 0`.

## 6. Text-layout static risk

| Metric | Value |
|---|---|
| STATIC_CLIP_RISK_COUNT | 0 |

No `singleLine=true`, `maxLines=1`, or `lines=1` found on user-visible `PreferenceEx` rows. Longest base strings are summaries (e.g. `system_clock_customformat_help_summ` 628 chars), but they are displayed in scrollable/multi-line areas.

Top 5 longest user-visible base strings for smoke targeting:

1. `system_clock_customformat_help_summ` (628)
2. `system_strong_toast_summ` (225)
3. `about_dynamic` (222)
4. `system_disable_window_blurs_summ` (209)
5. `various_remove_security_center_antivirus_confirm` (203)

## 7. Device smoke

| Metric | Value |
|---|---|
| ADB state | no devices/emulators found |
| DEVICE_SMOKE_LOCALES | none performed |
| DEVICE_CONFIRMED_CLIP_COUNT | 0 (cannot confirm; no device) |
| DEVICE_VISUAL_UNCERTAIN_COUNT | N/A |

`C:\Users\tv\Downloads\Peengeek\.tools\android-sdk\platform-tools\adb.exe devices -l` returned no attached devices. P5-A0 device smoke was therefore not possible.

## 8. Final-polish feature audit

| Feature | Canonical | Lazy page | Search | Locales |
|---|---|---|---|---|
| P2 reorganized categories | present | generated | reachable | n/a |
| P3 matched restart UI | present | generated | reachable | n/a |
| USB Default Function | `pref_key_system_usb_default_function` | `prefs_system_other.xml` | indexed | n/a |
| P4 Hide IME Dismiss Button | `pref_key_controls_hide_ime_dismiss_button` | `prefs_controls_navbar.xml` | indexed | all 10 locales complete |

| Metric | Value |
|---|---|
| FINAL_POLISH_LAZY_LEAK_COUNT | 0 |
| USB_DEFAULT_LAZY | PASS |
| P4_IME_DISMISS_LAZY | PASS |
| P4_IME_DISMISS_SEARCH | PASS |
| P4_IME_DISMISS_LOCALES | PASS |

No final-polish feature bypasses lazy loading or installs a hook while disabled. Both use existing `LazyFeatureSpec` / `SystemUiFeatures` wiring.

## 9. Findings

### P0 (correctness)

1. `app/src/main/res/xml/prefs_system_hideicons.xml` and `app/src/main/res/layout/fragment_selectcolor.xml` contain hard-coded user-visible text (3 instances). This breaks runtime locale switching.

### P1 (quality / maintenance)

1. 117 unique user-visible string keys are missing in one or more formal locales (414 key-locale pairs). Largest gaps: zh-TW (111), tr-TR (99), es-ES (68). Fallback to `values` works, so it is not a crash/functional P0.
2. Russian `controls_hide_ime_dismiss_button_title` is awkward and should be rephrased.
3. `tools/tests/test_p2_settings_information_architecture.py` baseline snapshot drifted after P4 added `pref_key_controls_hide_ime_dismiss_button`. The P2 structural test now fails until its snapshot is refreshed.

### SKIP

- No evidence of lazy-page runtime inflation of sibling categories.
- No evidence of search runtime loading all `PreferenceScreen` pages.
- No device-confirmed clipping.

## 10. Automation candidates

1. Search index reachability: every `<mod key>` in `mod_search_index.xml` must exist in the generated XML for its resolved `routeSub`. This is already covered by `tools/tests/test_generate_preference_artifacts`.
2. Hard-coded visible text scan: `android:title`, `android:summary`, `android:text`, `android:hint` should not contain literal text. Could become a `check-invariants` rule.
3. P2 structural snapshot should be regenerated when canonical XML changes.

## 11. Recommended P5-B shortlist

1. Move `SIM 1`, `SIM 2`, and `HSV` to `strings.xml` and translate across formal locales (P0).
2. Refresh `tools/tests/test_p2_settings_information_architecture.py` baseline to include `pref_key_controls_hide_ime_dismiss_button`.
3. Optionally improve the Russian `controls_hide_ime_dismiss_button_title` wording (P1).
4. Optional bulk-filling of the 117 missing translations from project backlog / community translation (P1).

`P5_A0_SELF_ASSESSMENT = PASS_CANDIDATE`
