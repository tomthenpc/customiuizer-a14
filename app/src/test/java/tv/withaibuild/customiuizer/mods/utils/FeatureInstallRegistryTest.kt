package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class FeatureInstallRegistryTest {

    private class TestId(override val name: String) : FeatureId {
        companion object {
            private var counter = 10000
            fun nextId(): Int = counter++
        }
        override val id = nextId()
    }

    private class DummyFeature(
        override val name: String,
        override val preferenceKey: String? = null,
        override val target: FeatureTarget = FeatureTarget.SYSTEM_UI,
        override val phase: InstallPhase = InstallPhase.PACKAGE_READY,
        val enabled: Boolean = true,
        val result: FeatureInstallResult = FeatureInstallResult.INSTALLED,
    ) : FeatureDefinition {
        override val id = TestId(name)
        var installCalls = 0
        var onPreferenceChangedCalls = 0
        override fun isEnabled(prefs: PrefMap): Boolean = enabled
        override fun install(): FeatureInstallResult {
            installCalls++
            return result
        }
        override fun onPreferenceChanged(key: String?, prefs: PrefMap) {
            onPreferenceChangedCalls++
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

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertEquals(FeatureInstallResult.INSTALLED, results[0])
        assertEquals(1, hit.installCalls)
        assertEquals(0, wrongTarget.installCalls)
        assertEquals(0, wrongPhase.installCalls)
    }

    @Test
    fun installAll_disabledFeatureSkipped() {
        val registry = FeatureInstallRegistry()
        registry.register(DummyFeature("off", enabled = false))

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertTrue(results[0] == FeatureInstallResult.SKIPPED)
    }

    @Test
    fun installAll_idempotent() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("idempotent")
        registry.register(f)

        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, f.installCalls)
        assertEquals(FeatureInstallResult.ALREADY_INSTALLED, results[0])
    }

    @Test
    fun installAll_failureRecordedOnce() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("fail", result = FeatureInstallResult.FAILED_PERMANENT)
        registry.register(f)

        val r1 = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        val r2 = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, f.installCalls)
        assertTrue(r1[0] == FeatureInstallResult.FAILED_PERMANENT)
        assertTrue(r2[0] == FeatureInstallResult.FAILED_PERMANENT)
    }

    @Test
    fun installAll_exceptionBecomesTransient() {
        val registry = FeatureInstallRegistry()
        val f = object : FeatureDefinition {
            override val id = TestId("explode")
            override val name = "explode"
            override val preferenceKey = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult = throw IllegalStateException("boom")
        }
        registry.register(f)

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertTrue(results[0] == FeatureInstallResult.FAILED_TRANSIENT)
    }

    @Test
    fun register_sameDefinitionIsIdempotent() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("same")
        registry.register(f)
        registry.register(f)

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertEquals(FeatureInstallResult.INSTALLED, results[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun register_differentDefinitionSameIdThrows() {
        val registry = FeatureInstallRegistry()
        val sharedId = TestId("shared")
        val a = object : FeatureDefinition {
            override val id = sharedId
            override val name = "a"
            override val preferenceKey = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install() = FeatureInstallResult.INSTALLED
        }
        val b = object : FeatureDefinition {
            override val id = sharedId
            override val name = "b"
            override val preferenceKey = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install() = FeatureInstallResult.INSTALLED
        }
        registry.register(a)
        registry.register(b)
    }

    @Test
    fun onPreferenceChanged_callsInstalledFeature() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("statusbar", preferenceKey = "system_statusbarheight")
        registry.register(f)
        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        assertEquals(1, f.installCalls)

        registry.onPreferenceChanged("system_statusbarheight", PrefMap())

        assertEquals(1, f.installCalls)
        assertEquals(1, f.onPreferenceChangedCalls)
    }

    @Test
    fun onPreferenceChanged_marksEarlyNotInstalledAsRestartRequired() {
        val registry = FeatureInstallRegistry()
        val f = DummyFeature("early", phase = InstallPhase.MODULE_LOADED, preferenceKey = "system_statusbarheight")
        registry.register(f)

        registry.onPreferenceChanged("system_statusbarheight", PrefMap())
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.MODULE_LOADED, PrefMap(), collectResults = true)

        assertEquals(0, f.installCalls)
        assertEquals(1, results.size)
        assertTrue(results[0] == FeatureInstallResult.RESTART_LATER)
    }

    @Test
    fun markForReinstall_onlyResetsTransientFailures() {
        val registry = FeatureInstallRegistry()
        val permanent = DummyFeature("permanent", result = FeatureInstallResult.FAILED_PERMANENT)
        val transient = DummyFeature("transient", result = FeatureInstallResult.FAILED_TRANSIENT)
        registry.register(permanent)
        registry.register(transient)

        registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        assertEquals(1, permanent.installCalls)
        assertEquals(1, transient.installCalls)

        registry.markForReinstall("permanent")
        registry.markForReinstall("transient")

        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        assertEquals(1, permanent.installCalls)
        assertEquals(2, transient.installCalls)
        assertTrue(results[0] == FeatureInstallResult.FAILED_PERMANENT)
        assertTrue(results[1] == FeatureInstallResult.FAILED_TRANSIENT)
    }
}
