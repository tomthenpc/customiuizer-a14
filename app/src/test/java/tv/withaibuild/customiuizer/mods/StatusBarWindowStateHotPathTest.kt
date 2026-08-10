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
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightRuntime
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
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
    fun knownEnabledStatusBarUpdatesLatestWithoutRebuildingWeakRef() {
        configureHeight(44)
        val a = FakeWindowState(statusType = true)
        val b = FakeWindowState(statusType = true)
        val runtime = statusBarHeightRuntime()

        runtime.rememberStatusBar(a)
        val refA = runtime.rememberStatusBar(a)
        runtime.rememberStatusBar(b)

        // Establish latest = b, then layout a again.
        runtime.markLatestIfKnown(b)
        assertSame(b, runtime.latestRefForTest()?.get())

        val snapshotBefore = runtime.knownSnapshotForTest()
        val refAIndex = snapshotBefore.indexOfFirst { it === refA }
        assertTrue(refAIndex >= 0)

        val chain = FakeChain(argList = listOf(a))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertSame(a, runtime.latestRefForTest()?.get())
        assertSame(refA, runtime.latestRefForTest())
        assertSame(refA, snapshotBefore[refAIndex])
    }

    @Test
    fun sourceGuard_usesSinglePassOwnerLookup() {
        val lines = facadeSourceLines()

        val isStatusBarCalls = callCount(lines, "isStatusBarWindow(")
        val isKnownCalls = callCount(lines, "isKnownStatusBarWindow(")
        val markLatestCalls = callCount(lines, "markLatestIfKnownStatusBar(")

        assertEquals(
            "onLayoutWindowLw must call isStatusBarWindow exactly once",
            1,
            isStatusBarCalls
        )
        assertEquals(
            "H3 (setFrames) must be the only isKnownStatusBarWindow caller",
            1,
            isKnownCalls
        )
        assertEquals(
            "markLatestIfKnownStatusBar must only be called from disabled path and isStatusBarWindow",
            2,
            markLatestCalls
        )
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

    private fun facadeSourceLines(): List<String> {
        val root = java.lang.System.getProperty("user.dir") ?: ""
        val project = if (File(root, "app").isDirectory) root else File(root).parent
        val file = File(project, "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt")
        return if (file.exists()) file.readLines() else emptyList()
    }

    private fun callCount(lines: List<String>, call: String): Int {
        return lines.count { it.contains(call) && !it.contains("fun ") }
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

    private fun statusBarHeightRuntime(): StatusBarHeightRuntime {
        val field = SystemStatusBarInsetsHooks::class.java.getDeclaredField("statusBarHeightRuntime")
        field.isAccessible = true
        return field.get(null) as StatusBarHeightRuntime
    }

    private fun rememberStatusBarWindow(win: Any) {
        statusBarHeightRuntime().rememberStatusBar(win)
    }

    private fun isKnownStatusBarWindow(win: Any): Boolean {
        return statusBarHeightRuntime().isKnownStatusBar(win)
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
