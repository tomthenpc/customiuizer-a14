package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for the Xposed scope list.
 *
 * The scope list defines which processes the module is allowed to load into.  A mistake here
 * (duplicate package, missing core target, stray blank line) is easy to introduce when adding a
 * new process and can prevent the module from loading or cause it to load where it should not.
 */
class ScopeListTest {

    private val scopeFile = locateScopeFile()

    @Test
    fun scopeList_isNotEmpty() {
        val lines = scopeFile.readLines()
        assertTrue("scope.list must not be empty", lines.isNotEmpty())
    }

    @Test
    fun scopeList_hasNoDuplicates() {
        val lines = scopeFile.readLines().filter { it.isNotBlank() }
        val unique = lines.toSet()
        assertEquals("scope.list must not contain duplicate package names", unique.size, lines.size)
    }

    @Test
    fun scopeList_hasNoBlankLines() {
        for ((index, line) in scopeFile.readLines().withIndex()) {
            assertFalse("scope.list contains a blank line at ${index + 1}", line.isBlank())
        }
    }

    @Test
    fun scopeList_containsCoreTargets() {
        val lines = scopeFile.readLines().toSet()
        val required = listOf(
            "system",
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.miui.home",
        )
        for (pkg in required) {
            assertTrue("scope.list must contain $pkg", pkg in lines)
        }
    }

    private fun locateScopeFile(): File {
        val start = System.getProperty("user.dir") ?: "."
        var dir = File(start).absoluteFile
        while (true) {
            val candidate = File(dir, "app/src/main/resources/META-INF/xposed/scope.list")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: error("Repository root not found")
        }
    }
}
