package tv.withaibuild.customiuizer.utils

import tv.withaibuild.customiuizer.R

/** Selects the smallest available XML resource for a system settings category. */
internal object SystemPreferenceResourceResolver {

    fun resolve(sub: String?): Int = when (sub) {
        "pref_key_system_cat_statusbar" -> R.xml.prefs_system_statusbar
        else -> R.xml.prefs_system
    }
}
