package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Sink for the side-effect commands produced by the gesture state machine.
 *
 * This interface keeps the pure state machine decoupled from Android calls.  The
 * concrete implementation is responsible for translating commands into brightness,
 * volume and global actions using the prepared [GestureDependencies].
 */
interface GestureEffectExecutor {
    fun applyTemporaryBrightness(ratio: Float)
    fun commitBrightness(ratio: Float)
    fun adjustVolume(raise: Boolean)
    fun triggerDoubleTap(position: DoubleTapPosition, config: GestureConfig)
    fun triggerLongPress(config: GestureConfig)
    fun reset()
}
