package tv.withaibuild.customiuizer.mods

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
            "restore must remove the shell from the parent",
            body.contains("parent.removeView(shell)")
        )
        assertTrue(
            "restore must add content back at the original index",
            body.contains("parent.addView(content, index)")
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
            "prepare must hide the ROM forehead bottom view",
            body.contains("findViewBySystemUiId(root, FOREHEAD_BOTTOM_ID)?.visibility = View.GONE")
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
}
