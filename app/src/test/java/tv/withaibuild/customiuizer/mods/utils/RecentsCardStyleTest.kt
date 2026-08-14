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
import java.io.File
import java.lang.reflect.Proxy

class RecentsCardStyleTest {

    @Test
    fun legacyAndInvalidModesDegradeToDefault() {
        assertEquals(0, Launcher.resolveRecentsCardStyle(-1))
        assertEquals(0, Launcher.resolveRecentsCardStyle(0))
        assertEquals(1, Launcher.resolveRecentsCardStyle(1))
        assertEquals(0, Launcher.resolveRecentsCardStyle(2))
        assertEquals(0, Launcher.resolveRecentsCardStyle(99))
    }

    @Test
    fun featureOnlyEnablesForHideAppName() {
        assertFalse(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap()))
        assertTrue(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", "1")
        }))
        assertFalse(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", "2")
        }))
        assertFalse(LauncherRecentsCardStyleFeature.evaluateEnabled(PrefMap().apply {
            put("system_recents_card_style", "99")
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

    @Test
    fun stackedImplementationIsRemovedFromSource() {
        val source = launcherSource()
        assertFalse("installRecentsStackedCards must be removed", source.contains("installRecentsStackedCards"))
        assertFalse("RECENTS_STACK_OVERLAP_RATIO must be removed", source.contains("RECENTS_STACK_OVERLAP_RATIO"))
        assertFalse("resolveRecentsStackGap must be removed", source.contains("resolveRecentsStackGap"))
        assertFalse("resolveRecentsStackDepth must be removed", source.contains("resolveRecentsStackDepth"))
        assertFalse("resolveRecentsStackScale must be removed", source.contains("resolveRecentsStackScale"))
        assertFalse("resolveRecentsStackTranslationZ must be removed", source.contains("resolveRecentsStackTranslationZ"))
        assertFalse("TaskStackViewsAlgorithmHorizontal must be removed", source.contains("TaskStackViewsAlgorithmHorizontal"))
        assertFalse("TaskViewTransform must be removed", source.contains("TaskViewTransform"))
    }

    @Test
    fun hideAppNameHookIsPreserved() {
        val source = launcherSource()
        val hookBody = source.substringAfter("fun RecentsCardStyleHook(")
            .substringBefore("fun resolveRecentsCardStyle(")
        assertTrue("TaskView target must remain", hookBody.contains("com.miui.home.recents.views.TaskView"))
        assertTrue("onFinishInflate hook must remain", hookBody.contains("onFinishInflate"))
        assertTrue("title View must be hidden", hookBody.contains("title"))
        assertTrue("title View must be set to GONE", hookBody.contains("View.GONE"))
        assertFalse("mode-2 branch must be removed", hookBody.contains("boundedMode == 2"))
        assertFalse("stacked installer must be removed", hookBody.contains("installRecentsStackedCards"))
    }

    @Test
    fun arrayContractHasOnlySystemDefaultAndHideAppName() {
        val arrays = source("app/src/main/res/values/arrays.xml").readText()
        val entries = arrays.substringAfter("<string-array name=\"recents_card_styles\">")
            .substringBefore("</string-array>")
        val values = arrays.substringAfter("<string-array name=\"recents_card_styles_val\">")
            .substringBefore("</string-array>")
        val entryItems = Regex("<item>([^<]+)</item>").findAll(entries).map { it.groupValues[1].trim() }.toList()
        val valueItems = Regex("<item>([^<]+)</item>").findAll(values).map { it.groupValues[1].trim() }.toList()
        assertEquals(
            listOf("@string/array_system_default", "@string/system_recents_card_style_hide_title"),
            entryItems
        )
        assertEquals(listOf("0", "1"), valueItems)
    }

    private fun launcherSource(): String =
        source("app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt").readText()

    private fun source(path: String): File {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
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
