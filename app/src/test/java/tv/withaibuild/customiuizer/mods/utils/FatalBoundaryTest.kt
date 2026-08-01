package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Tests for the public framework boundaries that must rethrow [OutOfMemoryError]
 * rather than converting it to a safe failure.  These boundaries also must not leave
 * behind a cached or registered state that looks like success after an OOM.
 */
class FatalBoundaryTest {

    private class OomClassLoader : ClassLoader(FatalBoundaryTest::class.java.classLoader) {
        val loaded = mutableListOf<String>()
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            if (name == "boom.Oom") throw OutOfMemoryError("simulated class load OOM")
            loaded.add(name ?: "null")
            return super.loadClass(name, resolve)
        }
    }

    private class FakeDependency {
        companion object {
            @JvmStatic
            fun get(clazz: Class<*>): Any? {
                if (throwOom) throw OutOfMemoryError("simulated dependency OOM")
                return if (ready) INSTANCE else null
            }

            var ready = false
            var throwOom = false
            val INSTANCE = Any()
        }
    }

    @After
    fun tearDown() {
        ReflectionCache.dependencyClassName = "com.android.systemui.Dependency"
        FakeDependency.ready = false
        FakeDependency.throwOom = false
        ReflectionCache.clearForTests()
    }

    @Test
    fun callbackGuard_swallowsRuntimeExceptionAndRuns() {
        var ran = false
        CallbackGuard.guarded {
            ran = true
            throw IllegalStateException("boom")
        }
        assertTrue("block must run", ran)
    }

    @Test(expected = OutOfMemoryError::class)
    fun callbackGuard_rethrowsOutOfMemoryError() {
        CallbackGuard.guarded {
            throw OutOfMemoryError("boom")
        }
    }

    @Test
    fun callbackGuard_returnsFallbackOnNonFatalError() {
        val result = CallbackGuard.guarded(fallback = 42) {
            throw IllegalStateException("boom")
        }
        assertEquals(42, result)
    }

    @Test(expected = OutOfMemoryError::class)
    fun callbackGuard_doesNotReturnFallbackOnOom() {
        CallbackGuard.guarded(fallback = 42) {
            throw OutOfMemoryError("boom")
        }
    }

    @Test
    fun featureInstallRegistry_installOom_leavesFailedTransientAndRethrows() {
        val registry = FeatureInstallRegistry()
        val feature = object : FeatureDefinition {
            override val id = object : FeatureId {
                override val id = 777
                override val name = "oomFeature"
            }
            override val name = "oomFeature"
            override val preferenceKey: String? = null
            override val target = FeatureTarget.SYSTEM_UI
            override val phase = InstallPhase.PACKAGE_READY
            override fun isEnabled(prefs: PrefMap) = true
            override fun install(): FeatureInstallResult {
                throw OutOfMemoryError("simulated install OOM")
            }
        }

        registry.register(feature)

        try {
            registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, PrefMap())
            fail("OutOfMemoryError must propagate")
        } catch (oom: OutOfMemoryError) {
            assertEquals(FeatureState.FAILED_TRANSIENT, FeatureInstallState.get(feature.id))
        }
    }

    @Test
    fun reflectionCache_classLookupOom_doesNotCacheClassMissing() {
        ReflectionCache.dependencyClassName = "does.not.exist.Dependency"
        val loader = OomClassLoader()

        try {
            ReflectionCache.getDepInstance(loader, "boom.Oom")
            fail("OutOfMemoryError must propagate")
        } catch (oom: OutOfMemoryError) {
            val state = ReflectionCache.loaderStateForTest(loader)
            assertNull("OOM must not be cached as ClassMissing", state?.classResults?.get("boom.Oom"))
        }
    }

    @Test
    fun reflectionCache_invocationTargetOom_unwrapsAndRethrows() {
        ReflectionCache.dependencyClassName = FakeDependency::class.java.name
        FakeDependency.throwOom = true
        val loader = FatalBoundaryTest::class.java.classLoader

        try {
            ReflectionCache.getDepInstance(loader, "java.lang.String")
            fail("OutOfMemoryError must propagate")
        } catch (oom: OutOfMemoryError) {
            val state = ReflectionCache.loaderStateForTest(loader)
            assertFalse(
                "OOM must not be cached as DependencyNotReady",
                state?.classResults?.get("java.lang.String") is DepResult.DependencyNotReady
            )
        }
    }
}
