package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for [ReflectionCache].
 *
 * The cache must never store null in a [ConcurrentHashMap] and must memoise both positive and
 * negative dependency lookups so hook setup does not repeat reflection on subsequent calls.
 */
class ReflectionCacheTest {

    @Test
    fun getDepInstance_missingDependency_cachesNegativeResult() {
        val loader = javaClass.classLoader

        // A class that exists, but its "Dependency" is the SystemUI one which is not in the
        // test class loader.
        val result1 = ReflectionCache.getDepInstance(loader, "java.lang.String")
        val result2 = ReflectionCache.getDepInstance(loader, "java.lang.String")

        assertNull(result1)
        assertNull(result2)

        val depCache = getDepInstanceCache()
        assertNotNull(depCache)
        val cached = depCache[java.lang.String::class.java]
        assertNotNull("negative result must be cached as a non-null sentinel", cached)
    }

    @Test
    fun getDepInstance_missingClass_cachesMissingClass() {
        val loader = javaClass.classLoader

        val result1 = ReflectionCache.getDepInstance(loader, "does.not.exist.Class")
        val result2 = ReflectionCache.getDepInstance(loader, "does.not.exist.Class")

        assertNull(result1)
        assertNull(result2)

        val missingClasses = getMissingClasses()
        assertNotNull(missingClasses)
        // The missing-class cache is keyed by (classLoader, className).
        val key = getClassCacheKey(loader, "does.not.exist.Class")
        val cached = missingClasses[key]
        assertNotNull("missing class must be cached as a non-null sentinel", cached)
    }

    @Test
    fun getDepInstance_idempotent_sameResult() {
        val loader = javaClass.classLoader
        val r1 = ReflectionCache.getDepInstance(loader, "java.lang.Runnable")
        val r2 = ReflectionCache.getDepInstance(loader, "java.lang.Runnable")
        val r3 = ReflectionCache.getDepInstance(loader, "java.lang.Runnable")

        assertEquals(r1, r2)
        assertEquals(r2, r3)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getDepInstanceCache(): java.util.concurrent.ConcurrentHashMap<Class<*>, Any> {
        val field = ReflectionCache::class.java.getDeclaredField("depInstanceCache").apply { isAccessible = true }
        return field.get(ReflectionCache) as java.util.concurrent.ConcurrentHashMap<Class<*>, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getMissingClasses(): java.util.concurrent.ConcurrentHashMap<Any, Any> {
        val field = ReflectionCache::class.java.getDeclaredField("missingClasses").apply { isAccessible = true }
        return field.get(ReflectionCache) as java.util.concurrent.ConcurrentHashMap<Any, Any>
    }

    private fun getClassCacheKey(classLoader: ClassLoader?, className: String): Any {
        val constructor = ReflectionCache::class.java.declaredClasses
            .first { it.simpleName == "ClassCacheKey" }
            .getDeclaredConstructor(ClassLoader::class.java, String::class.java)
            .apply { isAccessible = true }
        return constructor.newInstance(classLoader, className)
    }
}
