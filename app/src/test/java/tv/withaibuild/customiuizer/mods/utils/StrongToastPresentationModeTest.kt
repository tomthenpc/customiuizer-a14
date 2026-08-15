package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.feature.DynamicIslandMotionProfile
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
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPresentationMode.fromPreference(4))
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(-1))
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPresentationMode.fromPreference(99))
    }

    @Test
    fun legacyPreferenceValue4_isMigrationCoveredByEvaluateEnabled() {
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
            put("system_strong_toast_mode", "4")
        }))
        assertEquals(
            StrongToastPresentationMode.DYNAMIC_ISLAND,
            StrongToastPresentationFeature.resolveMode(PrefMap().apply {
                put("system_strong_toast_mode", "4")
            })
        )
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
    fun matchHeight_windowAndContainerStrictlyUseStatusBarInsetOrFallback() {
        // Window height strictly equals the current status-bar inset, falling back to ROM original.
        assertEquals(82, SystemUIStrongToastHooks.resolveMatchedStatusBarHeightPx(82, 208))
        assertEquals(104, SystemUIStrongToastHooks.resolveMatchedStatusBarHeightPx(104, 208))
        assertEquals(135, SystemUIStrongToastHooks.resolveMatchedStatusBarHeightPx(135, 208))
        assertEquals(182, SystemUIStrongToastHooks.resolveMatchedStatusBarHeightPx(182, 208))
        assertEquals(208, SystemUIStrongToastHooks.resolveMatchedStatusBarHeightPx(0, 208))

        // Container height matches the target so the visible capsule never exceeds the Window bounds.
        assertEquals(82, SystemUIStrongToastHooks.resolveMatchContainerHeightPx(82, 141))
        assertEquals(104, SystemUIStrongToastHooks.resolveMatchContainerHeightPx(104, 141))
        assertEquals(135, SystemUIStrongToastHooks.resolveMatchContainerHeightPx(135, 141))
        assertEquals(141, SystemUIStrongToastHooks.resolveMatchContainerHeightPx(141, 141))
        assertEquals(182, SystemUIStrongToastHooks.resolveMatchContainerHeightPx(182, 141))
        assertEquals(141, SystemUIStrongToastHooks.resolveMatchContainerHeightPx(0, 141))
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
    fun dynamicIslandMotionProfile_topEntranceFitsWindow() {
        val profile = DynamicIslandMotionProfile.forTop(
            visualHeightPx = 141,
            topMarginPx = 18,
            bottomSafetyMarginPx = 36,
            statusBarInsetPx = 82
        )
        assertTrue(profile.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141))
        assertEquals(StrongToastPosition.TOP, profile.position)
        assertEquals(0.88f, profile.entranceScaleY, 0.001f)
        assertTrue(profile.entranceTranslationY < 0f)
        assertEquals(profile.entranceTranslationY, profile.exitTranslationY, 0.001f)
    }

    @Test
    fun dynamicIslandMotionProfile_bottomEntranceFitsWindow() {
        val profile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topSafetyMarginPx = 18,
            bottomPaddingPx = 90
        )
        assertTrue(profile.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141))
        assertEquals(StrongToastPosition.BOTTOM, profile.position)
        assertEquals(0.88f, profile.entranceScaleY, 0.001f)
        assertTrue(profile.entranceTranslationY > 0f)
        assertEquals(profile.entranceTranslationY, profile.exitTranslationY, 0.001f)
    }

    @Test
    fun dynamicIslandWindow_usesVerticalSoftMotionSource() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT"))
        assertTrue(source.contains("shell.pivotY = profile.pivotY"))
        assertTrue(source.contains("shell.scaleY = profile.entranceScaleY"))
        assertTrue(source.contains("shell.translationY = profile.entranceTranslationY"))
        assertTrue(source.contains("shell.animate()"))
        assertTrue(source.contains(".scaleY(1f)"))
        assertTrue(source.contains(".scaleY(profile.exitScaleY)"))
        assertTrue(source.contains(".translationY(profile.exitTranslationY)"))
        assertTrue(source.contains("boundedDynamicIslandInterpolator"))
        assertTrue(source.contains("PathInterpolator(0.25f, 1f, 0.5f, 1f)"))
        assertFalse(source.contains("prepareDynamicIslandContent("))
        assertTrue(source.contains("resetDynamicIslandContent(capsule)"))
        assertTrue(source.contains("layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL"))
        assertTrue(source.contains("layoutParams.windowAnimations = 0"))
        assertTrue(source.contains("layoutParams.setFitInsetsTypes(0)"))
        assertTrue(source.contains("disableClippingThroughAncestors(shell, root)"))
        assertTrue(source.contains("OnComputeInternalInsetsListener"))
        assertTrue(source.contains("removeOnComputeInternalInsetsListener"))
        assertTrue(source.contains("shell.clipToOutline = false"))
        assertTrue(source.contains("resolveBottomPaddingForCapsule("))
        assertTrue(source.contains("resolveDynamicIslandMotionProfile("))
        assertTrue(source.contains("DynamicIslandMotionProfile"))
        assertTrue(source.contains("realHideStrongToast"))
        assertTrue(source.contains("XposedHelpers.callMethod(strongToast, \"onComplete\")"))
        assertTrue(source.contains("XposedHelpers.setBooleanField(strongToast, \"mCheckInOutStrongToasting\", true)"))
        assertTrue(source.contains("showingField.setBoolean(keyguardState, false)"))
        assertTrue(source.contains("override fun intercept(chain: XposedInterface.Chain)"))
        assertTrue(source.contains("closeLockscreenGate(token, showingField)"))

        assertFalse(source.contains("DYNAMIC_ISLAND_CENTER_POP"))
        assertFalse(source.contains("isCenterPop"))
        assertFalse(source.contains("resolveTopIslandOriginScaleX"))
        assertFalse(source.contains("BOTTOM_ISLAND_START_SCALE_X"))
        assertFalse(source.contains("CENTER_POP_START_SCALE_X"))
        assertFalse(source.contains("CENTER_POP_START_SCALE_Y"))
        assertFalse(source.contains("CENTER_POP_START_ALPHA"))
        assertFalse(source.contains("CENTER_POP_DURATION_MS"))
        assertFalse(source.contains("TOP_ISLAND_FALLBACK_SCALE_X"))
        assertFalse(source.contains("TOP_ISLAND_MAX_ORIGIN_SCALE_X"))
        assertFalse(source.contains("TOP_ISLAND_CUTOUT_PADDING_DP"))
        assertFalse(source.contains("dynamicIslandMotionView("))
        assertFalse(source.contains("resolveBottomEntranceTravelPx("))
        assertFalse(source.contains("import android.view.animation.OvershootInterpolator"))
        assertFalse(source.contains("ValueAnimator"))
        assertFalse(source.contains("startDynamicIslandRefresh"))
    }

    @Test
    fun matchHeight_sourceUsesStrictHelpersAndCleansUpAtDetach() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")

        // The MATCH branch must not call a maxOf helper and must drive both window and container.
        assertTrue(source.contains("resolveMatchedStatusBarHeightPx("))
        assertTrue(source.contains("resolveMatchContainerHeightPx("))
        assertFalse("match mode must not use maxOf(statusBarInset, visualHeight)",
            source.contains("maxOf(statusBarInsetPx, visualHeightPx)")
        )

        // Window and content mutation must be atomic: the Window height is only set when content
        // preparation succeeds.
        assertTrue(source.contains("val prepared = applyMatchStatusBarHeight("))
        assertTrue(source.contains("if (prepared) {"))
        assertTrue(source.contains("layoutParams.height = targetHeightPx"))

        // The container (cl_strong_toast_msg) must be sized to the target content height.
        val applyBody = extractFunctionBody(source, "internal fun applyMatchModeBaselineToViews(")
        assertTrue("apply must capture baseline before any mutation", applyBody.contains("captureMatchModeBaseline("))
        assertTrue(
            "baseline must be stored before any MATCH mutation",
            applyBody.indexOf("XposedHelpers.setAdditionalInstanceField(root, MATCH_BASELINE_FIELD, baseline)") <
                applyBody.indexOf("applyMatchModeMutations(")
        )
        assertTrue("apply must skip re-capture on double apply", applyBody.contains("if (existing == null)"))

        val mutationsBody = extractFunctionBody(source, "private fun applyMatchModeMutations(")
        assertTrue(mutationsBody.contains("lp.height = targetContentHeightPx"))

        // Child content is centered vertically and the bottom forehead sibling is hidden.
        assertTrue(source.contains("(capsule as? LinearLayout)?.gravity = Gravity.CENTER_VERTICAL"))
        assertTrue(source.contains("bottomView?.visibility = View.GONE"))

        // Detach / mode switch must restore the exact captured baseline, not hard-coded ROM guesses.
        val resetBody = extractFunctionBody(source, "internal fun resetMatchModeCapsule(")
        assertTrue("reset must read the captured baseline", resetBody.contains("as? MatchModeBaseline"))
        assertTrue("reset must call guaranteed-cleanup helper", resetBody.contains("resetMatchModeBaselineToViews("))

        val cleanupBody = extractFunctionBody(source, "internal fun resetMatchModeBaselineToViews(")
        assertTrue("cleanup helper must attempt restore", cleanupBody.contains("restoreMatchModeBaseline("))
        assertTrue("cleanup helper must use try/finally", cleanupBody.contains("try {"))
        assertTrue("cleanup helper must use finally block", cleanupBody.contains("finally {"))
        assertTrue(
            "cleanup helper must remove MATCH_BASELINE_FIELD exactly once in finally",
            cleanupBody.contains("XposedHelpers.removeAdditionalInstanceField(root, MATCH_BASELINE_FIELD)")
        )

        val restoreBody = extractFunctionBody(source, "internal fun restoreMatchModeBaseline(")
        assertTrue("restore must restore height from baseline", restoreBody.contains("lp.height = baseline.height"))
        assertTrue("restore must restore width from baseline", restoreBody.contains("lp.width = baseline.width"))
        assertTrue("restore must restore layout gravity from baseline", restoreBody.contains("lp.gravity = baseline.layoutGravity"))
        assertTrue("restore must restore capsule gravity from baseline", restoreBody.contains("?.gravity = baseline.capsuleGravity"))
        assertTrue("restore must restore parent padding from baseline", restoreBody.contains("parent.setPadding("))
        assertTrue("restore must restore parent gravity from baseline", restoreBody.contains("?.gravity = baseline.parentGravity"))
        assertTrue(
            "restore must restore bottom view visibility from baseline",
            restoreBody.contains("?.visibility = baseline.bottomViewVisibility")
        )
        assertFalse(
            "restore must not use the ROM visual height as a baseline substitute",
            restoreBody.contains("strongToastVisualHeightPx")
        )
        assertFalse(
            "restore must not use ROM dimension resources as baseline substitutes",
            restoreBody.contains("strong_toast_width") || restoreBody.contains("strong_toast_height")
        )
        assertFalse(
            "restore must not hard-code visible for the bottom sibling",
            restoreBody.contains("View.VISIBLE")
        )
    }

    private fun extractFunctionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "Signature not found: $signature" }
        var braceCount = 0
        var foundFirstBrace = false
        val sb = StringBuilder()
        for (i in start until source.length) {
            val c = source[i]
            if (c == '{') {
                foundFirstBrace = true
                braceCount++
            } else if (c == '}') {
                braceCount--
            }
            if (foundFirstBrace) {
                sb.append(c)
                if (braceCount == 0) break
            }
        }
        return sb.toString()
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
