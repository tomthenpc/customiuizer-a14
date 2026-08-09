package tv.withaibuild.customiuizer.mods

import android.view.View
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import java.lang.reflect.Executable

/**
 * Behavioral tests for the drawer blur per-frame scope and snapshot.
 */
class DrawerBlurScopeTest {

    private val blurKey = "system_drawer_blur"
    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        MainModule.mPrefs.put(blurKey, 100)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()
    }

    @After
    fun tearDown() {
        while (SystemDisplayHooks.DrawerBlurScope.isActive()) {
            SystemDisplayHooks.DrawerBlurScope.exit()
        }
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            (MainModule.mPrefs).replaceSnapshot(savedPrefs)
        }
        SystemDisplayHooks.refreshDrawerBlurSnapshot()
    }

    @Test
    fun applyBlurOutsideScopeDoesNotAdjustRatio() {
        val chain = FakeApplyBlurChain(originalRatio = 0.5f)
        val result = SystemDisplayHooks.onApplyBlur(chain)

        assertEquals("ok", result)
        assertEquals(1, chain.proceedCount)
        assertEquals(0.5f, chain.lastProceedArgs[1])
    }

    @Test
    fun applyBlurInsideScopeAdjustsRatioBySnapshot() {
        MainModule.mPrefs.put(blurKey, 50)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        val chain = FakeApplyBlurChain(originalRatio = 0.5f)
        SystemDisplayHooks.DrawerBlurScope.enter(50)
        val result = try {
            SystemDisplayHooks.onApplyBlur(chain)
        } finally {
            SystemDisplayHooks.DrawerBlurScope.exit()
        }

        assertEquals("ok", result)
        assertEquals(1, chain.proceedCount)
        assertEquals(0.25f, chain.lastProceedArgs[1])
    }

    @Test
    fun nestedScopeDoesNotDoubleAdjust() {
        MainModule.mPrefs.put(blurKey, 50)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        val chain = FakeApplyBlurChain(originalRatio = 0.5f)
        SystemDisplayHooks.DrawerBlurScope.enter(50)
        SystemDisplayHooks.DrawerBlurScope.enter(50)
        try {
            SystemDisplayHooks.onApplyBlur(chain)
        } finally {
            SystemDisplayHooks.DrawerBlurScope.exit()
            SystemDisplayHooks.DrawerBlurScope.exit()
        }

        assertEquals(0.25f, chain.lastProceedArgs[1])
    }

    @Test
    fun doFrameEntersAndExitsScopeAndCleansUpOnException() {
        MainModule.mPrefs.put(blurKey, 75)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        val error = OutOfMemoryError("doFrame OOM")
        val innerChain = FakeDoFrameChain(
            proceedResult = "inner",
            proceedThrow = error,
            onProceed = {
                assertTrue("scope must be active inside doFrame", SystemDisplayHooks.DrawerBlurScope.isActive())
                assertEquals(75, SystemDisplayHooks.DrawerBlurScope.getModifier())
            }
        )

        try {
            SystemDisplayHooks.onDoFrame(innerChain)
            assertTrue("expected exception", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertFalse("scope must be cleaned up after exception", SystemDisplayHooks.DrawerBlurScope.isActive())
        assertEquals(100, SystemDisplayHooks.DrawerBlurScope.getModifier())
        assertEquals(1, innerChain.proceedCount)
    }

    @Test
    fun doFrameProceedsAndExitsCleanly() {
        MainModule.mPrefs.put(blurKey, 60)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        val chain = FakeDoFrameChain(proceedResult = "done")
        val result = SystemDisplayHooks.onDoFrame(chain)

        assertEquals("done", result)
        assertFalse(SystemDisplayHooks.DrawerBlurScope.isActive())
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun scopeIsThreadLocal() {
        MainModule.mPrefs.put(blurKey, 50)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        val chain = FakeApplyBlurChain(originalRatio = 0.5f)
        val otherThread = Thread {
            SystemDisplayHooks.DrawerBlurScope.enter(50)
        }
        otherThread.start()
        otherThread.join()

        // Main thread should remain unaffected by the other thread's scope.
        val result = SystemDisplayHooks.onApplyBlur(chain)

        assertEquals("ok", result)
        assertEquals(0.5f, chain.lastProceedArgs[1])
    }

    @Test
    fun controlPanelSetBlurRatioUsesSnapshot() {
        MainModule.mPrefs.put(blurKey, 200)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        val chain = FakeControlPanelChain(originalRatio = 0.5f)
        val result = SystemDisplayHooks.onControlPanelSetBlurRatio(chain)

        assertEquals("ok", result)
        assertEquals(1.0f, chain.lastProceedArgs[0])
    }

    @Test
    fun unrelatedPreferenceKeyDoesNotRefreshSnapshot() {
        MainModule.mPrefs.put(blurKey, 50)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        // Change the preference but notify with an unrelated key.
        MainModule.mPrefs.put(blurKey, 200)
        SystemDisplayHooks.onDrawerBlurPreferenceChanged("system_some_other_key")

        // doFrame must still use the old (50) snapshot for applyBlur.
        val applyChain = FakeApplyBlurChain(originalRatio = 1.0f)
        val doFrameChain = FakeDoFrameChain(
            proceedResult = "done",
            onProceed = {
                SystemDisplayHooks.onApplyBlur(applyChain)
            }
        )

        SystemDisplayHooks.onDoFrame(doFrameChain)

        assertEquals(0.5f, applyChain.lastProceedArgs[1])
    }

    @Test
    fun nullPreferenceKeyRefreshesSnapshot() {
        MainModule.mPrefs.put(blurKey, 100)
        SystemDisplayHooks.refreshDrawerBlurSnapshot()

        MainModule.mPrefs.put(blurKey, 25)
        SystemDisplayHooks.onDrawerBlurPreferenceChanged(null)

        val chain = FakeApplyBlurChain(originalRatio = 1.0f)
        SystemDisplayHooks.DrawerBlurScope.enter(25)
        try {
            SystemDisplayHooks.onApplyBlur(chain)
        } finally {
            SystemDisplayHooks.DrawerBlurScope.exit()
        }

        assertEquals(0.25f, chain.lastProceedArgs[1])
    }

    private class FakeApplyBlurChain(val originalRatio: Float) : XposedInterface.Chain {

        var proceedCount = 0
        lateinit var lastProceedArgs: Array<Any?>

        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> = listOf(null, originalRatio, true)
        override fun getArg(index: Int): Any? = getArgs()[index]

        override fun proceed(): Any? = error("must use proceed(args)")

        override fun proceed(args: Array<Any?>): Any? {
            proceedCount++
            lastProceedArgs = args
            return "ok"
        }

        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }

    private class FakeDoFrameChain(
        private val proceedResult: Any?,
        private val proceedThrow: Throwable? = null,
        private val onProceed: () -> Unit = {},
    ) : XposedInterface.Chain {

        var proceedCount = 0

        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> = listOf(0L)
        override fun getArg(index: Int): Any? = getArgs()[index]

        override fun proceed(): Any? {
            proceedCount++
            onProceed()
            proceedThrow?.let { throw it }
            return proceedResult
        }

        override fun proceed(args: Array<Any?>): Any? = error("must use proceed()")
        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }

    private class FakeControlPanelChain(val originalRatio: Float) : XposedInterface.Chain {

        var proceedCount = 0
        lateinit var lastProceedArgs: Array<Any?>

        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? = null
        override fun getArgs(): List<Any?> = listOf(originalRatio, true)
        override fun getArg(index: Int): Any? = getArgs()[index]

        override fun proceed(): Any? = error("must use proceed(args)")

        override fun proceed(args: Array<Any?>): Any? {
            proceedCount++
            lastProceedArgs = args
            return "ok"
        }

        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }
}
