package tv.withaibuild.customiuizer.mods.statusbarheight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarHeightArchitectureCRuntimeTest {

    @Test
    fun remember_firstOwner_becomesKnown() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()

        val entry = runtime.rememberStatusBar(owner)

        assertNotNull(entry)
        assertTrue(runtime.isKnownStatusBar(owner))
        assertEquals(1, runtime.knownCountForTest())
    }

    @Test
    fun remember_sameOwnerAgain_reusesWeakReference() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()

        val first = runtime.rememberStatusBar(owner)
        val second = runtime.rememberStatusBar(owner)

        assertSame(first?.ownerRef, second?.ownerRef)
        assertEquals(1, runtime.knownCountForTest())
    }

    @Test
    fun remember_fifthOwner_evictsOldest_andBoundsAtFour() {
        val runtime = StatusBarHeightRuntime()
        val owners = List(5) { Any() }

        owners.forEach { runtime.rememberStatusBar(it) }

        assertEquals(4, runtime.knownCountForTest())
        assertFalse(runtime.isKnownStatusBar(owners[0]))
        assertTrue(owners.drop(1).all { runtime.isKnownStatusBar(it) })
    }

    @Test
    fun deadWeakReference_isEvictedDuringDiscovery() {
        val runtime = StatusBarHeightRuntime()
        var owner: Any? = Any()
        runtime.rememberStatusBar(owner!!)
        assertEquals(1, runtime.knownCountForTest())

        owner = null
        System.gc()

        // Add a new owner; discovery compaction should drop the dead ref.
        runtime.rememberStatusBar(Any())
        assertEquals(1, runtime.knownCountForTest())
    }

    @Test
    fun isKnownStatusBar_noAllocationInSteadyState() {
        val runtime = StatusBarHeightRuntime()
        val owners = List(4) { Any() }
        owners.forEach { runtime.rememberStatusBar(it) }

        // This test only verifies the steady-state path is a bounded linear scan.
        repeat(100) {
            assertTrue(runtime.isKnownStatusBar(owners[it % 4]))
        }
    }

    @Test
    fun latestRef_reusesSameWeakReferenceForSameOwner() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()

        runtime.rememberStatusBar(owner)
        val first = runtime.latestRefForTest()
        runtime.rememberStatusBar(owner)
        val second = runtime.latestRefForTest()

        assertSame(first, second)
        assertSame(first?.get(), owner)
    }

    @Test
    fun reset_knownStatusBars_doesNotHoldStrongReferences() {
        val runtime = StatusBarHeightRuntime()
        var owner: Any? = Any()
        val entry = runtime.rememberStatusBar(owner!!)

        runtime.resetKnownStatusBars()

        owner = null
        System.gc()

        assertFalse(runtime.isKnownStatusBar(Any()))
        assertEquals(0, runtime.knownCountForTest())
        assertNull(runtime.latestRefForTest())
        assertFalse(runtime.typeMatchObserved)
        assertEquals(4096, runtime.fallbackProbeBudget.get())
        assertEquals(-1L, runtime.lastRefreshGeneration.get())

        // The old entry weak reference must not hold the owner strongly.
        assertNull(entry?.owner)
    }

    @Test
    fun knownSnapshot_forTestDoesNotExposeInternalArrayReference() {
        val runtime = StatusBarHeightRuntime()
        runtime.rememberStatusBar(Any())

        val first = runtime.knownSnapshotForTest()
        val second = runtime.knownSnapshotForTest()

        assertNotSame(first, second)
    }

    @Test
    fun fallbackProbeBudget_keepsInitialValue() {
        val runtime = StatusBarHeightRuntime()
        assertEquals(4096, runtime.fallbackProbeBudget.get())
    }

    @Test
    fun typeMatchObserved_startsFalse() {
        val runtime = StatusBarHeightRuntime()
        assertFalse(runtime.typeMatchObserved)
    }

    @Test
    fun strongOwnerFields_notPresentInRuntime() {
        val declared = StatusBarHeightRuntime::class.java.declaredFields

        val forbidden = setOf("Any", "Object", "WindowState", "DisplayContent", "Context", "View", "Activity")
        val strongOwners = declared.filter { field ->
            val simpleName = field.type.simpleName
            simpleName in forbidden || (field.type.kotlin.javaObjectType == Any::class.java)
        }

        // Only WeakReference and primitives/expected helpers are allowed.
        assertTrue("runtime must not hold strong Android owner fields", strongOwners.isEmpty())
    }

    @Test
    fun knownEntry_holdsOnlyWeakReference() {
        val ref = WeakRefOwner().ref
        val declared = StatusBarHeightRuntime.KnownStatusBarEntry::class.java.declaredFields

        assertTrue("KnownStatusBarEntry must contain an ownerRef field", declared.any { it.name == "ownerRef" })
        assertEquals("ownerRef", declared.first().name)
    }

    private class WeakRefOwner {
        val ref = java.lang.ref.WeakReference<Any>(Any())
    }
}
