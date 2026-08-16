# A14 P5-A0/B — Localization / Search / Lazy Loading Audit

> Scope: docs/test/resources. No production source or lazy/search runtime changes. No APK built or installed.

Base: `ba504526d9e65299959d9b66eee52780bc3c2642`

## 1. Base / scope

- Branch: `devin/a14-final-polish-r14.20.0`
- Authoritative canonical XML: `app/src/main/res/xml/prefs_{system,launcher,controls,various}.xml`
- Generator: `tools/generate_preference_artifacts.py`
- Generated output: build-time `app/build/generated/res/preference-artifacts/main` (not committed)
- Resolver: `app/src/main/java/tv/withaibuild/customiuizer/utils/PreferenceResourceResolver.kt`
- Search runtime: `app/src/main/java/tv/withaibuild/customiuizer/utils/SearchNavigation.kt`, `Helpers.parseModSearchIndex`
- Formal string sets: `values` (base) + 9 localized (`values-zh-rCN`, `values-zh-rTW`, `values-cs-rCZ`, `values-es-rES`, `values-ja-rJP`, `values-pt-rBR`, `values-ru-rRU`, `values-tr-rTR`, `values-vi-rVN`)

## 2. Canonical / generated parity

| Metric | Value |
|---|---|
| CANONICAL_FUNCTIONAL_PREF_COUNT | 347 |
| GENERATED_LAZY_PAGE_COUNT | 36 (32 split pages + 4 category selectors) |
| EXPLICIT_SUB_ROUTE_COUNT | 32 |
| CATEGORY_SELECTOR_ROUTE_COUNT | 4 |
| CANONICAL_FALLBACK_ROOT_COUNT | 4 |
| GENERATED_MISMATCH_COUNT | 0 |
| `test_generate_preference_artifacts` | 7/7 PASS |

Top-level category selectors are derived directly from canonical categories. Every generated lazy page contains one category slice. No sibling leakage.

## 3. Lazy routing matrix

| Metric | Value |
|---|---|
| LAZY_ROUTE_COUNT | 32 explicit sub routes |
| LAZY_ROUTE_MISSING | 0 |
| CATEGORY_SELECTOR_INFLATES_FULL_PAGE | NO |
| SUBFRAGMENT_LOADS_ONLY_RESOLVED_XML | YES |

`PreferenceResourceResolver` covers all 32 generated category/sub routes (13 system, 6 launcher, 5 controls, 7 various) plus the 4 fallback roots. No missing or stale route.

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

All search titles, category titles, and breadcrumb titles in `mod_search_index.xml` are `@string/...` references. No literal text.

`SearchRouteResolver` only accepts the four known root categories and normalizes blank sub to `null`; `SearchStateMachine` clears search state on return.

`SEARCH_RUNTIME_INFLATES_ALL_PAGES = NO`.

## 5. Localization

| Metric | P5-A0 | P5-B after fixes |
|---|---|---|
| FORMAL_LOCALIZED_LOCALE_COUNT | 9 | 9 |
| FORMAL_STRING_SET_COUNT | 10 | 10 |
| USER_VISIBLE_REQUIRED_STRING_COUNT | 755 | 751 |
| UNIQUE_MISSING_KEY_COUNT | 114 | 114 |
| TOTAL_MISSING_KEY_LOCALE_PAIRS | 351 | 351 |
| FORMAT_MISMATCH_COUNT | 0 | 0 |
| HARD_CODED_VISIBLE_TEXT_COUNT | 3 | 0 |

### 5.1 P5-B completed localization hygiene

1. `app/src/main/res/xml/prefs_system_hideicons.xml` `SIM 1` / `SIM 2` moved to `@string/system_hideicons_sim1_title` / `system_hideicons_sim2_title`.
2. `app/src/main/res/layout/fragment_selectcolor.xml` `HSV` moved to `@string/selectcolor_hsv`.
3. `values-ru-rRU/strings.xml` `controls_hide_ime_dismiss_button_title` improved to `Скрыть кнопку скрытия клавиатуры`.

### 5.2 Missing translations

Still `MISSING_TRANSLATION_COUNT = 351` (114 unique keys). Per-locale breakdown:

