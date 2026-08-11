package tv.withaibuild.customiuizer.mods.volumedialogautohide

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Component tests for [VolumeDialogAutohideDelayRuntimeState].
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class VolumeDialogAutohideDelayRuntimeStateTest {

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
        VolumeDialogAutohideDelayRuntimeState.resetForTest()
        PreferenceObserverRegistry.resetForTest()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        VolumeDialogAutohideDelayRuntimeState.resetForTest()
        PreferenceObserverRegistry.resetForTest()
    }

    @Test
    fun initialSnapshot_isNullBeforeInitialize() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        assertNull(state.snapshotRef.get())
    }

    @Test
    fun initialize_buildsFromOneSource() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 100,
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()
        assertNotNull(snapshot)
        assertEquals(100, snapshot!!.expanded)
        assertEquals(50, snapshot.collapsed)
    }

    @Test
    fun initialize_typeMismatchValue_returnsDefaultForThatKey() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to "not an int",
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()!!
        assertEquals(0, snapshot.expanded)
        assertEquals(50, snapshot.collapsed)
    }

    @Test
    fun onPreferenceChanged_relevantExpandedKey_rebuilds() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 200,
                "system_volumedialogdelay_collapsed" to 100,
            ),
        )
        state.onPreferenceChanged("system_volumedialogdelay_expanded")

        val snapshot = state.snapshotRef.get()!!
        assertEquals(200, snapshot.expanded)
    }

    @Test
    fun onPreferenceChanged_relevantCollapsedKey_rebuilds() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 200,
                "system_volumedialogdelay_collapsed" to 100,
            ),
        )
        state.onPreferenceChanged("system_volumedialogdelay_collapsed")

        val snapshot = state.snapshotRef.get()!!
        assertEquals(100, snapshot.collapsed)
    }

    @Test
    fun onPreferenceChanged_nullKey_rebuilds() {
        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 999,
                "system_volumedialogdelay_collapsed" to 111,
            ),
        )
        state.onPreferenceChanged(null)

        val snapshot = state.snapshotRef.get()!!
        assertEquals(999, snapshot.expanded)
        assertEquals(111, snapshot.collapsed)
    }

    @Test
    fun onPreferenceChanged_irrelevantKey_doesNotRebuild() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 100,
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 999,
                "system_volumedialogdelay_collapsed" to 111,
            ),
        )
        state.onPreferenceChanged("system_volumeblur_collapsed")

        val snapshot = state.snapshotRef.get()!!
        assertEquals(100, snapshot.expanded)
        assertEquals(50, snapshot.collapsed)
    }

    @Test
    fun noCallbackTimeLazyBuild() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 100,
                "system_volumedialogdelay_collapsed" to 50,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        // Do not call initialize() — simulate callback racing with initial refresh.
        val snapshot = state.snapshotRef.get()
        assertNull(snapshot)
    }

    @Test
    fun install_returnsSameInstanceOnSequentialCalls() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 111,
                "system_volumedialogdelay_collapsed" to 222,
            ),
        )

        val first = VolumeDialogAutohideDelayRuntimeState.install()
        val second = VolumeDialogAutohideDelayRuntimeState.install()

        assertSame("install must return the same process singleton", first, second)

        val snapshot = first.snapshotRef.get()
        assertNotNull(snapshot)
        assertEquals(111, snapshot!!.expanded)
        assertEquals(222, snapshot.collapsed)
    }

    @Test
    fun install_concurrentCallersAllReceiveSameInitializedInstance() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 111,
                "system_volumedialogdelay_collapsed" to 222,
            ),
        )

        val threads = (0 until 8).map { _ ->
            object : Thread() {
                var returned: VolumeDialogAutohideDelayRuntimeState? = null
                override fun run() {
                    returned = VolumeDialogAutohideDelayRuntimeState.install()
                }
            }
        }

        val start = CountDownLatch(1)
        threads.forEach { it.start() }
        start.countDown()

        threads.forEach { it.join(2_000) }
        threads.forEach { assertTrue("all install threads must complete", !it.isAlive) }

        val first = threads[0].returned!!
        threads.forEach {
            assertSame("concurrent install must return the same instance", first, it.returned)
        }

        val snapshot = first.snapshotRef.get()!!
        assertEquals(111, snapshot.expanded)
        assertEquals(222, snapshot.collapsed)

        val observerCount = processScopedObserverCount()
        assertEquals("observer must be registered exactly once", 1, observerCount)
    }

    @Test
    fun install_initialRefreshFatalThenRetryReusesSameStateAndDoesNotDuplicateObserver() {
        val refreshCallCount = AtomicInteger(0)
        val values = mapOf(
            "system_volumedialogdelay_expanded" to 111,
            "system_volumedialogdelay_collapsed" to 222,
        )

        val runtimeState = VolumeDialogAutohideDelayRuntimeState {
            val call = refreshCallCount.incrementAndGet()
            if (call == 1) throw OutOfMemoryError("simulated fatal initial refresh")
            values
        }

        try {
            VolumeDialogAutohideDelayRuntimeState.install(runtimeState)
            fail("Expected OutOfMemoryError from initial refresh")
        } catch (e: OutOfMemoryError) {
            assertNull("snapshot must be cleared after fatal refresh", runtimeState.snapshotRef.get())
            assertEquals("observer must be registered before fatal is rethrown", 1, processScopedObserverCount())
            assertFalse(VolumeDialogAutohideDelayRuntimeState.isInstalled())
        }

        val installed = VolumeDialogAutohideDelayRuntimeState.install(runtimeState)

        assertSame("retry must return the same state", runtimeState, installed)
        assertNotNull("snapshot must be published after retry", installed.snapshotRef.get())
        assertEquals(111, installed.snapshotRef.get()!!.expanded)
        assertEquals(222, installed.snapshotRef.get()!!.collapsed)

        val observerCount = processScopedObserverCount()
        assertEquals("observer must still be registered exactly once", 1, observerCount)
        assertEquals(2, refreshCallCount.get())
    }

    @Test
    fun install_publishedInstanceIsCompleteBeforeVolatileFlag() {
        val runtimeState = VolumeDialogAutohideDelayRuntimeState {
            mapOf(
                "system_volumedialogdelay_expanded" to 777,
                "system_volumedialogdelay_collapsed" to 888,
            )
        }

        val installed = VolumeDialogAutohideDelayRuntimeState.install(runtimeState)

        assertTrue(VolumeDialogAutohideDelayRuntimeState.isInstalled())
        assertSame(runtimeState, installed)
        assertEquals(1, processScopedObserverCount())
        assertEquals(777, installed.snapshotRef.get()!!.expanded)
        assertEquals(888, installed.snapshotRef.get()!!.collapsed)
    }

    @Test
    fun initialize_ordinaryRefreshFailure_clearsPreviousSnapshot() {
        val state = VolumeDialogAutohideDelayRuntimeState {
            throw RuntimeException("simulated refresh failure")
        }
        state.snapshotRef.set(VolumeDialogAutohideDelaySnapshot(100, 50))
        state.initialize()

        assertNull("snapshot must be cleared after ordinary refresh failure", state.snapshotRef.get())
    }

    @Test(expected = OutOfMemoryError::class)
    fun initialize_fatalRefreshFailure_clearsThenRethrows() {
        val state = VolumeDialogAutohideDelayRuntimeState {
            throw OutOfMemoryError("simulated fatal refresh failure")
        }
        state.snapshotRef.set(VolumeDialogAutohideDelaySnapshot(100, 50))

        try {
            state.initialize()
        } catch (e: OutOfMemoryError) {
            assertNull("snapshot must be cleared before fatal rethrow", state.snapshotRef.get())
            throw e
        }
    }

    @Test
    fun refreshWaitingBehindLockCapturesLatestGeneration() {
        val mapA = mapOf(
            "system_volumedialogdelay_expanded" to 1,
            "system_volumedialogdelay_collapsed" to 2,
        )
        val mapB = mapOf(
            "system_volumedialogdelay_expanded" to 3,
            "system_volumedialogdelay_collapsed" to 4,
        )

        val blockingMapA = BlockingMap(mapA)
        val blockingLatch = CountDownLatch(1)

        var callCount = 0
        val state = VolumeDialogAutohideDelayRuntimeState {
            if (++callCount == 1) {
                blockingMapA
            } else {
                MainModule.mPrefs.getAll()
            }
        }

        val firstBlocked = CountDownLatch(1)
        val firstRefresh = Thread {
            state.initialize()
            firstBlocked.countDown()
        }

        firstRefresh.start()
        assertTrue(blockingMapA.awaitEntered(2, TimeUnit.SECONDS))

        // While the first refresh holds refreshLock, update the backing PrefMap and start a second refresh.
        MainModule.mPrefs.replaceSnapshot(mapB)
        val secondRefresh = Thread {
            state.onPreferenceChanged("system_volumedialogdelay_expanded")
        }
        secondRefresh.start()

        // Give the second thread a moment to become parked on refreshLock.
        Thread.sleep(50)

        // Release the first refresh; it publishes A.
        blockingMapA.release(blockingLatch)
        firstBlocked.await(2, TimeUnit.SECONDS)

        // The second refresh now acquires the lock and must see the latest generation B.
        secondRefresh.join(2_000)
        assertTrue("second refresh must complete", !secondRefresh.isAlive)

        val snapshot = state.snapshotRef.get()!!
        assertEquals("final snapshot must reflect generation B", 3, snapshot.expanded)
        assertEquals(4, snapshot.collapsed)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the number of process-scoped observers currently registered.
     */
    private fun processScopedObserverCount(): Int {
        val field = PreferenceObserverRegistry::class.java.getDeclaredField("observers")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val observers = field.get(PreferenceObserverRegistry) as java.util.Set<*>
        return observers.size
    }

    /**
     * A [Map] whose first [get] call blocks until the test releases it, while still
     * holding the [VolumeDialogAutohideDelayRuntimeState.refreshLock].
     */
    private class BlockingMap(
        private val map: Map<String, Any>,
    ) : AbstractMap<String, Any>() {

        private val entered = java.util.concurrent.atomic.AtomicBoolean(false)
        private val enteredLatch = CountDownLatch(1)
        private var blockLatch: CountDownLatch? = null

        override val entries: Set<Map.Entry<String, Any>> = map.entries

        override fun get(key: String): Any? {
            if (entered.compareAndSet(false, true)) {
                enteredLatch.countDown()
            }
            blockLatch?.await(2, TimeUnit.SECONDS)
            return map[key]
        }

        fun awaitEntered(timeout: Long, unit: TimeUnit): Boolean =
            enteredLatch.await(timeout, unit)

        fun release(latch: CountDownLatch) {
            blockLatch = latch
            latch.countDown()
        }
    }
}
