package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PreferenceRestartTargetRegistry].
 *
 * Key lists are embedded from the P3-A4 audit registry so the test runs
 * without touching any external JSON file at runtime.
 */
class PreferenceRestartTargetRegistryTest {

    private val EXPECTED_LAUNCHER_KEYS = setOf(
        "controls_fsg_assist_left_action",
        "controls_fsg_assist_right_action",
        "controls_fsg_coverage",
        "controls_fsg_horiz",
        "controls_fsg_swipeandstop_action",
        "controls_fsg_width",
        "controls_nonavbar",
        "launcher_closedrawer",
        "launcher_closefolders",
        "launcher_darkershadow",
        "launcher_disable_log",
        "launcher_disable_wallpaperscale",
        "launcher_dock_bottommargin",
        "launcher_dock_height",
        "launcher_dock_topmargin",
        "launcher_docktitles",
        "launcher_doubletap_action",
        "launcher_fixanim",
        "launcher_fixlaunch",
        "launcher_folder_cols",
        "launcher_folderblur_disable",
        "launcher_folderblur_opacity",
        "launcher_hideseekpoints",
        "launcher_hidetitles",
        "launcher_horizmargin",
        "launcher_horizwidgetmargin",
        "launcher_iconscale",
        "launcher_indicator_topmargin",
        "launcher_indicatorheight",
        "launcher_infinitescroll",
        "launcher_noclockhide",
        "launcher_nounlockanim",
        "launcher_nowidgetonly",
        "launcher_nozoomanim",
        "launcher_oldlaunchanim",
        "launcher_pinch_action",
        "launcher_privacyapps_gest",
        "launcher_renameapps",
        "launcher_sensorportrait",
        "launcher_shake_action",
        "launcher_spread_action",
        "launcher_swipedown2_action",
        "launcher_swipedown_action",
        "launcher_swipeleft_action",
        "launcher_swiperight_action",
        "launcher_swipeup2_action",
        "launcher_swipeup_action",
        "launcher_titlefontsize",
        "launcher_titletopmargin",
        "launcher_topmargin",
        "launcher_unlockgrids",
        "launcher_unlockhotseat",
        "launcher_wallpaper_colormode",
        "system_fw_splitscreen",
        "system_hidefromrecents",
        "system_recents_blur",
        "system_recents_card_style",
        "system_recents_disable_wallpaperscale",
        "system_recents_hide_statusbar",
        "system_removecleaner",
        "system_resizablewidgets",
    )

