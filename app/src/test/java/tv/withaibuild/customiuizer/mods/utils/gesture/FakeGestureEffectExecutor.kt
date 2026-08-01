package tv.withaibuild.customiuizer.mods.utils.gesture

class FakeGestureEffectExecutor : GestureEffectExecutor {

    val brightnessApplied = mutableListOf<Float>()
    val brightnessCommitted = mutableListOf<Float>()
    val volumeAdjusted = mutableListOf<Boolean>()
    val doubleTaps = mutableListOf<DoubleTapPosition>()
    var longPresses = 0
    var resets = 0

    override fun execute(
        commands: List<GestureCommand>,
        dependencies: GestureDependencies,
        config: GestureConfig,
        context: Any?,
    ) {
        for (command in commands) {
            when (command) {
                is GestureCommand.ApplyTemporaryBrightness -> brightnessApplied.add(command.ratio)
                is GestureCommand.CommitBrightness -> brightnessCommitted.add(command.ratio)
                is GestureCommand.AdjustVolume -> volumeAdjusted.add(command.raise)
                is GestureCommand.TriggerDoubleTap -> doubleTaps.add(command.position)
                is GestureCommand.TriggerLongPress -> longPresses++
                is GestureCommand.Reset -> resets++
                else -> { /* ignore */ }
            }
        }
    }
}
