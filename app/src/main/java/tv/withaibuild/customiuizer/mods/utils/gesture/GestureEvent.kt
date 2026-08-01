package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Pure data representation of a touch event passed to the gesture state machine.
 *
 * The [entry] and [ownerId] allow the machine to keep per-owner state; the physical
 * coordinates and timing are kept as primitives to avoid Android object coupling.
 */
data class GestureEvent(
    val entry: GestureEntry,
    val actionMasked: Int,
    val downTime: Long,
    val eventTime: Long,
    val x: Float,
    val y: Float,
    val pointerCount: Int,
    val ownerId: Int,
)
