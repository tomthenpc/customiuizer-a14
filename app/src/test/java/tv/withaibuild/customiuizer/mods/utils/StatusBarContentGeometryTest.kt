package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarContentGeometryTest {

    @Test
    fun defaultRawValueIsZeroDp() {
        assertEquals(0f, StatusBarContentGeometry.resolveOffsetDp(StatusBarContentGeometry.RAW_DEFAULT), 0.001f)
        assertEquals(0f, StatusBarContentGeometry.resolveOffsetPx(20, 3f), 0.001f)
    }

    @Test
    fun rawStepsAreHalfDp() {
        assertEquals(-10f, StatusBarContentGeometry.resolveOffsetDp(0), 0.001f)
        assertEquals(10f, StatusBarContentGeometry.resolveOffsetDp(40), 0.001f)
        assertEquals(-0.5f, StatusBarContentGeometry.resolveOffsetDp(19), 0.001f)
        assertEquals(0.5f, StatusBarContentGeometry.resolveOffsetDp(21), 0.001f)
        assertEquals(-15f, StatusBarContentGeometry.resolveOffsetPx(10, 3f), 0.001f)
        assertEquals(15f, StatusBarContentGeometry.resolveOffsetPx(30, 3f), 0.001f)
    }

    @Test
    fun dualRowsMayFillWindow_singleRowDoesNot() {
        assertTrue(StatusBarContentGeometry.shouldFillWindowForDualRows(true, 129))
        assertFalse(StatusBarContentGeometry.shouldFillWindowForDualRows(false, 129))
        assertFalse(StatusBarContentGeometry.shouldFillWindowForDualRows(true, 0))
    }

    @Test
    fun singleRowCentersNativeBlockWhenWindowGrew() {
        assertTrue(StatusBarContentGeometry.shouldCenterNativeBlock(false, 129, 80))
        assertFalse(StatusBarContentGeometry.shouldCenterNativeBlock(false, 80, 80))
        assertFalse(StatusBarContentGeometry.shouldCenterNativeBlock(true, 129, 80))
        assertFalse(StatusBarContentGeometry.shouldCenterNativeBlock(false, 0, 80))
    }

    @Test
    fun zeroUserOffsetIsZeroTranslation() {
        assertEquals(0f, StatusBarContentGeometry.resolveUserTranslationY(129, 80, 0f), 0.001f)
        assertEquals(0f, StatusBarContentGeometry.resolveUserTranslationY(80, 80, 0f), 0.001f)
    }

    @Test
    fun userOffsetClampsToSlack() {
        assertEquals(8f, StatusBarContentGeometry.resolveUserTranslationY(40, 24, 20f), 0.001f)
        assertEquals(-8f, StatusBarContentGeometry.resolveUserTranslationY(40, 24, -20f), 0.001f)
        assertEquals(4f, StatusBarContentGeometry.resolveUserTranslationY(100, 40, 4f), 0.001f)
    }

    @Test
    fun unmeasuredParentDoesNotProduceHugeTranslation() {
        assertEquals(0f, StatusBarContentGeometry.resolveUserTranslationY(0, 80, 80f), 0.001f)
        assertEquals(0f, StatusBarContentGeometry.resolveUserTranslationY(-1, 80, 80f), 0.001f)
    }

    @Test
    fun fillingContentHasNoGlobalSlack() {
        assertEquals(0f, StatusBarContentGeometry.resolveUserTranslationY(40, 40, 10f), 0.001f)
    }

    @Test
    fun requestedOffsetClampsToVisualSlack() {
        assertEquals(8f, StatusbarViewMaths.clampVerticalOffsetPx(20f, 40, 24), 0.001f)
        assertEquals(-8f, StatusbarViewMaths.clampVerticalOffsetPx(-20f, 40, 24), 0.001f)
        assertEquals(0f, StatusbarViewMaths.clampVerticalOffsetPx(8f, 40, 40), 0.001f)
    }
}
