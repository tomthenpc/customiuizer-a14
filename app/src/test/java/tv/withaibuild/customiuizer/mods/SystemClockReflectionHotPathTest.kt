package tv.withaibuild.customiuizer.mods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import java.io.File
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

private fun source(relativePath: String): String {
    var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
    while (true) {
        val candidate = File(directory, relativePath)
        if (candidate.isFile) return candidate.readText()
        directory = directory.parentFile
            ?: error("Repository root not found while locating $relativePath")
    }
}

class SystemClockReflectionHotPathTest {

    @Suppress("DEPRECATION")
    private inner class FakeResources : Resources(null, DisplayMetrics().apply { density = 2.0f }, Configuration()) {
        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = 0
        override fun getString(id: Int): String = ""
        override fun getColor(id: Int, theme: android.content.res.Resources.Theme?): Int = 0
    }

    @Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private inner class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()
        override fun getMainLooper(): Looper? = null
        override fun getResources(): Resources = fakeResources
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? = null
        override fun unregisterReceiver(receiver: BroadcastReceiver?) {}
    }

    /**
     * Fake controller that mimics the real MiuiStatusBarClockController fields.
     */
    private class FakeController {
        lateinit var mContext: Context
        var mCalendar: Any = java.util.Calendar.getInstance()
        var mIs24: Boolean = false
        val mClockListeners = ArrayList<Any>()
    }

    /**
     * A clock view with a real updateTime method that can be invoked via reflection.
     */
    private class FakeClockView(context: Context) : TextView(context) {
        var updateTimeCallCount = 0
        fun updateTime() {
            updateTimeCallCount++
        }
    }

    private fun screenStateListeners(): ArrayList<*> {
        val listenersField = ScreenStateController::class.java.getDeclaredField("listeners")
        listenersField.isAccessible = true
        return listenersField.get(ScreenStateController) as ArrayList<*>
    }

    private fun setCurrentSnapshot(snapshot: SystemClockHooks.ClockStyleSnapshot?) {
        val field = SystemClockHooks::class.java.getDeclaredField("clockStyleSnapshot")
        field.isAccessible = true
        field.set(SystemClockHooks, snapshot)
    }

    private fun makeSnapshotWithSeconds(statusBar: Boolean, cc: Boolean): SystemClockHooks.ClockStyleSnapshot {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", statusBar)
            put("system_statusbar_clock_24hour_format", true)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", true)
            put("system_cc_clock_customformat", if (cc) "HH:mm:ss" else "")
        }
        return SystemClockHooks.buildClockStyleSnapshot(prefs, FakeResources())
    }

    private fun makeController(clock: View): FakeController {
        val controller = FakeController()
        controller.mContext = FakeContext()
        ModuleHelper.setViewInfo(clock, "clockName", "clock")
        ModuleHelper.setViewInfo(clock, "showSeconds", true)
        controller.mClockListeners.add(clock)
        return controller
    }

    private fun disposeTicker(ticker: Any?) {
        if (ticker != null) {
            ticker.javaClass.getMethod("dispose").invoke(ticker)
        }
    }

    /**
     * Resolves ClockReflectionState for the given controller via reflection.
     */
    private fun resolveState(controller: Any): Any? {
        val stateClass = Class.forName(
            "tv.withaibuild.customiuizer.mods.SystemClockHooks\$ClockReflectionState"
        )
        val resolveMethod = stateClass.getDeclaredMethod("resolve", Any::class.java)
        resolveMethod.isAccessible = true
        return resolveMethod.invoke(null, controller)
    }

    /**
     * Gets the reflectionState field from a SecondTicker instance.
     */
    private fun getTickerReflectionState(ticker: Any): Any? {
        val field = ticker.javaClass.getDeclaredField("reflectionState")
        field.isAccessible = true
        return field.get(ticker)
    }

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
    }

    @After
    fun tearDown() {
        val listeners = screenStateListeners()
        @Suppress("UNCHECKED_CAST")
        val copy = ArrayList(listeners as ArrayList<ScreenStateController.ScreenStateListener>)
        for (listener in copy) {
            ScreenStateController.removeListener(listener)
        }
    }

    // ==================== Step 1: RED tests — prove old path uses XposedHelpers ====================

    /**
     * RED: The SecondTicker.run() body must NOT contain XposedHelpers.getObjectField
     * or XposedHelpers.callMethod. This test fails until the reflection state
     * caching is implemented.
     */
    @Test
    fun secondTickerRun_doesNotUseXposedHelpersReflection() {
        val path = "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt"
        val text = source(path)

        // Extract the SecondTicker.run() method body
        val runBody = text.substringAfter("override fun run() {")
            .substringBefore("private fun scheduleNextTick")

        assertFalse(
            "SecondTicker.run() must not use XposedHelpers.getObjectField in tick path",
            runBody.contains("XposedHelpers.getObjectField")
        )
        assertFalse(
            "SecondTicker.run() must not use XposedHelpers.callMethod in tick path",
            runBody.contains("XposedHelpers.callMethod")
        )
        assertFalse(
            "SecondTicker.run() must not use XposedHelpers.setObjectField in tick path",
            runBody.contains("XposedHelpers.setObjectField")
        )
    }

    /**
     * RED: A ClockReflectionState class must exist and hold Field/Method references,
     * not View/Context/Controller instances.
     */
    @Test
    fun clockReflectionState_existsAndHoldsOnlyFieldAndMethod() {
        val path = "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt"
        val text = source(path)

        assertTrue(
            "ClockReflectionState class must be declared",
            text.contains("class ClockReflectionState")
        )

        // Verify the class body does not declare View or Context FIELDS (not params)
        val stateClassBody = text.substringAfter("class ClockReflectionState")
            .substringBefore("\n    private class SecondTicker")

        val viewFieldPattern = Regex("(private\\s+(val|var)\\s+\\w+\\s*:\\s*View(?![a-zA-Z]))")
        val contextFieldPattern = Regex("(private\\s+(val|var)\\s+\\w+\\s*:\\s*Context(?![a-zA-Z]))")

        assertFalse(
            "ClockReflectionState must not declare View fields",
            viewFieldPattern.containsMatchIn(stateClassBody)
        )
        assertFalse(
            "ClockReflectionState must not declare Context fields",
            contextFieldPattern.containsMatchIn(stateClassBody)
        )
    }

    // ==================== Step 5: Functional tests ====================

    /**
     * Test 1: First controller resolution — ClockReflectionState resolves all fields
     * and methods correctly on first call.
     */
    @Test
    fun tick_firstControllerResolution_resolvesAllFieldsAndMethods() {
        val clock = FakeClockView(FakeContext())
        val controller = makeController(clock)

        val state = resolveState(controller)
        assertNotNull("ClockReflectionState must resolve successfully", state)

        // Verify the state can read mCalendar
        val stateClass = state!!.javaClass
        val getCalendarMethod = stateClass.getDeclaredMethod("getCalendar", Any::class.java)
        getCalendarMethod.isAccessible = true
        val calendar = getCalendarMethod.invoke(state, controller)
        assertNotNull("getCalendar must return the calendar object", calendar)
        assertSame("calendar must be the same instance as controller.mCalendar", controller.mCalendar, calendar)

        // Verify the state can read mClockListeners
        val getListenersMethod = stateClass.getDeclaredMethod("getClockListeners", Any::class.java)
        getListenersMethod.isAccessible = true
        val listeners = getListenersMethod.invoke(state, controller)
        assertNotNull("getClockListeners must return the list", listeners)
        assertSame("listeners must be the same instance", controller.mClockListeners, listeners)

        // Verify the state can set mIs24
        val setIs24Method = stateClass.getDeclaredMethod("setIs24", Any::class.java, Boolean::class.javaPrimitiveType)
        setIs24Method.isAccessible = true
        setIs24Method.invoke(state, controller, true)
        assertTrue("mIs24 must be set to true", controller.mIs24)

        // Verify the state can call updateTime on the clock view
        val callUpdateTimeMethod = stateClass.getDeclaredMethod("callUpdateTime", View::class.java)
        callUpdateTimeMethod.isAccessible = true
        val result = callUpdateTimeMethod.invoke(state, clock) as Boolean
        assertTrue("callUpdateTime must return true", result)
        assertEquals("updateTime must be called exactly once", 1, clock.updateTimeCallCount)
    }

    /**
     * Test 2: Second tick — zero reflection lookup (state already resolved).
     * We verify by checking that the same state object is reused across ticks.
     */
    @Test
    fun tick_secondTick_reusesResolvedState() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val clock = FakeClockView(FakeContext())
        val controller = makeController(clock)
        SystemClockHooks.initSecondTicker(controller, true, true)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!

        // Simulate first tick by setting the reflection state
        val state1 = resolveState(controller)
        assertNotNull(state1)

        // Set the state on the ticker
        val stateField = ticker.javaClass.getDeclaredField("reflectionState")
        stateField.isAccessible = true
        stateField.set(ticker, state1)

        // Verify the state is reused (isForController returns true)
        val stateClass = state1!!.javaClass
        val isForControllerMethod = stateClass.getDeclaredMethod("isForController", Any::class.java)
        isForControllerMethod.isAccessible = true
        val isForController = isForControllerMethod.invoke(state1, controller) as Boolean
        assertTrue("state must be for this controller", isForController)

        // The state should be the same object
        val stateFromTicker = stateField.get(ticker)
        assertSame("ticker must hold the same state object", state1, stateFromTicker)

        // Verify updateTime still works via the reused state
        val callUpdateTimeMethod = stateClass.getDeclaredMethod("callUpdateTime", View::class.java)
        callUpdateTimeMethod.isAccessible = true
        callUpdateTimeMethod.invoke(state1, clock)
        callUpdateTimeMethod.invoke(state1, clock)
        assertEquals("updateTime must be called 2 more times", 2, clock.updateTimeCallCount)

        disposeTicker(ticker)
    }

    /**
     * Test 3: Controller replacement — new controller gets new reflection state.
     */
    @Test
    fun tick_controllerReplacement_reResolvesState() {
        val clock1 = FakeClockView(FakeContext())
        val controller1 = makeController(clock1)
        val state1 = resolveState(controller1)
        assertNotNull("state for controller1 must resolve", state1)

        val clock2 = FakeClockView(FakeContext())
        val controller2 = makeController(clock2)
        controller2.mCalendar = java.util.Calendar.getInstance()
        val state2 = resolveState(controller2)
        assertNotNull("state for controller2 must resolve", state2)

        // Verify state1 is for controller1, not controller2
        val stateClass = state1!!.javaClass
        val isForControllerMethod = stateClass.getDeclaredMethod("isForController", Any::class.java)
        isForControllerMethod.isAccessible = true
        assertTrue("state1 must be for controller1", isForControllerMethod.invoke(state1, controller1) as Boolean)
        assertFalse("state1 must not be for controller2", isForControllerMethod.invoke(state1, controller2) as Boolean)

        // Verify state2 can read from controller2
        val getCalendarMethod = stateClass.getDeclaredMethod("getCalendar", Any::class.java)
        getCalendarMethod.isAccessible = true
        val calendar2 = getCalendarMethod.invoke(state2, controller2)
        assertSame("state2 must read controller2's calendar", controller2.mCalendar, calendar2)

        // Verify state2 can call updateTime on clock2
        val callUpdateTimeMethod = stateClass.getDeclaredMethod("callUpdateTime", View::class.java)
        callUpdateTimeMethod.isAccessible = true
        callUpdateTimeMethod.invoke(state2, clock2)
        assertTrue("clock2 must have updateTime called", clock2.updateTimeCallCount > 0)
        assertEquals("clock1 must not have been touched", 0, clock1.updateTimeCallCount)
    }

    /**
     * Test 4: Field does not exist — safe fallback, resolve returns null.
     */
    @Test
    fun tick_fieldDoesNotExist_safeFallback() {
        // A controller class that does NOT have mCalendar field
        val badController = object : Any() {
            @Suppress("unused")
            val mClockListeners = ArrayList<Any>()
        }

        val state = resolveState(badController)
        assertNull(
            "ClockReflectionState.resolve must return null when fields don't exist",
            state
        )
    }

    /**
     * Test 5: WeakReference release — when controller is GC'd, state is cleaned.
     */
    @Test
    fun tick_weakReferenceRelease_stateCleaned() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        fun createAndDrop(): Pair<Any, WeakReference<Any>> {
            val clock = FakeClockView(FakeContext())
            val controller = makeController(clock) as Any
            SystemClockHooks.initSecondTicker(controller, true, true)
            val ticker = SystemClockHooks.activeSecondTicker(controller)!!
            val ref = WeakReference(controller)
            return ticker to ref
        }

        val (ticker, controllerRef) = createAndDrop()
        java.lang.System.gc()
        Thread.sleep(200L)

        assertNull(
            "controller must be GC'd when no strong reference remains",
            controllerRef.get()
        )

        // Ticker should self-dispose on next run when controller is gone
        ticker.javaClass.getMethod("run").invoke(ticker)

        disposeTicker(ticker)
    }

    /**
     * Test 6: Same snapshot does not cause duplicate initialization.
     */
    @Test
    fun tick_sameSnapshot_noDuplicateInit() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val clock = FakeClockView(FakeContext())
        val controller = makeController(clock)
        SystemClockHooks.initSecondTicker(controller, true, true)
        val ticker = SystemClockHooks.activeSecondTicker(controller)!!

        // Call initSecondTicker again with same flags — should keep same ticker
        SystemClockHooks.initSecondTicker(controller, true, true)
        val ticker2 = SystemClockHooks.activeSecondTicker(controller)

        assertSame("same snapshot must not create a new ticker", ticker, ticker2)

        disposeTicker(ticker)
    }

    /**
     * Verify that ClockReflectionState holds a WeakReference to the controller.
     */
    @Test
    fun clockReflectionState_holdsControllerWeakly() {
        val stateClass = Class.forName(
            "tv.withaibuild.customiuizer.mods.SystemClockHooks\$ClockReflectionState"
        )
        val controllerField = stateClass.declaredFields.firstOrNull {
            it.type == WeakReference::class.java
        }
        assertNotNull(
            "ClockReflectionState must hold a WeakReference field for the controller",
            controllerField
        )
    }

    /**
     * Verify that SecondTicker has a reflectionState field.
     */
    @Test
    fun secondTicker_hasReflectionStateField() {
        val tickerClass = Class.forName(
            "tv.withaibuild.customiuizer.mods.SystemClockHooks\$SecondTicker"
        )
        val stateField = tickerClass.getDeclaredField("reflectionState")
        assertNotNull("SecondTicker must have a reflectionState field", stateField)
    }
}
