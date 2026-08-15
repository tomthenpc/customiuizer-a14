package tv.withaibuild.customiuizer.utils

/**
 * Executable restart targets supported by the matched-restart action.
 *
 * System-wide reboot, system-server restart and `NONE` are intentionally
 * not represented here; they are never executed as automatic restart actions.
 */
enum class RestartTarget {
    LAUNCHER,
    SYSTEMUI,
    SECURITY_CENTER,
}
