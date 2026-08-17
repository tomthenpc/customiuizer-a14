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
    fun expandWhenWindowGrewPastInflatedView() {
        assertFalse(StatusBarContentGeometry.shouldExpandToWindow(0, 80))
        assertFalse(StatusBarContentGeometry.shouldExpandToWindow(80, 0))
        assertFalse(StatusBarContentGeometry.shouldExpandToWindow(80, 80))
        assertFalse(StatusBarContentGeometry.shouldExpandToWindow(80, 81))
        assertTrue(StatusBarContentGeometry.shouldExpandToWindow(120, 80))
    }

    @Test
    fun centeredContentHasZeroDelta() {
        val delta = StatusBarContentGeometry.centerDeltaPx(0, 80, 20, 60)
        assertEquals(0f, delta, 0.001f)
        assertTrue(StatusBarContentGeometry.isCenteredWithinTolerance(delta, 1f))
    }

    @Test
    fun topStuckContentIsAboveParentCenter() {
        val delta = StatusBarContentGeometry.centerDeltaPx(0, 80, 0, 40)
        assertEquals(-20f, delta, 0.001f)
        assertFalse(StatusBarContentGeometry.isCenteredWithinTolerance(delta, 2f))
    }

    @Test
    fun visualHeightFallsBackWhenUnmeasured() {
        assertEquals(80, StatusBarContentGeometry.visualHeightPx(Int.MAX_VALUE, Int.MIN_VALUE, 80))
        assertEquals(80, StatusBarContentGeometry.visualHeightPx(10, 10, 80))
        assertEquals(24, StatusBarContentGeometry.visualHeightPx(8, 32, 80))
    }

    @Test
    fun requestedOffsetClampsToVisualSlack() {
        assertEquals(8f, StatusbarViewMaths.clampVerticalOffsetPx(20f, 40, 24), 0.001f)
        assertEquals(-8f, StatusbarViewMaths.clampVerticalOffsetPx(-20f, 40, 24), 0.001f)
        assertEquals(0f, StatusbarViewMaths.clampVerticalOffsetPx(8f, 40, 40), 0.001f)
    }

    @Test
    fun featureOffsetComposesAfterGlobalClamp() {
        val parent = 40
        val group = 24
        val global = StatusbarViewMaths.clampVerticalOffsetPx(20f, parent, group)
        val featureParent = 24
        val featureText = 16
        val feature = StatusbarViewMaths.clampVerticalOffsetPx(10f, featureParent, featureText)
        assertEquals(8f, global, 0.001f)
        assertEquals(4f, feature, 0.001f)
        assertEquals(12f, global + feature, 0.001f)
    }

    @Test
    fun dualRowGroupFillingParentHasNoGlobalSlack() {
        assertEquals(0f, StatusbarViewMaths.clampVerticalOffsetPx(10f, 40, 40), 0.001f)
    }
}
