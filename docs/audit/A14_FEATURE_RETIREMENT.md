# A14 Feature Retirement Audit

Source: `docs/rom-intelligence/A14_PROCESS_MATRIX.csv`

## Summary

- Total features: 240
- **KEEP**: 21
- **KEEP_GUARDED**: 214
- **EXPERIMENTAL**: 5
- **FREEZE_LEGACY**: 0
- **DELETE_DEAD**: 0

## Methodology

Each feature is classified from the process matrix using the following heuristic rules:

1. **DELETE_DEAD** ? no installer, no install hook, or unknown phase.
2. **EXPERIMENTAL** ? uses DexKit, has `FeatureTarget.ANY`, or the runtime path is otherwise unknown at build time.
3. **FREEZE_LEGACY** ? preference or feature name contains legacy/older ROM keywords.
4. **KEEP_GUARDED** ? runs in an app/UI process (`SYSTEM_UI`, `LAUNCHER`, `SYSTEM_PACKAGE`) or uses a SystemUI/system callback path; must keep `ModuleHelper.guarded` and owner-bound registration.
5. **KEEP** ? pure system service setting in `system_server` with no expected UI callback.

## Notes

The full per-feature classification is in `A14_FEATURE_RETIREMENT.csv`.
This is a static heuristic and should be reviewed before any actual feature removal.

## Category examples

### KEEP (21)
- `toast_time` (`system_toasttime`) ? System service setting, no UI callback expected
- `remove_secure` (`system_removesecure`) ? System service setting, no UI callback expected
- `remove_act_start_confirm` (`system_remove_startactconfirm`) ? System service setting, no UI callback expected
- `no_version_check` (`system_downgrade`) ? System service setting, no UI callback expected
- `no_ducking` (`system_noducking`) ? System service setting, no UI callback expected
- `clean_share_menu_service` (`system_cleanshare`) ? System service setting, no UI callback expected
- `clean_open_with_menu_service` (`system_cleanopenwith`) ? System service setting, no UI callback expected
- `alarm_compat_service` (`various_alarmcompat`) ? System service setting, no UI callback expected
- `no_call_interruption` (`system_ignorecalls`) ? System service setting, no UI callback expected
- `force_close` (`system_forceclose`) ? System service setting, no UI callback expected
- `hide_proximity_warning` (`system_hideproxywarn`) ? System service setting, no UI callback expected
- `first_volume_press` (`system_firstpress`) ? System service setting, no UI callback expected
- `no_signature_verify_service` (`system_apksign`) ? System service setting, no UI callback expected
- `disable_system_integrity` (`system_disableintegrity`) ? System service setting, no UI callback expected
- `muffled_vibration` (`system_vibration_amp`) ? System service setting, no UI callback expected
- `clear_all_tasks` (`system_clearalltasks`) ? System service setting, no UI callback expected
- `apps_disable_service` (`various_disableapp`) ? System service setting, no UI callback expected
- `disable_any_notification_block` (`system_disableanynotif`) ? System service setting, no UI callback expected
- `selective_vibration` (`system_vibration`) ? System service setting, no UI callback expected
- `selective_toasts` (`system_blocktoasts`) ? System service setting, no UI callback expected

### KEEP_GUARDED (214)
- `temp_hide_overlay_app` (`system_screenshot_overlay`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `open_app_in_free_form` (`system_notify_openinfw`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `nav_bar_actions` (`controls_backlong_action`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `power_double_tap_action` (`controls_powerdt_action`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `screen_anim` (`system_screenanim_duration`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `app_lock_timeout` (`system_applock_timeout`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `screen_dim_time` (`system_dimtime`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `enhanced_security` (`system_securelock`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `orientation_lock` (`system_orientationlock`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `auto_brightness_range` (`system_autobrightness`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `auto_brightness_after_screen_off` (`system_autobrightness_reset_when_screenoff`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `disable72h_strong_auth` (`system_lockscreen_disable_strongauth_72h`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `app_lock` (`system_applock`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `skip_app_lock` (`system_applock_skip`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `force_dark_all_apps` (`system_force_darken_allapps`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `set_lockscreen_wallpaper` (`system_lswallpaper`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `power_key` (`controls_powerflash`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `fingerprint_haptic_failure` (`controls_fingerprintfailure`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `fingerprint_screen_on` (`controls_fingerprintscreen`) ? App/UI-facing hook in SYSTEM_SERVER/system_server
- `no_fingerprint_wake` (`controls_fingerprintwake`) ? App/UI-facing hook in SYSTEM_SERVER/system_server

### EXPERIMENTAL (5)
- `status_bar_height` (`system_statusbarheight`) ? DexKit/ANY target or unknown runtime path
- `alarm_compat` (`various_alarmcompat`) ? DexKit/ANY target or unknown runtime path
- `input_method_volume_cursor` (`controls_volumecursor`) ? DexKit/ANY target or unknown runtime path
- `input_method_fix_bottom_margin` (`controls_nonavbar_fix_inputmethod`) ? DexKit/ANY target or unknown runtime path
- `input_method_gboard_padding` (`various_gboardpadding_port`) ? DexKit/ANY target or unknown runtime path

### FREEZE_LEGACY (0)

### DELETE_DEAD (0)
