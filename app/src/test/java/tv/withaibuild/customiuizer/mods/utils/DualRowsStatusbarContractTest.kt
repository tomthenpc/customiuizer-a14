package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DualRowsStatusbarContractTest {

    @Test
    fun dualRowsStacksRowsVerticallyAndFillsParentHeight() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt")
        val dualRows = methodBody(source, "fun DualRowsStatusbarHook")

        assertTrue(
            "left row group must be vertical so weight-1 rows stack instead of sitting side by side",
            dualRows.contains("leftGroup.orientation = LinearLayout.VERTICAL"),
        )
        assertTrue(
            "right column must stay vertical",
            dualRows.contains("rightLayout.orientation = LinearLayout.VERTICAL"),
        )
        assertTrue(
            "dual-row columns must fill the status-bar contents height",
            dualRows.contains("LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)"),
        )
        assertTrue(
            "custom text icons in the second row must use MATCH_PARENT height for shrink-to-fit",
            dualRows.contains("LinearLayout.LayoutParams(-2, ViewGroup.LayoutParams.MATCH_PARENT)"),
        )
        assertTrue("generation guard must remain", dualRows.contains("dualRowsLayoutAdded"))
        assertFalse(
            "system icons must not be scaled to fit a short dual-row",
            dualRows.contains("scaleX") || dualRows.contains("scaleY"),
        )
        assertFalse(
            "dual-row layout must not translate the whole MiuiPhoneStatusBarView",
            dualRows.contains("translationY"),
        )
    }

    @Test
    fun contentGeometryStaysOnViewLayer() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/StatusBarContentGeometryHooks.kt")
        assertTrue(source.contains("status_bar_contents"))
        assertTrue(source.contains("contents.translationY"))
        assertFalse(source.contains("import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks"))
        assertFalse(source.contains("statusBarView.translationY"))
        assertFalse(source.contains("setFrame"))
        assertFalse(source.contains("InsetsSourceControl"))
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate.readText()
            val parent = directory.parentFile
            if (parent == null || parent == directory) break
            directory = parent
        }
        throw IllegalStateException("Could not find $relativePath")
    }

    private fun methodBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) throw IllegalStateException("Missing $signature")
        val brace = source.indexOf('{', start)
        var depth = 0
        for (i in brace until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(brace, i + 1)
                }
            }
        }
        throw IllegalStateException("Unbalanced braces for $signature")
    }
}
