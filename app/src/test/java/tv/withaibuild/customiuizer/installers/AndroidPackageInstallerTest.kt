package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPackageInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"android\")) {",
            "if (pkg.equals(\"com.baidu.input\")"
        )

        assertTrue(
            "MainModule must keep the android package filter",
            section.contains("pkg.equals(\"android\")")
        )
        assertTrue(
            "MainModule must delegate android hooks to AndroidPackageInstaller",
            section.contains("AndroidPackageInstaller.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define the android hook conditions",
            section.contains("SystemShareMenuHooks.CleanShareMenuHook")
        )
        assertFalse(
            "MainModule must no longer define the all-rotations replacement",
            section.contains("MainModule.resHooks.setThemeValueReplacement")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/AndroidPackageInstaller.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/AndroidPackageInstaller.java")

        assertTrue(
            "CleanShareMenuHook condition and call must be preserved",
            installer.contains("mPrefs.getBoolean(\"system_cleanshare\")")
                && installer.contains("SystemShareMenuHooks.CleanShareMenuHook")
        )
        assertTrue(
            "CleanOpenWithMenuHook condition and call must be preserved",
            installer.contains("mPrefs.getBoolean(\"system_cleanopenwith\")")
                && installer.contains("SystemShareMenuHooks.CleanOpenWithMenuHook")
        )
        assertTrue(
            "all-rotations theme replacement condition and call must be preserved",
            installer.contains("mPrefs.getStringAsInt(\"system_allrotations2\", 1)")
                && installer.contains("MainModule.resHooks.setThemeValueReplacement")
                && installer.contains("config_allowAllRotations")
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
