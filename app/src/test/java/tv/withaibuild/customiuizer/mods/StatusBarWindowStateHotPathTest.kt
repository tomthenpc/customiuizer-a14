package tv.withaibuild.customiuizer.mods

import android.graphics.Rect
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.lang.reflect.Executable

/**
 * Behavioral tests for the WMS hot paths in [SystemStatusBarInsetsHooks]:
 * [onLayoutWindowLw] and [onSetFrames].
 */
class StatusBarWindowStateHotPathTest {

    @Before
    fun setUp() {
        SystemStatusBarInsetsHooks.resetForTest()
        StatusBarHeightConfig.resetForTest()
        setWindowStateClass(FakeWindowState::class.java)
        setClientWindowFramesClass(FakeClientWindowFrames::class.java)
    }

    @After
    fun tearDown() {
        SystemStatusBarInsetsHooks.resetForTest()
        StatusBarHeightConfig.resetForTest()
        setWindowStateClass(null)
        setClientWindowFramesClass(null)
    }

    @Test
    fun disabledUnknownWindowStateDoesNotDiscoverStatusBar() {
        val win = FakeWindowState(statusType = true)

        val chain = FakeChain(argList = listOf(win))

        val result = SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue(result == null || result == Unit)
        assertFalse("unknown WindowState must not be discovered in disabled path", isKnownStatusBarWindow(win))
    }

    @Test
    fun disabledKnownStatusBarRestoresOriginalHeight() {
        val win = FakeWindowState(statusType = true)
        win.mAttrs.height = 80
        rememberStatusBarWindow(win)
        XposedHelpers.setAdditionalInstanceField(win, "customiuizer_originalStatusBarHeight", 48)

        val chain = FakeChain(argList = listOf(win))

        val result = SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals("known status bar height must be restored when disabled", 48, win.mAttrs.height)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun enabledUnknownWindowStateDiscoversAndAppliesHeight() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        win.mAttrs.height = 80

        assertTrue("fake must be recognized as status bar", SystemStatusBarInsetsHooks.isStatusBarWindow(win))

        val chain = FakeChain(argList = listOf(win))

        val result = SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("unknown status bar must be discovered in enabled path", isKnownStatusBarWindow(win))
        assertEquals(44, win.mAttrs.height)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun setFramesNonKnownWindowStateProceedsImmediately() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))

        val result = SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(80, clientFrames.frame.bottom)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun setFramesKnownWindowStateModifiesFrameBottom() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        rememberStatusBarWindow(win)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))

        val result = SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(44, clientFrames.frame.bottom)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun layoutWindowLwProceedsExactlyOnce() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)

        val chain = FakeChain(argList = listOf(win))

        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals("chain.proceed must be called exactly once", 1, chain.proceedCount)
    }

    @Test
    fun setFramesProceedsExactlyOnce() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        rememberStatusBarWindow(win)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))

        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals("chain.proceed must be called exactly once", 1, chain.proceedCount)
    }

    @Test
    fun layoutWindowLwFatalPropagatesAndProceedsOnce() {
        val error = OutOfMemoryError("layout OOM")
        val win = FakeWindowState(statusType = true)
        val chain = FakeChain(argList = listOf(win), proceedThrow = error)

        try {
            SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)
            assertTrue("fatal error must propagate", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun setFramesFatalPropagatesAndProceedsOnce() {
        configureHeight(44)
        val error = OutOfMemoryError("setFrames OOM")
        val win = FakeWindowState(statusType = true)
        rememberStatusBarWindow(win)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)
        val chain = FakeChain(target = win, argList = listOf(clientFrames), proceedThrow = error)

        try {
            SystemStatusBarInsetsHooks.onSetFrames(chain)
            assertTrue("fatal error must propagate", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertEquals(1, chain.proceedCount)
    }

    private fun configureHeight(dp: Int) {
        StatusBarHeightConfig.configure(PrefMap().apply { put("system_statusbarheight", dp) })
    }

    private fun setWindowStateClass(clazz: Class<*>?) {
        val field = SystemStatusBarInsetsHooks::class.java.getDeclaredField("windowStateClass")
        field.isAccessible = true
        field.set(null, clazz)
    }

    private fun setClientWindowFramesClass(clazz: Class<*>?) {
        val field = SystemStatusBarInsetsHooks::class.java.getDeclaredField("clientWindowFramesClass")
        field.isAccessible = true
        field.set(null, clazz)
    }

    @Suppress("UNCHECKED_CAST")
    private fun rememberStatusBarWindow(win: Any) {
        val field = SystemStatusBarInsetsHooks::class.java.getDeclaredField("statusBarWindows")
        field.isAccessible = true
        val current = field.get(null) as? Array<WeakReference<Any>> ?: emptyArray()
        val updated = current.toMutableList()
        updated.add(WeakReference(win))
        field.set(null, updated.toTypedArray() as Array<WeakReference<Any>>)
    }

    private fun isKnownStatusBarWindow(win: Any): Boolean {
        val field = SystemStatusBarInsetsHooks::class.java.getDeclaredField("statusBarWindows")
        field.isAccessible = true
        val known = field.get(null) as? Array<WeakReference<Any>> ?: return false
        for (i in known.indices) {
            if (known[i].get() === win) return true
        }
        return false
    }

    class FakeWindowState(statusType: Boolean = true) {
        val mAttrs: FakeLayoutParams = FakeLayoutParams(
            type = if (statusType) TYPE_STATUS_BAR else 1,
            packageName = if (statusType) "com.android.systemui" else "com.example",
        )
        var mDisplayId = 0
        var mDisplayMetrics = DisplayMetrics().apply {
            densityDpi = 160
            density = 1.0f
        }
        var mDisplayContent = FakeDisplayContent(mDisplayMetrics)

        fun getDisplayId(): Int = mDisplayId
        fun getDisplayMetrics(): DisplayMetrics = mDisplayMetrics

        override fun toString(): String = if (mAttrs.type == TYPE_STATUS_BAR) {
            "FakeWindowState{...StatusBar...}"
        } else {
            "FakeWindowState"
        }
    }

    class FakeDisplayContent(private val metrics: DisplayMetrics) {
        fun getDisplayMetrics(): DisplayMetrics = metrics
        fun getDisplayId(): Int = 0
    }

    class FakeLayoutParams(var type: Int = 0, var packageName: String = "", var height: Int = 0)

    class FakeClientWindowFrames(val frame: Rect = Rect())

    private fun setRect(rect: Rect, left: Int, top: Int, right: Int, bottom: Int) {
        rect.left = left
        rect.top = top
        rect.right = right
        rect.bottom = bottom
    }

    private class FakeChain(
        private val target: Any? = null,
        private val argList: List<Any?> = emptyList(),
        private val proceedThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? = target
        override fun getArgs(): List<Any?> = argList
        override fun getArg(index: Int): Any? = if (index in argList.indices) argList[index] else null

        override fun proceed(): Any? {
            proceedCount++
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }

    companion object {
        private const val TYPE_STATUS_BAR = 2000
    }
}
