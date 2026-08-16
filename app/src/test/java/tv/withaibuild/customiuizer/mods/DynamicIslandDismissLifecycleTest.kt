package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DynamicIslandDismissLifecycleTest {
    @Test
    fun romDetachOwnsSharedHostCleanup() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("detachDynamicIslandHost(root)"))
        assertTrue(source.contains("DynamicIslandHost.shared.detachImmediate"))
        assertTrue(source.contains("restoreStatusBarContents(root)"))
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
