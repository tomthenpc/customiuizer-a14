package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.SystemUiFeatures
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy
import java.io.File

class StrongToastPresentationModeTest {

    @Test
    fun preferenceValues_mapToBoundedModes() {
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(0))
        assertEquals(StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT, StrongToastPresentationMode.fromPreference(1))
        assertEquals(StrongToastPresentationMode.HIDE, StrongToastPresentationMode.fromPreference(2))
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPresentationMode.fromPreference(3))
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(-1))
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(99))
    }

    @Test
    fun feature_isDisabledOnlyForSystemDefault() {
        assertFalse(StrongToastPresentationFeature.evaluateEnabled(PrefMap()))
        assertFalse(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "0")
        }))
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "1")
        }))
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "2")
        }))
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "3")
        }))
    }

    @Test
    fun feature_routesOnlyToSystemUiPackageReady() {
        val feature = SystemUiFeatures.all(fakePackageReadyParam(), PrefMap()).find {
            it.id == StrongToastPresentationFeatureId
        }

        assertNotNull(feature)
        assertEquals("system_strong_toast_mode", feature?.preferenceKey)
        assertEquals("Strong Toast Presentation", feature?.name)
        assertEquals(FeatureTarget.SYSTEM_UI, feature?.target)
        assertEquals(InstallPhase.PACKAGE_READY, feature?.phase)
    }

    @Test
    fun windowHeight_usesRuntimeInsetWithoutClippingRomVisual() {
        assertEquals(141, SystemUIStrongToastHooks.resolveWindowHeightPx(82, 141, 208))
        assertEquals(141, SystemUIStrongToastHooks.resolveWindowHeightPx(104, 141, 208))
        assertEquals(141, SystemUIStrongToastHooks.resolveWindowHeightPx(135, 141, 208))
        assertEquals(182, SystemUIStrongToastHooks.resolveWindowHeightPx(182, 141, 208))
        assertEquals(208, SystemUIStrongToastHooks.resolveWindowHeightPx(0, 141, 208))
        assertEquals(129, SystemUIStrongToastHooks.resolveWindowHeightPx(129, 0, 208))
    }

    @Test
    fun dynamicIslandVerticalScaleResolver_isBoundedAcrossDeviceGeometry() {
        assertEquals(0.72f, SystemUIStrongToastHooks.resolveDynamicIslandStartScaleY(104, 0))
        assertEquals(0.62f, SystemUIStrongToastHooks.resolveDynamicIslandStartScaleY(82, 141))
        assertEquals(0.90f, SystemUIStrongToastHooks.resolveDynamicIslandStartScaleY(182, 141))
    }

    @Test
    fun dynamicIslandWindow_keepsFullCapsuleAndOvershootSafetyArea() {
        assertEquals(195, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(82, 141, 18, 36))
        assertEquals(195, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(182, 141, 18, 36))
        assertEquals(141, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(104, 141, 0, 0))
        assertEquals(141, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(104, 141, -1, -1))
        assertEquals(104, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(104, 0, 18, 36))
    }

    @Test
    fun dynamicIslandWindow_usesFullWidthHostWithoutFirstAttachHorizontalCollapse() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT"))
        assertTrue(source.contains("capsule.scaleX = 1f"))
        assertFalse(source.contains("resolveDynamicIslandStartScaleX"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }

    private fun fakePackageReadyParam(): io.github.libxposed.api.XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(io.github.libxposed.api.XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                else -> null
            }
        } as io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
    }
}
