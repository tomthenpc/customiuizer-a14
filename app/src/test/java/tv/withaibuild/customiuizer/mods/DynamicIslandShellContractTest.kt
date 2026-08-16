package tv.withaibuild.customiuizer.mods

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Paths

/**
 * Source contract for the Dynamic Island shell architecture.
 *
 * The unit-test android.jar does not fully run ViewGroup reparenting, so the
 * assertions here verify the production body directly: a module-owned
 * [tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandCapsuleView]
 * shell is inserted around the ROM content, the shell owns the shape and
 * transforms, the ROM RoundRect is suppressed via alpha, and the hierarchy is
 * restored on teardown.
 */
class DynamicIslandShellContractTest {

    companion object {

        private fun productionSource(): String {
            val relative = Paths.get(
                "app",
                "src",
                "main",
                "java",
                "tv",
                "withaibuild",
                "customiuizer",
                "mods",
                "SystemUIStrongToastHooks.kt"
            )
            val current = Paths.get("").toAbsolutePath()
            val candidates = listOf(
                current.resolve(relative),
                current.parent?.resolve(relative)
            )
            val path = candidates.filterNotNull().firstOrNull { it.toFile().exists() }
                ?: throw java.io.FileNotFoundException(
                    "SystemUIStrongToastHooks.kt not found in any candidate: $candidates"
                )
            return path.toFile().readText(Charsets.UTF_8)
        }

        private fun functionBody(source: String, functionName: String): String {
            val start = source.indexOf("fun $functionName(")
            if (start == -1) fail("$functionName must exist")
            val brace = source.indexOf("{", start)
            if (brace == -1) fail("$functionName must have a body")

            var depth = 0
            for (i in brace until source.length) {
                val c = source[i]
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
            fail("$functionName body is not well-formed")
            return "" // unreachable
        }
    }

    @Test
    fun dynamicIslandShellState_capturesAllBaselines() {
        val source = productionSource()
        val body = source.substring(source.indexOf("data class DynamicIslandShellState"))

        assertTrue(
            "state must capture the content background",
            body.contains("val originalContentBackground: Drawable?")
        )
        assertTrue(
            "state must capture the content layout params",
            body.contains("val originalContentLayoutParams: ViewGroup.LayoutParams?")
        )
        assertTrue(
            "state must capture the original content clipToOutline",
            body.contains("val originalContentClipToOutline: Boolean")
        )
        assertTrue(
            "state must capture the original parent padding",
            body.containsAll(listOf(
                "val originalParentPaddingLeft: Int",
                "val originalParentPaddingTop: Int",
                "val originalParentPaddingRight: Int",
                "val originalParentPaddingBottom: Int"
            ))
        )
        assertTrue(
            "state must capture the original parent gravity",
            body.contains("val originalParentGravity: Int")
        )
        assertTrue(
            "state must capture the bottom view reference and visibility",
            body.containsAll(listOf(
                "val bottomView: View?",
                "val bottomViewOriginalVisibility: Int"
            ))
        )
        assertTrue(
            "state must capture the ROM RoundRect and its original alpha/visibility",
            body.containsAll(listOf(
                "val romRoundRect: View?",
                "val romRoundRectOriginalAlpha: Float",
                "val romRoundRectOriginalVisibility: Int"
            ))
        )
        assertFalse(
            "ancestor clip baselines must not remain in the shell state",
            body.contains("ancestorClipBaselines")
        )
    }

    @Test
    fun bindDynamicIslandShell_wrapsContentInCapsuleViewShell() {
        val source = productionSource()
        val body = functionBody(source, "bindDynamicIslandShell")

        assertTrue(
            "bind must create a module-owned DynamicIslandCapsuleView shell",
            body.contains("DynamicIslandCapsuleView(root.context)")
        )
        assertFalse(
            "bind must not use a plain FrameLayout shell",
            body.contains("FrameLayout(root.context)")
        )
        assertTrue(
            "bind must remove content from the original parent",
            body.contains("parent.removeView(content)")
        )
        assertTrue(
            "bind must insert the shell at the original content index",
            body.contains("parent.addView(shell, originalIndex)")
        )
        assertTrue(
            "bind must make the original content fill the shell",
            body.contains("shell.addView(content)")
        )
        assertTrue(
            "bind must save the original content background",
            body.contains("val originalBackground = content.background")
        )
        assertTrue(
            "bind must hide the original content background so the shell draws",
            body.contains("content.background = null")
        )
        assertTrue(
            "bind must store a stable shell state on the root",
            body.contains("SHELL_STATE_FIELD")
        )
    }

    @Test
    fun bindDynamicIslandShell_isIdempotentOnSameContent() {
        val source = functionBody(productionSource(), "bindDynamicIslandShell")

        assertTrue(
            "bind must check the existing state before creating a new shell",
            source.contains("val existing = XposedHelpers.getAdditionalInstanceField(")
        )
        assertTrue(
            "bind must return the existing shell when the state is still valid",
            source.contains("return existing.shell")
        )
        assertTrue(
            "bind must tear down a stale state before creating a new one",
            source.contains("restoreDynamicIslandShell(existing)")
        )
    }

    @Test
    fun bindDynamicIslandShell_hasTransactionRollback() {
        val body = functionBody(productionSource(), "bindDynamicIslandShell")

        assertTrue("bind must wrap reparent mutations in a single try block", body.contains("try {"))
        assertTrue(
            "bind must restore the original background on rollback",
            body.contains("content.background = originalBackground")
        )
        assertTrue(
            "bind must rethrow fatal errors",
            body.contains("FatalErrors.unwrapAndRethrowIfFatal(t)")
        )
    }

    @Test
    fun restoreDynamicIslandShell_returnsContentAndRemovesShell() {
        val source = productionSource()
        val body = functionBody(source, "restoreDynamicIslandShell")

        assertTrue("restore must cancel shell animation", body.contains("shell.animate().cancel()"))
        assertTrue("restore must reset module transforms on the shell", body.contains("resetDynamicIslandTransform(shell)"))
        assertTrue("restore must remove content from the shell", body.contains("shell.removeView(content)"))
        assertTrue(
            "restore must put the original background back",
            body.contains("content.background = state.originalContentBackground")
        )
        assertTrue("restore must remove the shell from the parent", body.contains("parent.removeView(shell)"))
        assertTrue("restore must add content back at the original index", body.contains("parent.addView(content, index)"))
    }

    @Test
    fun restoreDynamicIslandShell_restoresBottomViewAndRoundRect() {
        val body = functionBody(productionSource(), "restoreDynamicIslandShell")

        assertTrue(
            "restore must restore the exact bottom view visibility",
            body.contains("bottom.visibility = state.bottomViewOriginalVisibility")
        )
        assertTrue(
            "restore must delegate RoundRect restoration",
            body.contains("restoreRomRoundRect(state)")
        )
        assertFalse(
            "restore must not loop over ancestor clip baselines",
            body.contains("ancestorClipBaselines")
        )
    }

    @Test
    fun suppressRomRoundRect_usesAlphaNotVisibilityOrParentHide() {
        val source = productionSource()
        val suppressBody = functionBody(source, "suppressRomRoundRect")
        val restoreBody = functionBody(source, "restoreRomRoundRect")

        assertTrue(
            "RoundRect must be suppressed via alpha = 0",
            suppressBody.contains("roundRect?.alpha = 0f")
        )
        assertFalse(
            "RoundRect must not be hidden with GONE/INVISIBLE",
            suppressBody.contains(".visibility = View.GONE") ||
                suppressBody.contains(".visibility = View.INVISIBLE")
        )
        assertTrue(
            "RoundRect alpha must be restored exactly",
            restoreBody.contains("roundRect.alpha = state.romRoundRectOriginalAlpha")
        )
        assertTrue(
            "RoundRect visibility must be restored exactly",
            restoreBody.contains("roundRect.visibility = state.romRoundRectOriginalVisibility")
        )
    }

    @Test
    fun prepareDynamicIslandCapsule_findsRoundRectAndDoesNotHidePadParent() {
        val body = functionBody(productionSource(), "prepareDynamicIslandCapsule")

        assertTrue(
            "prepare must locate the exact round_rect id",
            body.contains("findViewBySystemUiId(root, ROUND_RECT_ID)")
        )
        assertTrue(
            "prepare must capture RoundRect original alpha before suppression",
            body.contains("romRoundRectOriginalAlpha = roundRect?.alpha")
        )
        assertTrue(
            "prepare must call suppressRomRoundRect",
            body.contains("suppressRomRoundRect(roundRect)")
        )
        assertFalse(
            "prepare must not hide fl_pad_toast_bg",
            body.contains("fl_pad_toast_bg")
        )
        assertFalse(
            "prepare must not use GradientDrawable for the shell background",
            body.contains("GradientDrawable")
        )
        assertFalse(
            "prepare must not mutate ancestor clip flags",
            body.contains("captureAncestorClipBaselines") ||
                body.contains("applyDisabledAncestorClipping")
        )
        assertTrue(
            "prepare must hide the ROM forehead bottom view",
            body.contains("bottomView?.visibility = View.GONE")
        )
    }

    @Test
    fun animateDynamicIslandDismiss_usesSolidScaleExitWithoutAlphaFade() {
        val source = productionSource()
        val body = functionBody(source, "animateDynamicIslandDismiss")

        assertFalse(
            "exit must not define EXIT_ALPHA_FRACTION",
            source.contains("EXIT_ALPHA_FRACTION")
        )
        assertTrue(
            "dismiss must keep shell alpha at 1 before exit",
            body.contains("shell.alpha = 1f")
        )
        assertTrue(
            "dismiss must scale uniformly to zero",
            body.contains(".scaleX(profile.exitEndScale)") &&
                body.contains(".scaleY(profile.exitEndScale)")
        )
        assertFalse(
            "dismiss must not animate alpha to zero",
            body.contains(".alpha(0") || body.contains(".alpha(0f")
        )
        assertTrue(
            "dismiss must clear any stale update listener without driving alpha",
            body.contains("setUpdateListener(null)")
        )
        assertTrue(
            "dismiss must attach the completion to the animator",
            body.contains("withEndAction(complete)")
        )
        assertFalse(
            "dismiss must not call clearAll before the animation completes",
            body.contains("XposedHelpers.callMethod(strongToast, \"clearAll\")")
        )
    }

    @Test
    fun buildDynamicIslandDismissComplete_callsClearAll_thenOnComplete() {
        val source = productionSource()
        val body = functionBody(source, "buildDynamicIslandDismissComplete")

        val restoreIndex = body.indexOf("restoreStatusBarContents(strongToast)")
        val clearAll = body.indexOf("XposedHelpers.callMethod(strongToast, \"clearAll\")")
        val onComplete = body.indexOf("XposedHelpers.callMethod(strongToast, \"onComplete\")")

        assertTrue("complete must restore status bar contents", restoreIndex != -1)
        assertTrue("complete must call clearAll", clearAll != -1)
        assertTrue("complete must call onComplete", onComplete != -1)
        assertTrue("restore must occur before clearAll", restoreIndex < clearAll)
        assertTrue("clearAll must occur before onComplete", clearAll < onComplete)
    }

    @Test
    fun runAndStartDynamicIslandEntrance_targetShellAndDoNotOwnChildAlpha() {
        val source = productionSource()

        assertFalse(
            "Dynamic Island helpers that set child alpha must be removed",
            source.contains("prepareDynamicIslandContent(") ||
                source.contains("animateDynamicIslandContent(") ||
                source.contains("animateDynamicIslandContentOut(")
        )

        val startBody = functionBody(source, "startDynamicIslandEntrance")
        val runBody = functionBody(source, "runDynamicIslandEntrance")

        assertTrue(
            "start must bind the shell before animating",
            startBody.contains("prepareDynamicIslandCapsule(view, position, bottomOffsetDp)")
        )
        assertTrue(
            "run must target the shell with uniform scale",
            runBody.contains("shell.scaleX = profile.entranceStartScale") &&
                runBody.contains("shell.scaleY = profile.entranceStartScale")
        )
        assertFalse(
            "run must not use non-uniform entranceScaleY",
            runBody.contains("entranceScaleY")
        )
    }

    @Test
    fun onDetachedFromWindow_restoresHierarchyAndCleansState() {
        val source = productionSource()

        assertTrue("detach must restore the original Dynamic Island hierarchy", source.contains("restoreDynamicIslandShell(state)"))
        assertTrue(
            "detach must remove the shell state",
            source.contains("XposedHelpers.removeAdditionalInstanceField(strongToast, SHELL_STATE_FIELD)")
        )
        assertFalse(
            "detach must not restore ancestor clip baselines",
            source.contains("ancestorClipBaselines")
        )
    }

    @Test
    fun findDynamicIslandShell_validatesAttachmentAndOwnership() {
        val source = functionBody(productionSource(), "findDynamicIslandShell")

        assertTrue("find must read the shell state", source.contains("SHELL_STATE_FIELD"))
        assertTrue("find must require the shell to be attached", source.contains("isAttachedToWindow"))
        assertTrue(
            "find must require the content to still be inside the shell",
            source.contains("content.parent !== state.shell")
        )
    }

    private fun String.containsAll(substrings: List<String>): Boolean {
        return substrings.all { this.contains(it) }
    }
}
