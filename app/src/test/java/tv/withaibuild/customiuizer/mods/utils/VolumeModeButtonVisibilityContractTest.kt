package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeModeButtonVisibilityContractTest {
    @Test
    fun visibilityIsPreparedAsSnapshotAndAppliedAtTheRomStateBoundary() {
        val source = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt"
        )
        val section = source.substringAfter("fun VolumeModeButtonColorsHook(")
            .substringBefore("fun ControlCenterPluginHook(")

        // The hook must refresh the process-owned visibility snapshot at install time and register
        // the process-owned preference observer exactly once through a single helper.
        assertTrue(section.contains("installVolumeModeButtonVisibilitySnapshot()"))

        // The ROM helper must still be hooked at constructor and updateState boundaries.
        assertTrue(section.contains("MiuiRingerModeLayout\\\$RingerButtonHelper"))
        assertTrue(section.contains("\"updateState\""))

        // The hot visibility callback must read from the prepared snapshot, not from preferences.
        val visibilityBody = section.substringAfter("val applyVisibility = { helper: Any ->")
            .substringBefore("val applyColors = { helper: Any ->")
        assertTrue(visibilityBody.contains("val snapshot = volumeModeButtonVisibilitySnapshot"))
        assertTrue(visibilityBody.contains("snapshot.hideMute"))
        assertTrue(visibilityBody.contains("snapshot.hideDnd"))
        assertTrue(visibilityBody.contains("View.GONE"))

        assertTrue(!visibilityBody.contains("MainModule.mPrefs.get"))
        assertTrue(!visibilityBody.contains("getResourceEntryName"))
        assertTrue(!visibilityBody.contains("getIdentifier"))
        assertTrue(!visibilityBody.contains("findViewById"))
        assertTrue(!visibilityBody.contains("getParent"))
        assertTrue(!visibilityBody.contains("resources"))
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
