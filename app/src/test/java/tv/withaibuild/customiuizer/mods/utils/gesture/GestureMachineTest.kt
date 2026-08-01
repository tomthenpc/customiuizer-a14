package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMachineTest {

    private val geometry = GestureGeometry(
        screenWidth = 1080,
        density = 3.0f,
        statusBarHeight = 80,
        minBacklight = 0.0f,
        maxBacklight = 1.0f,
    )

    private val deps = GestureDependencies(
        ownerId = 1,
        classLoaderIdentity = "cl-1",
        displayManager = Any(),
        displayId = 0,
        minimumBacklight = 0.0f,
        maximumBacklight = 1.0f,
        audioManager = Any(),
        statusBarHeight = 80,
        screenWidth = 1080,
        density = 3.0f,
    )

    private val config = GestureConfig(
        singleAction = 2,
        brightnessSensitivityFactor = 0.618f,
        volumeSensitivityFactor = 1.0f,
    )

    private fun machine(
        depsResolver: GestureDependenciesResolver = readyResolver(),
    ): GestureMachine =
        GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { config },
            depsResolver = depsResolver,
        )

    private fun readyResolver() = object : GestureDependenciesResolver {
        override fun prepare(ownerId: Int, classLoaderIdentity: String): GestureDependenciesResult {
            return GestureDependenciesResult.Ready(deps)
        }
    }

    private fun event(
        action: Int,
        x: Float = 0f,
        y: Float = 0f,
        eventTime: Long = 0L,
        downTime: Long = 0L,
        pointerCount: Int = 1,
        entry: GestureEntry = GestureEntry.STATUS_BAR_TOUCH,
        ownerId: Int = 1,
    ) = GestureEvent(
        entry = entry,
        actionMasked = action,
        downTime = downTime,
        eventTime = eventTime,
        x = x,
        y = y,
        pointerCount = pointerCount,
        ownerId = ownerId,
    )

    @Test
    fun statusBarTouch_brightnessCycle() {
        val m = machine()
        val exec = FakeGestureEffectExecutor()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L))
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L)).forEach { execute(it, exec) }
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L)).forEach { execute(it, exec) }

        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(1, exec.brightnessCommitted.size)
        assertEquals(1, exec.resets)
    }

    @Test
    fun interceptObservesButDoesNotExecute() {
        val m = machine()
        val exec = FakeGestureEffectExecutor()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT))
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, entry = GestureEntry.STATUS_BAR_INTERCEPT))
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, entry = GestureEntry.STATUS_BAR_INTERCEPT))

        assertTrue(exec.brightnessApplied.isEmpty())
        assertTrue(exec.brightnessCommitted.isEmpty())
    }

    @Test
    fun sameEventInterceptThenTouch_allowsOnce() {
        val m = machine()
        val exec = FakeGestureEffectExecutor()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_INTERCEPT))
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_TOUCH))

        val e = event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 1)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_INTERCEPT))
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH)).forEach { execute(it, exec) }
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH)).forEach { execute(it, exec) }

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun dependencyNotReady_returnsPassThrough() {
        val m = machine(object : GestureDependenciesResolver {
            override fun prepare(ownerId: Int, classLoaderIdentity: String) = GestureDependenciesResult.NotReady
        })
        val commands = m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L))
        assertEquals(GestureCommand.PassThrough, commands.single())
    }

    @Test
    fun ownerChange_isIndependent() {
        val m = machine()
        val exec = FakeGestureEffectExecutor()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1))
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 2))
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 2)).forEach { execute(it, exec) }
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 60L, downTime = 0L, ownerId = 1)).forEach { execute(it, exec) }

        assertEquals(2, exec.brightnessApplied.size)
    }

    private fun execute(command: GestureCommand, exec: FakeGestureEffectExecutor) {
        when (command) {
            is GestureCommand.ApplyTemporaryBrightness -> exec.applyTemporaryBrightness(command.ratio)
            is GestureCommand.CommitBrightness -> exec.commitBrightness(command.ratio)
            is GestureCommand.AdjustVolume -> exec.adjustVolume(command.raise)
            is GestureCommand.TriggerDoubleTap -> exec.triggerDoubleTap(command.position, config)
            is GestureCommand.TriggerLongPress -> exec.triggerLongPress(config)
            is GestureCommand.Reset -> exec.reset()
            else -> { /* pass through */ }
        }
    }

    private fun List<GestureCommand>.single(): GestureCommand {
        assertEquals(1, size)
        return first()
    }
}
