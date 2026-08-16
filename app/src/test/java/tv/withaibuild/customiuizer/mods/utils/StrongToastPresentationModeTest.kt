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
    fun feature_isEnabledForAllModesToKeepLiveConfiguration() {
        assertTrue("StrongToast hooks must be present even in SYSTEM_DEFAULT",
            StrongToastPresentationFeature.evaluateEnabled(PrefMap()))
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(PrefMap().apply {
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
    fun matchHeight_totalVisibleHeightEqualsStatusBar() {
        // Window height is exactly the status bar; content + chin must sum to that height.
        assertEquals(100, SystemUIStrongToastHooks.resolveMatchWindowHeightPx(100))
        assertEquals(0, SystemUIStrongToastHooks.resolveMatchWindowHeightPx(-1))

        // content = statusBar - chin when the chin can share the matched height.
        assertEquals(80, SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 20))
        assertEquals(100, SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 0))
        assertEquals(100, SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, -5))

        // chin >= statusBar/2: hide chin and let content take the full matched height.
        assertTrue(SystemUIStrongToastHooks.matchModeHidesChin(100, 60))
        assertEquals(100, SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 60))
        assertTrue(SystemUIStrongToastHooks.matchModeHidesChin(100, 100))
        assertEquals(100, SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 100))

        // Never produce zero/negative content.
        assertTrue(SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 20) >= 1)
        assertTrue(SystemUIStrongToastHooks.resolveMatchContentHeightPx(100, 100) >= 1)
    }

    @Test
    fun dynamicIslandWindow_isMarginPlusCapsulePlusClearanceOnly() {
        assertEquals(195, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(141, 18, 36))
        assertEquals(141, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(141, 0, 0))
        assertEquals(141, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(141, -1, -1))
        assertEquals(54, SystemUIStrongToastHooks.resolveDynamicIslandWindowHeightPx(0, 18, 36))
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
            bottomClearancePx = 36,
            maxEdgeTravelPx = 55
        )
        assertTrue(profile.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141))
        assertEquals(StrongToastPosition.TOP, profile.position)
        assertTrue(profile.entranceStartScale < 1f && profile.entranceStartScale > 0f)
        assertTrue(profile.entranceStartTranslationY < 0f)
        assertEquals(profile.entranceStartTranslationY, profile.exitEndTranslationY, 0.001f)
        assertEquals(0f, profile.exitEndScale, 0.0f)
    }

    @Test
    fun dynamicIslandMotionProfile_bottomEntranceFitsWindow() {
        val profile = DynamicIslandMotionProfile.forBottom(
            visualHeightPx = 141,
            topClearancePx = 18,
            bottomPaddingPx = 90,
            maxEdgeTravelPx = 55
        )
        assertTrue(profile.capsuleFitsWindow(capsuleTopAtRest = 18, capsuleHeightPx = 141))
        assertEquals(StrongToastPosition.BOTTOM, profile.position)
        assertTrue(profile.entranceStartScale < 1f && profile.entranceStartScale > 0f)
        assertTrue(profile.entranceStartTranslationY > 0f)
        assertEquals(profile.entranceStartTranslationY, profile.exitEndTranslationY, 0.001f)
        assertEquals(0f, profile.exitEndScale, 0.0f)
    }

    @Test
    fun dynamicIslandWindow_usesUniformShellMotionSource() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")
        assertTrue(source.contains("layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT"))
        assertTrue(source.contains("shell.pivotY = profile.pivotY"))
        assertTrue(source.contains("shell.scaleX = profile.entranceStartScale"))
        assertTrue(source.contains("shell.scaleY = profile.entranceStartScale"))
        assertTrue(source.contains("shell.translationY = profile.entranceStartTranslationY"))
        assertTrue(source.contains("shell.animate()"))
        assertTrue(source.contains(".scaleX(profile.restingScale)"))
        assertTrue(source.contains(".scaleY(profile.restingScale)"))
        assertTrue(source.contains(".scaleX(profile.exitEndScale)"))
        assertTrue(source.contains(".scaleY(profile.exitEndScale)"))
        assertTrue(source.contains(".translationY(profile.exitEndTranslationY)"))
        assertTrue(source.contains("dynamicIslandEntranceInterpolator"))
        assertTrue(source.contains("dynamicIslandExitInterpolator"))
        assertTrue("entrance/exit/rest animations must use hardware layers", source.contains(".withLayer()"))
        assertTrue("entrance must be gated by a one-shot pre-draw listener", source.contains("addOnPreDrawListener"))
        assertFalse(source.contains("prepareDynamicIslandContent("))
        assertTrue(source.contains("resetDynamicIslandContent(capsule)"))
        assertTrue(source.contains("layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL"))
        assertTrue(source.contains("layoutParams.windowAnimations = 0"))
        assertTrue(source.contains("layoutParams.setFitInsetsTypes(0)"))
        assertTrue(source.contains("DynamicIslandCapsuleView(root.context)"))
        assertTrue(source.contains("suppressRomRoundRect(roundRect)"))
        assertTrue(source.contains("OnComputeInternalInsetsListener"))
        assertTrue(source.contains("removeOnComputeInternalInsetsListener"))
        assertTrue(source.contains("resolveBottomPaddingForCapsule("))
        assertTrue(source.contains("resolveDynamicIslandMotionProfile("))
        assertTrue(source.contains("DynamicIslandMotionProfile"))
        assertTrue(source.contains("realHideStrongToast"))
        assertTrue(source.contains("XposedHelpers.callMethod(strongToast, \"onComplete\")"))
        assertTrue(source.contains("XposedHelpers.setBooleanField(strongToast, \"mCheckInOutStrongToasting\", true)"))
        assertTrue(source.contains("showingField.setBoolean(keyguardState, false)"))
        assertTrue(source.contains("override fun intercept(chain: XposedInterface.Chain)"))
        assertTrue(source.contains("closeLockscreenGate(token, showingField)"))

        assertFalse(source.contains("GEOMETRY_DEBUG"))
        assertFalse(source.contains("captureAncestorClipBaselines"))
        assertFalse(source.contains("EXIT_ALPHA_FRACTION"))
        assertFalse(source.contains("DYNAMIC_ISLAND_CENTER_POP"))
        assertFalse(source.contains("ValueAnimator"))
        assertFalse(source.contains("startDynamicIslandRefresh"))
    }

    @Test
    fun matchHeight_sourceUsesStrictHelpersAndCleansUpAtDetach() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")

        assertTrue(source.contains("resolveMatchContentHeightPx("))
        assertTrue(source.contains("resolveMatchWindowHeightPx("))
        assertTrue(source.contains("matchModeHidesChin("))
        assertFalse(
            "match mode must not use maxOf(statusBarInset, visualHeight)",
            source.contains("maxOf(statusBarInsetPx, visualHeightPx)")
        )

        assertTrue(source.contains("val prepared = applyMatchStatusBarHeight("))
        assertTrue(source.contains("if (prepared) {"))
        assertTrue(source.contains("layoutParams.height =\n                                        resolveMatchWindowHeightPx(statusBarHeightPx)"))

        val applyBody = extractFunctionBody(source, "internal fun applyMatchModeBaselineToViews(")
        assertTrue("apply must capture baseline before any mutation", applyBody.contains("captureMatchModeBaseline("))
        assertTrue("apply must skip re-capture on double apply", applyBody.contains("if (existing == null)"))

        val mutationsBody = extractFunctionBody(source, "private fun applyMatchModeMutations(")
        assertTrue(
            "match must resize the message row to the target content height",
            mutationsBody.contains("capsuleLp.height = targetContentHeightPx")
        )
        assertTrue(
            "match must hide the chin only when requested",
            mutationsBody.contains("if (hideChin) View.GONE else View.VISIBLE")
        )
        assertFalse(
            "match mode must not use DynamicIslandCapsuleView",
            mutationsBody.contains("DynamicIslandCapsuleView")
        )

        val resetBody = extractFunctionBody(source, "internal fun resetMatchModeCapsule(")
        assertTrue("reset must read the captured baseline", resetBody.contains("as? MatchModeBaseline"))
        assertTrue("reset must call guaranteed-cleanup helper", resetBody.contains("resetMatchModeBaselineToViews("))

        val cleanupBody = extractFunctionBody(source, "internal fun resetMatchModeBaselineToViews(")
        assertTrue("cleanup helper must attempt restore", cleanupBody.contains("restoreMatchModeBaseline("))
        assertTrue(
            "cleanup helper must remove MATCH_BASELINE_FIELD exactly once in finally",
            cleanupBody.contains("XposedHelpers.removeAdditionalInstanceField(root, MATCH_BASELINE_FIELD)")
        )
    }

    @Test
    fun dynamicIslandAnimation_ownsShellAndRejectsRomWindowAnimation() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt")

        val installBody = extractFunctionBody(
            source,
            "private fun installHeightMatch(lpparam: PackageReadyParam)"
        )
        val matchBranch = extractWhenBranch(
            installBody,
            "StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT ->"
        )
        val islandBranch = extractWhenBranch(
            installBody,
            "StrongToastPresentationMode.DYNAMIC_ISLAND ->"
        )

        // MATCH leaves the ROM window/style path untouched.
        assertFalse(
            "MATCH must not zero or overwrite windowAnimations",
            matchBranch.contains("windowAnimations")
        )
        assertFalse(
            "MATCH must not force a full-width host window",
            matchBranch.contains("layoutParams.width")
        )

        // Dynamic Island must disable the ROM window-level animation so the module owns
        // the only shell transform.
        assertTrue(
            "Dynamic Island must set windowAnimations to 0",
            islandBranch.contains("layoutParams.windowAnimations = 0")
        )
        assertTrue(
            "Dynamic Island must use a full-screen transparent host",
            islandBranch.contains("layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT")
        )

        assertTrue(
            "entrance must animate shell with uniform scale",
            source.contains("shell.scaleX = profile.entranceStartScale") &&
                source.contains("shell.scaleY = profile.entranceStartScale")
        )
        assertTrue(
            "entrance must animate shell translationY",
            source.contains("shell.translationY = profile.entranceStartTranslationY")
        )
        assertTrue(
            "entrance/exit must use dedicated interpolators",
            source.contains("dynamicIslandEntranceInterpolator") &&
                source.contains("dynamicIslandExitInterpolator")
        )
        assertTrue(
            "entrance/exit must use hardware layers",
            source.contains(".withLayer()")
        )

        // The module must not try to import or call Folme / MIUIX animation directly.
        assertFalse(
            "module must not directly depend on MIUIX Folme",
            source.contains("miuix.animation")
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

    private fun extractWhenBranch(source: String, label: String): String {
        val start = source.indexOf(label)
        require(start >= 0) { "Branch label not found: $label" }
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
