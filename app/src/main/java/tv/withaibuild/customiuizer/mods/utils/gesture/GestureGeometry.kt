package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Immutable, Android-free geometry used by the gesture state machine.
 *
 * The consumer is responsible for publishing a consistent snapshot when the screen,
 * density, status bar height or brightness bounds change.
 */
data class GestureGeometry(
    val screenWidth: Int,
    val density: Float,
    val statusBarHeight: Int,
    val minBacklight: Float,
    val maxBacklight: Float,
)
