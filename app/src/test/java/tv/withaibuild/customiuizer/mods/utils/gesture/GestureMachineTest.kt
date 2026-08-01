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

    private val dummyContext = Any()

    private fun machine(exec: FakeGestureEffectExecutor = FakeGestureEffectExecutor()): Pair<GestureMachine, FakeGestureEffectExecutor> {
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { config },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(
                    ownerId: Int,
                    classLoaderIdentity: String,
                    context: Any,
                ): GestureDependenciesResult = GestureDependenciesResult.Ready(deps)
            },
            effectExecutor = exec,
        )
        return m to exec
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
        val (m, exec) = machine()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(1, exec.brightnessCommitted.size)
        assertEquals(1, exec.resets)
    }

    @Test
    fun interceptObservesButDoesNotExecute() {
        val (m, exec) = machine()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)

        assertTrue(exec.brightnessApplied.isEmpty())
        assertTrue(exec.brightnessCommitted.isEmpty())
    }

    @Test
    fun sameEventInterceptThenTouch_allowsOnce() {
        val (m, exec) = machine()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)

        val e = event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 1)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun dependencyNotReady_returnsPassThrough() {
        val exec = FakeGestureEffectExecutor()
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { config },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(
                    ownerId: Int,
                    classLoaderIdentity: String,
                    context: Any,
                ): GestureDependenciesResult = GestureDependenciesResult.NotReady
            },
            effectExecutor = exec,
        )
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        assertTrue(exec.brightnessApplied.isEmpty())
    }

    @Test
    fun ownerChange_isIndependent() {
        val (m, exec) = machine()

        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 2), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 2), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 60L, downTime = 0L, ownerId = 1), dummyContext)

        assertEquals(2, exec.brightnessApplied.size)
    }
}
