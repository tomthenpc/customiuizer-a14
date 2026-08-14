package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.DYNAMIC_ISLAND
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.DYNAMIC_ISLAND_CENTER_POP
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.HIDE
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.MATCH_STATUS_BAR_HEIGHT
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode.SYSTEM_DEFAULT
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition.BOTTOM
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition.TOP
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
            put("system_strong_toast_bottom_offset", "25")
        }

        val state = StrongToastRuntimeState.install(prefs)

        val snapshot = state.snapshotRef.get()
        assertEquals(DYNAMIC_ISLAND, snapshot.mode)
        assertEquals(BOTTOM, snapshot.position)
        assertEquals(25, snapshot.bottomOffsetDp)
    }

    @Test
    fun systemDefaultToActive_publishesFirstEnableSnapshot() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "0")
            put("system_strong_toast_position", "0")
            put("system_strong_toast_bottom_offset", "0")
        }

        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(SYSTEM_DEFAULT, state.snapshotRef.get().mode)

        prefs.put("system_strong_toast_mode", "2")
        state.preferenceObserver.onChange("system_strong_toast_mode")

        assertEquals(HIDE, state.snapshotRef.get().mode)
    }

    @Test
    fun mode3To4_publishesDynamicIslandCenterPop() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
        }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(DYNAMIC_ISLAND, state.snapshotRef.get().mode)

        prefs.put("system_strong_toast_mode", "4")
        state.preferenceObserver.onChange("system_strong_toast_mode")

        assertEquals(DYNAMIC_ISLAND_CENTER_POP, state.snapshotRef.get().mode)
    }

    @Test
    fun mode4To3_publishesDynamicIsland() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "4")
        }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(DYNAMIC_ISLAND_CENTER_POP, state.snapshotRef.get().mode)

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
    fun bottomOffsetChange_publishesBoundedValue() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_strong_toast_bottom_offset", "10")
        }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(10, state.snapshotRef.get().bottomOffsetDp)

        prefs.put("system_strong_toast_bottom_offset", "100")
        state.preferenceObserver.onChange("system_strong_toast_bottom_offset")
        assertEquals(80, state.snapshotRef.get().bottomOffsetDp)

        prefs.put("system_strong_toast_bottom_offset", "-50")
        state.preferenceObserver.onChange("system_strong_toast_bottom_offset")
        assertEquals(-40, state.snapshotRef.get().bottomOffsetDp)

        prefs.put("system_strong_toast_bottom_offset", "-20")
        state.preferenceObserver.onChange("system_strong_toast_bottom_offset")
        assertEquals(-20, state.snapshotRef.get().bottomOffsetDp)
    }

    @Test
    fun unrelatedPreferenceKey_leavesStrongToastConfigUnchanged() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put("system_strong_toast_position", "0")
            put("system_strong_toast_bottom_offset", "10")
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
    fun callbackRouting_readsCurrentSnapshotAndPreservesPerViewSnapshot() {
        val snapshot1 = StrongToastRuntimeSnapshot(DYNAMIC_ISLAND, TOP, 0)
        val snapshot2 = StrongToastRuntimeSnapshot(DYNAMIC_ISLAND_CENTER_POP, BOTTOM, 24)

        SystemUIStrongToastHooks.snapshotRef = AtomicReference(snapshot1)

        val firstView = Object()
        SystemUIStrongToastHooks.storeSnapshot(firstView, snapshot1)
        assertEquals(snapshot1, SystemUIStrongToastHooks.resolveSnapshot(firstView))

        SystemUIStrongToastHooks.snapshotRef = AtomicReference(snapshot2)

        val secondView = Object()
        assertEquals(snapshot2, SystemUIStrongToastHooks.resolveSnapshot(secondView))
        assertEquals(snapshot1, SystemUIStrongToastHooks.resolveSnapshot(firstView))

        // Changing the live snapshot must not alter a snapshot already bound to a specific view.
        assertNotSame(snapshot2, SystemUIStrongToastHooks.resolveSnapshot(firstView))
    }

    @Test
    fun buildSnapshot_coercesUnknownModeAndPositionToDefaults() {
        val source = mapOf(
            "system_strong_toast_mode" to "99",
            "system_strong_toast_position" to "-1",
            "system_strong_toast_bottom_offset" to "0"
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
            DYNAMIC_ISLAND_CENTER_POP,
            StrongToastRuntimeState.buildSnapshot(
                mapOf("system_strong_toast_mode" to "4")
            ).mode
        )
    }
}
