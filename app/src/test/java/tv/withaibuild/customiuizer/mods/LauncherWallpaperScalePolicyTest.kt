package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherWallpaperScalePolicyTest {

    @Test
    fun recentsPrefKeepsZoomDisabledWithoutPermanentLauncherPref() {
        assertFalse(LauncherAnimationHooks.shouldDisableLauncherWallpaperZoomPermanently(false))
        assertTrue(LauncherAnimationHooks.shouldDisableLauncherWallpaperZoomPermanently(true))
        assertTrue(LauncherAnimationHooks.shouldKeepRecentsWallpaperZoomDisabled(true, false))
        assertTrue(LauncherAnimationHooks.shouldKeepRecentsWallpaperZoomDisabled(false, true))
        assertFalse(LauncherAnimationHooks.shouldKeepRecentsWallpaperZoomDisabled(false, false))
    }

    @Test
    fun recentsOnStateEnabledDoesNotRestoreZoomBeforeExit() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt")
        val enabledStart = source.indexOf("\"onStateEnabled\"")
        check(enabledStart >= 0) { "onStateEnabled hook missing" }
        val enabledEnd = source.indexOf("\"onStateDisabled\"", enabledStart)
        check(enabledEnd > enabledStart) { "onStateDisabled hook missing" }
        val onStateEnabled = source.substring(enabledStart, enabledEnd)
        assertTrue(onStateEnabled.contains("setWallpaperZoomEnabled(zoomClass, false)"))
        assertFalse(
            "restoring ZOOM_ENABLED inside onStateEnabled lets the recents gesture keep scaling",
            onStateEnabled.contains("setWallpaperZoomEnabled(zoomClass, true)")
        )
        assertTrue(source.contains("\"fastBlurWhenEnterRecents\""))
        assertTrue(source.contains("\"fastBlurWhenExitRecents\""))
        assertTrue(source.contains("\"getWallpaperZoomOut\""))
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
