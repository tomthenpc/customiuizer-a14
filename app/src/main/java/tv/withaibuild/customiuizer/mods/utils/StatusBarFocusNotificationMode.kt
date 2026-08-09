package tv.withaibuild.customiuizer.mods.utils

/**
 * Bounded user-facing modes for the HyperOS status-bar focus-notification prompt.
 *
 * Unknown persisted values deliberately fall back to the ROM default so a future value cannot
 * accidentally hide a system surface.
 */
enum class StatusBarFocusNotificationMode(val preferenceValue: Int) {
    SYSTEM_DEFAULT(0),
    MATCH_STATUS_BAR_HEIGHT(1),
    HIDE(2);

    companion object {
        @JvmStatic
        fun fromPreference(value: Int): StatusBarFocusNotificationMode = when (value) {
            MATCH_STATUS_BAR_HEIGHT.preferenceValue -> MATCH_STATUS_BAR_HEIGHT
            HIDE.preferenceValue -> HIDE
            else -> SYSTEM_DEFAULT
        }
    }
}
