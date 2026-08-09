package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.feature.StatusBarFocusNotificationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StatusBarFocusNotificationFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.SystemUiFeatures
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class StatusBarFocusNotificationModeTest {

    @Test
    fun preferenceValues_mapToBoundedModes() {
        assertEquals(StatusBarFocusNotificationMode.SYSTEM_DEFAULT, StatusBarFocusNotificationMode.fromPreference(0))
        assertEquals(StatusBarFocusNotificationMode.MATCH_STATUS_BAR_HEIGHT, StatusBarFocusNotificationMode.fromPreference(1))
        assertEquals(StatusBarFocusNotificationMode.HIDE, StatusBarFocusNotificationMode.fromPreference(2))
        assertEquals(StatusBarFocusNotificationMode.SYSTEM_DEFAULT, StatusBarFocusNotificationMode.fromPreference(-1))
        assertEquals(StatusBarFocusNotificationMode.SYSTEM_DEFAULT, StatusBarFocusNotificationMode.fromPreference(99))
    }

    @Test
    fun feature_isDisabledOnlyForSystemDefault() {
        assertFalse(StatusBarFocusNotificationFeature.evaluateEnabled(PrefMap()))
        assertFalse(StatusBarFocusNotificationFeature.evaluateEnabled(PrefMap().apply {
            put("system_statusbar_focus_notification", "0")
        }))
        assertTrue(StatusBarFocusNotificationFeature.evaluateEnabled(PrefMap().apply {
            put("system_statusbar_focus_notification", "1")
        }))
        assertTrue(StatusBarFocusNotificationFeature.evaluateEnabled(PrefMap().apply {
            put("system_statusbar_focus_notification", "2")
        }))
    }

    @Test
    fun feature_routesOnlyToSystemUiPackageReady() {
        val feature = SystemUiFeatures.all(fakePackageReadyParam(), PrefMap()).find {
            it.id == StatusBarFocusNotificationFeatureId
        }

        assertNotNull(feature)
        assertEquals("system_statusbar_focus_notification", feature?.preferenceKey)
        assertEquals("Status Bar Focus Notification", feature?.name)
        assertEquals(FeatureTarget.SYSTEM_UI, feature?.target)
        assertEquals(InstallPhase.PACKAGE_READY, feature?.phase)
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
