package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.Launcher
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherPostAttachFeatures
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherRecentsCardStyleFeature
import tv.withaibuild.customiuizer.mods.utils.feature.LauncherRecentsCardStyleFeatureId
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy

class RecentsCardStyleTest {
    @Test
    fun modesAreBounded() {
        assertEquals(0, Launcher.resolveRecentsCardStyle(-1))
        assertEquals(0, Launcher.resolveRecentsCardStyle(0))
        assertEquals(1, Launcher.resolveRecentsCardStyle(1))
        assertEquals(2, Launcher.resolveRecentsCardStyle(2))
        assertEquals(0, Launcher.resolveRecentsCardStyle(3))
    }

    @Test
    fun stackedCardsUseBoundedProportionalOverlap() {
        assertEquals(0f, Launcher.resolveRecentsStackGap(-1), 0f)
        assertEquals(0f, Launcher.resolveRecentsStackGap(0), 0f)
        assertEquals(-320f, Launcher.resolveRecentsStackGap(1000), 0.001f)
    }

    @Test
    fun stackedCardsUseNativeDistanceForBoundedDepth() {
        assertEquals(0f, Launcher.resolveRecentsStackDepth(500f, 500f, 1000f), 0f)
        assertEquals(1f, Launcher.resolveRecentsStackDepth(1500f, 500f, 1000f), 0f)
        assertEquals(3f, Launcher.resolveRecentsStackDepth(5000f, 500f, 1000f), 0f)
        assertEquals(0f, Launcher.resolveRecentsStackDepth(5000f, 500f, 0f), 0f)
        assertEquals(1f, Launcher.resolveRecentsStackScale(0f), 0f)
        assertEquals(0.835f, Launcher.resolveRecentsStackScale(3f), 0.0001f)
        assertEquals(6f, Launcher.resolveRecentsStackTranslationZ(0f, 1000f), 0.0001f)
        assertEquals(0f, Launcher.resolveRecentsStackTranslationZ(3f, 1000f), 0f)
    }

    @Test
    fun featureOnlyEnablesForCustomModes() {
        assertFalse(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap()))
        assertTrue(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", "1")
        }))
        assertTrue(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", "2")
        }))
    }

    @Test
    fun featureRoutesToLauncherAttachOnly() {
        val feature = LauncherPostAttachFeatures.all(fakePackageReadyParam(), PrefMap()).find {
            it.id == LauncherRecentsCardStyleFeatureId
        }
        assertNotNull(feature)
        assertEquals("system_recents_card_style", feature?.preferenceKey)
        assertEquals(FeatureTarget.LAUNCHER, feature?.target)
        assertEquals(InstallPhase.APPLICATION_ATTACHED, feature?.phase)
    }

    private fun fakePackageReadyParam(): PackageReadyParam {
        return Proxy.newProxyInstance(
            PackageReadyParam::class.java.classLoader,
            arrayOf(PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.miui.home"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                else -> null
            }
        } as PackageReadyParam
    }
}
