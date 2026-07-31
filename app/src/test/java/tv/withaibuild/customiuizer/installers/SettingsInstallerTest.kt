package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.android.settings\"))",
            "if (pkg.equals(\"com.miui.packageinstaller\"))"
        )

        assertTrue(
            "MainModule must keep the Settings package filter",
            section.contains("pkg.equals(\"com.android.settings\")")
        )
        assertTrue(
            "MainModule must delegate Settings hooks to SettingsInstaller",
            section.contains("SettingsInstaller.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define Settings hook conditions",
            section.contains("GlobalActions.miuizerSettingsHook")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SettingsInstaller.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SettingsInstaller.java")

        assertTrue(
            "miuizerSettingsHook condition must be preserved",
            installer.contains("mPrefs.getStringAsInt(\"miuizer_settingsiconpos\", 1)")
                && installer.contains("GlobalActions.miuizerSettingsHook")
        )
        assertTrue(
            "DisableAnyNotificationHook conditions must be preserved",
            installer.contains("mPrefs.getBoolean(\"system_disableanynotif\")")
                && installer.contains("SystemNotificationHooks.DisableAnyNotificationHook")
                && installer.contains("SystemNotificationHooks.DisableAnyNotificationBlockHook")
        )
        assertTrue(
            "NotificationImportanceHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"system_notifimportance\")")
                && installer.contains("SystemNotificationHooks.NotificationImportanceHook")
        )
        assertTrue(
            "ViewWifiPasswordHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"system_wifipassword\")")
                && installer.contains("System.ViewWifiPasswordHook")
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
