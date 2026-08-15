package tv.withaibuild.customiuizer.utils

import androidx.preference.CheckBoxPreference
import androidx.preference.DropDownPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference as AndroidXSeekBarPreference
import androidx.preference.SwitchPreference
import androidx.preference.TwoStatePreference
import tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx
import tv.withaibuild.customiuizer.prefs.ColorPreferenceEx
import tv.withaibuild.customiuizer.prefs.DropDownPreferenceEx
import tv.withaibuild.customiuizer.prefs.EditTextPreferenceEx
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx
import tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx
import tv.withaibuild.customiuizer.prefs.PreferenceEx
import tv.withaibuild.customiuizer.prefs.SeekBarPreference

/**
 * Resolves the set of [RestartTarget]s that must be restarted for a given
 * collection of preference keys, or for an in-memory preference screen.
 *
 * The resolver is strictly positive-allowlist based: a lookup miss contributes
 * nothing (fail-closed).  Page names, XML locations, and source filenames are
 * never used as restart evidence.
 */
object PreferenceRestartTargetResolver {

    /**
     * Returns the union of executable restart targets for the given keys.
     * Both `pref_key_...` and canonical `...` forms are accepted; nulls are
     * ignored.
     */
    fun resolveForKeys(keys: List<String?>): Set<RestartTarget> {
        val result = linkedSetOf<RestartTarget>()
        for (key in keys) {
            result.addAll(PreferenceRestartTargetRegistry.targetsFor(key))
        }
        return result
    }

    /**
     * Walks the current in-memory preference tree starting at [root] and
     * returns the union of executable restart targets for visible, enabled,
     * functional leaf preferences.
     *
     * Eligible leaf types are two-state (CheckBox/Switch), List/DropDown,
     * EditText, MultiSelect, SeekBar, and Color preferences.  [PreferenceCategory],
     * [PreferenceCategoryEx], navigation-only [PreferenceEx], nested
     * [PreferenceScreen] rows and non-functional leaves are ignored.  Children
     * of [PreferenceGroup]s are inspected recursively, but nested sub-screens
     * and sub-fragments/intents are not crossed.
     */
    fun resolvePreferenceScreen(root: PreferenceScreen): Set<RestartTarget> {
        val result = linkedSetOf<RestartTarget>()
        collectFromGroup(root, result)
        return result
    }

    private fun collectFromGroup(group: PreferenceGroup, result: MutableSet<RestartTarget>) {
        val count = group.preferenceCount
        for (i in 0 until count) {
            val child = group.getPreference(i)
            if (!child.isVisible || !child.isEnabled) continue

            when (child) {
                is PreferenceCategoryEx,
                is PreferenceCategory -> collectFromGroup(child as PreferenceGroup, result)
                is TwoStatePreference,
                is ListPreference,
                is EditTextPreference,
                is MultiSelectListPreference,
                is ColorPreferenceEx,
                is SeekBarPreference,
                is AndroidXSeekBarPreference -> addIfKey(child, result)
                else -> {
                    // Ignore nested PreferenceScreen navigation rows,
                    // navigation-only PreferenceEx and any other non-functional row.
                }
            }
        }
    }

    private fun addIfKey(preference: Preference, result: MutableSet<RestartTarget>) {
        val key = preference.key ?: return
        result.addAll(PreferenceRestartTargetRegistry.targetsFor(key))
    }
}
