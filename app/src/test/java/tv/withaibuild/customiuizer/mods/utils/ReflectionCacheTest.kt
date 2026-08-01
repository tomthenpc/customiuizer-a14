package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A fake [com.android.systemui.Dependency] stand-in used to exercise the four
 * ReflectionCache states.
 *
 * It is package-private and only referenced by name so that the cache under test
 * has to locate it, resolve its `get(Class)` method, and invoke it just like it
 * would with the real SystemUI class.
 */
private class FakeDependency {
    companion object {
        @JvmField
        var ready: Boolean = false

        @JvmStatic
        fun get(clazz: Class<*>): Any? = if (ready) INSTANCE else null

        val INSTANCE = Any()
    }
}

/**
 * Tests for [ReflectionCache].
 *
 * The cache must distinguish CLASS_MISSING, METHOD_MISSING, DEPENDENCY_NOT_READY
 * and DEPENDENCY_FOUND; it must cache negatives per ClassLoader, allow retry after
 * a safe lifecycle, and stay bounded.
 */
class ReflectionCacheTest {

    @Before
    fun setUp() {
        ReflectionCache.clearForTests()
    }

    @After
    fun tearDown() {
        ReflectionCache.dependencyClassName = "com.android.systemui.Dependency"
        FakeDependency.ready = false
        ReflectionCache.clearForTests()
    }

    @Test
    fun getDepInstance_missingClass_cachesClassMissing() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        val className = "does.not.exist.Class"

        assertNull(ReflectionCache.getDepInstance(loader, className))
        assertNull(ReflectionCache.getDepInstance(loader, className))

        val state = ReflectionCache.loaderStateForTest(loader)
        assertNotNull(state)
        assertTrue(
            "missing class must be cached as ClassMissing",
            state!!.classResults[className] is DepResult.ClassMissing
        )
    }

    @Test
    fun getDepInstance_missingMethod_cachesMethodMissingPerLoader() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        ReflectionCache.dependencyClassName = "does.not.exist.Dependency"

        assertNull(ReflectionCache.getDepInstance(loader, "java.lang.String"))
        assertNull(ReflectionCache.getDepInstance(loader, "java.lang.Runnable"))

        val state = ReflectionCache.loaderStateForTest(loader)
        assertNotNull(state)
        assertTrue(
            "first missing dependency method must be cached as MethodMissing",
            state!!.classResults["java.lang.String"] is DepResult.MethodMissing
        )
        assertTrue(
            "second class must also be cached as MethodMissing",
            state.classResults["java.lang.Runnable"] is DepResult.MethodMissing
        )
    }

    @Test
    fun getDepInstance_dependencyNotReady_retriesAfterSafeLifecycle() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        ReflectionCache.dependencyClassName = FakeDependency::class.java.name
        val className = "java.lang.String"

        FakeDependency.ready = false

        // Not ready is cached within the current lifecycle.
        assertNull(ReflectionCache.getDepInstance(loader, className))
        assertNull(ReflectionCache.getDepInstance(loader, className))

        val state = ReflectionCache.loaderStateForTest(loader)
        assertTrue(
            "null Dependency.get result must be cached as DependencyNotReady",
            state!!.classResults[className] is DepResult.DependencyNotReady
        )

        // Make the dependency ready, but without a lifecycle signal the cached
        // negative result must still be returned.
        FakeDependency.ready = true
        assertNull(ReflectionCache.getDepInstance(loader, className))

        // A safe lifecycle allows retry.
        ReflectionCache.onSafeLifecycle(loader)
        val found = ReflectionCache.getDepInstance(loader, className)
        assertSame(FakeDependency.INSTANCE, found)

        // Found results are stable and returned without reflection on the hot path.
        assertSame(found, ReflectionCache.getDepInstance(loader, className))
    }

    @Test
    fun getDepInstance_dependencyFound_idempotent() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        ReflectionCache.dependencyClassName = FakeDependency::class.java.name
        FakeDependency.ready = true

        val r1 = ReflectionCache.getDepInstance(loader, "java.lang.Runnable")
        val r2 = ReflectionCache.getDepInstance(loader, "java.lang.Runnable")
        val r3 = ReflectionCache.getDepInstance(loader, "java.lang.Runnable")

        assertNotNull(r1)
        assertSame(r1, r2)
        assertSame(r2, r3)

        val state = ReflectionCache.loaderStateForTest(loader)
        assertTrue(
            "positive result must be cached as DependencyFound",
            state!!.classResults["java.lang.Runnable"] is DepResult.DependencyFound
        )
    }

    @Test
    fun getDepInstance_isolatedPerClassLoader() {
        val loader1 = object : ClassLoader(javaClass.classLoader) {}
        val loader2 = object : ClassLoader(javaClass.classLoader) {}
        ReflectionCache.dependencyClassName = FakeDependency::class.java.name
        FakeDependency.ready = true

        val r1 = ReflectionCache.getDepInstance(loader1, "java.lang.Runnable")
        val r2 = ReflectionCache.getDepInstance(loader2, "java.lang.Runnable")

        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(r1, ReflectionCache.getDepInstance(loader1, "java.lang.Runnable"))
        assertEquals(r2, ReflectionCache.getDepInstance(loader2, "java.lang.Runnable"))
    }

    @Test
    fun loaderCache_isBounded() {
        ReflectionCache.dependencyClassName = "does.not.exist.Dependency"

        for (i in 0 until 100) {
            val loader = object : ClassLoader(javaClass.classLoader) {}
            ReflectionCache.getDepInstance(loader, "java.lang.Runnable")
        }

        val loaders = ReflectionCache.loaderCountForTest()
        assertTrue(
            "global loader cache must stay within ${ReflectionCache.MAX_LOADERS}, was $loaders",
            loaders <= ReflectionCache.MAX_LOADERS
        )
    }

    @Test(expected = OutOfMemoryError::class)
    fun getDepInstance_dependencyMethodOom_doesNotPolluteState() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        ReflectionCache.dependencyClassName = FakeDependency::class.java.name
        ReflectionCache.dependencyMethodThrowableForTest = OutOfMemoryError("OOM in getDeclaredMethod")

        try {
            ReflectionCache.getDepInstance(loader, "java.lang.String")
        } finally {
            val state = ReflectionCache.loaderStateForTest(loader)
            assertNotNull(state)
            assertFalse(
                "dependencyMethodResolved must not be set after OOM",
                state!!.dependencyMethodResolved
            )
            assertNull(
                "dependencyMethod must not be set after OOM",
                state.dependencyMethod
            )
        }
    }

    @Test
    fun classResultCache_isBounded() {
        val loader = object : ClassLoader(javaClass.classLoader) {}
        ReflectionCache.dependencyClassName = "does.not.exist.Dependency"

        for (i in 0 until 100) {
            ReflectionCache.getDepInstance(loader, "does.not.exist.Class$i")
        }

        val state = ReflectionCache.loaderStateForTest(loader)
        assertNotNull(state)
        assertTrue(
            "per-loader class cache must stay within ${ReflectionCache.MAX_CLASSES_PER_LOADER}, was ${state!!.classResults.size}",
            state.classResults.size <= ReflectionCache.MAX_CLASSES_PER_LOADER
        )
    }
}
