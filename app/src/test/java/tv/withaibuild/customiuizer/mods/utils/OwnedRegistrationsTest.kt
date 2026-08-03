package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedRegistrationsTest {

    @Test
    fun cleanupRunsOnlyStaleEntriesAndDropsThem() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("old") { cleaned.add("old-a") }
        registrations.register("old") { cleaned.add("old-b") }
        registrations.register("current") { cleaned.add("current") }

        registrations.cleanupWhere { it == "old" }

        assertEquals(setOf("old-a", "old-b"), cleaned.toSet())
        assertEquals(1, registrations.size)

        // A second pass must not run the already-removed cleanups again.
        cleaned.clear()
        registrations.cleanupWhere { it == "old" }
        assertTrue(cleaned.isEmpty())
        assertEquals(1, registrations.size)
    }

    @Test
    fun allowsMultipleRegistrationsPerOwner() {
        val registrations = OwnedRegistrations<String>()
        registrations.register("gen1") {}
        registrations.register("gen1") {}
        registrations.register("gen1") {}
        assertEquals(3, registrations.size)

        registrations.cleanupWhere { it == "gen1" }
        assertEquals(0, registrations.size)
    }

    @Test
    fun ordinaryCleanupFailureIsIsolatedPerEntry() {
        val registrations = OwnedRegistrations<String>()
        val cleaned = mutableListOf<String>()
        registrations.register("old") { cleaned.add("first") }
        registrations.register("old") { throw IllegalStateException("ROM removed the member") }
        registrations.register("old") { cleaned.add("last") }

        registrations.cleanupWhere { it == "old" }

        // The failing entry must not prevent the other stale registrations from being released,
        // and every stale entry must be dropped so the failure is not retried forever.
        assertEquals(setOf("first", "last"), cleaned.toSet())
        assertEquals(0, registrations.size)
    }

    @Test(expected = OutOfMemoryError::class)
    fun fatalCleanupErrorPropagates() {
        val registrations = OwnedRegistrations<String>()
        registrations.register("old") { throw OutOfMemoryError("fatal") }
        registrations.cleanupWhere { it == "old" }
    }

    @Test
    fun identityPredicateMatchesGenerationSemantics() {
        val registrations = OwnedRegistrations<Any>()
        val gen1 = Any()
        val gen2 = Any()
        val cleaned = mutableListOf<Any>()
        registrations.register(gen1) { cleaned.add(it) }
        registrations.register(gen2) { cleaned.add(it) }

        // Same pattern as SystemUIStatusBarHooks.cleanupStaleStatusBarRegistrations: everything
        // not owned by the current generation is stale, including entries whose owner was lost.
        val current: Any? = gen2
        registrations.cleanupWhere { it !== current }

        assertEquals(listOf<Any>(gen1), cleaned)
        assertEquals(1, registrations.size)
    }
}
