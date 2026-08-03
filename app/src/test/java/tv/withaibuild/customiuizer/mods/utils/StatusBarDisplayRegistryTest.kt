package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarDisplayRegistryTest {

    @Test
    fun bindAttachesOwnerToDisplayAndProvidesState() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val state = registry.bind(owner, 0)

        assertNotNull(state)
        assertSame(owner, state.generation?.get())
        assertSame(state, registry.get(0))
    }

    @Test
    fun pendingOwnersAreIsolated() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val a = Any()
        val b = Any()
        val stateA = registry.getOrCreatePending(a)
        val stateB = registry.getOrCreatePending(b)

        assertNotSame(stateA, stateB)
        assertEquals(2, registry.allStates().size)
    }

    @Test
    fun sameDisplayReplacementCleansOldGeneration() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val oldOwner = Any()
        val newOwner = Any()
        val cleanups = mutableListOf<Any>()

        val oldState = registry.bind(oldOwner, 0)
        oldState.registrations.register(oldOwner) { cleanups.add(it) }

        val newState = registry.bind(newOwner, 0)

        assertSame(newState, registry.get(0))
        assertEquals(listOf(oldOwner), cleanups)
        assertEquals(0, oldState.registrations.size)
    }

    @Test
    fun crossDisplayIsolation() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val display0Owner = Any()
        val display1Owner = Any()

        registry.bind(display0Owner, 0)
        registry.bind(display1Owner, 1)

        assertEquals(2, registry.allStates().size)
        assertSame(display0Owner, registry.get(0)?.generation?.get())
        assertSame(display1Owner, registry.get(1)?.generation?.get())

        // Replacing display 0 must not touch display 1.
        val newDisplay0Owner = Any()
        registry.bind(newDisplay0Owner, 0)
        assertSame(display1Owner, registry.get(1)?.generation?.get())
    }

    @Test
    fun sameOwnerReattachKeepsState() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val state = registry.bind(owner, 0)
        state.registrations.register(owner) { }

        val again = registry.bind(owner, 0)
        assertSame(state, again)
        assertEquals(1, again.registrations.size)
    }

    @Test
    fun pendingMigratesToDisplayOnBind() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        val owner = Any()
        val pending = registry.getOrCreatePending(owner)
        pending.registrations.register(owner) { }

        val bound = registry.bind(owner, 0)
        assertSame(pending, bound)
        assertSame(bound, registry.get(0))
        assertTrue(registry.allStates().none { it === pending && it !== bound })
    }

    @Test
    fun pruneRemovesDeadStates() {
        val registry = StatusBarDisplayRegistry<Any, Any>()
        var owner0: Any? = Any()
        var owner1: Any? = Any()
        registry.bind(owner0!!, 0)
        registry.bind(owner1!!, 1)

        assertEquals(2, registry.allStates().size)

        owner0 = null
        owner1 = null
        @Suppress("UNUSED_VARIABLE")
        val forceGc = ByteArray(1024 * 1024)
        System.gc()
        Thread.sleep(50)

        registry.prune()
        assertEquals(0, registry.allStates().size)
    }

    @Test
    fun multiDisplaySecondRowsAreIndependent() {
        val registry = StatusBarDisplayRegistry<Any, String>()
        val owner0 = Any()
        val owner1 = Any()
        val state0 = registry.bind(owner0, 0)
        val state1 = registry.bind(owner1, 1)

        state0.secondRow = java.lang.ref.WeakReference("row-0")
        state1.secondRow = java.lang.ref.WeakReference("row-1")

        assertEquals("row-0", state0.secondRow?.get())
        assertEquals("row-1", state1.secondRow?.get())
    }
}
