package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerKeeperInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.miui.powerkeeper\"))",
            "if (pkg.equals(\"com.android.settings\"))"
        )

        assertTrue(
            "MainModule must keep the Power Keeper package filter",
            section.contains("pkg.equals(\"com.miui.powerkeeper\")")
        )
        assertTrue(
            "MainModule must delegate Power Keeper hooks to PowerKeeperInstaller",
            section.contains("PowerKeeperInstaller.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define Power Keeper hook conditions",
            section.contains("Various.AppsRestrictPowerHook")
        )
        assertFalse(
            "MainModule must no longer define Power Keeper hook conditions",
            section.contains("Various.PersistBatteryOptimizationHook")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/PowerKeeperInstaller.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/PowerKeeperInstaller.java")

        assertTrue(
            "AppsRestrictPowerHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_restrictapp\")")
                && installer.contains("Various.AppsRestrictPowerHook")
        )
        assertTrue(
            "PersistBatteryOptimizationHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_persist_batteryoptimization\")")
                && installer.contains("Various.PersistBatteryOptimizationHook")
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
