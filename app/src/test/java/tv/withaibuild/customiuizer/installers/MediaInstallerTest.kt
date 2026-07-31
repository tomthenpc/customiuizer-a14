package tv.withaibuild.customiuizer.installers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaInstallerTest {

    @Test
    fun installerIsWiredInMainModule() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val section = main.section(
            "if (pkg.equals(\"com.miui.miwallpaper\")",
            "if (pkg.equals(\"com.android.systemui\")) {"
        )

        assertTrue(
            "MainModule must keep the media package filter",
            section.contains("pkg.equals(\"com.miui.miwallpaper\")")
                && section.contains("pkg.equals(\"com.miui.screenshot\")")
                && section.contains("pkg.equals(\"com.miui.gallery\")")
        )
        assertTrue(
            "MainModule must delegate media hooks to MediaInstaller",
            section.contains("MediaInstaller.install(lpparam, mPrefs);")
        )
        assertFalse(
            "MainModule must no longer define the media hook conditions",
            section.contains("LauncherAnimationHooks.DisableUnlockWallpaperScale")
        )
    }

    @Test
    fun installerUsesLibxposedPackageReadyParam() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/MediaInstaller.java")

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
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/MediaInstaller.java")

        assertTrue(
            "miwallpaper DisableUnlockWallpaperScale condition and call must be preserved",
            installer.contains("com.miui.miwallpaper")
                && installer.contains("mPrefs.getBoolean(\"launcher_disable_wallpaperscale\")")
                && installer.contains("LauncherAnimationHooks.DisableUnlockWallpaperScale")
        )
        assertTrue(
            "screenshot ScreenshotConfigHook condition, DexKit lifecycle and bridge must be preserved",
            installer.contains("com.miui.screenshot")
                && installer.contains("mPrefs.getBoolean(\"system_screenshot\")")
                && installer.contains("MainModule.loadDexKit()")
                && installer.contains("XposedHelpers.createBridge(lpparam.getApplicationInfo().sourceDir)")
                && installer.contains("System.ScreenshotConfigHook")
                && installer.contains("XposedHelpers.closeBridge()")
        )
        assertTrue(
            "gallery GalleryScreenshotPathHook condition and call must be preserved",
            installer.contains("com.miui.gallery")
                && installer.contains("mPrefs.getStringAsInt(\"system_gallery_screenshots_path\", 1)")
                && installer.contains("System.GalleryScreenshotPathHook")
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