| Locale | Missing pairs |
|---|---|
| values-zh-rCN | 4 |
| values-zh-rTW | 108 |
| values-cs-rCZ | 24 |
| values-es-rES | 62 |
| values-ja-rJP | 10 |
| values-pt-rBR | 10 |
| values-ru-rRU | 28 |
| values-tr-rTR | 95 |
| values-vi-rVN | 10 |

Reliable translation sources checked:

- `tomthenpc/customiuizer-a13`: base has 65 of the missing keys, but the localized `strings.xml` files do not contain the missing entries.
- `devin/a14-settings-maintenance-final-r14.20.0`: same coverage as current A14 for the missing pairs; 0 usable extra translations.
- A14 Git history on the current branch: no additional localized strings.

Therefore the 351 missing pairs cannot be resolved from existing project sources. Human/native translation is required. They are **not** filled with English placeholders.

### 5.3 Placeholder contract

`FORMAT_MISMATCH_COUNT = 0`. No translated string has altered placeholder set, index, or type.

## 6. Text-layout static risk

| Metric | Value |
|---|---|
| STATIC_CLIP_RISK_COUNT | 0 |

No `singleLine=true`, `maxLines=1`, or `lines=1` found on user-visible `PreferenceEx` rows. Longest base strings are summaries displayed in scrollable/multi-line areas.

## 7. Device smoke

| Metric | Value |
|---|---|
| ADB state | no devices/emulators found |
| DEVICE_LAYOUT_SMOKE | DEFERRED_NO_DEVICE |
| DEVICE_CONFIRMED_CLIP_COUNT | 0 |
| DEVICE_VISUAL_UNCERTAIN_COUNT | N/A |

`adb devices -l` returned no attached devices. Static layout risk is 0; runtime smoke not performed.

## 8. Final-polish feature audit

| Feature | Canonical | Lazy page | Search | Locales |
|---|---|---|---|---|
| USB Default Function | `pref_key_system_usb_default_function` | `prefs_system_other.xml` | indexed | n/a |
| P4 Hide IME Dismiss Button | `pref_key_controls_hide_ime_dismiss_button` | `prefs_controls_navbar.xml` | indexed | all 10 locales (Russian wording improved) |

| Metric | Value |
|---|---|
| FINAL_POLISH_LAZY_LEAK_COUNT | 0 |
| USB_DEFAULT_LAZY | PASS |
| P4_IME_DISMISS_LAZY | PASS |
| P4_IME_DISMISS_SEARCH | PASS |
| P4_IME_DISMISS_LOCALES | PASS |

## 9. Findings

### P0

None.

### P1

1. 114 unique user-visible keys are missing in one or more formal locales (351 key-locale pairs). Android fallback to `values` keeps functionality, but this is a translation quality gap.
2. `tools/tests/test_p5_localization_contract.py` added to enforce the contract; it currently fails on the 351 missing pairs, which is expected and correct until the translations are provided.

### SKIP

- No incorrect lazy-page runtime inflation.
- No search runtime loading of all `PreferenceScreen` pages.
- No device-confirmed clipping.

## 10. Automation candidates

1. `tools/tests/test_p5_localization_contract.py` is now in place and will fail if new user-visible strings are added without all-locale coverage.
2. `tools/tests/test_p2_settings_information_architecture.py` now has a narrow `POST_P2_ALLOWED_NEW_PREFERENCES` and `POST_P2_ALLOWED_ATTR_CHANGES` block so future authorized additions are explicit.

## 11. P5-B status

| Check | State |
|---|---|
| Hard-coded visible text | 0 |
| Russian P4 wording | fixed |
| P2 structural test | PASS |
| P2 test drift for P4 | exempted |
| P5 localization contract test | added, currently FAIL on missing translations |
| Missing translation source from repo/A13/maintenance | none found |
| `MISSING_TRANSLATION_COUNT` | 351 |

P5-B is **HOLD** pending a translation source for the 351 missing key-locale pairs. No English placeholders were inserted.

## 12. Validation commands

- `python tools/verify.py fast --changed` — expected PASS (no production source changes)
- `python -m compileall tools` — must PASS
- `python -m unittest tools.tests.test_p2_settings_information_architecture` — PASS
- `python -m unittest tools.tests.test_p5_localization_contract` — FAIL (missing translations)
- `git diff --check` — PASS
