package tv.withaibuild.customiuizer.mods.utils.gesture

class FakeGestureEffectExecutor : GestureEffectExecutor {

    val brightnessApplied = mutableListOf<Float>()
    val brightnessCommitted = mutableListOf<Float>()
    val volumeAdjusted = mutableListOf<Boolean>()
    val doubleTaps = mutableListOf<DoubleTapPosition>()
    var longPresses = 0
    var resets = 0

    override fun applyTemporaryBrightness(ratio: Float) {
        brightnessApplied.add(ratio)
    }

    override fun commitBrightness(ratio: Float) {
        brightnessCommitted.add(ratio)
    }

    override fun adjustVolume(raise: Boolean) {
        volumeAdjusted.add(raise)
    }

    override fun triggerDoubleTap(position: DoubleTapPosition, config: GestureConfig) {
        doubleTaps.add(position)
    }

    override fun triggerLongPress(config: GestureConfig) {
        longPresses++
    }

    override fun reset() {
        resets++
    }
}
