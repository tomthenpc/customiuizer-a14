package tv.withaibuild.customiuizer.mods.utils.gesture

import android.content.Context
import android.media.AudioManager
import android.view.View
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Executes the side-effect commands for status bar gestures.
 *
 * This class is deliberately stateless between events; all per-gesture state is held
 * by [GestureMachine].  It only performs the actual Android calls.
 */
class StatusBarGestureEffectExecutor : GestureEffectExecutor {

    /**
     * Volume flags mirror the legacy GlobalActions implementation:
     * FLAG_FROM_KEY, SHOW_UI, ALLOW_RINGER_MODES, PLAY_SOUND and VIBRATE.
     */
    internal companion object {
        const val VOLUME_FLAGS = (1 shl 12) or
            AudioManager.FLAG_SHOW_UI or
            AudioManager.FLAG_ALLOW_RINGER_MODES or
            AudioManager.FLAG_PLAY_SOUND or
            AudioManager.FLAG_VIBRATE
    }

    private var lastSentBrightnessRatio = -1f

    override fun execute(
        commands: List<GestureCommand>,
        dependencies: GestureDependencies,
        config: GestureConfig,
        context: Any?,
    ) {
        val ctx = when (context) {
            is View -> context.context
            is Context -> context
            else -> null
        }

        val audioManager = dependencies.audioManager as? AudioManager

        for (command in commands) {
            when (command) {
                is GestureCommand.ApplyTemporaryBrightness -> {
                    if (kotlin.math.abs(command.ratio - lastSentBrightnessRatio) >= 0.0001f) {
                        dependencies.setTemporaryBrightnessMethod!!.invoke(
                            dependencies.displayManager,
                            dependencies.displayId,
                            command.ratio,
                        )
                        lastSentBrightnessRatio = command.ratio
                    }
                }
                is GestureCommand.CommitBrightness -> {
                    dependencies.setBrightnessMethod!!.invoke(
                        dependencies.displayManager,
                        dependencies.displayId,
                        command.ratio,
                    )
                    lastSentBrightnessRatio = -1f
                }
                is GestureCommand.AdjustVolume -> {
                    if (audioManager != null) {
                        val direction = if (command.raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                        @Suppress("WrongConstant")
                        audioManager.adjustVolume(direction, VOLUME_FLAGS)
                    }
                }
                is GestureCommand.TriggerDoubleTap -> {
                    if (ctx == null) continue
                    val key = when (command.position) {
                        DoubleTapPosition.LEFT -> "system_statusbarcontrols_dt_left"
                        DoubleTapPosition.CENTER -> "system_statusbarcontrols_dt"
                        DoubleTapPosition.RIGHT -> "system_statusbarcontrols_dt_right"
                    }
                    GlobalActions.handleAction(ctx, key)
                }
                is GestureCommand.TriggerLongPress -> {
                    if (ctx == null) continue
                    if (config.longPressVibrate) {
                        HookUtils.performStrongVibration(ctx, config.ignoreVibrateOff)
                    }
                    GlobalActions.handleAction(ctx, "system_statusbarcontrols_longpress")
                }
                GestureCommand.Reset,
                GestureCommand.BeginTracking -> { lastSentBrightnessRatio = -1f }
                else -> { /* PassThrough has no side effect here */ }
            }
        }
    }
}