    private val EXPECTED_SYSTEMUI_KEYS = setOf(
        "controls_fsg_assist_left_action",
        "controls_fsg_assist_right_action",
        "controls_hidenavbar_whenscreenshot",
        "controls_navbarleft_action",
        "controls_nonavbar",
        "controls_volumecursor",
        "system_4gtolte",
        "system_albumartonlock",
        "system_allownotiffloat",
        "system_allownotifonkeyguard",
        "system_batteryindicator",
        "system_betterpopups_allowfloat",
        "system_betterpopups_autoclose_expanded",
        "system_betterpopups_center",
        "system_betterpopups_delay",
        "system_betterpopups_disablewhenmute",
        "system_betterpopups_nohide",
        "system_cc_btandtorch_ascard",
        "system_cc_card_enabled_color",
        "system_cc_clock_centeralign",
        "system_cc_clocktweak",
        "system_cc_collapse_after_clicked",
        "system_cc_floatingtimetile",
        "system_cc_fpstile",
        "system_cc_freeform_when_longclick",
        "system_cc_hide_edit",
        "system_cc_hide_profile_monitoring",
        "system_cc_hideoperator_delimiter",
        "system_cc_show_stepcount",
        "system_cc_slider_color_enable",
        "system_cc_switch_qsandnotification",
        "system_cc_tile_enabled_color",
        "system_cc_tile_roundedrect",
        "system_cc_volume_showpct",
        "system_ccgridcolumns",
        "system_chargeanimtime",
        "system_charginginfo",
        "system_colorizenotifs",
        "system_detailednetspeed_style",
        "system_disableanynotif",
        "system_drawer_blur",
        "system_drawer_remove_emptynotify",
        "system_drawer_removeshortcut",
        "system_dttosleep",
        "system_epm",
        "system_expandheadups",
        "system_expandnotifs",
        "system_fivegtile",
        "system_fw_noblacklist",
        "system_hidelsclock",
        "system_hidelshint",
        "system_hidelsstatusbar",
        "system_hidestatusbar_whenscreenshot",
        "system_lockscreen_disable_edit",
        "system_lockscreen_hidezenmode",
        "system_lockscreenshortcuts",
        "system_ls_force_systemfonts",
        "system_lsalarm",
        "system_maxsbicons",
        "system_minimalnotifview",
        "system_mobiletypeicon",
        "system_morenotif",
        "system_mutevisiblenotif",
        "system_netspeedinterval",
        "system_networkindicator_wifi",
        "system_nolightuponcharges",
        "system_nopassword",
        "system_nosafevolume",
        "system_noscreenlock_act",
        "system_nosilentvibrate",
        "system_nosos",
        "system_notif_disable_fold",
        "system_notifafterunlock",
        "system_notifchannelsettings",
        "system_notifimportance",
        "system_notifrowmenu",
        "system_notify_openinfw",
        "system_qs_disable_fakeclock_anim",
        "system_qs_force_systemfonts",
        "system_qs_hideoperator",
        "system_qshaptics",
        "system_removedismiss",
        "system_scramblepin",
        "system_screenshot_overlay",
        "system_secureqs",
        "system_shortcut_app",
        "system_showpct",
        "system_statusbar_alarm_atright",
        "system_statusbar_batterystyle",
        "system_statusbar_batterytempandcurrent",
        "system_statusbar_clock_position",
        "system_statusbar_clocktweak",
        "system_statusbar_dualrows",
        "system_statusbar_dualsimin2rows",
        "system_statusbar_horizmargin",
        "system_statusbar_mobile_digital_signal",
        "system_statusbar_mobiletype_single",
        "system_statusbar_topmargin",
        "system_statusbarcontrols",
        "system_statusbaricons_alarm",
        "system_statusbaricons_battery1",
        "system_statusbaricons_battery3",
        "system_statusbaricons_privacy",
        "system_statusbaricons_privacy_prompt",
        "system_statusbaricons_signal",
        "system_statusbaricons_vowifi",
        "system_statusbaricons_wifi",
        "system_statusbaricons_wifistandard",
        "system_taptounlock",
        "system_visualizer",
        "system_volume_mode_button_colors",
        "system_volumebar_blur_mtk",
        "system_volumeblur_collapsed",
        "system_volumeblur_expanded",
        "system_volumedialogdelay_collapsed",
        "system_volumedialogdelay_expanded",
        "system_volumetimer",
        "various_showcallui",
    )

    private val EXPECTED_SECURITY_CENTER_KEYS = setOf(
        "system_applock_scramblepin",
        "system_hidelowbatwarn",
        "various_appdetails",
        "various_appsort",
        "various_disable_dock_suggest",
        "various_disable_freeform_suggest_blacklist",
        "various_disable_reset_recents_privacy_blur",
        "various_disableapp",
        "various_enable_expand_sidebar",
        "various_hide_report_ondetails",
        "various_privacyapps_column_nums4",
        "various_replace_defaultopen_with_openbydefault",
        "various_restrictapp",
        "various_show_battery_temperature",
        "various_skip_interceptperm",
        "various_skip_securityscan",
    )

    private val EXPECTED_MULTI_HOST_KEYS = setOf(
        "controls_fsg_assist_left_action",
        "controls_fsg_assist_right_action",
        "controls_nonavbar",
    )

