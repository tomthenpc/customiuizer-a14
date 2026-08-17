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
    fun resizeWhenWindowGrewOrShrank() {
        assertFalse(StatusBarContentGeometry.shouldResizeOwner(0, 80))
        assertFalse(StatusBarContentGeometry.shouldResizeOwner(80, 0))
        assertFalse(StatusBarContentGeometry.shouldResizeOwner(80, 80))
        assertFalse(StatusBarContentGeometry.shouldResizeOwner(80, 81))
        assertTrue(StatusBarContentGeometry.shouldResizeOwner(120, 80))
        assertTrue(StatusBarContentGeometry.shouldResizeOwner(80, 120))
    }

    @Test
    fun matchParentFillingLeafIsNotOptical() {
        assertFalse(
            StatusBarContentGeometry.isOpticalLeaf(100, 100, StatusBarContentGeometry.MATCH_PARENT),
        )
        assertTrue(StatusBarContentGeometry.isOpticalLeaf(40, 100, 40))
        assertFalse(StatusBarContentGeometry.isOpticalLeaf(0, 100, 40))
    }

    @Test
    fun centeredContentHasZeroCorrection() {
        val correction = StatusBarContentGeometry.centerCorrectionPx(0, 100, 30, 70)
        assertEquals(0f, correction, 0.001f)
        assertEquals(
            0f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 100, 30, 70, 0f, 1f),
            0.001f,
        )
    }

    @Test
    fun topStuckContentMovesDownToParentCenter() {
        val correction = StatusBarContentGeometry.centerCorrectionPx(0, 120, 30, 70)
        assertEquals(10f, correction, 0.001f)
        assertEquals(
            10f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 120, 30, 70, 0f, 1f),
            0.001f,
        )
    }

    @Test
    fun userOffsetAddsAfterCenterCorrection() {
        assertEquals(
            14f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 120, 30, 70, 4f, 1f),
            0.001f,
        )
        assertEquals(
            6f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 120, 30, 70, -4f, 1f),
            0.001f,
        )
    }

    @Test
    fun withinToleranceProducesZeroAutoCorrection() {
        assertTrue(StatusBarContentGeometry.isCenteredWithinTolerance(0.5f, 1f))
        assertEquals(
            0f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 100, 30, 71, 0f, 1f),
            0.001f,
        )
    }

    @Test
    fun unmeasuredBoundsDoNotProduceHugeTranslation() {
        assertEquals(
            0f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 0, 30, 70, 80f, 1f),
            0.001f,
        )
        assertEquals(
            40f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 80, Int.MAX_VALUE, Int.MIN_VALUE, 80f, 1f),
            0.001f,
        )
        assertEquals(
            -40f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 80, 10, 10, -80f, 1f),
            0.001f,
        )
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
        assertEquals(
            0f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 40, 0, 40, 10f, 1f),
            0.001f,
        )
    }

    @Test
    fun stockNativePathDoesNotAutoCenter() {
        assertEquals(
            0f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 100, 0, 40, 0f, 1f, false),
            0.001f,
        )
        assertEquals(
            4f,
            StatusBarContentGeometry.resolveContentsTranslationY(0, 100, 0, 40, 4f, 1f, false),
            0.001f,
        )
    }
}
