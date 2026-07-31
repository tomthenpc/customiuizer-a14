package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.android.incallui\"))",
            "if (pkg.equals(\"com.miui.securitycenter\"))"
        )

        assertTrue(
            "MainModule must keep the Phone package filter",
            section.contains("pkg.equals(\"com.android.incallui\")")
        )
        assertTrue(
            "MainModule must delegate Phone hooks to PhoneInstaller",
            section.contains("PhoneInstaller.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define Phone hook conditions",
            section.contains("Various.ShowCallUIHook")
        )
        assertFalse(
            "MainModule must no longer define Phone hook conditions",
            section.contains("Various.InCallBrightnessHook")
        )
        assertFalse(
            "MainModule must no longer define Phone hook conditions",
            section.contains("Various.AnswerCallInHeadUpHook")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/PhoneInstaller.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/PhoneInstaller.java")

        assertTrue(
            "ShowCallUIHook condition must be preserved",
            installer.contains("mPrefs.getStringAsInt(\"various_showcallui\", 0)")
                && installer.contains("Various.ShowCallUIHook")
        )
        assertTrue(
            "InCallBrightnessHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_calluibright\")")
                && installer.contains("Various.InCallBrightnessHook")
        )
        assertTrue(
            "AnswerCallInHeadUpHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_answerinheadup\")")
                && installer.contains("Various.AnswerCallInHeadUpHook")
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
