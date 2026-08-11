package tv.withaibuild.customiuizer.mods.statusbariconvisibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks
import java.io.File
import java.lang.reflect.Modifier

/**
 * B2 consolidation structural invariants for the HideIconsSignal Architecture C migration.
 *
 * These tests are source/reflection invariants, not runtime ROM callbacks.
 *
 * Evidence classification: STRUCTURAL.
 */
class C4HideIconsSignalB2StructuralTest {

    // -------------------------------------------------------------------------
    // A. production hook wiring source invariant
    // -------------------------------------------------------------------------
    @Test
    fun hideIconsSignalHook_wiringSourceInvariants() {
        val source = readMainSource("SystemUIStatusBarHooks.kt")

        // Resolver called once with the install classloader.
        assertTrue(
            "HideIconsSignalHook must resolve ABI once at install",
            source.contains("val abi = StatusBarIconVisibilityResolver.resolve(lpparam.classLoader)"),
        )

        // Effect created as a local, immutable val and captured by the hook.
        assertTrue(
            "HideIconsSignalHook must create the Effect as a local val",
            source.contains("val effect = StatusBarIconVisibilityEffect(abi) { currentOrBuildStatusBarIconVisibilitySnapshot() }"),
        )

        // Callback delegates to the captured Effect.
        assertTrue(
            "MethodHook.before must delegate to effect.before(param)",
            source.contains("effect.before(param)"),
        )

        // Both methods are still installed via hookAllMethods.
        assertTrue(
            "applyMobileState must be installed with hookAllMethods",
            source.contains("\"applyMobileState\""),
        )
        assertTrue(
            "updateState must be installed with hookAllMethods",
            source.contains("\"updateState\""),
        )
        assertTrue(
            "hookAllMethods must be called on com.android.systemui.statusbar.StatusBarMobileView",
            source.contains("\"com.android.systemui.statusbar.StatusBarMobileView\""),
        )
    }

    // -------------------------------------------------------------------------
    // B. FAST source invariant
    // -------------------------------------------------------------------------
    @Test
    fun processFast_containsNoXposedHelpersFieldAccess() {
        val source = readMainSource("StatusBarIconVisibilityEffect.kt")

        // Extract the processFast body up to the start of processLegacy.
        val fastStart = source.indexOf("private fun processFast")
        val legacyStart = source.indexOf("private fun processLegacy")
        assertTrue("processFast must exist", fastStart >= 0)
        assertTrue("processLegacy must exist", legacyStart >= 0)
        val fastBody = source.substring(fastStart, legacyStart)

        // FAST path must not use runtime XposedHelpers field accessors or field lookup.
        assertFalse(
            "processFast must not call XposedHelpers.getObjectField",
            fastBody.contains("XposedHelpers.getObjectField"),
        )
        assertFalse(
            "processFast must not call XposedHelpers.getBooleanField",
            fastBody.contains("XposedHelpers.getBooleanField"),
        )
        assertFalse(
            "processFast must not call XposedHelpers.setObjectField",
            fastBody.contains("XposedHelpers.setObjectField"),
        )
        assertFalse(
            "processFast must not perform findField lookup",
            fastBody.contains("findField(") || fastBody.contains("findFieldIfExists"),
        )
    }

    // -------------------------------------------------------------------------
    // C. no mutable process-global Effect
    // -------------------------------------------------------------------------
    @Test
    fun noMutableProcessGlobalEffectField() {
        val clazz = SystemUIStatusBarHooks::class.java
        for (field in clazz.declaredFields) {
            assertFalse(
                "SystemUIStatusBarHooks must not hold a StatusBarIconVisibilityEffect field (process-global): ${field.name}",
                StatusBarIconVisibilityEffect::class.java.isAssignableFrom(field.type),
            )
            if (StatusBarIconVisibilityEffect::class.java.isAssignableFrom(field.type)) {
                assertTrue(
                    "Any StatusBarIconVisibilityEffect field must be final/immutable: ${field.name}",
                    Modifier.isFinal(field.modifiers),
                )
            }
        }
    }

    private fun readMainSource(fileName: String): String {
        val path = File("src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/$fileName")
        val altPath = File("src/main/java/tv/withaibuild/customiuizer/mods/$fileName")
        val file = if (path.exists()) path else altPath
        return file.readText(Charsets.UTF_8)
    }
}
