package tv.withaibuild.customiuizer.mods.utils

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class FeatureInstallRegistryTest {

    @Before
    fun reset() {
        FeatureInstallState.reset()
        HookDiagnostics.reset()
        HookDiagnostics.currentProcessName = "test"
    }

    @Test
    fun featureRegistry_rethrowsThreadDeath() {
        val feature = featureThat { throw ThreadDeath() }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("ThreadDeath must propagate")
        } catch (t: ThreadDeath) {
            assertTrue("ThreadDeath propagated", true)
        }

        assertEquals(
            "state must roll back to FAILED_TRANSIENT before rethrow",
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_rethrowsVirtualMachineError() {
        val feature = featureThat { throw TestVirtualMachineError() }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("VirtualMachineError must propagate")
        } catch (e: VirtualMachineError) {
            assertTrue("expected custom VirtualMachineError", e is TestVirtualMachineError)
        }

        assertEquals(
            "state must roll back to FAILED_TRANSIENT before rethrow",
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_rethrowsWrappedFatal() {
        val feature = featureThat { throw InvocationTargetException(ThreadDeath()) }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("wrapped ThreadDeath must propagate")
        } catch (t: ThreadDeath) {
            assertTrue("wrapped ThreadDeath propagated", true)
        }

        assertEquals(
            "state must roll back to FAILED_TRANSIENT before rethrow",
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_executionExceptionWrappingVirtualMachineError_propagates() {
        val feature = featureThat { throw ExecutionException(TestVirtualMachineError()) }
        val registry = FeatureInstallRegistry()
        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("ExecutionException-wrapped VirtualMachineError must propagate")
        } catch (e: VirtualMachineError) {
            assertTrue("expected unwrapped VirtualMachineError", e is TestVirtualMachineError)
        }

        assertEquals(
            FeatureState.FAILED_TRANSIENT,
            FeatureInstallState.get(feature.id)
        )
    }

    @Test
    fun featureRegistry_nonFatalStillReturnsFailedTransient() {
        val first = featureThat { throw RuntimeException("non-fatal") }
        val second = featureThat { FeatureInstallResult.INSTALLED }

        val registry = FeatureInstallRegistry()
        registry.register(first)
        registry.register(second)

        val results = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertEquals(
            listOf(FeatureInstallResult.FAILED_TRANSIENT, FeatureInstallResult.INSTALLED),
            results
        )
        assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(first.id))
        assertEquals(FeatureState.INSTALLED, FeatureInstallState.get(second.id))
    }

    @Test
    fun featureRegistry_nonFatal_doesNotStopRemainingInstalls() {
        val first = featureThat { throw RuntimeException("non-fatal") }
        val second = featureThat { FeatureInstallResult.INSTALLED }

        val registry = FeatureInstallRegistry()
        registry.register(first)
        registry.register(second)

        val results = registry.installAll(
            FeatureTarget.SYSTEM_UI,
            InstallPhase.PACKAGE_READY,
            PrefMap(),
            collectResults = true
        )

        assertEquals(2, results.size)
        assertEquals(FeatureInstallResult.FAILED_TRANSIENT, results[0])
        assertEquals(FeatureInstallResult.INSTALLED, results[1])
    }

    private fun featureThat(install: () -> FeatureInstallResult): FeatureDefinition {
        val id = object : FeatureId {
            override val id = ids.nextId()
            override val name = "test-feature-${ids.nextId()}"
        }
        return object : FeatureDefinition {
            override val id = id
            override val name = "test"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install() = install()
        }
    }

    private class TestVirtualMachineError : VirtualMachineError("test virtual machine error")

    private object ids {
        private var counter = 10000
        fun nextId(): Int = counter--
    }
}
