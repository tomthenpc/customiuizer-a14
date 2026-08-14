package tv.withaibuild.customiuizer.mods.utils.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
import java.io.File

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
            "each event must create a fresh SwipeGestureState that owns the capsule",
            startEntranceBody.contains("XposedHelpers.setAdditionalInstanceField(view, SWIPE_STATE_FIELD, SwipeGestureState(capsule))")
        )

        val entranceBody = extractFunctionBody(source, "private fun runDynamicIslandEntrance(")
        assertTrue(
            "entrance must resolve the profile once",
            entranceBody.contains("val profile = resolveDynamicIslandMotionProfile(view, capsule, position)")
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

        // Detach removes the event-owned state (capsule + profile) and clears listeners.
        val onDetachedBody = realHideBody.substring(realHideBody.indexOf("onDetachedFromWindow"))
        assertTrue(
            "detach must read the event-owned capsule when available",
            onDetachedBody.contains("swipeState?.capsule")
        )
        assertTrue(
            "detach must remove the swipe state that owns capsule and profile",
            onDetachedBody.contains("XposedHelpers.removeAdditionalInstanceField(strongToast, SWIPE_STATE_FIELD)")
        )
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
