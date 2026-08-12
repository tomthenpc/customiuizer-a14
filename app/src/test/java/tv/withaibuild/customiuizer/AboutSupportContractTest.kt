package tv.withaibuild.customiuizer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutSupportContractTest {
    @Test
    fun supportEntries_areColdPathAndUseCanonicalLinks() {
        val fragment = source("app/src/main/java/tv/withaibuild/customiuizer/AboutFragment.kt")
        val preferences = source("app/src/main/res/xml/prefs_about.xml")

        assertTrue(preferences.contains("pref_key_about_donate"))
        assertTrue(preferences.contains("pref_key_about_repository"))
        assertTrue(preferences.contains("pref_key_about_contact"))
        assertTrue(fragment.contains("inSampleSize = DONATION_IMAGE_SAMPLE_SIZE"))
        assertTrue(fragment.contains("image.setImageDrawable(null)"))
        assertTrue(fragment.contains("bitmap.recycle()"))
        assertTrue(fragment.contains("https://github.com/tomthenpc/customiuizer-a14"))
        assertTrue(fragment.contains("https://t.me/Jinji_Kiko"))
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
