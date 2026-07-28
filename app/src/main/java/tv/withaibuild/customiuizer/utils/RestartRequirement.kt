package tv.withaibuild.customiuizer.utils

/**
 * Lightweight, unified classification for how a setting change takes effect.
 *
 * This is used only for user-facing hints and for explicit restart/exit actions.
 * It must not grow into a complex setting framework and must not raise the
 * restart level of ordinary settings just to make the UI "consistent".
 */
enum class RestartRequirement {
    /** Change is effective immediately. */
    NONE,

    /** Change requires the settings application to be closed and reopened. */
    APP_REOPEN,

    /** Change requires a SystemUI restart. */
    SYSTEM_UI,

    /** Change requires a Launcher restart. */
    LAUNCHER,

    /** Change requires a system_server / soft reboot. */
    SYSTEM_SERVER,

    /** Change requires a full device reboot. */
    DEVICE
}
