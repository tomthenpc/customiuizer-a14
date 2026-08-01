package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Lightweight identity of a physical touch event.
 *
 * Two events with the same [downTime], [eventTime], [actionMasked] and [pointerCount]
 * on the same [ownerId] are considered the same physical event even if they are
 * delivered through different [GestureEntry] points.
 *
 * The [entry] is intentionally excluded so that intercept vs touch vs control-center
 * duplicates can be detected.  [ownerId] is included because the same timestamp on a
 * different owner is a different physical event.
 */
data class GestureEventFingerprint(
    val ownerId: Int,
    val downTime: Long,
    val eventTime: Long,
    val actionMasked: Int,
    val pointerCount: Int,
)
