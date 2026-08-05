package tv.withaibuild.customiuizer.mods.utils

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class StatusBarHeightConfigTest {

    @After
    fun tearDown() {
        StatusBarHeightConfig.resetForTest()
    }

    @Test
    fun resolveHeightDp_defaultSentinel_returns27dp() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 11) }
        assertEquals(27, StatusBarHeightConfig.resolveHeightDp(prefs))
    }

    @Test
    fun resolveHeightDp_customValue_returnsValue() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 40) }
        assertEquals(40, StatusBarHeightConfig.resolveHeightDp(prefs))
    }

    @Test
    fun resolveHeightDp_belowSentinel_returnsValue() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 10) }
        assertEquals(10, StatusBarHeightConfig.resolveHeightDp(prefs))
    }

    @Test
    fun isEnabled_defaultSentinel_false() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 11) }
        assertFalse(StatusBarHeightConfig.isEnabled(prefs))
    }

    @Test
    fun isEnabled_customValueAboveSentinel_true() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 12) }
        assertTrue(StatusBarHeightConfig.isEnabled(prefs))
    }

    @Test
    fun isEnabled_missingValue_false() {
        assertFalse(StatusBarHeightConfig.isEnabled(PrefMap()))
    }

    @Test
    fun dpToPx_160dpi_27dpIs27px() {
        assertEquals(27, StatusBarHeightConfig.dpToPx(27, fakeResources(160)))
    }

    @Test
    fun dpToPx_480dpi_27dpIs81px() {
        assertEquals(81, StatusBarHeightConfig.dpToPx(27, fakeResources(480)))
    }

    @Test
    fun configure_defaultSentinel_disabledAndDefaultDp() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 11) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertFalse(StatusBarHeightConfig.enabled)
        assertEquals(27, StatusBarHeightConfig.configuredDp)
        assertEquals(27, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_customValue_enabledAndCorrectPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 40) }
        StatusBarHeightConfig.configure(prefs, fakeResources(480))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(40, StatusBarHeightConfig.configuredDp)
        assertEquals(120, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun recomputePx_densityChange_updatesConfiguredPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 40) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))
        assertEquals(40, StatusBarHeightConfig.configuredPx)

        StatusBarHeightConfig.recomputePx(fakeResources(480))
        assertEquals(120, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun recomputePx_disabled_doesNothing() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 11) },
            fakeResources(160),
        )
        StatusBarHeightConfig.recomputePx(fakeResources(480))
        assertEquals(27, StatusBarHeightConfig.configuredPx)
    }

    private fun fakeResources(densityDpi: Int): Resources {
        val metrics = DisplayMetrics().apply { this.densityDpi = densityDpi }
        val constructor = AssetManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val assetManager = constructor.newInstance()
        return FakeResources(assetManager, metrics)
    }

    private class FakeResources(assetManager: AssetManager, private val metrics: DisplayMetrics) :
        Resources(assetManager, metrics, Configuration()) {
        override fun getDisplayMetrics(): DisplayMetrics = metrics
    }
}
