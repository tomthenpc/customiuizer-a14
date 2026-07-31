package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInstallerRouterTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.miui.packageinstaller\"))",
            "if (pkg.equals(\"com.miui.screenshot\"))"
        )

        assertTrue(
            "MainModule must keep the Package Installer package filter",
            section.contains("pkg.equals(\"com.miui.packageinstaller\")")
        )
        assertTrue(
            "MainModule must delegate Package Installer hooks to PackageInstallerRouter",
            section.contains("PackageInstallerRouter.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define Package Installer hook conditions",
            section.contains("Various.MiuiPackageInstallerHook")
        )
        assertFalse(
            "MainModule must no longer define Package Installer hook conditions",
            section.contains("Various.AppInfoDuringMiuiInstallHook")
        )
        assertFalse(
            "MainModule must no longer define Package Installer hook conditions",
            section.contains("Various.PurePackageInstallerHook")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.java")

        assertTrue(
            "MiuiPackageInstallerHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_miuiinstaller\")")
                && installer.contains("Various.MiuiPackageInstallerHook")
        )
        assertTrue(
            "AppInfoDuringMiuiInstallHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_installappinfo\")")
                && installer.contains("Various.AppInfoDuringMiuiInstallHook")
        )
        assertTrue(
            "PurePackageInstallerHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_installer_purify\")")
                && installer.contains("Various.PurePackageInstallerHook")
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
