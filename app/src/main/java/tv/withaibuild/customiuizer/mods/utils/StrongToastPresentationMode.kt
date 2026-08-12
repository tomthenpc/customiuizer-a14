package tv.withaibuild.customiuizer.mods.utils

/**
 * Bounded user-facing modes for the HyperOS 1 StrongToast top capsule.
 *
 * Unknown persisted values deliberately fall back to the ROM default so a future value cannot
 * accidentally hide a system surface.
 */
enum class StrongToastPresentationMode(val preferenceValue: Int) {
    SYSTEM_DEFAULT(0),
    MATCH_STATUS_BAR_HEIGHT(1),
    HIDE(2),
    DYNAMIC_ISLAND(3),
    DYNAMIC_ISLAND_CENTER_POP(4);

    companion object {
        @JvmStatic
        fun fromPreference(value: Int): StrongToastPresentationMode = when (value) {
            MATCH_STATUS_BAR_HEIGHT.preferenceValue -> MATCH_STATUS_BAR_HEIGHT
            HIDE.preferenceValue -> HIDE
            DYNAMIC_ISLAND.preferenceValue -> DYNAMIC_ISLAND
            DYNAMIC_ISLAND_CENTER_POP.preferenceValue -> DYNAMIC_ISLAND_CENTER_POP
            else -> SYSTEM_DEFAULT
        }
    }
}
