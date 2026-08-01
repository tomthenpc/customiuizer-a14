package tv.withaibuild.customiuizer.mods

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemUiViewLifecycleTest {

    @Test
    fun percentageOverlayDoesNotKeepStrongProcessLifetimeView() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")

        assertFalse(source.contains("@SuppressLint(\"StaticFieldLeak\")\n    private var mPct"))
        assertTrue(source.contains("private var mPctRef: WeakReference<TextView>? = null"))
        assertTrue(source.contains("if (mPctRef?.get() === mPctText) mPctRef = null"))
        assertTrue(source.contains("val pct = mPct ?: return"))
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
