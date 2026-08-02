package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemUiInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (scope == ProcessScope.SYSTEM_UI) {",
            "if (scope == ProcessScope.GUARD_PROVIDER) {"
        )

        assertTrue(
            "MainModule must delegate SystemUI bootstrap to SystemUiBootstrapCoordinator",
            section.contains("SystemUiBootstrapCoordinator.install(lpparam, mPrefs, this::initPrefs);")
        )
    }

    @Test
    fun coordinatorOwnsSystemUiLifecycle() {
        val coordinator = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt")

        assertTrue(
            "SystemUiBootstrapCoordinator must install a hook on SystemUIInitializer.init",
            coordinator.contains("com.android.systemui.SystemUIInitializer") &&
                coordinator.contains("\"init\"")
        )
        assertTrue(
            "SystemUiBootstrapCoordinator must initialize ReflectionCache",
            coordinator.contains("ReflectionCache.onSafeLifecycle")
        )
        assertTrue(
            "10-second restart guard must live in SystemUiBootstrapCoordinator",
            coordinator.contains("currentTime - restartTime < restartThresholdMs")
        )
        assertTrue(
            "SystemUiBootstrapCoordinator must delegate non-essential hooks to SystemUiInstaller",
            coordinator.contains("SystemUiInstaller.install(lpparam, mPrefs)")
        )
        assertTrue(
            "SystemUiBootstrapCoordinator must call FatalErrors.rethrowIfFatal in catch(Throwable)",
            coordinator.contains("FatalErrors.rethrowIfFatal(")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.java")

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
