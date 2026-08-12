package tv.withaibuild.customiuizer.mods.notificationautoexpand

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

/**
 * Component tests for [NotificationAutoExpandRuntimeState].
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class NotificationAutoExpandRuntimeStateTest {

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
        NotificationAutoExpandRuntimeState.resetForTest()
        PreferenceObserverRegistry.resetForTest()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        NotificationAutoExpandRuntimeState.resetForTest()
        PreferenceObserverRegistry.resetForTest()
    }

    @Test
    fun initialSnapshot_isNullBeforeInitialize() {
        val state = NotificationAutoExpandRuntimeState()
        assertNull(state.snapshotRef.get())
    }

    @Test
    fun initialize_buildsFromOneSource() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "2",
                "system_expandnotifs_apps" to setOf("com.example.one", "com.example.two"),
            ),
        )

        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()
        assertNotNull(snapshot)
        assertEquals("2", snapshot!!.modeRaw)
        assertEquals(setOf("com.example.one", "com.example.two"), snapshot.selectedApps)
    }

    @Test
    fun initialize_defaultModeRaw_isOne() {
        MainModule.mPrefs.replaceSnapshot(emptyMap<String, Any>())

        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()!!
        assertEquals("1", snapshot.modeRaw)
        assertEquals(emptySet<String>(), snapshot.selectedApps)
    }

    @Test
    fun initialize_missingOrWrongTypeAppsValue_isEmptySet() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "2",
                "system_expandnotifs_apps" to listOf("com.example"),
            ),
        )

        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()!!
        assertEquals(emptySet<String>(), snapshot.selectedApps)
    }

    @Test
    fun initialize_selectedAppsCopyOwned() {
        val sourceSet = HashSet<String>().apply { add("com.example") }
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "2",
                "system_expandnotifs_apps" to sourceSet,
            ),
        )

        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        val snapshot = state.snapshotRef.get()!!
        assertEquals(setOf("com.example"), snapshot.selectedApps)

        sourceSet.add("com.other")
        assertEquals("snapshot must own its copy", setOf("com.example"), snapshot.selectedApps)
    }

    @Test
    fun onPreferenceChanged_relevantModeKey_rebuilds() {
        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "3",
                "system_expandnotifs_apps" to setOf("com.example"),
            ),
        )
        state.onPreferenceChanged("system_expandnotifs")

        val snapshot = state.snapshotRef.get()!!
        assertEquals("3", snapshot.modeRaw)
    }

    @Test
    fun onPreferenceChanged_relevantAppsKey_rebuilds() {
        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "2",
                "system_expandnotifs_apps" to setOf("com.new"),
            ),
        )
        state.onPreferenceChanged("system_expandnotifs_apps")

        val snapshot = state.snapshotRef.get()!!
        assertEquals(setOf("com.new"), snapshot.selectedApps)
    }

    @Test
    fun onPreferenceChanged_nullKey_rebuilds() {
        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "3",
                "system_expandnotifs_apps" to setOf("com.new"),
            ),
        )
        state.onPreferenceChanged(null)

        val snapshot = state.snapshotRef.get()!!
        assertEquals("3", snapshot.modeRaw)
        assertEquals(setOf("com.new"), snapshot.selectedApps)
    }

    @Test
    fun onPreferenceChanged_irrelevantKey_doesNotRebuild() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "2",
                "system_expandnotifs_apps" to setOf("com.example"),
            ),
        )

        val state = NotificationAutoExpandRuntimeState()
        state.initialize()

        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "3",
                "system_expandnotifs_apps" to setOf("com.other"),
            ),
        )
        state.onPreferenceChanged("system_other_key")

        val snapshot = state.snapshotRef.get()!!
        assertEquals("2", snapshot.modeRaw)
        assertEquals(setOf("com.example"), snapshot.selectedApps)
    }

    @Test
    fun install_returnsSameInstanceOnSequentialCalls() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "2",
                "system_expandnotifs_apps" to setOf("com.example"),
            ),
        )

        val first = NotificationAutoExpandRuntimeState.install()
        val second = NotificationAutoExpandRuntimeState.install()

        assertSame("install must return the same process singleton", first, second)
    }

    @Test
    fun install_singletonPublication() {
        MainModule.mPrefs.replaceSnapshot(
            mapOf(
                "system_expandnotifs" to "2",
                "system_expandnotifs_apps" to setOf("com.example"),
            ),
        )

        val installed = NotificationAutoExpandRuntimeState.install()

        assertTrue(NotificationAutoExpandRuntimeState.isInstalled())
        assertEquals(1, processScopedObserverCount())
        assertEquals("2", installed.snapshotRef.get()!!.modeRaw)
    }

    @Test
    fun install_initialRefreshFatalThenRetryReusesSameState() {
        val refreshCallCount = java.util.concurrent.atomic.AtomicInteger(0)
        val values = mapOf(
            "system_expandnotifs" to "2",
            "system_expandnotifs_apps" to setOf("com.example"),
        )

        val runtimeState = NotificationAutoExpandRuntimeState {
            val call = refreshCallCount.incrementAndGet()
            if (call == 1) throw OutOfMemoryError("simulated fatal initial refresh")
            values
        }

        try {
            NotificationAutoExpandRuntimeState.install(runtimeState)
            fail("Expected OutOfMemoryError from initial refresh")
        } catch (e: OutOfMemoryError) {
            assertNull("snapshot must be cleared after fatal refresh", runtimeState.snapshotRef.get())
            assertFalse(NotificationAutoExpandRuntimeState.isInstalled())
        }

        val installed = NotificationAutoExpandRuntimeState.install(runtimeState)

        assertSame("retry must return the same state", runtimeState, installed)
        assertNotNull("snapshot must be published after retry", installed.snapshotRef.get())
        assertEquals("2", installed.snapshotRef.get()!!.modeRaw)
    }

    @Test
    fun initialize_ordinaryRefreshFailure_clearsPreviousSnapshot() {
        val state = NotificationAutoExpandRuntimeState {
            throw RuntimeException("simulated refresh failure")
        }
        state.snapshotRef.set(NotificationAutoExpandSnapshot("2", setOf("com.example")))
        state.initialize()

        assertNull("snapshot must be cleared after ordinary refresh failure", state.snapshotRef.get())
    }

    @Test(expected = OutOfMemoryError::class)
    fun initialize_fatalRefreshFailure_clearsThenRethrows() {
        val state = NotificationAutoExpandRuntimeState {
            throw OutOfMemoryError("simulated fatal refresh failure")
        }
        state.snapshotRef.set(NotificationAutoExpandSnapshot("2", setOf("com.example")))

        try {
            state.initialize()
        } catch (e: OutOfMemoryError) {
            assertNull("snapshot must be cleared before fatal rethrow", state.snapshotRef.get())
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun processScopedObserverCount(): Int {
        val field = PreferenceObserverRegistry::class.java.getDeclaredField("observers")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val observers = field.get(PreferenceObserverRegistry) as java.util.Set<*>
        return observers.size
    }
}
