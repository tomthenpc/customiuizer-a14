package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeModeButtonColorsContractTest {
    @Test
    fun colorsArePreparedAsSnapshotAndReappliedAtTheRomStateBoundary() {
        val source = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt"
        )
        val section = source.substringAfter("fun VolumeModeButtonColorsHook(")
            .substringBefore("fun ControlCenterPluginHook(")

        // The hook must refresh the process-owned snapshot at install time and register the
        // process-owned preference observer exactly once through a single helper.
        assertTrue(section.contains("installVolumeModeButtonColorSnapshot()"))

        // The ROM helper must still be hooked at constructor and updateState boundaries.
        assertTrue(section.contains("MiuiRingerModeLayout\\\$RingerButtonHelper"))
        assertTrue(section.contains("\"updateState\""))

        // The hot callback must read from the prepared snapshot, not from preferences.
        assertTrue(section.contains("val snapshot = volumeModeButtonColorSnapshot"))
        assertTrue(section.contains("if (snapshot.enabled) {"))
        assertTrue(section.contains("standardView?.backgroundTintList = snapshot.backgroundTint"))
        assertTrue(section.contains("blurView?.backgroundTintList = snapshot.backgroundTint"))
        assertTrue(section.contains("icon?.imageTintList = snapshot.iconTint"))

        val applyColorsBody = section.substringAfter("val applyColors = { helper: Any ->")
            .substringBefore("ModuleHelper.hookAllConstructors")
        assertTrue(!applyColorsBody.contains("MainModule.mPrefs.get"))
        assertTrue(!applyColorsBody.contains("ColorStateList.valueOf"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
