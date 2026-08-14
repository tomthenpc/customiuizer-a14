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
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND_CENTER_POP, StrongToastPresentationMode.fromPreference(4))
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(-1))
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(99))
    }

    @Test
    fun positionPreference_isBoundedAndBackwardsCompatible() {
        assertEquals(StrongToastPosition.TOP, StrongToastPosition.fromPreference(0))
        assertEquals(StrongToastPosition.BOTTOM, StrongToastPosition.fromPreference(1))
        assertEquals(StrongToastPosition.TOP, StrongToastPosition.fromPreference(-1))
        assertEquals(StrongToastPosition.TOP, StrongToastPosition.fromPreference(99))
        assertEquals(
            StrongToastPosition.TOP,
            StrongToastPresentationFeature.resolvePosition(PrefMap())
        )
        assertEquals(
            StrongToastPosition.BOTTOM,
            StrongToastPresentationFeature.resolvePosition(PrefMap().apply {
                put("system_strong_toast_position", "1")
            })
        )
    }

    @Test
    fun bottomOffsetPreference_isBoundedAndBackwardsCompatible() {
        assertEquals(0, StrongToastPresentationFeature.resolveBottomOffsetDp(PrefMap()))
        assertEquals(
            -1,
            StrongToastPresentationFeature.resolveBottomOffsetDp(PrefMap().apply {
                put("system_strong_toast_bottom_offset", -1)
            })
        )
        assertEquals(
            -40,
            StrongToastPresentationFeature.resolveBottomOffsetDp(PrefMap().apply {
                put("system_strong_toast_bottom_offset", -999)
            })
        )
        assertEquals(
            24,
            StrongToastPresentationFeature.resolveBottomOffsetDp(PrefMap().apply {
                put("system_strong_toast_bottom_offset", 24)
            })
        )
        assertEquals(
            80,
            StrongToastPresentationFeature.resolveBottomOffsetDp(PrefMap().apply {
                put("system_strong_toast_bottom_offset", 999)
            })
        )
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
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "4")
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
    fun dynamicIslandWindow_keepsFullCapsuleAndOvershootSafetyArea() {
        assertEquals(195, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(82, 141, 18, 36))
        assertEquals(195, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(182, 141, 18, 36))
        assertEquals(141, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(104, 141, 0, 0))
        assertEquals(141, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(104, 141, -1, -1))
        assertEquals(104, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(104, 0, 18, 36))
    }

    @Test
    fun swipeDismiss_requiresDirectionAndThreshold() {
        assertTrue(SystemUIStrongToastHooks.shouldDismissDynamicIsland(-29f, StrongToastPosition.TOP, 28f))
        assertFalse(SystemUIStrongToastHooks.shouldDismissDynamicIsland(29f, StrongToastPosition.TOP, 28f))
        assertFalse(SystemUIStrongToastHooks.shouldDismissDynamicIsland(-27f, StrongToastPosition.TOP, 28f))
        assertTrue(SystemUIStrongToastHooks.shouldDismissDynamicIsland(29f, StrongToastPosition.BOTTOM, 28f))
        assertFalse(SystemUIStrongToastHooks.shouldDismissDynamicIsland(-29f, StrongToastPosition.BOTTOM, 28f))
        assertFalse(SystemUIStrongToastHooks.shouldDismissDynamicIsland(29f, StrongToastPosition.BOTTOM, 0f))
    }

    @Test
    fun dynamicIslandWindow_supportsSafeFullWidthEntranceStyles() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT"))
        assertTrue(source.contains("capsule.scaleX = 1f"))
        assertTrue(source.contains("if (!centerPop) motionView.scaleY = 1f"))
        assertTrue(source.contains("motionView.pivotX = motionView.width / 2f"))
        assertTrue(source.contains("CENTER_POP_START_SCALE_X = 0.52f"))
        assertTrue(source.contains("CENTER_POP_START_ALPHA = 1f"))
        assertTrue(source.contains("CENTER_POP_DURATION_MS = 420L"))
        assertTrue(source.contains("boundedDynamicIslandInterpolator"))
        assertTrue(source.contains("PathInterpolator(0.25f, 1f, 0.5f, 1f)"))
        assertTrue(source.contains("prepareDynamicIslandContent(capsule)"))
        assertTrue(source.contains("resetDynamicIslandContent(capsule)"))
        assertTrue(source.contains("motionView.scaleX = CENTER_POP_START_SCALE_X"))
        assertTrue(source.contains("motionView.scaleY = CENTER_POP_START_SCALE_Y"))
        assertTrue(source.contains("motionView.pivotY = motionView.height / 2f"))
        assertTrue(source.contains("resetDynamicIslandHostTransform(strongToast)"))
        assertFalse(source.contains("resolveDynamicIslandStartScaleX"))
        assertTrue(source.contains("layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL"))
        assertTrue(source.contains("layoutParams.windowAnimations = 0"))
        assertTrue(source.contains("layoutParams.setFitInsetsTypes(0)"))
        assertTrue(source.contains("disableClippingThroughAncestors(capsule, root)"))
        assertTrue(source.contains("dynamicIslandMotionView(capsule, centerPop)"))
        assertTrue(source.contains("motionView.translationY = 0f"))
        assertTrue(source.contains("resolveTopIslandOriginScaleX(capsule)"))
        assertTrue(source.contains("val screenTopExit = -capsule.top.toFloat()"))
        assertTrue(source.contains("OnComputeInternalInsetsListener"))
        assertTrue(source.contains("removeOnComputeInternalInsetsListener"))
        assertTrue(source.contains("capsule.clipToOutline = false"))
        assertTrue(source.contains("resolveBottomEntranceTravelPx(motionView)"))
        assertTrue(source.contains("BOTTOM_ISLAND_START_SCALE_X = 0.58f"))
        assertTrue(source.contains("capsule.animate()"))
        assertTrue(source.contains(".scaleX(BOTTOM_ISLAND_START_SCALE_X)"))
        assertTrue(source.contains(".translationY(targetOffset.toFloat())"))
        assertFalse(source.contains("import android.view.animation.OvershootInterpolator"))
        assertFalse(source.contains("ValueAnimator"))
        assertFalse(source.contains("animateBottomLayoutOffset"))
        assertTrue(source.contains("} else if (position == StrongToastPosition.BOTTOM)"))
        assertFalse(source.contains("BOTTOM_SURFACE_SETTLE_MS"))
        assertFalse(source.contains("BOTTOM_ENTRANCE_DISTANCE_FACTOR"))
        assertTrue(source.contains("\"realHideStrongToast\""))
        assertTrue(source.contains("XposedHelpers.callMethod(strongToast, \"onComplete\")"))
        assertTrue(source.contains("XposedHelpers.setBooleanField(strongToast, \"mCheckInOutStrongToasting\", true)"))
        assertFalse(source.contains("startDynamicIslandRefresh"))
        assertTrue(source.contains("showingField.setBoolean(keyguardState, false)"))
        assertTrue(source.contains("closeLockscreenGate"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")!!).absoluteFile
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

    @Test
    fun bottomDynamicIslandWindow_reservesNavigationArea() {
        assertEquals(
            249,
            SystemUIStrongToastHooks.resolveBottomDynamicIslandWindowHeightPx(141, 18, 90)
        )
        assertEquals(
            141,
            SystemUIStrongToastHooks.resolveBottomDynamicIslandWindowHeightPx(141, 0, 0)
        )
        assertEquals(
            54,
            SystemUIStrongToastHooks.resolveBottomDynamicIslandWindowHeightPx(0, 18, 54)
        )
        assertEquals(90, SystemUIStrongToastHooks.resolveBottomPaddingPx(54, 36, 0))
        assertEquals(54, SystemUIStrongToastHooks.resolveBottomPaddingPx(54, 18, -18))
        assertEquals(0, SystemUIStrongToastHooks.resolveBottomPaddingPx(54, 18, -999))
    }
}
