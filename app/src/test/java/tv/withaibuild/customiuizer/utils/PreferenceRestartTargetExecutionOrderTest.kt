package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PreferenceRestartTargetExecutor].
 *
 * A fake [commandRunner] is injected so no real root shell commands are run.
 */
class PreferenceRestartTargetExecutionOrderTest {

    @Test
    fun empty_targets_skip_target_commands() {
        val commands = mutableListOf<String>()
        val executor = PreferenceRestartTargetExecutor { cmd ->
            commands.add(cmd)
            if (cmd == "id") Pair(0, "uid=0") else Pair(0, "")
        }

        val result = executor.execute(emptySet())

        assertTrue(result.rootGranted)
        assertTrue(result.attempted.isEmpty())
        assertTrue(result.succeeded.isEmpty())
        assertTrue(result.failed.isEmpty())
        // Root check is still performed, but no target commands are issued.
        assertEquals(listOf("id"), commands)
    }

    @Test
    fun no_root_returns_no_attempts() {
        val commands = mutableListOf<String>()
        val executor = PreferenceRestartTargetExecutor { cmd ->
            commands.add(cmd)
            if (cmd == "id") Pair(1, "uid=1000") else Pair(0, "")
        }

        val result = executor.execute(setOf(RestartTarget.LAUNCHER))

        assertFalse(result.rootGranted)
        assertTrue(result.attempted.isEmpty())
        assertTrue(result.succeeded.isEmpty())
        assertTrue(result.failed.isEmpty())
        assertEquals(listOf("id"), commands)
    }

    @Test
    fun fixed_order_security_center_then_launcher_then_systemui() {
        val commands = mutableListOf<String>()
        val executor = PreferenceRestartTargetExecutor { cmd ->
            commands.add(cmd)
            when (cmd) {
                "id" -> Pair(0, "uid=0")
                "am force-stop com.miui.securitycenter" -> Pair(0, "")
                "am force-stop com.miui.home" -> Pair(0, "")
                "pidof com.android.systemui" -> Pair(0, "1234 5678")
                "kill -9 1234 5678" -> Pair(0, "")
                else -> Pair(1, "")
            }
        }

        val result = executor.execute(
            setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI, RestartTarget.SECURITY_CENTER)
        )

        assertTrue(result.rootGranted)
        assertEquals(
            listOf(
                RestartTarget.SECURITY_CENTER,
                RestartTarget.LAUNCHER,
                RestartTarget.SYSTEMUI
            ),
            result.attempted
        )
        assertEquals(result.attempted, result.succeeded)
        assertTrue(result.failed.isEmpty())

        val expectedCommands = listOf(
            "id",
            "am force-stop com.miui.securitycenter",
            "am force-stop com.miui.home",
            "pidof com.android.systemui",
            "kill -9 1234 5678"
        )
        assertEquals(expectedCommands, commands)
    }

    @Test
    fun one_failure_does_not_cancel_other_targets() {
        val commands = mutableListOf<String>()
        val executor = PreferenceRestartTargetExecutor { cmd ->
            commands.add(cmd)
            when (cmd) {
                "id" -> Pair(0, "uid=0")
                "am force-stop com.miui.securitycenter" -> Pair(0, "")
                "am force-stop com.miui.home" -> Pair(1, "failed")
                "pidof com.android.systemui" -> Pair(0, "9999")
                "kill -9 9999" -> Pair(0, "")
                else -> Pair(1, "")
            }
        }

        val result = executor.execute(
            setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI, RestartTarget.SECURITY_CENTER)
        )

        assertTrue(result.rootGranted)
        assertEquals(
            listOf(
                RestartTarget.SECURITY_CENTER,
                RestartTarget.LAUNCHER,
                RestartTarget.SYSTEMUI
            ),
            result.attempted
        )
        assertEquals(
            listOf(RestartTarget.SECURITY_CENTER, RestartTarget.SYSTEMUI),
            result.succeeded
        )
        assertEquals(listOf(RestartTarget.LAUNCHER), result.failed)
        assertTrue("am force-stop com.miui.home" in commands)
        assertTrue("pidof com.android.systemui" in commands)
    }

    @Test
    fun systemui_not_running_is_a_failure() {
        val commands = mutableListOf<String>()
        val executor = PreferenceRestartTargetExecutor { cmd ->
            commands.add(cmd)
            when (cmd) {
                "id" -> Pair(0, "uid=0")
                "pidof com.android.systemui" -> Pair(1, "")
                else -> Pair(0, "")
            }
        }

        val result = executor.execute(setOf(RestartTarget.SYSTEMUI))

        assertTrue(result.rootGranted)
        assertEquals(listOf(RestartTarget.SYSTEMUI), result.attempted)
        assertTrue(result.succeeded.isEmpty())
        assertEquals(listOf(RestartTarget.SYSTEMUI), result.failed)
    }

    @Test
    fun partial_set_only_attempts_selected_targets() {
        val commands = mutableListOf<String>()
        val executor = PreferenceRestartTargetExecutor { cmd ->
            commands.add(cmd)
            when (cmd) {
                "id" -> Pair(0, "uid=0")
                "am force-stop com.miui.securitycenter" -> Pair(0, "")
                else -> Pair(1, "")
            }
        }

        val result = executor.execute(setOf(RestartTarget.SECURITY_CENTER))

        assertTrue(result.rootGranted)
        assertEquals(listOf(RestartTarget.SECURITY_CENTER), result.attempted)
        assertEquals(listOf(RestartTarget.SECURITY_CENTER), result.succeeded)
        assertTrue(result.failed.isEmpty())
        assertEquals(
            listOf("id", "am force-stop com.miui.securitycenter"),
            commands
        )
    }
}
