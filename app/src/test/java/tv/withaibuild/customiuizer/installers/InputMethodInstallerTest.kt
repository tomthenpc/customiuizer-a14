package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMethodInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.baidu.input\")",
            "if (mPrefs.getInt(\"system_statusbarheight\", 11) > 11)"
        )

        assertTrue(
            "MainModule must keep the input method package filter",
            section.contains("pkg.equals(\"com.baidu.input\")")
        )
        assertTrue(
            "MainModule must keep the input method package filter",
            section.contains("pkg.startsWith(\"com.google.android.inputmethod\")")
        )
        assertTrue(
            "MainModule must delegate input method hooks to InputMethodInstaller",
            section.contains("InputMethodInstaller.install(lpparam, mPrefs);")
        )
        assertTrue(
            "MainModule must keep the onPackageReady diagnostic summary",
            section.contains("HookDiagnostics.printSummaryForStage(\"onPackageReady\");")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/InputMethodInstaller.java")

        assertTrue(
            "install method signature missing or changed",
            installer.contains("public static void install(PackageReadyParam lpparam, PrefMap mPrefs)")
        )
        assertFalse(
            "installer must not reference legacy Xposed package",
            installer.contains("de.robv.android.xposed")
        )
        assertFalse(
            "installer must not use legacy XC_LoadPackage",
            installer.contains("XC_LoadPackage")
        )
    }

    @Test
    fun installerPreservesHookConditions() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/InputMethodInstaller.java")

        assertTrue(
            "VolumeCursorHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"controls_volumecursor\")")
        )
        assertTrue(
            "FixInputMethodBottomMarginHook conditions must be preserved",
            installer.contains("mPrefs.getBoolean(\"controls_nonavbar_fix_inputmethod\")")
                && installer.contains("mPrefs.getBoolean(\"controls_nonavbar\")")
        )
        assertTrue(
            "GboardPaddingHook package and conditions must be preserved",
            installer.contains("pkg.startsWith(\"com.google.android.inputmethod\")")
                && installer.contains("mPrefs.getInt(\"various_gboardpadding_port\", 0)")
                && installer.contains("mPrefs.getInt(\"various_gboardpadding_land\", 0)")
        )
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return substring(startIndex, endIndex)
    }
}
