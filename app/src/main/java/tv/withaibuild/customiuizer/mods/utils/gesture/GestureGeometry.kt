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

/** Convert runtime dependencies to the geometry snapshot used by the pure state machine. */
fun GestureDependencies.toGeometry(): GestureGeometry =
    GestureGeometry(
        screenWidth = screenWidth,
        density = density,
        statusBarHeight = statusBarHeight,
        minBacklight = minimumBacklight,
        maxBacklight = maximumBacklight,
    )
