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
 * FrameLayout shell is inserted around the ROM content, the shell owns the
 * transforms and background, the ROM content is restored on teardown, and the
 * dismiss completion runs clearAll before onComplete.
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
            "state must capture the ancestor clip baselines",
            body.contains("val ancestorClipBaselines: List<AncestorClipBaseline>")
        )
    }

    @Test
    fun ancestorClipBaseline_holdsOriginalFlags() {
        val source = productionSource()
        val body = source.substring(source.indexOf("data class AncestorClipBaseline"))

        assertTrue(
            "AncestorClipBaseline must hold the target view",
            body.contains("val view: ViewGroup")
        )
        assertTrue(
            "AncestorClipBaseline must hold the original clipChildren",
            body.contains("val originalClipChildren: Boolean")
        )
        assertTrue(
            "AncestorClipBaseline must hold the original clipToPadding",
            body.contains("val originalClipToPadding: Boolean")
        )
    }

    @Test
    fun bindDynamicIslandShell_wrapsContentInFrameLayoutShell() {
        val source = productionSource()
        val body = functionBody(source, "bindDynamicIslandShell")

        assertTrue(
            "bind must create a module-owned FrameLayout shell",
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
            "bind must save the original content clipToOutline",
            body.contains("val originalContentClipToOutline = content.clipToOutline")
        )
        assertTrue(
            "bind must hide the original content background so the shell draws",
            body.contains("content.background = null")
        )
        assertTrue(
            "bind must save the original content layout params",
            body.contains("val originalLp = content.layoutParams")
        )
        assertTrue(
            "bind must set content to fill the shell",
            body.contains("FrameLayout.LayoutParams(")
        )
        assertTrue(
            "bind must capture original parent padding",
            body.containsAll(listOf(
                "val originalParentPaddingLeft = parent.paddingLeft",
                "val originalParentPaddingTop = parent.paddingTop",
                "val originalParentPaddingRight = parent.paddingRight",
                "val originalParentPaddingBottom = parent.paddingBottom"
            ))
        )
        assertTrue(
            "bind must capture original parent gravity",
            body.contains("val originalParentGravity = (parent as? LinearLayout)?.gravity")
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
            "bind must compare the existing content by reference",
            source.contains("existing.content === content")
        )
        assertTrue(
            "bind must compare the existing shell parent by reference",
            source.contains("existing.shell.parent === existing.originalParent")
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

        assertTrue(
            "bind must wrap reparent mutations in a single try block",
            body.contains("try {")
        )
        assertTrue(
            "bind must detach content from the shell on rollback if it got that far",
            body.contains("if (content.parent === shell) {")
        )
        assertTrue(
            "bind must remove content from the shell before re-adding",
            body.contains("shell.removeView(content)")
        )
        assertTrue(
            "bind must re-add content to the original parent on rollback",
            body.contains("parent.addView(content, originalIndex)")
        )
        assertTrue(
            "bind must restore the original background on rollback",
            body.contains("content.background = originalBackground")
        )
        assertTrue(
            "bind must restore the original layout params on rollback",
            body.contains("content.layoutParams = originalLp")
        )
        assertTrue(
            "bind must restore the original clipToOutline on rollback",
            body.contains("content.clipToOutline = originalContentClipToOutline")
        )
        assertTrue(
            "bind must remove the orphan shell on rollback",
            body.contains("parent.removeView(shell)")
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

        assertTrue(
            "restore must cancel shell animation",
            body.contains("shell.animate().cancel()")
        )
        assertTrue(
            "restore must reset module transforms on the shell",
            body.contains("resetDynamicIslandTransform(shell)")
        )
        assertTrue(
            "restore must remove content from the shell",
            body.contains("shell.removeView(content)")
        )
        assertTrue(
            "restore must put the original background back",
            body.contains("content.background = state.originalContentBackground")
        )
        assertTrue(
            "restore must put the original layout params back",
            body.contains("content.layoutParams = state.originalContentLayoutParams")
        )
        assertTrue(
            "restore must put the original clipToOutline back",
            body.contains("content.clipToOutline = state.originalContentClipToOutline")
        )
        assertTrue(
            "restore must remove the shell from the parent",
            body.contains("parent.removeView(shell)")
        )
        assertTrue(
            "restore must add content back at the original index",
            body.contains("parent.addView(content, index)")
        )
    }

    @Test
    fun restoreDynamicIslandShell_restoresParentBaseline() {
        val body = functionBody(productionSource(), "restoreDynamicIslandShell")

        assertTrue(
            "restore must restore the exact original parent padding",
            body.contains(
                "parent.setPadding(\n" +
                "                state.originalParentPaddingLeft,\n" +
                "                state.originalParentPaddingTop,\n" +
                "                state.originalParentPaddingRight,\n" +
                "                state.originalParentPaddingBottom\n" +
                "            )"
            )
        )
        assertTrue(
            "restore must restore the exact original parent gravity",
            body.contains("(parent as? LinearLayout)?.gravity = state.originalParentGravity")
        )
    }

    @Test
    fun restoreDynamicIslandShell_restoresBottomViewAndAncestors() {
        val body = functionBody(productionSource(), "restoreDynamicIslandShell")

        assertTrue(
            "restore must not hard-code a View.VISIBLE",
            !body.contains("View.VISIBLE")
        )
        assertTrue(
            "restore must restore the exact bottom view visibility",
            body.contains("bottom.visibility = state.bottomViewOriginalVisibility")
        )
        assertTrue(
            "restore must loop over the ancestor clip baselines",
            body.contains("for (baseline in state.ancestorClipBaselines)")
        )
        assertTrue(
            "restore must restore each ancestor clipChildren",
            body.contains("baseline.view.clipChildren = baseline.originalClipChildren")
        )
        assertTrue(
            "restore must restore each ancestor clipToPadding",
            body.contains("baseline.view.clipToPadding = baseline.originalClipToPadding")
        )
    }

    @Test
    fun onDetachedFromWindow_restoresHierarchyAndCleansState() {
        // onDetachedFromWindow is a MethodHook override, not a top-level fun,
        // so the source contract searches the whole file for its unique pattern set.
        val source = productionSource()

        assertTrue(
            "detach must retrieve the shell state",
            source.contains("SHELL_STATE_FIELD")
        )
        assertTrue(
            "detach must cancel shell animation",
            source.contains("shell.animate().cancel()")
        )
        assertTrue(
            "detach must reset shell transforms",
            source.contains("resetDynamicIslandTransform(shell)")
        )
        assertTrue(
            "detach must remove swipe listeners",
            source.contains("setSwipeListenerRecursively(shell, null)")
        )
        assertTrue(
            "detach must remove the parent touch listener",
            source.contains("(shell.parent as? View)?.setOnTouchListener(null)")
        )
        assertTrue(
            "detach must remove the expanded touch region",
            source.contains("removeExpandedWindowTouchRegion(strongToast)")
        )
        assertTrue(
            "detach must restore status bar contents",
            source.contains("restoreStatusBarContents(strongToast)")
        )
        assertTrue(
            "detach must restore the original Dynamic Island hierarchy",
            source.contains("restoreDynamicIslandShell(state)")
        )
        assertTrue(
            "detach must remove the shell state",
            source.contains("XposedHelpers.removeAdditionalInstanceField(strongToast, SHELL_STATE_FIELD)")
        )
        assertTrue(
            "detach must remove the swipe state",
            source.contains("XposedHelpers.removeAdditionalInstanceField(strongToast, SWIPE_STATE_FIELD)")
        )
        assertTrue(
            "detach must remove the dismiss-running flag",
            source.contains("XposedHelpers.removeAdditionalInstanceField(strongToast, DISMISS_RUNNING_FIELD)")
        )
    }

    @Test
    fun prepareDynamicIslandCapsule_validatesDimensionsBeforeBind() {
        val body = functionBody(productionSource(), "prepareDynamicIslandCapsule")

        val widthIndex = body.indexOf("val visualWidthPx = strongToastDimensionPx(root, \"strong_toast_width\")")
        val heightIndex = body.indexOf("val visualHeightPx = strongToastVisualHeightPx(root)")
        val validationIndex = body.indexOf("if (visualWidthPx <= 0 || visualHeightPx <= 0)")
        val bindIndex = body.indexOf("bindDynamicIslandShell(root, content, position, bottomOffsetDp)")

        assertTrue("prepare must resolve width first", widthIndex != -1)
        assertTrue("prepare must resolve height first", heightIndex != -1)
        assertTrue("prepare must validate dimensions", validationIndex != -1)
        assertTrue("prepare must bind only after validation", bindIndex > validationIndex)
    }

    @Test
    fun prepareDynamicIslandCapsule_usesShellAndHidesBottomView() {
        val source = productionSource()
        val body = functionBody(source, "prepareDynamicIslandCapsule")

        assertTrue(
            "prepare must find the ROM content container",
            body.contains("findDynamicIslandCapsule(root)")
        )
        assertTrue(
            "prepare must bind the ROM content into a shell",
            body.contains("bindDynamicIslandShell(root, content, position, bottomOffsetDp)")
        )
        assertTrue(
            "prepare must give the shell the rounded pill background",
            body.contains("GradientDrawable()")
        )
        assertTrue(
            "prepare must set shell size from ROM dimensions",
            body.contains("strongToastVisualHeightPx(root)")
        )
        assertTrue(
            "prepare must capture the bottom view visibility before hiding",
            body.contains("val bottomViewOriginalVisibility = bottomView?.visibility")
        )
        assertTrue(
            "prepare must capture ancestor clip baselines without mutating",
            body.contains("captureAncestorClipBaselines(shell, root)")
        )
        assertTrue(
            "prepare must apply disabled ancestor clipping",
            body.contains("applyDisabledAncestorClipping(clipBaselines)")
        )
        assertTrue(
            "prepare must hide the ROM forehead bottom view",
            body.contains("bottomView?.visibility = View.GONE")
        )
    }

    @Test
    fun prepareDynamicIslandCapsule_publishesUpdatedStateBeforeMutations() {
        val body = functionBody(productionSource(), "prepareDynamicIslandCapsule")

        val bottomCaptureIndex = body.indexOf("val bottomViewOriginalVisibility = bottomView?.visibility")
        val clipCaptureIndex = body.indexOf("val clipBaselines = captureAncestorClipBaselines(shell, root)")
        val updatedStateIndex = body.indexOf("val updatedState = state.copy(")
        val publishIndex = body.indexOf("XposedHelpers.setAdditionalInstanceField(root, SHELL_STATE_FIELD, updatedState)")
        val parentGravityIndex = body.indexOf("(parent as? LinearLayout)?.apply")
        val applyClipIndex = body.indexOf("applyDisabledAncestorClipping(clipBaselines)")
        val bottomGoneIndex = body.indexOf("bottomView?.visibility = View.GONE")

        assertTrue("prepare must capture bottom visibility", bottomCaptureIndex != -1)
        assertTrue("prepare must capture clip baselines", clipCaptureIndex != -1)
        assertTrue("prepare must build the updated state", updatedStateIndex != -1)
        assertTrue("prepare must publish updated state", publishIndex != -1)
        assertTrue("bottom capture must precede state publication", bottomCaptureIndex < publishIndex)
        assertTrue("clip capture must precede state publication", clipCaptureIndex < publishIndex)
        assertTrue("state publication must precede parent mutation", publishIndex < parentGravityIndex)
        assertTrue("state publication must precede clip mutation", publishIndex < applyClipIndex)
        assertTrue("state publication must precede bottom view gone", publishIndex < bottomGoneIndex)
    }

    @Test
    fun prepareDynamicIslandCapsule_hasFailOpenRollback() {
        val body = functionBody(productionSource(), "prepareDynamicIslandCapsule")

        assertTrue(
            "prepare must wrap post-bind setup in try/catch",
            body.contains("return try {")
        )
        assertTrue(
            "prepare must look up the published state in catch",
            body.contains("XposedHelpers.getAdditionalInstanceField(") &&
                body.contains("SHELL_STATE_FIELD") &&
                body.contains("as? DynamicIslandShellState")
        )
        assertTrue(
            "prepare must restore from the published (or fallback) state",
            body.contains("restoreDynamicIslandShell(publishedState ?: state)")
        )
        assertTrue(
            "prepare must remove the shell state on failure",
            body.contains("XposedHelpers.removeAdditionalInstanceField(root, SHELL_STATE_FIELD)")
        )
        assertTrue(
            "prepare must return null on failure",
            body.contains("null")
        )
    }

    @Test
    fun prepareDynamicIslandCapsule_usesCapturedParentPadding() {
        val body = functionBody(productionSource(), "prepareDynamicIslandCapsule")

        assertTrue(
            "prepare must set parent padding using the captured left/right",
            body.contains("state.originalParentPaddingLeft") &&
                body.contains("state.originalParentPaddingRight")
        )
    }

    @Test
    fun captureAncestorClipBaselines_capturesWithoutMutating() {
        val body = functionBody(productionSource(), "captureAncestorClipBaselines")

        assertTrue(
            "capture must record original clipChildren",
            body.contains("originalClipChildren = ancestor.clipChildren")
        )
        assertTrue(
            "capture must record original clipToPadding",
            body.contains("originalClipToPadding = ancestor.clipToPadding")
        )
        assertTrue(
            "capture must return the baselines list",
            body.contains("return baselines")
        )
        assertFalse(
            "capture must not assign clipChildren",
            body.contains(".clipChildren = false")
        )
        assertFalse(
            "capture must not assign clipToPadding",
            body.contains(".clipToPadding = false")
        )
    }

    @Test
    fun applyDisabledAncestorClipping_mutatesFromBaselines() {
        val body = functionBody(productionSource(), "applyDisabledAncestorClipping")

        assertTrue(
            "apply must disable clipChildren",
            body.contains("baseline.view.clipChildren = false")
        )
        assertTrue(
            "apply must disable clipToPadding",
            body.contains("baseline.view.clipToPadding = false")
        )
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
            "start must store the shell in the swipe state",
            startBody.contains("SwipeGestureState(shell)")
        )
        assertTrue(
            "start must install swipe-to-dismiss on the shell",
            startBody.contains("installSwipeToDismiss(view, shell, position)")
        )
        assertFalse(
            "start must not call prepareDynamicIslandContent",
            startBody.contains("prepareDynamicIslandContent")
        )
        assertTrue(
            "run must target the shell",
            runBody.contains("shell.animate()")
        )
        assertTrue(
            "run must set the shell pivot",
            runBody.contains("shell.pivotY")
        )
        assertFalse(
            "run must not call animateDynamicIslandContent",
            runBody.contains("animateDynamicIslandContent")
        )
    }

    @Test
    fun animateDynamicIslandDismiss_usesShellAndDelaysClearAllUntilAfterExit() {
        val source = productionSource()
        val body = functionBody(source, "animateDynamicIslandDismiss")

        assertTrue(
            "dismiss must mark dismiss-running before starting exit",
            body.contains("setAdditionalInstanceField(strongToast, DISMISS_RUNNING_FIELD, true)")
        )
        assertTrue(
            "dismiss must remove swipe listeners from the shell",
            body.contains("setSwipeListenerRecursively(shell, null)")
        )
        assertTrue(
            "dismiss must remove the parent touch listener",
            body.contains("(shell.parent as? View)?.setOnTouchListener(null)")
        )
        assertTrue(
            "dismiss must run a shell exit ViewPropertyAnimator",
            body.contains("shell.animate()")
        )
        assertTrue(
            "dismiss must attach the completion to the animator",
            body.contains("withEndAction(complete)")
        )
        assertFalse(
            "dismiss must not call clearAll before the animation completes",
            body.contains("XposedHelpers.callMethod(strongToast, \"clearAll\")")
        )
        assertTrue(
            "dismiss completion must be built by the helper",
            body.contains("buildDynamicIslandDismissComplete(strongToast)")
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
    fun realHideStrongToast_findsExistingShellOrPreparesOneBeforeDismiss() {
        // realHideStrongToast is also a MethodHook override, so search the whole source.
        val source = productionSource()

        assertTrue(
            "realHideStrongToast must look for the existing shell",
            source.contains("findDynamicIslandShell(strongToast)")
        )
        assertTrue(
            "realHideStrongToast must fall back to preparing the shell",
            source.contains("prepareDynamicIslandCapsule(")
        )
        assertTrue(
            "realHideStrongToast must call the dismiss animation with the resolved shell",
            source.contains("strongToast,\n                        shell,\n                        snapshot.position")
        )
    }

    @Test
    fun installExpandedWindowTouchRegion_usesShellBounds() {
        val source = functionBody(productionSource(), "installExpandedWindowTouchRegion")

        assertTrue(
            "touch region must be computed from the shell",
            source.contains("val shell = findDynamicIslandShell(view) ?: return@guarded")
        )
        assertTrue(
            "touch region must use shell screen bounds",
            source.contains("shell.left") && source.contains("shell.right")
        )
    }

    @Test
    fun findDynamicIslandShell_validatesAttachmentAndOwnership() {
        val source = functionBody(productionSource(), "findDynamicIslandShell")

        assertTrue(
            "find must read the shell state",
            source.contains("SHELL_STATE_FIELD")
        )
        assertTrue(
            "find must require the shell to be attached",
            source.contains("isAttachedToWindow")
        )
        assertTrue(
            "find must require the content to still be inside the shell",
            source.contains("content.parent !== state.shell")
        )
        assertTrue(
            "find must require the shell to still be in the original parent",
            source.contains("state.shell.parent !== state.originalParent")
        )
    }

    private fun String.containsAll(substrings: List<String>): Boolean {
        return substrings.all { this.contains(it) }
    }
}
