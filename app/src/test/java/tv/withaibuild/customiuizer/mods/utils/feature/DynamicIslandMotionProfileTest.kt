package tv.withaibuild.customiuizer.mods.utils.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition

class DynamicIslandMotionProfileTest {

    @Test
    fun topProfile_entranceScaleIsSoftAndLessThanOne() {
        val profile = DynamicIslandMotionProfile.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyMarginPx = 36,
            statusBarInsetPx = 82
        )

        assertEquals(StrongToastPosition.TOP, profile.position)
        assertTrue(profile.entranceScaleY < 1f)
        assertEquals(profile.entranceScaleY, profile.exitScaleY, 0.0001f)
        assertTrue(profile.entranceTranslationY < 0f)
        assertEquals(profile.entranceTranslationY, profile.exitTranslationY, 0.0001f)
        assertEquals(0f, profile.restingTranslationY, 0.0001f)
    }

    @Test
    fun topProfile_capsuleFitsWindowAtAllPhases() {
        val profile = DynamicIslandMotionProfile.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyMarginPx = 36,
            statusBarInsetPx = 82
        )

        assertTrue(
            "full transformed capsule must stay inside Window surface",
            profile.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141)
        )
    }

    @Test
    fun bottomProfile_entranceScaleIsSoftAndLessThanOne() {
        val profile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topSafetyMarginPx = 18,
            bottomPaddingPx = 90
        )

        assertEquals(StrongToastPosition.BOTTOM, profile.position)
        assertTrue(profile.entranceScaleY < 1f)
        assertEquals(profile.entranceScaleY, profile.exitScaleY, 0.0001f)
        assertTrue(profile.entranceTranslationY > 0f)
        assertEquals(profile.entranceTranslationY, profile.exitTranslationY, 0.0001f)
        assertEquals(0f, profile.restingTranslationY, 0.0001f)
    }

    @Test
    fun bottomProfile_capsuleFitsWindowAtAllPhases() {
        val profile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topSafetyMarginPx = 18,
            bottomPaddingPx = 90
        )

        assertTrue(
            "full transformed capsule must stay inside Window surface",
            profile.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141)
        )
    }

    @Test
    fun profileWindowHeight_matchesPublicHelpers() {
        val topProfile = DynamicIslandMotionProfile.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyMarginPx = 36,
            statusBarInsetPx = 82
        )
        // The helper uses maxOf(statusBarInset, visual + top + bottom).
        assertEquals(195, topProfile.windowHeightPx)

        val bottomProfile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topSafetyMarginPx = 18,
            bottomPaddingPx = 90
        )
        assertEquals(249, bottomProfile.windowHeightPx)
    }

    @Test
    fun topMaxDrag_isNegativeAndMatchesEntranceTravel() {
        val profile = DynamicIslandMotionProfile.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyMarginPx = 36,
            statusBarInsetPx = 82
        )

        assertEquals(profile.entranceTranslationY, profile.maxDragTranslationY, 0.0001f)
        assertTrue(profile.maxDragTranslationY < 0f)
    }

    @Test
    fun bottomMaxDrag_isPositiveAndMatchesEntranceTravel() {
        val profile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topSafetyMarginPx = 18,
            bottomPaddingPx = 90
        )

        assertEquals(profile.entranceTranslationY, profile.maxDragTranslationY, 0.0001f)
        assertTrue(profile.maxDragTranslationY > 0f)
    }

    @Test
    fun noOvershootScaleY() {
        val top = DynamicIslandMotionProfile.forTop(
            141, 18, 36, 82
        )
        val bottom = DynamicIslandMotionProfile.forBottom(
            141, 18, 90
        )

        assertTrue("entrance scale must not overshoot", top.entranceScaleY <= 1f)
        assertTrue("entrance scale must not overshoot", bottom.entranceScaleY <= 1f)
        assertTrue("exit scale must not overshoot", top.exitScaleY <= 1f)
        assertTrue("exit scale must not overshoot", bottom.exitScaleY <= 1f)
    }
}
