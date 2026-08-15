# A14 P2 — Settings Information Architecture / Grouping / Summary Cleanup

## Base

```text
BASE SHA = 79a0eb20c96604743c3129675d0ec0678a703471
```

## Scope

P2 only touches user-facing preference XML layout, category titles and redundant
summaries.  No production Java/Kotlin changes, no feature IDs, no preference
key/default/entry/dependency/fragment/intent changes, no cross-page migrations.

## Production user-facing page inventory

| Main page | Generated split pages | Manual secondary pages |
|---|---|---|
| `prefs_main.xml` | `prefs_*_cat.xml` selectors | — |
| `prefs_system.xml` | `prefs_system_screen.xml`, `prefs_system_audio.xml`, `prefs_system_vibration.xml`, `prefs_system_toasts.xml`, `prefs_system_statusbar.xml`, `prefs_system_drawer.xml`, `prefs_system_notifications.xml`, `prefs_system_qs.xml`, `prefs_system_recents.xml`, `prefs_system_betterpopups.xml`, `prefs_system_floatingwindows.xml`, `prefs_system_applock.xml`, `prefs_system_lockscreen.xml`, `prefs_system_other.xml` | `prefs_system_alarmonlock.xml`, `prefs_system_albumartonlock.xml`, `prefs_system_autobrightness.xml`, `prefs_system_batteryindicator.xml`, `prefs_system_charginginfo.xml`, `prefs_system_controlcenter_clock.xml`, `prefs_system_controlcenter_themestyle.xml`, `prefs_system_detailednetspeed.xml`, `prefs_system_hideicons.xml`, `prefs_system_lockscreenshortcuts.xml`, `prefs_system_noscreenlock.xml`, `prefs_system_screenshot.xml`, `prefs_system_secureqs.xml`, `prefs_system_statusbar_*.xml`, `prefs_system_vibration_amp.xml`, `prefs_system_visualizer.xml` |
| `prefs_launcher.xml` | `prefs_launcher_folders.xml`, `prefs_launcher_titles.xml`, `prefs_launcher_privacyapps.xml`, `prefs_launcher_gestures.xml`, `prefs_launcher_bugfixes.xml`, `prefs_launcher_other.xml` | — |
| `prefs_controls.xml` | `prefs_controls_fingerprint.xml`, `prefs_controls_power.xml`, `prefs_controls_volume.xml`, `prefs_controls_navbar.xml`, `prefs_controls_fsg.xml` | — |
| `prefs_various.xml` | `prefs_various_exclusive.xml`, `prefs_various_general.xml`, `prefs_various_package_installer.xml`, `prefs_various_security_center.xml`, `prefs_various_calls.xml`, `prefs_various_settings.xml`, `prefs_various_gboard.xml` | `prefs_various_calluibright.xml`, `prefs_various_hiddenfeatures.xml` |

Total production user-facing XML files in `app/src/main/res/xml`: **30**
- 1 top-level selector: `prefs_main.xml`
- 4 canonical category pages: `prefs_system.xml`, `prefs_launcher.xml`, `prefs_controls.xml`, `prefs_various.xml`
- 23 manual secondary pages
- 2 tertiary pages: `prefs_various_calluibright.xml`, `prefs_various_hiddenfeatures.xml`
- 1 `shortcuts.xml` (internal, not user-facing preference structure)

## Changed pages

| Page | Change | Rationale |
|---|---|---|
| `prefs_system.xml` | Added nested `PreferenceCategoryEx @string/system_mods_connectivity` inside `pref_key_system_cat_other`; moved `pref_key_system_wifipassword` and `pref_key_system_usb_default_function` under it; placed the group near the top of "Other". | USB default function and Wi-Fi password share the connection/communication mental model. Grouping them makes the feature discoverable and avoids burying USB at the bottom of the page after animation scale settings. The group contains 2 items, satisfying the "no single-item category unless required" rule. |
| `prefs_system.xml` | Removed `android:summary` from `pref_key_system_fivegtile`. | Title "5G tile" already clearly identifies the toggle, and the category is "Control center". The summary only restated "Add 5G tile to control center" without adding side effects. |
| `prefs_system.xml` | Removed `android:summary` from `pref_key_system_recents_blur`. | Title is "Background blur" inside the "Recent apps list" category; the slider format (`%d%%`) makes the intensity self-evident. The summary only restated the location. |
| `prefs_system_secureqs.xml` | Removed the one-item `PreferenceCategoryEx @string/settings` wrapper around `pref_key_system_secureqs_keepopened`; added `android:dependency="pref_key_system_secureqs"` directly to the preference. | A single generic "Settings" category with one dependent item is redundant. The dependency is preserved on the preference itself, so the behavior (master switch enabling the item) is unchanged. |
| `prefs_system_statusbar_batterystyle.xml` | Removed the empty `PreferenceCategoryEx @string/settings` element (no children, self-closing). | Empty category with no children provides no grouping value. Removing it leaves the page with the master switch and the "Battery digit" group. |
| `values*/strings.xml` (10 files) | Added `system_mods_connectivity` string and translations. | Required for the new category title to render in all supported locales. |

## New groups

