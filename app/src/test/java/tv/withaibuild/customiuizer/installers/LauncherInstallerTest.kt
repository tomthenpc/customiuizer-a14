package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (isLauncherPkg) {",
            "if (isStatusBarColor)"
        )

        assertTrue(
            "ReflectionCache.onSafeLifecycle must be called at package-ready for launcher",
            section.contains("ReflectionCache.onSafeLifecycle(lpparam.getClassLoader());")
        )
        assertTrue(
            "MainModule must delegate launcher package-ready hooks to LauncherInstaller",
            section.contains("LauncherInstaller.install(lpparam, mPrefs);")
        )
        assertTrue(
            "MainModule must keep the preference bootstrap in the launcher branch",
            section.contains("initPrefs();")
        )
        assertTrue(
            "MainModule must delegate launcher post-attach hooks to LauncherInstaller",
            main.contains("if (isLauncherPkg) LauncherInstaller.handleLoadLauncher(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define the launcher post-attach handler",
            main.contains("private void handleLoadLauncher")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.java")

        assertTrue(
            "install method signature missing or changed",
            installer.contains("public static void install(PackageReadyParam lpparam, PrefMap mPrefs)")
        )
        assertTrue(
            "handleLoadLauncher method signature missing or changed",
            installer.contains("public static void handleLoadLauncher(PackageReadyParam lpparam, PrefMap mPrefs)")
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
