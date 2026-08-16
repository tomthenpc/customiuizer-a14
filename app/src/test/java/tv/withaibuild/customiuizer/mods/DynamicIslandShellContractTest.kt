package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DynamicIslandShellContractTest {
    @Test
    fun dynamicIslandMovesContentToModuleOwnedSharedHost() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("DynamicIslandHost.shared.attach"))
        assertTrue(source.contains("capsule.addView("))
        assertTrue(source.contains("parent.removeView(content)"))
        assertTrue(source.contains("root.alpha = 0f"))
        assertTrue(source.contains("DynamicIslandHost.shared.detachImmediate"))
        assertTrue(source.contains("state.parent.addView(state.content"))
    }

    @Test
    fun dynamicIslandDoesNotUseRomStatusInsetAsHostGeometry() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        val hostAttach = functionBody(source, "attachDynamicIslandHost")
        assertFalse(hostAttach.contains("currentStatusBarInsetPx"))
        assertFalse(hostAttach.contains("WindowInsets.Type.statusBars"))
        assertTrue(hostAttach.contains("CAPSULE_TOP_MARGIN_DP"))
        assertTrue(hostAttach.contains("CAPSULE_BOTTOM_CLEARANCE_DP"))
    }

    @Test
    fun detachCleansHostAndRestoresStatusBarContents() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        val detach = functionBody(source, "detachDynamicIslandHost")
        assertTrue(detach.contains("DynamicIslandHost.shared.detachImmediate"))
        assertTrue(source.contains("restoreStatusBarContents(root)"))
        assertTrue(source.contains("removeAdditionalInstanceField(root, HOST_STATE_FIELD)"))
    }

    private fun functionBody(source: String, functionName: String): String {
        val start = source.indexOf("fun $functionName(")
        require(start >= 0)
        var depth = 0
        var opened = false
        for (index in start until source.length) {
            when (source[index]) {
                '{' -> { opened = true; depth++ }
                '}' -> if (opened && --depth == 0) return source.substring(start, index + 1)
            }
        }
        error("Unclosed function")
    }

    private fun source(path: String): String {
        var directory = File(java.lang.System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found")
        }
    }
}