| Group title key | Locales | Location |
|---|---|---|
| `system_mods_connectivity` | default, zh-rCN, zh-rTW, cs, es, ja, pt, ru, tr, vi | Nested inside `pref_key_system_cat_other` (`prefs_system.xml`) |

## Unchanged pages

The following pages were reviewed and intentionally left unchanged:

- `prefs_main.xml` — already uses the approved top-level "Mods"/"Settings" groups.
- `prefs_controls.xml` — already has clean fingerprint/power/volume/navbar/FSG top-level groups; nested `Vibration`/`Actions` headers are meaningful section dividers.
- `prefs_launcher.xml` — already has folders/titles/privacy/gestures/bug fixes/other top-level groups.
- `prefs_various.xml` — the explicit header-based grouping is bound to `tools/generate_preference_artifacts.py` `VARIOUS_GROUPS`; reordering headers or renaming would require updating the generator, which is out of P2 resource-only scope. Children within each group are already logically ordered.
- Manual secondary pages not listed in "Changed pages" — most have master-switch + single-group structure or already-specific group titles (`Time period`, `Vibration intensity`, `Adjustment sensitivity`, `Actions`, etc.); the remaining generic "Settings" wrappers are tied to master-switch dependencies and would require per-item dependency migration, which was not needed for the scope of this round.

## Removed/redundant summaries

| Page | Preference key | Removed summary rationale |
|---|---|---|
| `prefs_system.xml` | `pref_key_system_fivegtile` | Title and category already identify the feature. |
| `prefs_system.xml` | `pref_key_system_recents_blur` | Title and category identify the feature; slider shows intensity. |

## Retained important summaries

Examples of summaries that were intentionally kept because they add information not in the title:

- `pref_key_system_noscreenlock` / `pref_key_system_noscreenlock_cat`: title "Disable screen lock", summary explains that app/fingerprint security is not changed.
- `pref_key_system_credentials`: title "Unlock credentials", summary explains the launcher-icon behavior and security boundary.
- `pref_key_system_drawer_blur`: title "Background blur", summary identifies the notification-drawer scope.
- `pref_key_controls_fsg_horiz`: title "Horizontal gestures", summary explains that it also enables the navigation bar.

## Preference contract preservation

```text
PREFERENCE_KEYS_CHANGED = 0
DEFAULT_VALUES_CHANGED = 0
ENTRY_VALUES_CHANGED = 0
PREFERENCES_MOVED_ACROSS_PAGES = 0
PRODUCTION_JAVA_KOTLIN_CHANGED = NO
RECENTS_HIDE_APP_NAME_CONTRACT = PRESERVED
USB_PREFERENCE_CONTRACT = PRESERVED
```

The verification script `tools/tests/test_p2_settings_information_architecture.py` checks:
- new category title localized in every supported locale
- no duplicate preference keys across all `prefs_*.xml`
- USB preference key/entries/entryValues/default/title unchanged
- recents hide-app-name preference key/title/class unchanged
- no preference keys lost/added within each `prefs_*.xml`
- no preference key migrated to a different XML file
- no protected attributes (title, entries, entryValues, defaultValue, dependency, fragment, persistent) changed except for intentional summary removal

## Runtime category parent audit

Search for runtime `findPreference`, `getParent`, `PreferenceGroup`, `removePreference`, `addPreference` usage against changed keys:

- `pref_key_system_usb_default_function` — used in `SystemUsbDefaultHooks.kt` via `MainModule.mPrefs` only; no UI parent lookup.
- `pref_key_system_wifipassword` — no runtime parent/category references found.
- `pref_key_system_secureqs_keepopened` — no runtime parent/category references found.
- `pref_key_system_statusbar_batterystyle` and children — no runtime parent/category references found.

No parent/category code depends on the removed or added category wrappers.

## Search compatibility

- All existing preference keys and title string references are unchanged.
- `tools/generate_preference_artifacts.py` and `tools/audit-feature-semantics.py` still process the canonical files successfully (verified by full `verify.py` run).
- No new preference keys were introduced; `system_mods_connectivity` is a category title string only.

## Locale coverage

`system_mods_connectivity` is present in:

- `values` (default)
- `values-zh-rCN`
- `values-zh-rTW`
- `values-cs-rCZ`
- `values-es-rES`
- `values-ja-rJP`
- `values-pt-rBR`
- `values-ru-rRU`
- `values-tr-rTR`
- `values-vi-rVN`

## Structural contract check

Run:

```powershell
python -m unittest tools/tests/test_p2_settings_information_architecture.py
```

Expected result: all tests pass.

## Validation

```text
python tools/verify.py fast --changed
python tools/verify.py full
python tools/audit-feature-semantics.py --validate
git diff --check
```

All must report PASS for P2 to be accepted.

## Remaining P5 concerns

- Full manual-secondary-page summary audit (some pages still contain generic "Settings" groups that could not be safely collapsed in this round).
- Long-language clipping check for `system_mods_connectivity` on small screens.
- Complete search-index rendering after re-grouping.
- Detailed per-page ordering optimization inside `prefs_various.xml` within the generator-bound groups.

## Device acceptance

```text
DEVICE_ACCEPTANCE = NOT_REQUIRED_FOR_P2_STRUCTURE
```

P2 is a resource/IA change; runtime device acceptance is expected at the final signed-APK stage.
