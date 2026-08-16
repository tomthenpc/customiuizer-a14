package tv.withaibuild.customiuizer.mods.utils.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
import java.io.File

class DynamicIslandMotionProfileTest {

    @Test
    fun topProfile_entranceSettlesInFromASlightlySmallerPose() {
        val profile = DynamicIslandMotionProfile.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomClearancePx = 24,
            maxEdgeTravelPx = 55
        )

        assertEquals(StrongToastPosition.TOP, profile.position)
        assertTrue(profile.entranceStartScale < 1f)
        assertTrue(profile.entranceStartScale > 0f)
        assertTrue(profile.entranceStartTranslationY < 0f)
        assertEquals(1f, profile.restingScale, 0.0001f)
        assertEquals(0f, profile.restingTranslationY, 0.0001f)
    }

    @Test
    fun bottomProfile_entranceSettlesInFromASlightlySmallerPose() {
        val profile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topClearancePx = 18,
            bottomPaddingPx = 90,
            maxEdgeTravelPx = 55
        )

        assertEquals(StrongToastPosition.BOTTOM, profile.position)
        assertTrue(profile.entranceStartScale < 1f)
        assertTrue(profile.entranceStartTranslationY > 0f)
        assertEquals(1f, profile.restingScale, 0.0001f)
        assertEquals(0f, profile.restingTranslationY, 0.0001f)
    }

    @Test
    fun exitEndsAtZeroGeometryForBothPositions() {
        val top = DynamicIslandMotionProfile.forTop(141, 18, 24, 55)
        val bottom = DynamicIslandMotionProfile.forBottom(141, 18, 90, 55)

        // The island retracts to nothing instead of being cut away by the Window teardown.
        assertEquals(0f, top.exitEndScale, 0.0f)
        assertEquals(0f, bottom.exitEndScale, 0.0f)
        assertEquals(0f, DynamicIslandMotionProfile.EXIT_END_SCALE, 0.0f)

        // The exit retracts toward the same near screen edge the entrance came from.
        assertEquals(top.entranceStartTranslationY, top.exitEndTranslationY, 0.0001f)
        assertEquals(bottom.entranceStartTranslationY, bottom.exitEndTranslationY, 0.0001f)
    }

    @Test
    fun exitIsShorterThanEntrance() {
        val top = DynamicIslandMotionProfile.forTop(141, 18, 24, 55)
        val bottom = DynamicIslandMotionProfile.forBottom(141, 18, 90, 55)

        assertTrue(top.exitDurationMs < top.entranceDurationMs)
        assertTrue(bottom.exitDurationMs < bottom.entranceDurationMs)
    }

    @Test
    fun profileWindowHeightIsTheEnvelopeHostHeight() {
        val topProfile = DynamicIslandMotionProfile.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomClearancePx = 24,
            maxEdgeTravelPx = 55
        )
        assertEquals(183, topProfile.windowHeightPx)
        assertEquals(topProfile.windowEnvelope.requiredHostHeightPx, topProfile.windowHeightPx)

        val bottomProfile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topClearancePx = 18,
            bottomPaddingPx = 90,
            maxEdgeTravelPx = 55
        )
        assertEquals(249, bottomProfile.windowHeightPx)
        assertEquals(
            bottomProfile.windowEnvelope.requiredHostHeightPx,
            bottomProfile.windowHeightPx
        )
    }

    @Test
    fun capsuleFitsWindowAtAllPhases() {
        val top = DynamicIslandMotionProfile.forTop(141, 18, 24, 55)
        assertTrue(top.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141))

        val bottom = DynamicIslandMotionProfile.forBottom(141, 18, 90, 55)
        assertTrue(bottom.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141))
    }

    @Test
    fun dragScaleInterpolatesBetweenRestingAndEntranceStart() {
        val profile = DynamicIslandMotionProfile.forTop(141, 18, 24, 55)

        assertEquals(profile.restingScale, profile.scaleForProgress(0f), 0.0001f)
        assertEquals(profile.entranceStartScale, profile.scaleForProgress(1f), 0.0001f)
        // Out-of-range progress is clamped, never extrapolated.
        assertEquals(profile.restingScale, profile.scaleForProgress(-2f), 0.0001f)
        assertEquals(profile.entranceStartScale, profile.scaleForProgress(4f), 0.0001f)
    }

    @Test
    fun maxDragMatchesTheEnvelopeTravelDirection() {
        val top = DynamicIslandMotionProfile.forTop(141, 18, 24, 55)
        assertTrue(top.maxDragTranslationY < 0f)
        assertEquals(18, top.edgeTravelPx)

        val bottom = DynamicIslandMotionProfile.forBottom(141, 18, 90, 55)
        assertTrue(bottom.maxDragTranslationY > 0f)
        assertEquals(55, bottom.edgeTravelPx)
    }

    @Test
    fun eventStateOwnsCapsuleAndProfileAcrossTouchAndDismiss() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")

        // The event-owned SwipeGestureState owns the capsule and the prepared profile.
        assertTrue(
            "SwipeGestureState must declare a primary constructor that owns the capsule",
            source.contains("private class SwipeGestureState(")
        )
        assertTrue(
            "SwipeGestureState must own the capsule",
            source.contains("val capsule: View")
        )
        assertTrue(
            "SwipeGestureState must own the prepared profile",
            source.contains("var motionProfile: DynamicIslandMotionProfile? = null")
        )

        // Entrance resolves the capsule once, creates the event state, and binds the profile.
        val startEntranceBody = extractFunctionBody(source, "private fun startDynamicIslandEntrance(")
        assertTrue(
            "each event must create a fresh SwipeGestureState that owns the shell",
            startEntranceBody.contains("XposedHelpers.setAdditionalInstanceField(view, SWIPE_STATE_FIELD, SwipeGestureState(shell))")
        )

        val entranceBody = extractFunctionBody(source, "private fun runDynamicIslandEntrance(")
        assertTrue(
            "entrance must resolve the profile once",
            entranceBody.contains("val profile = resolveDynamicIslandMotionProfile(view, shell, position)")
        )
        assertTrue(
            "entrance must store the profile in the event-owned state",
            entranceBody.contains("state?.motionProfile = profile")
        )

        // MotionEvent hot path only reads state.capsule and state.motionProfile;
        // it must never re-discover or re-prepare anything.
        val touchBody = extractFunctionBody(source, "private fun handleDynamicIslandTouch(")
        assertTrue(
            "touch must read the capsule from the event state",
            touchBody.contains("val capsule = state.capsule")
        )
        assertFalse(
            "ACTION_MOVE must not call resolveDynamicIslandMotionProfile",
            touchBody.contains("resolveDynamicIslandMotionProfile")
        )
        assertFalse(
            "ACTION_MOVE must not call findDynamicIslandCapsule",
            touchBody.contains("findDynamicIslandCapsule")
        )
        assertFalse(
            "ACTION_MOVE must not call findViewBySystemUiId",
            touchBody.contains("findViewBySystemUiId")
        )
        assertTrue(
            "ACTION_MOVE must read state.motionProfile",
            touchBody.contains("val profile = state.motionProfile ?: return false")
        )
        assertTrue(
            "ACTION_UP must read state.motionProfile for restoration",
            touchBody.contains("val profile = state.motionProfile")
        )

        // Dismiss reuses the same event capsule and profile; a single cold fallback is allowed.
        val realHideBody = extractFunctionBody(source, "private fun installDynamicIslandMotion(")
        val realHideHook = realHideBody.substring(realHideBody.indexOf("\"realHideStrongToast\""))
        assertTrue(
            "dismiss must prefer the event-owned capsule",
            realHideHook.contains("swipeState?.capsule")
        )

        val dismissBody = extractFunctionBody(source, "private fun animateDynamicIslandDismiss(")
        assertTrue(
            "dismiss must read the event-owned profile first",
            dismissBody.contains("swipeState?.motionProfile")
        )
        assertTrue(
            "dismiss must keep a single cold fallback",
            dismissBody.contains("resolveDynamicIslandMotionProfile")
        )
        assertEquals(
            "resolveDynamicIslandMotionProfile must appear exactly once in dismiss (cold fallback)",
            1,
            dismissBody.split("resolveDynamicIslandMotionProfile").size - 1
        )

        // Detach restores the shell state and removes the event-owned state.
        val onDetachedBody = realHideBody.substring(realHideBody.indexOf("onDetachedFromWindow"))
        assertTrue(
            "detach must read the shell state that owns the capsule",
            onDetachedBody.contains("SHELL_STATE_FIELD")
        )
        assertTrue(
            "detach must restore the original Dynamic Island hierarchy",
            onDetachedBody.contains("restoreDynamicIslandShell(")
        )
        assertTrue(
            "detach must remove the swipe state that owns the motion profile",
            onDetachedBody.contains("XposedHelpers.removeAdditionalInstanceField(strongToast, SWIPE_STATE_FIELD)")
        )
    }

    @Test
    fun exitContract_hasNoAlphaFadeConstantOrAnimation() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        val motionSource = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/DynamicIslandMotionProfile.kt"
        )

        assertFalse(source.contains("EXIT_ALPHA_FRACTION"))
        assertFalse(motionSource.contains("EXIT_ALPHA"))
        assertFalse(motionSource.contains("exitAlpha"))
        assertEquals(0f, DynamicIslandMotionProfile.EXIT_END_SCALE, 0.0f)
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }

    private fun extractFunctionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "Signature not found: $signature" }
        var braceCount = 0
        var foundFirstBrace = false
        val sb = StringBuilder()
        for (i in start until source.length) {
            val c = source[i]
            if (c == '{') {
                foundFirstBrace = true
                braceCount++
            } else if (c == '}') {
                braceCount--
            }
            if (foundFirstBrace) {
                sb.append(c)
                if (braceCount == 0) break
            }
        }
        return sb.toString()
    }
}
