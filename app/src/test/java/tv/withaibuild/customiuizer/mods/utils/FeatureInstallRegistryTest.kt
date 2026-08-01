package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun lazySpec_disabledFeatureDoesNotCreateDefinition() {
        val registry = FeatureInstallRegistry()
        var createCalls = 0
        val spec = LazyFeatureSpec(
            id = TestId("lazy-off"),
            name = "Lazy Off",
            preferenceKey = "lazy_off",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { false },
            factory = {
                createCalls++
                DummyFeature("lazy-off")
            },
        )

        registry.register(spec)
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertTrue(results[0] == FeatureInstallResult.SKIPPED)
        assertEquals("disabled feature must not call factory", 0, createCalls)
    }

    @Test
    fun lazySpec_enabledFeatureCreatesAndInstalls() {
        val registry = FeatureInstallRegistry()
        var createCalls = 0
        val definition = object : FeatureDefinition {
            override val id = TestId("lazy-on")
            override val name = "Lazy On"
            override val preferenceKey = "lazy_on"
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            var installCalls = 0
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult {
                installCalls++
                return FeatureInstallResult.INSTALLED
            }
        }
        val spec = LazyFeatureSpec(
            id = definition.id,
            name = definition.name,
            preferenceKey = definition.preferenceKey,
            target = definition.target,
            phase = definition.phase,
            enabled = { true },
            factory = {
                createCalls++
                definition
            },
        )

        registry.register(spec)
        val results = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)

        assertEquals(1, results.size)
        assertTrue(results[0] == FeatureInstallResult.INSTALLED)
        assertEquals(1, createCalls)
        assertEquals(1, definition.installCalls)
    }

    @Test(expected = OutOfMemoryError::class)
    fun installOne_rethrowsOutOfMemoryErrorAndRollsBackState() {
        val registry = FeatureInstallRegistry()
        val spec = LazyFeatureSpec(
            id = TestId("oom"),
            name = "OOM",
            preferenceKey = "oom",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.PACKAGE_READY,
            enabled = { true },
            factory = { throw OutOfMemoryError("OOM in factory") },
        )
        registry.register(spec)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        } finally {
            assertTrue(FeatureInstallState.get(spec.id) == FeatureState.FAILED_TRANSIENT)
        }
    }

    @Test(expected = OutOfMemoryError::class)
    fun installOne_createdDefinitionRemovedWhenInstallThrowsOom() {
        val registry = FeatureInstallRegistry()
        val definition = object : FeatureDefinition {
            override val id = TestId("oom-install")
            override val name = "OOM Install"
            override val preferenceKey = "oom_install"
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult {
                throw OutOfMemoryError("OOM in install")
            }
        }
        val spec = LazyFeatureSpec(
            id = definition.id,
            name = definition.name,
            preferenceKey = definition.preferenceKey,
            target = definition.target,
            phase = definition.phase,
            enabled = { true },
            factory = { definition },
        )
        registry.register(spec)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap(), collectResults = true)
        } finally {
            assertTrue(FeatureInstallState.get(spec.id) == FeatureState.FAILED_TRANSIENT)
            assertNull(
                "active definition must be removed after install OOM",
                registry.activeDefinitionForTest(spec.id)
            )
        }
    }

    @Test
    fun onPreferenceChanged_earlyDisabledRemainsDisabled() {
        val registry = FeatureInstallRegistry()
        var factoryCalls = 0
        val spec = LazyFeatureSpec(
            id = TestId("early-disabled"),
            name = "Early Disabled",
            preferenceKey = "early_disabled",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.MODULE_LOADED,
            enabled = { prefs -> prefs.getBoolean("early_disabled") },
            factory = {
                factoryCalls++
                DummyFeature("early-disabled", phase = InstallPhase.MODULE_LOADED)
            },
        )
        registry.register(spec)

        registry.onPreferenceChanged("early_disabled", PrefMap())

        assertTrue(FeatureInstallState.get(spec.id) == FeatureState.NOT_INSTALLED)
        assertEquals("factory must not be called for disabled early feature", 0, factoryCalls)
    }

    @Test
    fun onPreferenceChanged_earlyEnabledRequiresRestart() {
        val registry = FeatureInstallRegistry()
        var factoryCalls = 0
        val spec = LazyFeatureSpec(
            id = TestId("early-enabled"),
            name = "Early Enabled",
            preferenceKey = "early_enabled",
            target = FeatureTarget.SYSTEM_UI,
            phase = InstallPhase.MODULE_LOADED,
            enabled = { prefs -> prefs.getBoolean("early_enabled") },
            factory = {
                factoryCalls++
                DummyFeature("early-enabled", phase = InstallPhase.MODULE_LOADED)
            },
        )
        registry.register(spec)

        val prefs = PrefMap().apply { put("early_enabled", true) }
        registry.onPreferenceChanged("early_enabled", prefs)

        assertTrue(FeatureInstallState.get(spec.id) == FeatureState.RESTART_REQUIRED)
        assertEquals("factory must not be called for early restart decision", 0, factoryCalls)
    }
}
