package tv.withaibuild.customiuizer.mods.utils.feature

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

class DynamicIslandWindowEnvelopeTest {

    @Test
    fun topEnvelope_hasExpectedGeometry() {
        val envelope = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyPx = 36,
            roundingSafetyPx = 2
        )

        assertEquals(StrongToastPosition.TOP, envelope.position)
        assertEquals(197, envelope.requiredHostHeightPx)
        assertEquals(18, envelope.shellTopMarginPx)
        assertEquals(0, envelope.shellBottomMarginPx)
        assertEquals(0, envelope.parentPaddingTopPx)
        assertEquals(38, envelope.parentPaddingBottomPx)
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, envelope.parentGravity)
        assertEquals(18, envelope.restingShellTopPx)
        assertEquals(159, envelope.restingShellBottomPx)
        assertTrue(envelope.entranceTranslationY < 0f)
        assertEquals(-32f, envelope.entranceTranslationY, 0.01f)
    }

    @Test
    fun bottomEnvelope_hasExpectedGeometry() {
        val envelope = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 141,
            topSafetyPx = 18,
            bottomPaddingPx = 90,
            roundingSafetyPx = 2
        )

        assertEquals(StrongToastPosition.BOTTOM, envelope.position)
        assertEquals(251, envelope.requiredHostHeightPx)
        assertEquals(0, envelope.shellTopMarginPx)
        assertEquals(90, envelope.shellBottomMarginPx)
        assertEquals(20, envelope.parentPaddingTopPx)
        assertEquals(0, envelope.parentPaddingBottomPx)
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, envelope.parentGravity)
        assertEquals(20, envelope.restingShellTopPx)
        assertEquals(161, envelope.restingShellBottomPx)
        assertTrue(envelope.entranceTranslationY > 0f)
        assertEquals(104f, envelope.entranceTranslationY, 0.01f)
    }

    @Test
    fun topFitsAllPhasesWithRounding() {
        val envelope = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyPx = 36,
            roundingSafetyPx = 2
        )

        assertTrue(
            "capsule must fit at every phase including scaled edge",
            envelope.fitsAllPhases(141, envelope.restingShellTopPx)
        )
    }

    @Test
    fun bottomFitsAllPhasesWithRounding() {
        val envelope = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 141,
            topSafetyPx = 18,
            bottomPaddingPx = 90,
            roundingSafetyPx = 2
        )

        assertTrue(
            "capsule must fit at every phase including scaled edge",
            envelope.fitsAllPhases(141, envelope.restingShellTopPx)
        )
    }

    @Test
    fun topRoundingMovesScaledStartInsideWindow() {
        val withRounding = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyPx = 36,
            roundingSafetyPx = 2
        )
        val withoutRounding = DynamicIslandWindowEnvelope.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyPx = 36,
            roundingSafetyPx = 0
        )

        val startTopWith = withRounding.restingShellTopPx +
            withRounding.entranceTranslationY +
            withRounding.pivotY * (1f - withRounding.edgeScaleY)
        val startTopWithout = withoutRounding.restingShellTopPx +
            withoutRounding.entranceTranslationY +
            withoutRounding.pivotY * (1f - withoutRounding.edgeScaleY)

        // With rounding, the scaled start top should sit at or above the rounding margin.
        assertTrue(startTopWith >= withRounding.roundingSafety())
        // Without rounding, the scaled start top sits at or below the rounding margin.
        assertTrue(startTopWithout < withRounding.roundingSafety())
    }

    @Test
    fun bottomRoundingMovesScaledStartInsideWindow() {
        val withRounding = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 141,
            topSafetyPx = 18,
            bottomPaddingPx = 90,
            roundingSafetyPx = 2
        )
        val withoutRounding = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 141,
            topSafetyPx = 18,
            bottomPaddingPx = 90,
            roundingSafetyPx = 0
        )

        val startBottomWith = withRounding.restingShellTopPx +
            withRounding.entranceTranslationY +
            withRounding.edgeScaleY * 141
        val startBottomWithout = withoutRounding.restingShellTopPx +
            withoutRounding.entranceTranslationY +
            withoutRounding.edgeScaleY * 141

        // With rounding, the scaled start bottom is clearly inside the host bottom.
        assertTrue(startBottomWith <= withRounding.requiredHostHeightPx - withRounding.roundingSafety())
        // Without rounding, the scaled start bottom is much closer to the host bottom.
        assertTrue(startBottomWithout >= withoutRounding.requiredHostHeightPx - 1f)
    }

    @Test
    fun bottomSmallBottomPaddingStillFits() {
        // Simulates a device with very small bottom safe inset/gap: the envelope must
        // still keep the scaled entrance start inside the host Window.
        val envelope = DynamicIslandWindowEnvelope.forBottom(
            visualHeightPx = 100,
            topSafetyPx = 4,
            bottomPaddingPx = 4,
            roundingSafetyPx = 2
        )

        assertTrue(envelope.fitsAllPhases(100, envelope.restingShellTopPx))
        assertTrue(envelope.entranceTranslationY >= 0f)
    }

    private fun DynamicIslandWindowEnvelope.roundingSafety(): Float {
        return roundingSafetyPx.toFloat()
    }
}
