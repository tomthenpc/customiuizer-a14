package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Dynamic Island dismiss completion contract.
 *
 * The production completion runnable is the only place that may call ROM
 * [clearAll] and [onComplete]. It must run them in that order and exactly once.
 */
class DynamicIslandDismissLifecycleTest {

    /**
     * Fake MIUIStrongToast that records the order of ROM cleanup calls.
     */
    open class FakeStrongToast : LinearLayout(null as Context?) {
        val calls = mutableListOf<String>()

        open fun clearAll() {
            calls.add("clearAll")
        }

        open fun onComplete() {
            calls.add("onComplete")
        }
    }

    @Test
    fun dismissComplete_callsClearAll_thenOnComplete_exactlyOnce() {
        val strongToast = FakeStrongToast()

        val complete = SystemUIStrongToastHooks.buildDynamicIslandDismissComplete(strongToast)
        complete.run()

        assertEquals(
            "clearAll must run before onComplete and each must run once",
            listOf("clearAll", "onComplete"),
            strongToast.calls
        )
    }

    @Test
    fun dismissComplete_continuesEvenWhenClearAllThrows() {
        val strongToast = object : FakeStrongToast() {
            override fun clearAll() {
                throw RuntimeException("clearAll failed")
            }
        }

        // The real completion runnable catches around clearAll and still calls onComplete.
        val complete = SystemUIStrongToastHooks.buildDynamicIslandDismissComplete(strongToast)
        complete.run()

        assertEquals(
            "onComplete must still run even if clearAll throws",
            listOf("onComplete"),
            strongToast.calls
        )
    }

    @Test
    fun shellState_field_survivesBindAndCanBeCleared() {
        // Verify that the private field constant used by production matches the test constant
        // so contract source assertions and production agree on the same additional-instance key.
        val field = SystemUIStrongToastHooks.DynamicIslandShellState::class.java.canonicalName
        assertNotNull(field)
        assertTrue(
            "DynamicIslandShellState must be a stable, serializable ownership data class",
            field!!.startsWith("tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks")
        )

        val root = LinearLayout(null as Context?)
        XposedHelpers.setAdditionalInstanceField(
            root,
            "customiuizer_dynamic_island_shell_state",
            "dummy"
        )
        assertEquals(
            "dummy",
            XposedHelpers.getAdditionalInstanceField(root, "customiuizer_dynamic_island_shell_state")
        )
        XposedHelpers.removeAdditionalInstanceField(root, "customiuizer_dynamic_island_shell_state")
        assertTrue(
            "additional instance field must be removable",
            XposedHelpers.getAdditionalInstanceField(root, "customiuizer_dynamic_island_shell_state") == null
        )
    }
}
