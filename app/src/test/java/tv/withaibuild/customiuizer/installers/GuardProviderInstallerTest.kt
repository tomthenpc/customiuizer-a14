package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardProviderInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.miui.guardprovider\"))",
            "if (pkg.equals(\"com.android.incallui\"))"
        )

        assertTrue(
            "MainModule must keep the Guard Provider package filter",
            section.contains("pkg.equals(\"com.miui.guardprovider\")")
        )
        assertTrue(
            "MainModule must delegate Guard Provider hooks to GuardProviderInstaller",
            section.contains("GuardProviderInstaller.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define Guard Provider hook conditions",
            section.contains("Various.DisableDefraudAppsCheck")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/GuardProviderInstaller.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/GuardProviderInstaller.java")

        assertTrue(
            "DisableDefraudAppsCheck condition, DexKit bridge lifecycle and process must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_disable_defraud_apps_detect\")")
                && installer.contains("MainModule.loadDexKit()")
                && installer.contains("XposedHelpers.createBridge(lpparam.getApplicationInfo().sourceDir)")
                && installer.contains("Various.DisableDefraudAppsCheck(lpparam)")
                && installer.contains("XposedHelpers.closeBridge()")
                && installer.contains("XposedHelpers.log(t)")
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
