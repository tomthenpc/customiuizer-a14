package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericAppInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "final boolean isLauncherPkg = pkg.equals(\"com.miui.home\");",
            "HookDiagnostics.printSummaryForStage(\"onPackageReady\");"
        )

        assertTrue(
            "MainModule must compute the four post-attach booleans",
            section.contains("final boolean isLauncherPkg")
                && section.contains("final boolean isStatusBarColor")
                && section.contains("final boolean isNoOverscroll")
                && section.contains("final boolean controlMedia")
        )
        assertTrue(
            "MainModule must delegate the post-attach hook to GenericAppInstaller",
            section.contains("GenericAppInstaller.installPostAttach(lpparam, mPrefs, isLauncherPkg, isStatusBarColor, isNoOverscroll, controlMedia)")
        )
        assertFalse(
            "MainModule must no longer register the Application.attach hook directly",
            section.contains("ModuleHelper.findAndHookMethod(Application.class")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.java")

        assertTrue(
            "installPostAttach method signature missing or changed",
            installer.contains("public static void installPostAttach(PackageReadyParam lpparam, PrefMap mPrefs, boolean isLauncherPkg, boolean isStatusBarColor, boolean isNoOverscroll, boolean controlMedia)")
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
    fun installerInstallsPostAttachHooks() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.java")

        assertTrue(
            "Application.attach hook must be installed",
            installer.contains("Application.class")
                && installer.contains("\"attach\"")
                && installer.contains("Context.class")
        )
        assertTrue(
            "Launcher post-attach hooks must be installed for launcher package",
            installer.contains("if (isLauncherPkg)")
                && installer.contains("LauncherInstaller.handleLoadLauncher")
        )
        assertTrue(
            "Status-bar color hooks must be installed",
            installer.contains("if (isStatusBarColor)")
                && installer.contains("StatusBarBackgroundCompatHook")
                && installer.contains("StatusBarBackgroundHook")
        )
        assertTrue(
            "No-overscroll hook must be installed",
            installer.contains("if (isNoOverscroll)")
                && installer.contains("NoOverscrollAppHook")
        )
        assertTrue(
            "Media player volume hook must be installed",
            installer.contains("if (controlMedia)")
                && installer.contains("VolumeMediaPlayerHook")
        )
        assertTrue(
            "post-attach summary must be printed",
            installer.contains("HookDiagnostics.printSummaryForStage(\"post-attach\")")
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
