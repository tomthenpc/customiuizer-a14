package tv.withaibuild.customiuizer.mods.utils.gesture

import android.content.Context
import android.media.AudioManager
import android.view.View
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Executes the side-effect commands for status bar gestures.
 *
 * This class is deliberately stateless between events; all per-gesture state is held
 * by [GestureMachine].  It only performs the actual Android calls.
 */
class StatusBarGestureEffectExecutor : GestureEffectExecutor {

    override fun execute(
        commands: List<GestureCommand>,
        dependencies: GestureDependencies,
        config: GestureConfig,
        context: Any?,
    ) {
        val ctx = when (context) {
            is View -> context.context
            is Context -> context
            else -> return
        }

        val audioManager = dependencies.audioManager as? AudioManager

        for (command in commands) {
            when (command) {
                is GestureCommand.ApplyTemporaryBrightness -> {
                    XposedHelpers.callMethod(
                        dependencies.displayManager,
                        "setTemporaryBrightness",
                        dependencies.displayId,
                        command.ratio,
                    )
                }
                is GestureCommand.CommitBrightness -> {
                    XposedHelpers.callMethod(
                        dependencies.displayManager,
                        "setBrightness",
                        dependencies.displayId,
                        command.ratio,
                    )
                }
                is GestureCommand.AdjustVolume -> {
                    if (audioManager != null) {
                        val direction = if (command.raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                        @Suppress("WrongConstant")
                        audioManager.adjustVolume(direction, AudioManager.FLAG_PLAY_SOUND)
                    }
                }
                is GestureCommand.TriggerDoubleTap -> {
                    val key = when (command.position) {
                        DoubleTapPosition.LEFT -> "system_statusbarcontrols_dt_left"
                        DoubleTapPosition.CENTER -> "system_statusbarcontrols_dt"
                        DoubleTapPosition.RIGHT -> "system_statusbarcontrols_dt_right"
                    }
                    GlobalActions.handleAction(ctx, key)
                }
                is GestureCommand.TriggerLongPress -> {
                    if (config.longPressVibrate) {
                        HookUtils.performStrongVibration(ctx, config.ignoreVibrateOff)
                    }
                    GlobalActions.handleAction(ctx, "system_statusbarcontrols_longpress")
                }
                else -> { /* Reset / PassThrough / BeginTracking have no side effect here */ }
            }
        }
    }
}
