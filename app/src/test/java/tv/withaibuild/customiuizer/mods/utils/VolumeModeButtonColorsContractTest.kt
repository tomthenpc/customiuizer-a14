package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeModeButtonColorsContractTest {
    @Test
    fun colorsAreCapturedOnceAndReappliedAtTheRomStateBoundary() {
        val source = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt"
        )
        val section = source.substringAfter("fun VolumeModeButtonColorsHook(")
            .substringBefore("fun ControlCenterPluginHook(")
        assertTrue(section.contains("val backgroundTint = ColorStateList.valueOf("))
        assertTrue(section.contains("val iconTint = ColorStateList.valueOf("))
        assertTrue(section.contains("MiuiRingerModeLayout\\\$RingerButtonHelper"))
        assertTrue(section.contains("\"updateState\""))
        assertTrue(section.contains("standardView?.backgroundTintList = backgroundTint"))
        assertTrue(section.contains("blurView?.backgroundTintList = backgroundTint"))
        assertTrue(section.contains("icon?.imageTintList = iconTint"))
        assertTrue(!section.substringAfter("val applyColors").contains("MainModule.mPrefs.get"))
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
