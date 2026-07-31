package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class FeatureInstallRegistryTest {

    private class DummyFeature(
        override val name: String,
        override val preferenceKey: String? = null,
        override val target: FeatureTarget = FeatureTarget.SYSTEM_UI,
        override val phase: InstallPhase = InstallPhase.PACKAGE_READY,
        val enabled: Boolean = true,
        val result: FeatureInstallResult = FeatureInstallResult.Installed,
    ) : FeatureDefinition {
        var installCalls = 0
        override fun isEnabled(prefs: PrefMap): Boolean = enabled
        override fun install(): FeatureInstallResult {
            installCalls++
            return result
        }
    }

    @Test
    fun installAll_onlyMatchesTargetAndPhase() {
        val registry = FeatureInstallRegistry()
        val hit = DummyFeature("hit", target = FeatureTarget.SYSTEM_UI, phase = InstallPhase.PACKAGE_READY)
        val wrongTarget = DummyFeature("wrongTarget", target = FeatureTarget.LAUNCHER, phase = InstallPhase.PACKAGE_READY)
        val wrongPhase = DummyFeature("wrongPhase", target = FeatureTarget.SYSTEM_UI, phase = InstallPhase.SYSTEM_UI_INITIALIZED)

        registry.register(hit)
        registry.register(wrongTarget)
        registry.register(wrongPhase)

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        assertEquals(1, results.size)
        assertEquals(FeatureInstallResult.Installed, results[0])
        assertEquals(1, hit.installCalls)
        assertEquals(0, wrongTarget.installCalls)
        assertEquals(0, wrongPhase.installCalls)
    }

    @Test
    fun installAll_disabledFeatureSkipped() {
        val registry = FeatureInstallRegistry()
        registry.register(DummyFeature("off", enabled = false))

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        assertEquals(1, results.size)
        assertTrue(results[0] is FeatureInstallResult.Skipped)
    }

    @Test
    fun installAll_idempotent() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("idempotent")
        registry.register(f)

        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        assertEquals(1, f.installCalls)
        assertEquals(FeatureInstallResult.AlreadyInstalled, results[0])
    }

    @Test
    fun installAll_failureRecordedOnce() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("fail", result = FeatureInstallResult.FailedPermanent("missing"))
        registry.register(f)

        val r1 = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
        val r2 = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        assertEquals(1, f.installCalls)
        assertTrue(r1[0] is FeatureInstallResult.FailedPermanent)
        assertTrue(r2[0] is FeatureInstallResult.FailedPermanent)
    }

    @Test
    fun installAll_exceptionBecomesTransient() {
        val registry = FeatureInstallRegistry()
        val f = object : FeatureDefinition {
            override val name = "explode"
            override val preferenceKey = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult = throw IllegalStateException("boom")
        }
        registry.register(f)

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        assertTrue(results[0] is FeatureInstallResult.FailedTransient)
    }

    @Test
    fun onPreferenceChanged_marksMatchingFeaturesForReinstall() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("statusbar", preferenceKey = "system_statusbarheight")
        registry.register(f)
        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
        assertEquals(1, f.installCalls)

        registry.onPreferenceChanged("system_statusbarheight")
        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        assertEquals(2, f.installCalls)
    }

    @Test
    fun onPreferenceChanged_nullKeyMarksAllWithKey() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("clock", preferenceKey = "system_clock")
        registry.register(f)
        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        registry.onPreferenceChanged(null)
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())

        assertEquals(2, (f as DummyFeature).installCalls)
        assertEquals(FeatureInstallResult.Installed, results[0])
    }
}
