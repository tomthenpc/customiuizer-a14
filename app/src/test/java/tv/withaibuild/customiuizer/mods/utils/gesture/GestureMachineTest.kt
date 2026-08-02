package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessDisplayStub(var currentBrightness: Float = 0.5f) {
    fun getBrightness(displayId: Int): Float = currentBrightness
    fun setTemporaryBrightness(displayId: Int, value: Float) {}
    fun setBrightness(displayId: Int, value: Float) {}
}

class GestureMachineTest {

    private val geometry = GestureGeometry(
        screenWidth = 1080,
        density = 3.0f,
        statusBarHeight = 80,
        minBacklight = 0.0f,
        maxBacklight = 1.0f,
    )

    private val displayStub = BrightnessDisplayStub()

    private val deps = GestureDependencies(
        ownerId = 1,
        classLoaderIdentity = "cl-1",
        displayManager = displayStub,
        displayId = 0,
        minimumBacklight = 0.0f,
        maximumBacklight = 1.0f,
        audioManager = Any(),
        statusBarHeight = 80,
        screenWidth = 1080,
        density = 3.0f,
        getBrightnessMethod = BrightnessDisplayStub::class.java.getMethod("getBrightness", Int::class.java),
    )

    private val config = GestureConfig(
        singleAction = 2,
        brightnessSensitivityFactor = 0.618f,
        volumeSensitivityFactor = 1.0f,
    )

    private val dummyContext = Any()

    private fun machine(
        exec: FakeGestureEffectExecutor = FakeGestureEffectExecutor(),
        testConfig: GestureConfig = config,
        testDeps: GestureDependencies = deps,
    ): Pair<GestureMachine, FakeGestureEffectExecutor> {
        val m = GestureMachine(
            classLoaderIdentity = "cl-1",
            configResolver = { testConfig },
            depsResolver = object : GestureDependenciesResolver {
                override fun prepare(
                    ownerId: Int,
                    classLoaderIdentity: String,
                    context: Any,
                ): GestureDependenciesResult = GestureDependenciesResult.Ready(testDeps)
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

        m.prepare(1, dummyContext)
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

        m.prepare(1, dummyContext)
        m.observe(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.observe(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.observe(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)

        assertTrue(exec.brightnessApplied.isEmpty())
        assertTrue(exec.brightnessCommitted.isEmpty())
    }

    @Test
    fun sameEventInterceptThenTouch_allowsOnce() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.observe(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1, entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)

        val e = event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 1)
        m.observe(e.copy(entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)
        m.dispatch(e.copy(entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun interceptDown_thenTouchDown_stateOnlyChangesOnce() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.observe(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, entry = GestureEntry.STATUS_BAR_TOUCH), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        assertEquals(1, exec.brightnessApplied.size)
    }

    @Test
    fun interceptMove_thenTouchMove_volumeExecutesOnce() {
        val (m, exec) = machine(testConfig = config.copy(singleAction = 3))

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.observe(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        assertEquals(1, exec.volumeAdjusted.size)
    }

    @Test
    fun interceptUp_thenTouchUp_brightnessCommitsOnce() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.observe(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L, entry = GestureEntry.STATUS_BAR_INTERCEPT), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertEquals(1, exec.brightnessCommitted.size)
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
        assertTrue(!m.prepare(1, dummyContext))
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        assertTrue(exec.brightnessApplied.isEmpty())
    }

    @Test
    fun ownerChange_isIndependent() {
        val (m, exec) = machine()

        m.prepare(1, dummyContext)
        m.prepare(2, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 1), dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L, ownerId = 2), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L, ownerId = 2), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 60L, downTime = 0L, ownerId = 1), dummyContext)

        assertEquals(2, exec.brightnessApplied.size)
    }

    @Test
    fun brightnessStartsFromCurrentValue() {
        for (current in listOf(0.1f, 0.5f, 0.9f)) {
            val stub = BrightnessDisplayStub(current)
            val (m, exec) = machine(testDeps = deps.copy(displayManager = stub))

            m.prepare(1, dummyContext)
            m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
            m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
            m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

            val expected = (current + 200f / 1080f * config.brightnessSensitivityFactor)
                .coerceIn(0.0f, 1.0f)

            assertEquals("current=$current applied", 1, exec.brightnessApplied.size)
            assertEquals("current=$current", expected, exec.brightnessApplied[0], 0.0001f)
            assertEquals("current=$current committed", expected, exec.brightnessCommitted[0], 0.0001f)
        }
    }

    @Test
    fun brightnessReadFailure_passesThrough() {
        val (m, exec) = machine(testDeps = deps.copy(getBrightnessMethod = null))

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.UP, x = 300f, y = 10f, eventTime = 100L, downTime = 0L), dummyContext)

        assertTrue(exec.brightnessApplied.isEmpty())
        assertTrue(exec.brightnessCommitted.isEmpty())
        assertEquals(1, exec.resets)
    }

    @Test
    fun brightnessFirstMoveDoesNotJump() {
        val stub = BrightnessDisplayStub(0.1f)
        val (m, exec) = machine(testDeps = deps.copy(displayManager = stub))

        m.prepare(1, dummyContext)
        m.dispatch(event(GestureAction.DOWN, x = 100f, y = 10f, eventTime = 0L), dummyContext)
        m.dispatch(event(GestureAction.MOVE, x = 300f, y = 10f, eventTime = 50L, downTime = 0L), dummyContext)

        val expected = 0.1f + 200f / 1080f * config.brightnessSensitivityFactor
        assertEquals(1, exec.brightnessApplied.size)
        assertEquals(expected, exec.brightnessApplied[0], 0.0001f)
    }
}
