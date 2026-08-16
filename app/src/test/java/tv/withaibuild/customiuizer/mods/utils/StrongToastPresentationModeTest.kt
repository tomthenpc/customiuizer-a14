package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File

class StrongToastPresentationModeTest {
    @Test
    fun preferenceValues_mapToBoundedModesAndLegacyMigration() {
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(0))
        assertEquals(StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT, StrongToastPresentationMode.fromPreference(1))
        assertEquals(StrongToastPresentationMode.HIDE, StrongToastPresentationMode.fromPreference(2))
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPresentationMode.fromPreference(3))
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPresentationMode.fromPreference(4))
    }

    @Test
    fun hooksRemainEnabledForLiveModeChanges() {
        for (mode in listOf("0", "1", "2", "3", "4")) {
            assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
                put("system_strong_toast_mode", mode)
            }))
        }
    }

    @Test
    fun matchModeAndTopIslandGeometryRemainBounded() {
        assertEquals(100, SystemUIStrongToastHooks.resolveMatchWindowHeightPx(100))
        assertEquals(80, SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 20))
        assertTrue(SystemUIStrongToastHooks.matchModeHidesChin(100, 60))
        assertEquals(195, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(141, 18, 36))
    }

    @Test
    fun dynamicIslandUsesSharedHostAndHasNoLegacyBottomPath() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("DynamicIslandEventAdapter.fromStrongToast"))
        assertTrue(source.contains("DynamicIslandHost.shared.attach"))
        assertTrue(source.contains("DynamicIslandHost.shared.detachImmediate"))
        assertTrue(source.contains("SOURCE_CHARGING_BATTERY"))
        assertTrue(source.contains("SOURCE_CUSTOM_SHOW"))
        assertTrue(source.contains("layoutParams.width = 1"))
        assertTrue(source.contains("layoutParams.height = 1"))
        assertFalse(source.contains("Gravity.BOTTOM"))
        assertFalse(source.contains("forBottom("))
        assertFalse(source.contains("resolveBottom"))
        assertFalse(source.contains("currentStatusBarInsetPx(root)"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found")
        }
    }
}
