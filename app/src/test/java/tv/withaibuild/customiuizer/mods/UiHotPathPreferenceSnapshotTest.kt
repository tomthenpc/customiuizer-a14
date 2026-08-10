package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural regression guards for the P1-B hot paths.
 *
 * These tests scan source files to verify that the identified callbacks do not reach the
 * preference map directly and that the expected snapshot/cached helpers are present. They do
 * not exercise runtime behavior; behavioral coverage lives in the dedicated behavior test files.
 */
class UiHotPathPreferenceSnapshotTest {

    @Test
    fun keyguardSetTranslationReadsSnapshotFlags() {
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt",
            "\"com.android.keyguard.KeyguardMoveHelper\"",
        )

        assertFalse("setTranslation runs per touch sample", body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("swipeRightOff"))
        assertTrue(body.contains("swipeLeftOff"))
    }

    @Test
    fun folderLayoutReadsSnapshotAndCachedFields() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt")
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "\"onLayout\"",
        )

        assertFalse("onLayout is a layout callback", body.contains("MainModule.mPrefs"))
        assertFalse("field lookups belong to the cold path", body.contains("XposedHelpers.getObjectField"))
        assertTrue(body.contains("folderWidthEnabled"))
        assertTrue(source.contains("private fun resolveFolderLayoutFields("))
    }

    @Test
    fun folderBlurReadsSnapshotRatio() {
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt",
            "\"getLauncherBlur\"",
        )

        assertFalse(body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("folderBlurRatio"))
    }

    @Test
    fun batteryUpdateAllReadsOneSnapshotAndAvoidsHotPathAllocation() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt")
        val body = hookBody(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt",
            "\"updateAll\"",
        )

        assertFalse("updateAll must not read nine preferences per call", body.contains("MainModule.mPrefs"))
        assertTrue(body.contains("val style = batteryStyle ?: return"))
        assertFalse("child order must be checked before mutating the hierarchy", body.contains("removeView"))
        assertTrue("snapshot-driven style is applied from the hook", body.contains("applyBatteryStyle("))
        assertTrue("baseline is restored when style returns to default", body.contains("restoreBatteryBaseline("))
        assertTrue("swap helper is still used", source.contains("applyBatteryChildSwapIfNeeded("))
        assertTrue("text size changes are idempotent", source.contains("setTextSizeIfChanged("))
        assertTrue("padding changes are idempotent", source.contains("setPaddingRelativeIfChanged("))
    }

    /** Returns the brace-balanced hook body that follows the first occurrence of [marker]. */
    private fun hookBody(relativePath: String, marker: String): String {
        val source = source(relativePath)
        val markerOffset = source.indexOf(marker)
        check(markerOffset >= 0) { "Marker not found: $marker" }
        var index = source.indexOf("object : MethodHook() {", markerOffset)
        check(index >= 0) { "Hook body not found after $marker" }
        index = source.indexOf('{', index)
        var depth = 0
        while (true) {
            when (source[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) return source.substring(markerOffset, index + 1)
            index++
        }
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
