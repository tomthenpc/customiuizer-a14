package tv.withaibuild.customiuizer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutMigrationTest {

    private val aboutFragment = source("app/src/main/java/tv/withaibuild/customiuizer/AboutFragment.kt")
    private val appLocaleController = source("app/src/main/java/tv/withaibuild/customiuizer/utils/AppLocaleController.kt")
    private val mainActivity = source("app/src/main/java/tv/withaibuild/customiuizer/MainActivity.kt")
    private val preferenceFragmentBase = source("app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")
    private val buildGradle = source("app/build.gradle.kts")

    @Test
    fun aboutFragmentIsAPlainFragment() {
        assertTrue("AboutFragment should extend Fragment", aboutFragment.contains("class AboutFragment : Fragment()"))
        assertFalse("AboutFragment should not extend SubFragment", aboutFragment.contains("class AboutFragment : SubFragment"))
    }

    @Test
    fun aboutFragmentDoesNotUsePreferenceInfrastructure() {
        assertFalse("AboutFragment must not use findPreference", aboutFragment.contains("findPreference"))
        assertFalse("AboutFragment must not use ListPreferenceEx", aboutFragment.contains("ListPreferenceEx"))
        assertFalse("AboutFragment must not use PreferenceScreen", aboutFragment.contains("PreferenceScreen"))
        assertFalse("AboutFragment must not use Preference", aboutFragment.contains("androidx.preference.Preference"))
        assertFalse("AboutFragment must not load prefs_about", aboutFragment.contains("R.xml.prefs_about"))
        assertFalse("AboutFragment must not call setPreferencesFromResource", aboutFragment.contains("setPreferencesFromResource"))
    }

    @Test
    fun appLocaleControllerDoesNotDependOnListPreferenceEx() {
        assertFalse("AppLocaleController must not import ListPreferenceEx", appLocaleController.contains("ListPreferenceEx"))
        assertFalse("AppLocaleController must not expose setupLocalePreference", appLocaleController.contains("setupLocalePreference"))
    }

    @Test
    fun aboutLaunchDoesNotRequirePrefsAboutXml() {
        assertFalse("PreferenceFragmentBase must not reference R.xml.prefs_about", preferenceFragmentBase.contains("R.xml.prefs_about"))
        assertTrue("PreferenceFragmentBase should launch AboutFragment", preferenceFragmentBase.contains("AboutFragment()"))
    }

    @Test
    fun toolbarHomeHandlesAboutFragment() {
        assertTrue("MainActivity must pop the back stack for AboutFragment", mainActivity.contains("fragment is AboutFragment"))
        assertTrue("MainActivity must call popBackStackImmediate for AboutFragment", mainActivity.contains("popBackStackImmediate"))
    }

    @Test
    fun prefsAboutXmlIsRemoved() {
        val prefsAbout = File(System.getProperty("user.dir")).absoluteFile.let { root ->
            var dir: File? = root
            while (dir != null) {
                val candidate = File(dir, "app/src/main/res/xml/prefs_about.xml")
                if (candidate.isFile) return@let candidate
                dir = dir.parentFile
            }
            null
        }
        assertTrue("prefs_about.xml should be deleted", prefsAbout == null || !prefsAbout.exists())
    }

    @Test
    fun noComposeDependenciesIntroduced() {
        assertFalse("build.gradle.kts must not add Compose dependencies", buildGradle.contains("androidx.compose"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
