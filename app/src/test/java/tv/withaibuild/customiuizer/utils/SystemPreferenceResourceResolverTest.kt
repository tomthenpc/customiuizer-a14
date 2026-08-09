package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.withaibuild.customiuizer.R

class SystemPreferenceResourceResolverTest {

    @Test
    fun statusBarCategoryUsesDedicatedResource() {
        assertEquals(
            R.xml.prefs_system_statusbar,
            SystemPreferenceResourceResolver.resolve("pref_key_system_cat_statusbar")
        )
    }

    @Test
    fun otherCategoriesKeepUsingCompleteResource() {
        assertEquals(R.xml.prefs_system, SystemPreferenceResourceResolver.resolve(null))
        assertEquals(
            R.xml.prefs_system,
            SystemPreferenceResourceResolver.resolve("pref_key_system_cat_lockscreen")
        )
    }
}
