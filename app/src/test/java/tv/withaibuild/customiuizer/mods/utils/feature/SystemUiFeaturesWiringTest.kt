package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
import java.lang.reflect.Proxy

class SystemUiFeaturesWiringTest {

    @Test
    fun installMethodNoLongerContainsDirectIfConditions() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.java")
        val installMethod = methodBody(source, "public static void install(")

        // The old direct preference guards and hook calls should be gone from the installer body.
        assertFalse(
            "install() should not directly read mPrefs",
            installMethod.contains("mPrefs.get")
        )

        // It should now delegate to the registry and the generated features list.
        assertTrue(
            "install() should register features from SystemUiFeatures",
            installMethod.contains("SystemUiFeatures.all(lpparam, mPrefs)")
        )
        assertTrue(
            "install() should call installAll for SYSTEM_UI at PACKAGE_READY",
            installMethod.contains("installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, mPrefs)")
        )
    }

    @Test
    fun allSystemUiFeaturesAreRegistered() {
        val features = SystemUiFeatures.all(fakePackageReadyParam(), PrefMap())

        assertEquals(
            "All 96 preference-guarded SystemUI features should be present",
            96,
            features.size
        )
        val uniqueIds = features.map { it.id }.toSet()
        assertEquals("Every feature must have a unique FeatureId", features.size, uniqueIds.size)

        for (feature in features) {
            assertEquals(FeatureTarget.SYSTEM_UI, feature.target)
            assertEquals(InstallPhase.PACKAGE_READY, feature.phase)
        }
    }

    @Test
    fun foregroundMonitorFeatureHasCorrectMultiKeyGuard() {
        val feature = ForegroundMonitorFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(ForegroundMonitorFeatureId, feature.id)
        assertEquals("various_showcallui", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)
        assertEquals(InstallPhase.PACKAGE_READY, feature.phase)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("various_showcallui", 2) }
        assertTrue(feature.isEnabled(on))

        val onByCursor = PrefMap().apply { put("controls_volumecursor", true) }
        assertTrue(feature.isEnabled(onByCursor))
    }

    @Test
    fun navBarButtonsFeatureHasCorrectMultiIntGuard() {
        val feature = NavBarButtonsFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(NavBarButtonsFeatureId, feature.id)
        assertEquals("controls_navbarleft_action", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("controls_navbarrightlong_action", 5) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun statusBarClockTweakFeatureHasCorrectMultiPreferenceGuard() {
        val feature = StatusBarClockTweakFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(StatusBarClockTweakFeatureId, feature.id)
        assertEquals("system_statusbar_clocktweak", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("system_cc_dateformat", "yyyy") }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun monitorDeviceInfoFeatureHasCorrectMultiPreferenceGuard() {
        val feature = MonitorDeviceInfoFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(MonitorDeviceInfoFeatureId, feature.id)
        assertEquals("system_statusbar_batterytempandcurrent", feature.preferenceKey)
        assertEquals(FeatureTarget.SYSTEM_UI, feature.target)

        val off = PrefMap()
        assertFalse(feature.isEnabled(off))

        val on = PrefMap().apply { put("system_statusbar_showdevicetemperature", true) }
        assertTrue(feature.isEnabled(on))
    }

    @Test
    fun dualRowSignalFeaturePreservesElseIfSemantics() {
        val feature = DualRowSignalFeature(fakePackageReadyParam(), PrefMap())

        assertEquals(DualRowSignalFeatureId, feature.id)
        assertEquals("system_statusbar_dualsimin2rows", feature.preferenceKey)

        val digitalOn = PrefMap().apply {
            put("system_statusbar_mobile_digital_signal", true)
            put("system_statusbar_dualsimin2rows", true)
        }
        assertFalse(feature.isEnabled(digitalOn))

        val dualOnly = PrefMap().apply {
            put("system_statusbar_dualsimin2rows", true)
        }
        assertTrue(feature.isEnabled(dualOnly))
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
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

    private fun methodBody(source: String, header: String): String {
        val start = source.indexOf(header)
        check(start >= 0) { "Method header '$header' not found" }
        val bodyStart = source.indexOf('{', start)
        check(bodyStart >= 0) { "Method body start not found for '$header'" }
        var braceCount = 0
        var i = bodyStart
        while (i < source.length) {
            when (source[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        return source.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        error("Method closing brace not found for '$header'")
    }
}