package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks

class StatusBarInsetsGeometryTest {

    @Test
    fun computeStatusBarFrameBottom_enabled_grow() {
        assertEquals(100, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(60, 100, true))
    }

    @Test
    fun computeStatusBarFrameBottom_enabled_shrinkPreservesOriginal() {
        assertEquals(100, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(100, 60, true))
    }

    @Test
    fun computeStatusBarFrameBottom_enabled_preserveCutout() {
        assertEquals(120, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(120, 80, true))
    }

    @Test
    fun computeStatusBarFrameBottom_disabled_returnsOriginal() {
        assertEquals(60, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(60, 100, false))
    }

    @Test
    fun computeStatusBarFrameBottom_zeroConfigured_returnsOriginal() {
        assertEquals(60, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(60, 0, true))
    }

    @Test
    fun computeStatusBarFrameBottom_idempotent() {
        val first = SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(60, 100, true)
        val second = SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(first, 100, true)
        assertEquals(first, second)
    }

    @Test
    fun computeStatusBarFrameBottom_customLessThanOriginal_preserved() {
        assertEquals(90, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(90, 40, true))
    }

    @Test
    fun computeStatusBarFrameBottom_customGreaterThanOriginal_applied() {
        assertEquals(150, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(80, 150, true))
    }

    @Test
    fun computeStatusBarFrameBottom_negativeConfigured_returnsOriginal() {
        assertEquals(60, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(60, -1, true))
    }

    @Test
    fun computeStatusBarFrameBottom_zeroOriginalAndEnabled_applied() {
        assertEquals(100, SystemStatusBarInsetsHooks.computeStatusBarFrameBottom(0, 100, true))
    }
}
