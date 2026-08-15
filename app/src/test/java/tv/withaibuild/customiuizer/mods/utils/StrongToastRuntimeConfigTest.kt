package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.DYNAMIC_ISLAND
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.HIDE
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.SYSTEM_DEFAULT
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition.BOTTOM
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition.TOP
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicReference

class StrongToastRuntimeConfigTest {

    @After
    fun tearDown() {
        StrongToastRuntimeState.instance?.let { state ->
            PreferenceObserverRegistry.observers.remove(state.preferenceObserver)
        }
        StrongToastRuntimeState.instance = null
        StrongToastRuntimeState.installed = false
        SystemUIStrongToastHooks.snapshotRef = null
        SystemUIStrongToastHooks.installed = false
    }

    @Test
    fun initialSnapshot_matchesCurrentPreferenceValues() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_strong_toast_position", "1")
            put("system_strong_toast_bottom_offset", 25)
        }

        val state = StrongToastRuntimeState.install(prefs)

        val snapshot = state.snapshotRef.get()
        assertEquals(DYNAMIC_ISLAND, snapshot.mode)
        assertEquals(BOTTOM, snapshot.position)
        assertEquals(25, snapshot.bottomOffsetDp)
    }

    @Test
    fun systemDefaultAtBoot_featureIsEnabledForFastPassthrough() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "0")
        }

        assertTrue("StrongToast feature must install even in SYSTEM_DEFAULT so live preference changes affect the next event",
            StrongToastPresentationFeature.evaluateEnabled(prefs))
        assertFalse("installation must not run in a unit-test JVM", SystemUIStrongToastHooks.installed)
    }

    @Test
    fun activeModeAtBoot_featureIsEnabled() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
        }

        assertTrue(StrongToastPresentationFeature.evaluateEnabled(prefs))
    }

    @Test
    fun legacyMode4_mapsToDynamicIsland() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "4")
        }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(DYNAMIC_ISLAND, state.snapshotRef.get().mode)

        prefs.put("system_strong_toast_mode", "3")
        state.preferenceObserver.onChange("system_strong_toast_mode")

        assertEquals(DYNAMIC_ISLAND, state.snapshotRef.get().mode)
    }

    @Test
    fun positionTopToBottom_updatesSnapshotPosition() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_strong_toast_position", "0")
        }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(TOP, state.snapshotRef.get().position)

        prefs.put("system_strong_toast_position", "1")
        state.preferenceObserver.onChange("system_strong_toast_position")

        assertEquals(BOTTOM, state.snapshotRef.get().position)
    }

    @Test
    fun bottomOffsetInt_publishesBoundedValue() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_strong_toast_bottom_offset", 10)
        }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(10, state.snapshotRef.get().bottomOffsetDp)

        prefs.put("system_strong_toast_bottom_offset", 100)
        state.preferenceObserver.onChange("system_strong_toast_bottom_offset")
        assertEquals(80, state.snapshotRef.get().bottomOffsetDp)

        prefs.put("system_strong_toast_bottom_offset", -50)
        state.preferenceObserver.onChange("system_strong_toast_bottom_offset")
        assertEquals(-40, state.snapshotRef.get().bottomOffsetDp)

        prefs.put("system_strong_toast_bottom_offset", -20)
        state.preferenceObserver.onChange("system_strong_toast_bottom_offset")
        assertEquals(-20, state.snapshotRef.get().bottomOffsetDp)
    }

    @Test
    fun bottomOffsetString_parsesWhenStoredAsString() {
        val source = mapOf(
            "system_strong_toast_mode" to "3",
            "system_strong_toast_position" to "0",
            "system_strong_toast_bottom_offset" to "25"
        )

        val snapshot = StrongToastRuntimeState.buildSnapshot(source)
        assertEquals(25, snapshot.bottomOffsetDp)
    }

    @Test
    fun bottomOffsetNumber_parsesWhenStoredAsNumber() {
        val source = mapOf(
            "system_strong_toast_mode" to 3,
            "system_strong_toast_position" to 0,
            "system_strong_toast_bottom_offset" to 25
        )

        val snapshot = StrongToastRuntimeState.buildSnapshot(source)
        assertEquals(DYNAMIC_ISLAND, snapshot.mode)
        assertEquals(TOP, snapshot.position)
        assertEquals(25, snapshot.bottomOffsetDp)
    }

    @Test
    fun unrelatedPreferenceKey_leavesStrongToastConfigUnchanged() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_strong_toast_position", "0")
            put("system_strong_toast_bottom_offset", 10)
        }
        val state = StrongToastRuntimeState.install(prefs)
        val before = state.snapshotRef.get()

        prefs.put("system_some_unrelated_key", "99")
        state.preferenceObserver.onChange("system_some_unrelated_key")

        assertSame(before, state.snapshotRef.get())
    }

    @Test
    fun repeatedInstall_doesNotDuplicateObserverOrState() {
        val initialObserverCount = PreferenceObserverRegistry.observers.size
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
        }

        val first = StrongToastRuntimeState.install(prefs)
        val afterFirstObserverCount = PreferenceObserverRegistry.observers.size
        assertEquals(initialObserverCount + 1, afterFirstObserverCount)

        val second = StrongToastRuntimeState.install(prefs)
        assertSame(first, second)
        assertEquals(afterFirstObserverCount, PreferenceObserverRegistry.observers.size)
    }

    @Test
    fun storeSnapshot_overwritesPreviousValueForSameView() {
        val snapshot1 = StrongToastRuntimeSnapshot(DYNAMIC_ISLAND, TOP, 0)
        val snapshot2 = StrongToastRuntimeSnapshot(HIDE, BOTTOM, 24)

        SystemUIStrongToastHooks.snapshotRef = AtomicReference(snapshot1)

        val view = Object()
        SystemUIStrongToastHooks.storeSnapshot(view, snapshot1)
        assertEquals(snapshot1, SystemUIStrongToastHooks.resolveSnapshot(view))

        // New event begins: the current snapshot is captured and stored again.
        SystemUIStrongToastHooks.snapshotRef = AtomicReference(snapshot2)
        SystemUIStrongToastHooks.storeSnapshot(view, SystemUIStrongToastHooks.currentSnapshot()!!)

        assertEquals(snapshot2, SystemUIStrongToastHooks.resolveSnapshot(view))
        assertNotSame(snapshot1, SystemUIStrongToastHooks.resolveSnapshot(view))
    }

    @Test
    fun resolveSnapshot_withoutStoredValue_fallsBackToCurrent() {
        val snapshot = StrongToastRuntimeSnapshot(DYNAMIC_ISLAND, TOP, 0)
        SystemUIStrongToastHooks.snapshotRef = AtomicReference(snapshot)

        val view = Object()
        assertEquals(snapshot, SystemUIStrongToastHooks.resolveSnapshot(view))
    }

    @Test
    fun perEventSnapshot_acquiredOnce() {
        val snapshot1 = StrongToastRuntimeSnapshot(DYNAMIC_ISLAND, TOP, 0)
        val snapshot2 = StrongToastRuntimeSnapshot(HIDE, BOTTOM, 24)

        SystemUIStrongToastHooks.snapshotRef = AtomicReference(snapshot1)

        val view = Object()
        // First event boundary (e.g. getWindowParam) captures and stores.
        val firstSnapshot = SystemUIStrongToastHooks.resolveSnapshot(view)
            ?: SystemUIStrongToastHooks.currentSnapshot()!!
        SystemUIStrongToastHooks.storeSnapshot(view, firstSnapshot)
        assertEquals(snapshot1, firstSnapshot)

        // Global snapshot changes before the second boundary runs.
        SystemUIStrongToastHooks.snapshotRef = AtomicReference(snapshot2)

        // Second event boundary (e.g. onAttachedToWindow) must reuse the stored
        // event snapshot, not acquire a new one from currentSnapshot().
        val secondSnapshot = SystemUIStrongToastHooks.resolveSnapshot(view)
            ?: SystemUIStrongToastHooks.currentSnapshot()!!
        SystemUIStrongToastHooks.storeSnapshot(view, secondSnapshot)

        assertEquals(snapshot1, secondSnapshot)
        assertEquals(snapshot1, SystemUIStrongToastHooks.resolveSnapshot(view))
    }

    @Test
    fun buildSnapshot_coercesUnknownModeAndPositionToDefaults() {
        val source = mapOf(
            "system_strong_toast_mode" to "99",
            "system_strong_toast_position" to "-1",
            "system_strong_toast_bottom_offset" to 0
        )

        val snapshot = StrongToastRuntimeState.buildSnapshot(source)
        assertEquals(SYSTEM_DEFAULT, snapshot.mode)
        assertEquals(TOP, snapshot.position)
        assertEquals(0, snapshot.bottomOffsetDp)
    }

    @Test
    fun buildSnapshot_supportsAllNonDefaultModes() {
        assertEquals(
            MATCH_STATUS_BAR_HEIGHT,
            StrongToastRuntimeState.buildSnapshot(
                mapOf("system_strong_toast_mode" to "1")
            ).mode
        )
        assertEquals(
            HIDE,
            StrongToastRuntimeState.buildSnapshot(
                mapOf("system_strong_toast_mode" to "2")
            ).mode
        )
        assertEquals(
            DYNAMIC_ISLAND,
            StrongToastRuntimeState.buildSnapshot(
                mapOf("system_strong_toast_mode" to "3")
            ).mode
        )
        assertEquals(
            DYNAMIC_ISLAND,
            StrongToastRuntimeState.buildSnapshot(
                mapOf("system_strong_toast_mode" to "4")
            ).mode
        )
    }
}
