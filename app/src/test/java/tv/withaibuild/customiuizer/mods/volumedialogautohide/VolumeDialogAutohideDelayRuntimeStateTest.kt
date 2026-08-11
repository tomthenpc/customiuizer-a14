package tv.withaibuild.customiuizer.mods.volumedialogautohide

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Component tests for [VolumeDialogAutohideDelayRuntimeState].
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class VolumeDialogAutohideDelayRuntimeStateTest {

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
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
    fun install_initializesAndReturnsSameInstance() {
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
    fun refreshSourceCapturedInsideLock() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_volumedialogdelay_expanded" to 1,
                "system_volumedialogdelay_collapsed" to 2,
            ),
        )

        val state = VolumeDialogAutohideDelayRuntimeState()
        state.initialize()

        val completed = CountDownLatch(1)
        Thread {
            MainModule.mPrefs.replaceSnapshot(
                mapOf(
                    "system_volumedialogdelay_expanded" to 3,
                    "system_volumedialogdelay_collapsed" to 4,
                ),
            )
            state.onPreferenceChanged("system_volumedialogdelay_expanded")
            completed.countDown()
        }.start()

        assertTrue(completed.await(1, TimeUnit.SECONDS))

        val snapshot = state.snapshotRef.get()!!
        assertEquals(3, snapshot.expanded)
        assertEquals(4, snapshot.collapsed)
    }
}
