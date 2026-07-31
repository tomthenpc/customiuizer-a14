package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityCenterInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.miui.securitycenter\"))",
            "if (pkg.equals(\"com.miui.powerkeeper\"))"
        )

        assertTrue(
            "MainModule must keep the Security Center package filter",
            section.contains("pkg.equals(\"com.miui.securitycenter\")")
        )
        assertTrue(
            "MainModule must delegate Security Center hooks to SecurityCenterInstaller",
            section.contains("SecurityCenterInstaller.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define Security Center hook conditions",
            section.contains("Various.AppsRestrictHook")
        )
        assertFalse(
            "MainModule must no longer define Security Center hook conditions",
            section.contains("Various.SkipSecurityScanHook")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.java")

        assertTrue(
            "AppInfoHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_appdetails\")")
                && installer.contains("Various.AppInfoHook")
        )
        assertTrue(
            "AppsDisableHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_disableapp\")")
                && installer.contains("Various.AppsDisableHook")
        )
        assertTrue(
            "AppsRestrictHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_restrictapp\")")
                && installer.contains("Various.AppsRestrictHook")
        )
        assertTrue(
            "HideReportButtonHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_hide_report_ondetails\")")
                && installer.contains("Various.HideReportButtonHook")
        )
        assertTrue(
            "ScrambleAppLockPINHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"system_applock_scramblepin\")")
                && installer.contains("SystemLockScreenHooks.ScrambleAppLockPINHook")
        )
        assertTrue(
            "AppsDefaultSortHook condition must be preserved",
            installer.contains("mPrefs.getStringAsInt(\"various_appsort\", 1)")
                && installer.contains("Various.AppsDefaultSortHook")
        )
        assertTrue(
            "InterceptPermHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_skip_interceptperm\")")
                && installer.contains("Various.InterceptPermHook")
        )
        assertTrue(
            "OpenByDefaultHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_replace_defaultopen_with_openbydefault\")")
                && installer.contains("Various.OpenByDefaultHook")
        )
        assertTrue(
            "SkipSecurityScanHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_skip_securityscan\")")
                && installer.contains("Various.SkipSecurityScanHook")
        )
        assertTrue(
            "ShowTempInBatteryHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_show_battery_temperature\")")
                && installer.contains("Various.ShowTempInBatteryHook")
        )
        assertTrue(
            "DisableSideBarSuggestionHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_disable_freeform_suggest_blacklist\")")
                && installer.contains("SystemWindowHooks.DisableSideBarSuggestionHook")
        )
        assertTrue(
            "DisableDockSuggestHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_disable_dock_suggest\")")
                && installer.contains("Various.DisableDockSuggestHook")
        )
        assertTrue(
            "AddSideBarExpandReceiverHook condition and process check must be preserved",
            installer.contains("com.miui.securitycenter:ui")
                && installer.contains("mPrefs.getBoolean(\"various_enable_expand_sidebar\")")
                && installer.contains("Various.AddSideBarExpandReceiverHook")
        )
        assertTrue(
            "NoLowBatteryWarningHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"system_hidelowbatwarn\")")
                && installer.contains("Various.NoLowBatteryWarningHook")
        )
        assertTrue(
            "PrivacyAppsLayoutHook condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_privacyapps_column_nums4\")")
                && installer.contains("Various.PrivacyAppsLayoutHook")
        )
        assertTrue(
            "PersistPrivacyThumbnailBlur condition must be preserved",
            installer.contains("mPrefs.getBoolean(\"various_disable_reset_recents_privacy_blur\")")
                && installer.contains("Various.PersistPrivacyThumbnailBlur")
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
