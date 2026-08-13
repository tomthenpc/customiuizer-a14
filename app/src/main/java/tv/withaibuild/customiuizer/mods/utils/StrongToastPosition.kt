package tv.withaibuild.customiuizer.mods.utils

/**
 * Screen edge used by the event-scoped HyperOS StrongToast window.
 *
 * Unknown persisted values stay at the ROM edge. This keeps existing backups compatible and
 * prevents an unsupported future value from moving a trusted SystemUI overlay.
 */
enum class StrongToastPosition(val preferenceValue: Int) {
    TOP(0),
    BOTTOM(1);

    companion object {
        @JvmStatic
        fun fromPreference(value: Int): StrongToastPosition = when (value) {
            BOTTOM.preferenceValue -> BOTTOM
            else -> TOP
        }
    }
}
