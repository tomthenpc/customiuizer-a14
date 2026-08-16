package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastPresentationFeature
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicReference

class StrongToastRuntimeConfigTest {
    @After
    fun tearDown() {
        StrongToastRuntimeState.instance?.let { PreferenceObserverRegistry.observers.remove(it.preferenceObserver) }
        StrongToastRuntimeState.instance = null
        StrongToastRuntimeState.installed = false
        SystemUIStrongToastHooks.snapshotRef = null
        SystemUIStrongToastHooks.installed = false
    }

    @Test
    fun snapshotIgnoresLegacyPositionAndOffsetButKeepsCompatibilityValues() {
        val snapshot = StrongToastRuntimeState.buildSnapshot(
            mapOf(
                "system_strong_toast_mode" to "3",
                "system_strong_toast_position" to "1",
                "system_strong_toast_bottom_offset" to "25",
            )
        )
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, snapshot.mode)
        assertEquals(StrongToastPosition.TOP, snapshot.position)
        assertEquals(0, snapshot.bottomOffsetDp)
    }

    @Test
    fun modeChangesApplyLiveToTheNextEvent() {
        val prefs = PrefMap().apply { put("system_strong_toast_mode", "0") }
        val state = StrongToastRuntimeState.install(prefs)
        assertEquals(StrongToastPresentationMode.SYSTEM_DEFAULT, state.snapshotRef.get().mode)

        prefs.put("system_strong_toast_mode", "3")
        state.preferenceObserver.onChange("system_strong_toast_mode")
        assertEquals(StrongToastPresentationMode.DYNAMIC_ISLAND, state.snapshotRef.get().mode)
        assertTrue(StrongToastPresentationFeature.evaluateEnabled(prefs))
    }

    @Test
    fun legacyModeFourMigratesToDynamicIsland() {
        assertEquals(
            StrongToastPresentationMode.DYNAMIC_ISLAND,
            StrongToastRuntimeState.buildSnapshot(mapOf("system_strong_toast_mode" to "4")).mode,
        )
    }

    @Test
    fun unrelatedPreferenceDoesNotReplaceSnapshot() {
        val state = StrongToastRuntimeState.install(PrefMap().apply { put("system_strong_toast_mode", "3") })
        val before = state.snapshotRef.get()
        state.preferenceObserver.onChange("system_unrelated")
        assertSame(before, state.snapshotRef.get())
    }

    @Test
    fun eventSnapshotRemainsImmutableAfterLiveChange() {
        val first = StrongToastRuntimeSnapshot(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPosition.TOP, 0)
        val second = StrongToastRuntimeSnapshot(StrongToastPresentationMode.HIDE, StrongToastPosition.TOP, 0)
        val view = Object()
        SystemUIStrongToastHooks.snapshotRef = AtomicReference(first)
        SystemUIStrongToastHooks.storeSnapshot(view, first)
        SystemUIStrongToastHooks.snapshotRef = AtomicReference(second)

        assertEquals(first, SystemUIStrongToastHooks.resolveSnapshot(view))
    }
}
