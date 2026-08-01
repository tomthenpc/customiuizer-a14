package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Immutable snapshot of the runtime objects and geometry needed to execute a gesture.
 *
 * This object does not hold a [View], [Activity], or [Context]; it only keeps the
 * system service objects and screen geometry resolved ahead of time.
 */
data class GestureDependencies(
    val ownerId: Int,
    val classLoaderIdentity: String,
    val displayManager: Any,
    val displayId: Int,
    val minimumBacklight: Float,
    val maximumBacklight: Float,
    val audioManager: Any?,
    val statusBarHeight: Int,
    val screenWidth: Int,
    val density: Float,
)
