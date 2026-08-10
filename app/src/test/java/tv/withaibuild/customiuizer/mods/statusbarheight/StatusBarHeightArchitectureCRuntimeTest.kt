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

        runtime.rememberStatusBar(owner)

        assertTrue(runtime.isKnownStatusBar(owner))
        assertEquals(1, runtime.knownCountForTest())
    }

    @Test
    fun remember_sameOwner_returnsExactSameWeakReference() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()

        val first = runtime.rememberStatusBar(owner)
        val second = runtime.rememberStatusBar(owner)

        assertSame(first, second)
        assertEquals(1, runtime.knownCountForTest())
    }

    @Test
    fun remember_sameOwner_snapshotRetainsSameWeakReference() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()

        val first = runtime.rememberStatusBar(owner)
        val snapshotAfterFirst = runtime.knownSnapshotForTest()
        val second = runtime.rememberStatusBar(owner)
        val snapshotAfterSecond = runtime.knownSnapshotForTest()

        assertSame(first, second)
        assertSame(first, snapshotAfterFirst[0])
        assertSame(first, snapshotAfterSecond[0])
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
    fun remember_explicitlyClearedWeakReference_isEvictedDuringDiscovery() {
        val runtime = StatusBarHeightRuntime()
        var owner: Any? = Any()
        val ref = runtime.rememberStatusBar(owner!!)

        assertEquals(1, runtime.knownCountForTest())

        ref.clear()
        owner = null

        val newOwner = Any()
        runtime.rememberStatusBar(newOwner)

        // The new owner is retained, and the dead ref is compacted away.
        assertTrue(runtime.isKnownStatusBar(newOwner))
        assertEquals(1, runtime.knownCountForTest())
    }

    @Test
    fun isKnownStatusBar_boundedScanNoAllocation() {
        val runtime = StatusBarHeightRuntime()
        val owners = List(4) { Any() }
        owners.forEach { runtime.rememberStatusBar(it) }

        repeat(100) {
            assertTrue(runtime.isKnownStatusBar(owners[it % 4]))
        }
    }

    @Test
    fun markLatestIfKnown_unknown_returnsFalse() {
        val runtime = StatusBarHeightRuntime()
        assertFalse(runtime.markLatestIfKnown(Any()))
        assertNull(runtime.latestRefForTest())
    }

    @Test
    fun markLatestIfKnown_known_returnsTrue() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()
        val ref = runtime.rememberStatusBar(owner)

        assertTrue(runtime.markLatestIfKnown(owner))
        assertSame(ref, runtime.latestRefForTest())
    }

    @Test
    fun markLatestIfKnown_reusesExactWeakReference() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()
        val ref = runtime.rememberStatusBar(owner)

        repeat(10) {
            assertTrue(runtime.markLatestIfKnown(owner))
        }

        assertSame(ref, runtime.latestRefForTest())
    }

    @Test
    fun markLatestIfKnown_multipleOwners_latestFollowsLastMarked() {
        val runtime = StatusBarHeightRuntime()
        val a = Any()
        val b = Any()
        val refA = runtime.rememberStatusBar(a)
        val refB = runtime.rememberStatusBar(b)

        assertTrue(runtime.markLatestIfKnown(a))
        assertSame(refA, runtime.latestRefForTest())

        assertTrue(runtime.markLatestIfKnown(b))
        assertSame(refB, runtime.latestRefForTest())

        assertSame(refA, runtime.rememberStatusBar(a))
        assertSame(refA, runtime.latestRefForTest())
    }

    @Test
    fun markLatestIfKnown_repeatedMarkDoesNotReallocate() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()
        val ref = runtime.rememberStatusBar(owner)

        val snapshot = runtime.knownSnapshotForTest()
        repeat(10) {
            runtime.markLatestIfKnown(owner)
        }

        assertSame(ref, runtime.latestRefForTest())
        assertSame(ref, snapshot[0])
    }

    @Test
    fun latestRef_reusesSameWeakReferenceForSameOwner() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()

        val ref = runtime.rememberStatusBar(owner)
        runtime.rememberStatusBar(owner)

        assertSame(ref, runtime.latestRefForTest())
        assertSame(owner, runtime.latestRefForTest()?.get())
    }

    @Test
    fun reset_knownStatusBars_publishesFreshEmptySnapshot() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()
        runtime.rememberStatusBar(owner)

        val before = runtime.knownSnapshotForTest()
        runtime.resetKnownStatusBars()

        assertFalse(runtime.isKnownStatusBar(owner))
        assertEquals(0, runtime.knownCountForTest())
        assertNull(runtime.latestRefForTest())
        assertFalse(runtime.typeMatchObserved)
        assertEquals(4096, runtime.fallbackProbeBudget.get())
        assertEquals(-1L, runtime.lastRefreshGeneration.get())

        val after = runtime.knownSnapshotForTest()
        assertNotSame(before, after)
        assertTrue(after.all { it == null })
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

        val forbidden = listOf("Any", "Object", "WindowState", "DisplayContent", "Context", "View", "Activity")
        val strongOwners = declared.filter { field ->
            val simpleName = field.type.simpleName
            forbidden.contains(simpleName) && !field.type.isArray
        }

        assertTrue("runtime must not hold strong Android owner fields", strongOwners.isEmpty())
    }

    @Test
    fun knownArray_containsOnlyWeakReferences() {
        val runtime = StatusBarHeightRuntime()
        val owner = Any()
        runtime.rememberStatusBar(owner)

        val snapshot = runtime.knownSnapshotForTest()
        assertNotNull(snapshot[0])
        assertEquals("WeakReference", snapshot[0]!!.javaClass.simpleName)
        assertSame(owner, snapshot[0]!!.get())
    }
}