    private val EXPECTED_EXECUTABLE_KEYS = setOf(
        "controls_fsg_assist_left_action",
        "controls_fsg_assist_right_action",
        "controls_fsg_coverage",
        "controls_fsg_horiz",
        "controls_fsg_swipeandstop_action",
        "controls_fsg_width",
        "controls_hidenavbar_whenscreenshot",
        "controls_navbarleft_action",
        "controls_nonavbar",
        "controls_volumecursor",
        "launcher_closedrawer",
        "launcher_closefolders",
        "launcher_darkershadow",
        "launcher_disable_log",
        "launcher_disable_wallpaperscale",
        "launcher_dock_bottommargin",
        "launcher_dock_height",
        "launcher_dock_topmargin",
        "launcher_docktitles",
        "launcher_doubletap_action",
        "launcher_fixanim",
        "launcher_fixlaunch",
        "launcher_folder_cols",
        "launcher_folderblur_disable",
        "launcher_folderblur_opacity",
        "launcher_hideseekpoints",
        "launcher_hidetitles",
        "launcher_horizmargin",
        "launcher_horizwidgetmargin",
        "launcher_iconscale",
        "launcher_indicator_topmargin",
        "launcher_indicatorheight",
        "launcher_infinitescroll",
        "launcher_noclockhide",
        "launcher_nounlockanim",
        "launcher_nowidgetonly",
        "launcher_nozoomanim",
        "launcher_oldlaunchanim",
        "launcher_pinch_action",
        "launcher_privacyapps_gest",
        "launcher_renameapps",
        "launcher_sensorportrait",
        "launcher_shake_action",
        "launcher_spread_action",
        "launcher_swipedown2_action",
        "launcher_swipedown_action",
        "launcher_swipeleft_action",
        "launcher_swiperight_action",
        "launcher_swipeup2_action",
        "launcher_swipeup_action",
        "launcher_titlefontsize",
        "launcher_titletopmargin",
        "launcher_topmargin",
        "launcher_unlockgrids",
        "launcher_unlockhotseat",
        "launcher_wallpaper_colormode",
        "system_4gtolte",
        "system_albumartonlock",
        "system_allownotiffloat",
        "system_allownotifonkeyguard",
        "system_applock_scramblepin",
        "system_batteryindicator",
        "system_betterpopups_allowfloat",
        "system_betterpopups_autoclose_expanded",
        "system_betterpopups_center",
        "system_betterpopups_delay",
        "system_betterpopups_disablewhenmute",
        "system_betterpopups_nohide",
        "system_cc_btandtorch_ascard",
        "system_cc_card_enabled_color",
        "system_cc_clock_centeralign",
        "system_cc_clocktweak",
        "system_cc_collapse_after_clicked",
        "system_cc_floatingtimetile",
        "system_cc_fpstile",
        "system_cc_freeform_when_longclick",
        "system_cc_hide_edit",
        "system_cc_hide_profile_monitoring",
        "system_cc_hideoperator_delimiter",
        "system_cc_show_stepcount",
        "system_cc_slider_color_enable",
        "system_cc_switch_qsandnotification",
        "system_cc_tile_enabled_color",
        "system_cc_tile_roundedrect",
        "system_cc_volume_showpct",
        "system_ccgridcolumns",
        "system_chargeanimtime",
        "system_charginginfo",
        "system_colorizenotifs",
        "system_detailednetspeed_style",
        "system_disableanynotif",
        "system_drawer_blur",
        "system_drawer_remove_emptynotify",
        "system_drawer_removeshortcut",
        "system_dttosleep",
        "system_epm",
        "system_expandheadups",
        "system_expandnotifs",
        "system_fivegtile",
        "system_fw_noblacklist",
        "system_fw_splitscreen",
        "system_hidefromrecents",
        "system_hidelowbatwarn",
        "system_hidelsclock",
        "system_hidelshint",
        "system_hidelsstatusbar",
        "system_hidestatusbar_whenscreenshot",
        "system_lockscreen_disable_edit",
        "system_lockscreen_hidezenmode",
        "system_lockscreenshortcuts",
        "system_ls_force_systemfonts",
        "system_lsalarm",
        "system_maxsbicons",
        "system_minimalnotifview",
        "system_mobiletypeicon",
        "system_morenotif",
        "system_mutevisiblenotif",
        "system_netspeedinterval",
        "system_networkindicator_wifi",
        "system_nolightuponcharges",
        "system_nopassword",
        "system_nosafevolume",
        "system_noscreenlock_act",
        "system_nosilentvibrate",
        "system_nosos",
        "system_notif_disable_fold",
        "system_notifafterunlock",
        "system_notifchannelsettings",
        "system_notifimportance",
        "system_notifrowmenu",
        "system_notify_openinfw",
        "system_qs_disable_fakeclock_anim",
        "system_qs_force_systemfonts",
        "system_qs_hideoperator",
        "system_qshaptics",
        "system_recents_blur",
        "system_recents_card_style",
        "system_recents_disable_wallpaperscale",
        "system_recents_hide_statusbar",
        "system_removecleaner",
        "system_removedismiss",
        "system_resizablewidgets",
        "system_scramblepin",
        "system_screenshot_overlay",
        "system_secureqs",
        "system_shortcut_app",
        "system_showpct",
        "system_statusbar_alarm_atright",
        "system_statusbar_batterystyle",
        "system_statusbar_batterytempandcurrent",
        "system_statusbar_clock_position",
        "system_statusbar_clocktweak",
        "system_statusbar_dualrows",
        "system_statusbar_dualsimin2rows",
        "system_statusbar_horizmargin",
        "system_statusbar_mobile_digital_signal",
        "system_statusbar_mobiletype_single",
        "system_statusbar_topmargin",
        "system_statusbarcontrols",
        "system_statusbaricons_alarm",
        "system_statusbaricons_battery1",
        "system_statusbaricons_battery3",
        "system_statusbaricons_privacy",
        "system_statusbaricons_privacy_prompt",
        "system_statusbaricons_signal",
        "system_statusbaricons_vowifi",
        "system_statusbaricons_wifi",
        "system_statusbaricons_wifistandard",
        "system_taptounlock",
        "system_visualizer",
        "system_volume_mode_button_colors",
        "system_volumebar_blur_mtk",
        "system_volumeblur_collapsed",
        "system_volumeblur_expanded",
        "system_volumedialogdelay_collapsed",
        "system_volumedialogdelay_expanded",
        "system_volumetimer",
        "various_appdetails",
        "various_appsort",
        "various_disable_dock_suggest",
        "various_disable_freeform_suggest_blacklist",
        "various_disable_reset_recents_privacy_blur",
        "various_disableapp",
        "various_enable_expand_sidebar",
        "various_hide_report_ondetails",
        "various_privacyapps_column_nums4",
        "various_replace_defaultopen_with_openbydefault",
        "various_restrictapp",
        "various_show_battery_temperature",
        "various_showcallui",
        "various_skip_interceptperm",
        "various_skip_securityscan",
    )

