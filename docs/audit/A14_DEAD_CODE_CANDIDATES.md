# CustoMIUIzer A14 Dead Code Candidates

This document lists `DELETE_DEAD` candidates with concrete evidence and the recommended action, plus non-dead classes that are experimental, guarded, or high-risk.

## DELETE_DEAD candidates

### Preference XML without code backing

| Preference key | XML file / line | Evidence | Recommended action |
| --- | --- | --- | --- |
| `pref_key_system_hidestatusbar_whenscreenrecord` | `app/src/main/res/xml/prefs_system.xml:430` | SwitchPreference in prefs_system.xml:430; no Java/Kotlin reference to 'system_hidestatusbar_whenscreenrecord' (mPrefs or findPreference). | Remove the SwitchPreference and string 'system_hidestatusbar_whenscreenrecord_title'. |
| `pref_key_system_cc_tile_enabled_color_usemonet` | `app/src/main/res/xml/prefs_system_controlcenter_themestyle.xml:23` | CheckBoxPreference in prefs_system_controlcenter_themestyle.xml:22, isPreferenceVisible='false', no code reference. | Remove hidden preference and any related strings if not intended for future use. |
| `pref_key_system_cc_tile_roundedrect_inlinetext` | `app/src/main/res/xml/prefs_system.xml:770` | XML is commented out in prefs_system.xml:769-774; no active code reference. | Delete the commented-out block and the title string 'system_cc_tile_roundedrect_inlinetext_title'. |
| `pref_key_system_statusbar_icons_atleft_onkeyguard` | `app/src/main/res/xml/prefs_system_statusbar_righticons.xml:59` | CheckBoxPreference in prefs_system_statusbar_righticons.xml:58, isPreferenceVisible='false', no code reference. | Remove the hidden preference and its title string. |
| `pref_key_various_memorystats` | `app/src/main/res/xml/prefs_various_hiddenfeatures.xml:16` | PreferenceEx in prefs_various_hiddenfeatures.xml:15, isPreferenceVisible='false', no click listener or mPrefs reference. | Remove hidden preference and 'various_memorystats_title' string. |
| `pref_key_various_appusagestats` | `app/src/main/res/xml/prefs_various_hiddenfeatures.xml:21` | PreferenceEx in prefs_various_hiddenfeatures.xml:21, isPreferenceVisible='false', no click listener or mPrefs reference. | Remove hidden preference and 'various_appusagestats_title' string. |
| `pref_key_various_aospnotiflog` | `app/src/main/res/xml/prefs_various_hiddenfeatures.xml:30` | PreferenceEx in prefs_various_hiddenfeatures.xml:29, isPreferenceVisible='false', no click listener or mPrefs reference. | Remove hidden preference and 'various_aospnotiflog_title' string. |

### Feature definitions that can never be enabled

| Feature | Registry file / line | Evidence | Recommended action |
| --- | --- | --- | --- |
| `ForceClockUseSystemFontsFeature` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt:2338` | FeatureDefinition is reachable via SystemUiFeatures / SystemUiInstaller, but its preferenceKey 'system_ls_force_systemfonts' is not in any XML and no code writes it (app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt:2338). It can never be enabled by the user. | Remove the FeatureSpec/FeatureDefinition or add the missing settings entry and string. |

## Other high-risk / non-DELETE classes

| Kind | Name | Classification | Evidence | Recommended action |
| --- | --- | --- | --- | --- |
| helper | `Api102HookBridge` | EXPERIMENTAL | app/src/main/java/tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt defines setStableHookId and STABLE_ID_* constants, but no caller uses them. | Keep isolated as API102-only path; do not delete just because it is unwired. |
| helper | `XposedApiCapabilities.supportsStableHookId / supportsReplaceHook` | EXPERIMENTAL | app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedApiCapabilities.kt:35,39 are API102 capability checks, only referenced from the unused Api102 bridge. | Keep with the bridge; mark for future API102 hook work. |

## Items that look orphaned but are NOT dead

| Key / Pattern | Why it is not dead |
| --- | --- |
| `pref_key_various_calluibright_night_start / _end` | Time picker preferences; code concatenates 'pref_key_various_calluibright_night_' + 'start'/'end' (subs/Various_CallUIBright.kt:15,33,46). |
| `pref_key_system_vibration_amp_period_start / _end` | Time picker preferences; code concatenates 'pref_key_system_vibration_amp_period_' + 'start'/'end' (subs/System_VibrationAmp.kt:15,33,46). |
| `pref_key_system_statusbar_batterytempandcurrent_*` | Dynamic subKey: 'batterytempandcurrent' used in SystemUIStatusBarHooks.kt and DeviceInfoMonitor (e.g. system_statusbar_${subKey}_fontsize). |
| `pref_key_system_statusbar_showdevicetemperature_*` | Dynamic subKey: 'showdevicetemperature' used in DeviceInfoMonitor and SystemUIStatusBarHooks. |
| `pref_key_system_statusbar_mobile_digital_signal_*` | Dynamic subKey: 'mobile_digital_signal' used in SystemUIStatusBarHooks.kt. |
| `pref_key_controls_backlong etc.` | Runtime action keys: base key in XML, value stored as <base>_action and read by GlobalActions/NavBarActionsFeature. |
| `pref_key_launcher_swipedown etc.` | Runtime action keys: base key in XML, value stored as <base>_action and read by LauncherGestureHooks/LauncherPostAttachFeatures. |
| `pref_key_system_netspeed_prerequisite` | Informational PreferenceEx with persistent='false'; no hook. |
| `pref_key_miuizer` | Branding header in prefs_main.xml; not a hook. |
| `pref_key_system_statusbar_clocktweak` | PreferenceScreen in prefs_system_statusbar_clock.xml; parent of clock settings. |

## Verification status

All `DELETE_DEAD` findings are `STATIC_VERIFIED`.
No `BUILD_VERIFIED`, `LOG_VERIFIED`, `DEVICE_HOOK_VERIFIED`, or `DEVICE_BEHAVIOR_VERIFIED` claims are made because no build or device logs were run.
A14-7B `NOT_EXERCISED` status is explicitly not used as a deletion reason.