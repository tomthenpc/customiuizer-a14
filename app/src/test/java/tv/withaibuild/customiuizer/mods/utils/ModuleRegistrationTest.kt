package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for [ModuleHelper.replaceModuleRegistration].
 */
class ModuleRegistrationTest {

    @After
    fun tearDown() {
        getModuleRegistrationsMap().clear()
        getStaleModuleRegistrationsMap().clear()
    }

    @Test
    fun replaceModuleRegistration_replacesOldCleanup() {
        val oldRan = AtomicInteger(0)
        val newRan = AtomicInteger(0)

        ModuleHelper.replaceModuleRegistration("regKey") { oldRan.incrementAndGet() }
        assertEquals("first cleanup must not run yet", 0, oldRan.get())

        ModuleHelper.replaceModuleRegistration("regKey") { newRan.incrementAndGet() }
        assertEquals("old cleanup must have run once", 1, oldRan.get())
        assertEquals("new cleanup must not have run yet", 0, newRan.get())
    }

    @Test
    fun replaceModuleRegistration_staleCleanupRetriesOnNextCall() {
        val attempts = AtomicInteger(0)
        val cleanup = Runnable {
            attempts.incrementAndGet()
            if (attempts.get() == 1) throw IllegalStateException("simulated cleanup failure")
        }

        ModuleHelper.replaceModuleRegistration("staleRegKey", cleanup)
        assertEquals("first cleanup must not run on first install", 0, attempts.get())

        ModuleHelper.replaceModuleRegistration("staleRegKey") {}
        assertEquals("first cleanup must run once when replaced", 1, attempts.get())
        assertTrue("stale queue must contain the failed cleanup", getStaleModuleRegistrationsMap().containsKey("staleRegKey"))

        ModuleHelper.replaceModuleRegistration("staleRegKey") {}
        assertEquals("cleanup must be retried exactly once", 2, attempts.get())
        assertFalse("stale queue must be empty after successful retry", getStaleModuleRegistrationsMap().containsKey("staleRegKey"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun getModuleRegistrationsMap(): ConcurrentHashMap<String, Any> {
        val field = ModuleHelper::class.java.getDeclaredField("moduleRegistrations")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStaleModuleRegistrationsMap(): ConcurrentHashMap<String, Any> {
        val field = ModuleHelper::class.java.getDeclaredField("staleModuleRegistrations")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, Any>
    }
}
