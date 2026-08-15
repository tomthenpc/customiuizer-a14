package tv.withaibuild.customiuizer.utils

/**
 * Executes the set of matched [RestartTarget]s in a fixed order.
 *
 * The executor performs a single root check, then attempts every selected
 * target independently.  Failures are isolated: one failing target does not
 * cancel the remaining attempts.  No soft reboot or system reboot is invoked.
 */
class PreferenceRestartTargetExecutor(
    private val commandRunner: (String) -> Pair<Int, String> = { cmd ->
        val result = AppHelper.executeRootCommand(cmd)
        Pair(result.first, result.second)
    }
) {

    /**
     * Result of a matched restart execution attempt.
     */
    data class Result(
        val rootGranted: Boolean,
        val attempted: List<RestartTarget>,
        val succeeded: List<RestartTarget>,
        val failed: List<RestartTarget>
    )

    companion object {
        private val EXECUTION_ORDER = listOf(
            RestartTarget.SECURITY_CENTER,
            RestartTarget.LAUNCHER,
            RestartTarget.SYSTEMUI
        )
        private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
        private const val LAUNCHER_PACKAGE = "com.miui.home"
        private const val SYSTEMUI_PROCESS = "com.android.systemui"
    }

    /**
     * Attempts to restart every target in [targets] that the registry has
     * resolved for the current page.
     */
    fun execute(targets: Set<RestartTarget>): Result {
        val idResult = commandRunner("id")
        val rootGranted = idResult.first == 0 && idResult.second.contains("uid=0")
        if (!rootGranted) {
            return Result(
                rootGranted = false,
                attempted = emptyList(),
                succeeded = emptyList(),
                failed = emptyList()
            )
        }

        val attempted = mutableListOf<RestartTarget>()
        val succeeded = mutableListOf<RestartTarget>()
        val failed = mutableListOf<RestartTarget>()

        for (target in EXECUTION_ORDER) {
            if (target !in targets) continue

            attempted.add(target)
            val ok = when (target) {
                RestartTarget.SECURITY_CENTER -> forceStopPackage(SECURITY_CENTER_PACKAGE)
                RestartTarget.LAUNCHER -> forceStopPackage(LAUNCHER_PACKAGE)
                RestartTarget.SYSTEMUI -> killProcess(SYSTEMUI_PROCESS)
            }

            if (ok) {
                succeeded.add(target)
            } else {
                failed.add(target)
            }
        }

        return Result(
            rootGranted = true,
            attempted = attempted,
            succeeded = succeeded,
            failed = failed
        )
    }

    private fun forceStopPackage(packageName: String): Boolean {
        val result = commandRunner("am force-stop $packageName")
        return result.first == 0
    }

    private fun killProcess(processName: String): Boolean {
        val pidResult = commandRunner("pidof $processName")
        if (pidResult.first != 0 || pidResult.second.isBlank()) return false

        val pids = pidResult.second
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (pids.isBlank()) return false

        val killResult = commandRunner("kill -9 $pids")
        return killResult.first == 0
    }
}