    @Test
    fun exact_counts() {
        assertEquals(61, EXPECTED_LAUNCHER_KEYS.size)
        assertEquals(118, EXPECTED_SYSTEMUI_KEYS.size)
        assertEquals(16, EXPECTED_SECURITY_CENTER_KEYS.size)
        assertEquals(192, EXPECTED_EXECUTABLE_KEYS.size)
        assertEquals(192, PreferenceRestartTargetRegistry.executableUniqueKeysCount())
    }

    @Test
    fun all_keys_match_registry() {
        assertEquals(EXPECTED_LAUNCHER_KEYS, PreferenceRestartTargetRegistry.allKeysFor(RestartTarget.LAUNCHER))
        assertEquals(EXPECTED_SYSTEMUI_KEYS, PreferenceRestartTargetRegistry.allKeysFor(RestartTarget.SYSTEMUI))
        assertEquals(EXPECTED_SECURITY_CENTER_KEYS, PreferenceRestartTargetRegistry.allKeysFor(RestartTarget.SECURITY_CENTER))
    }

    @Test
    fun multi_host_regression() {
        val launcherAndSystemUi = EXPECTED_LAUNCHER_KEYS intersect EXPECTED_SYSTEMUI_KEYS
        assertEquals(EXPECTED_MULTI_HOST_KEYS, launcherAndSystemUi)

        val left = PreferenceRestartTargetRegistry.targetsFor("controls_fsg_assist_left_action")
        assertEquals(setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI), left)

        val right = PreferenceRestartTargetRegistry.targetsFor("controls_fsg_assist_right_action")
        assertEquals(setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI), right)

        val nonavbar = PreferenceRestartTargetRegistry.targetsFor("controls_nonavbar")
        assertEquals(setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI), nonavbar)
    }

    @Test
    fun strong_toast_and_usb_and_charging_info_return_empty() {
        // Always-installed / runtime-handled preferences must not produce executable targets.
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_strong_toast_mode").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_strong_toast_position").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_strong_toast_bottom_offset").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_usb_default_function").isEmpty())

        // Charging-info detail keys are live-read and must not restart anything.
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_charginginfo_current").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_charginginfo_temp").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_charginginfo_voltage").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_charginginfo_wattage").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("system_charginginfo_fontsize").isEmpty())
    }

    @Test
    fun canonical_and_pref_key_forms_are_equivalent() {
        val canonical = "system_charginginfo"
        val prefixed = "pref_key_system_charginginfo"
        assertEquals(
            PreferenceRestartTargetRegistry.targetsFor(canonical),
            PreferenceRestartTargetRegistry.targetsFor(prefixed)
        )

        val canonical2 = "launcher_fixanim"
        val prefixed2 = "pref_key_launcher_fixanim"
        assertEquals(
            PreferenceRestartTargetRegistry.targetsFor(canonical2),
            PreferenceRestartTargetRegistry.targetsFor(prefixed2)
        )

        val canonical3 = "various_disableapp"
        val prefixed3 = "pref_key_various_disableapp"
        assertEquals(
            setOf(RestartTarget.SECURITY_CENTER),
            PreferenceRestartTargetRegistry.targetsFor(canonical3)
        )
        assertEquals(
            PreferenceRestartTargetRegistry.targetsFor(canonical3),
            PreferenceRestartTargetRegistry.targetsFor(prefixed3)
        )
    }

    @Test
    fun fail_closed_for_unknown_keys() {
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("not_a_real_key").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor("pref_key_not_a_real_key").isEmpty())
        assertTrue(PreferenceRestartTargetRegistry.targetsFor(null).isEmpty())
    }
}
