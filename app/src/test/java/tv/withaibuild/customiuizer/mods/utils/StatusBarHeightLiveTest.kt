package tv.withaibuild.customiuizer.mods.utils

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.android.server.wm.WindowState as FakeWindowState
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable

class StatusBarHeightLiveTest {

    @After
    fun tearDown() {
        StatusBarHeightConfig.resetForTest()
        SystemStatusBarInsetsHooks.resetForTest()
    }

    @Test
    fun setFrames_statusBarEnabled_adjustsFrameBottomToTopPlusConfiguredPx() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        discoverStatusBarWindow(win)

        val clientFrames = ClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 150)
        setRect(clientFrames.displayFrame, 0, 0, 1080, 2400)
        setRect(clientFrames.parentFrame, 0, 0, 1080, 2400)

        val chain = FakeChain(target = win, args = arrayOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals("original setFrames must be called exactly once", 1, chain.proceedCount)
        assertEquals(0, clientFrames.frame.top)
        assertEquals(129, clientFrames.frame.bottom) // 44dp * 469 / 160 = 128.975 -> 129
        assertEquals(1080, clientFrames.frame.right)
        assertEquals(0, clientFrames.frame.left)

        assertEquals(0, clientFrames.displayFrame.top)
        assertEquals(2400, clientFrames.displayFrame.bottom)
        assertEquals(0, clientFrames.parentFrame.top)
        assertEquals(2400, clientFrames.parentFrame.bottom)
    }

    @Test
    fun setFrames_statusBarEnabled_respectsNonZeroTop() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        discoverStatusBarWindow(win)

        val clientFrames = ClientWindowFrames()
        setRect(clientFrames.frame, 0, 20, 1080, 150)

        val chain = FakeChain(target = win, args = arrayOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(20, clientFrames.frame.top)
        assertEquals(149, clientFrames.frame.bottom) // 20 + 129
    }

    @Test
    fun setFrames_disabled_doesNotModifyAnyFrame() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 11) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val clientFrames = ClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 150)

        val chain = FakeChain(target = win, args = arrayOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(150, clientFrames.frame.bottom)
    }

    @Test
    fun setFrames_nonStatusBar_doesNotModify() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = FakeWindowState().apply {
            mAttrs.type = WindowManager.LayoutParams.TYPE_APPLICATION
            mAttrs.packageName = "com.example.app"
        }
        val clientFrames = ClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 150)

        val chain = FakeChain(target = win, args = arrayOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(150, clientFrames.frame.bottom)
    }

    @Test
    fun setFrames_proceedCalledExactlyOnceEvenWhenFrameChanges() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val clientFrames = ClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 150)

        val chain = FakeChain(target = win, args = arrayOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun layoutWindowLw_statusBarEnabled_setsAttrsHeightAndProceedsOnce() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        setRect(win.mFrame, 0, 0, 1080, 150)

        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(129, win.mAttrs.height)
    }

    @Test
    fun layoutWindowLw_disabled_doesNotSetAttrsHeight() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 11) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        win.mAttrs.height = WindowManager.LayoutParams.WRAP_CONTENT

        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, win.mAttrs.height)
    }

    @Test
    fun requestStatusBarTraversal_usesRequestTraversalNotPerformSurfacePlacementAndCoalesces() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        val placer = (win.mWmService as FakeWindowState.FakeWindowManagerService)
            .mWindowPlacerLocked as FakeWindowState.FakeWindowSurfacePlacer

        SystemStatusBarInsetsHooks.requestStatusBarTraversal()
        SystemStatusBarInsetsHooks.requestStatusBarTraversal()

        assertEquals("requestTraversal must be used", 1, placer.requestTraversalCount)
        assertEquals("performSurfacePlacement must not be called directly", 0, placer.performSurfacePlacementCount)
    }

    @Test
    fun requestStatusBarTraversal_differentGenerationRequestsAreNotCoalesced() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        val placer = (win.mWmService as FakeWindowState.FakeWindowManagerService)
            .mWindowPlacerLocked as FakeWindowState.FakeWindowSurfacePlacer

        // First request for the initial generation.
        SystemStatusBarInsetsHooks.requestStatusBarTraversal()

        // Bump generation by reconfiguring to a different preference.
        StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 40) })

        // Second request for the new generation must not be coalesced.
        SystemStatusBarInsetsHooks.requestStatusBarTraversal()

        assertEquals(2, placer.requestTraversalCount)
    }

    @Test
    fun reconfigure_returnsRealOldAndNewState() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 40) },
            fakeResources(469),
        )

        val change = StatusBarHeightConfig.reconfigure(
            PrefMap().apply { put("system_statusbarheight", 44) },
        )

        assertTrue(change.changed)
        assertEquals(40, change.previous.configuredDp)
        assertEquals(44, change.current.configuredDp)
        assertEquals(117, change.previous.configuredPx) // 40 * 469 / 160 = 117.25 -> 117
        assertEquals(129, change.current.configuredPx)
    }

    @Test
    fun setFrames_copiesOldBottomBeforeModifyingFrame() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        discoverStatusBarWindow(win)

        val clientFrames = ClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 150)

        val chain = FakeChain(target = win, args = arrayOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(129, clientFrames.frame.bottom)
    }

    @Test
    fun diagnosticsAreBoundedPerGeneration() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val chain1 = FakeChain(target = Any(), args = arrayOf(win))
        val clientFrames1 = ClientWindowFrames()
        setRect(clientFrames1.frame, 0, 0, 1080, 150)
        val chain2 = FakeChain(target = win, args = arrayOf(clientFrames1))

        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain1)
        SystemStatusBarInsetsHooks.onSetFrames(chain2)

        val liveCount = SystemStatusBarInsetsHooks.liveKeyCountForTest()

        // Re-run with the same generation: no new diagnostic keys.
        val chain3 = FakeChain(target = Any(), args = arrayOf(win))
        val clientFrames2 = ClientWindowFrames()
        setRect(clientFrames2.frame, 0, 0, 1080, 150)
        val chain4 = FakeChain(target = win, args = arrayOf(clientFrames2))

        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain3)
        SystemStatusBarInsetsHooks.onSetFrames(chain4)

        assertEquals("live diagnostic keys must not grow for the same generation", liveCount, SystemStatusBarInsetsHooks.liveKeyCountForTest())
    }

    @Test
    fun layoutWindowLw_nonStatusBar_returnsBeforeMetricsRecomputeOrRef() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(160),
        )

        val win = FakeWindowState().apply {
            mAttrs.type = WindowManager.LayoutParams.TYPE_APPLICATION
            mAttrs.packageName = "com.example.app"
            mDisplayContent = FakeWindowState.FakeDisplayContent(
                displayId = 1,
                metrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f },
            )
        }

        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(160, StatusBarHeightConfig.densityDpi)
        assertEquals(44, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun layoutWindowLw_disabledAfterEnabled_restoresOriginalHeight() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val originalHeight = WindowManager.LayoutParams.WRAP_CONTENT

        val chain1 = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain1)

        assertEquals(1, chain1.proceedCount)
        assertEquals(129, win.mAttrs.height)

        StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 11) })

        val chain2 = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain2)

        assertEquals(1, chain2.proceedCount)
        assertEquals(originalHeight, win.mAttrs.height)
    }

    @Test
    fun requestStatusBarTraversal_disabledAfterCustomChange_requestsOnceAndCoalesces() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        val placer = (win.mWmService as FakeWindowState.FakeWindowManagerService)
            .mWindowPlacerLocked as FakeWindowState.FakeWindowSurfacePlacer

        StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 11) })
        SystemStatusBarInsetsHooks.requestStatusBarTraversal()
        SystemStatusBarInsetsHooks.requestStatusBarTraversal()

        assertEquals(1, placer.requestTraversalCount)
    }

    @Test
    fun requestStatusBarTraversal_unavailable_doesNotFallbackToPerformSurfacePlacement() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        val placer = NoRequestTraversalPlacer()
        val wmService = win.mWmService as FakeWindowState.FakeWindowManagerService
        wmService.mWindowPlacerLocked = placer

        SystemStatusBarInsetsHooks.requestStatusBarTraversal()

        assertEquals(0, placer.performSurfacePlacementCount)
    }

    @Test
    fun layoutWindowLw_secondaryDisplay_usesLocalPxAndDoesNotPolluteGlobal() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )

        val win = newStatusBarWindow()
        win.mDisplayContent = FakeWindowState.FakeDisplayContent(
            displayId = 1,
            metrics = DisplayMetrics().apply { densityDpi = 200; density = 1.25f },
        )

        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(55, win.mAttrs.height) // 44 * 200 / 160
        assertEquals(469, StatusBarHeightConfig.densityDpi)
        assertEquals(129, StatusBarHeightConfig.configuredPx)
    }

    private fun setRect(rect: Rect, left: Int, top: Int, right: Int, bottom: Int) {
        rect.left = left
        rect.top = top
        rect.right = right
        rect.bottom = bottom
    }

    private fun newStatusBarWindow(): FakeWindowState {
        return FakeWindowState().apply {
            mAttrs.type = WindowManager.LayoutParams.TYPE_STATUS_BAR
            mAttrs.packageName = "com.android.systemui"
            mAttrs.height = WindowManager.LayoutParams.WRAP_CONTENT
        }
    }

    private fun discoverStatusBarWindow(win: FakeWindowState) {
        val chain = FakeChain(target = Any(), args = arrayOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)
        assertEquals(1, chain.proceedCount)
    }

    private fun fakeResources(densityDpi: Int): Resources {
        val metrics = DisplayMetrics().apply { this.densityDpi = densityDpi }
        val constructor = AssetManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val assetManager = constructor.newInstance()
        return object : Resources(assetManager, metrics, Configuration()) {
            override fun getDisplayMetrics(): DisplayMetrics = metrics
        }
    }

    private class FakeChain(
        private val target: Any?,
        val args: Array<Any?>,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        var calledWithArgs = false
            private set

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? = target
        override fun getArgs(): List<Any?> = args.toList()
        override fun getArg(index: Int): Any? = args.getOrNull(index)

        override fun proceed(): Any? {
            proceedCount++
            calledWithArgs = false
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            calledWithArgs = true
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}

/**
 * Test double for `android.window.ClientWindowFrames`.
 *
 * The real class is not available on the unit-test classpath, and the
 * `android.graphics.Rect` implementation in unit tests has stub `set()` methods.
 * This double uses plain public `Rect` fields that the hook touches by
 * reflection (`getObjectField(clientFrames, "frame")`) and direct field
 * assignment (`frame.bottom = ...`).
 */
class ClientWindowFrames {
    @JvmField
    val frame: Rect = Rect()

    @JvmField
    val displayFrame: Rect = Rect()

    @JvmField
    val parentFrame: Rect = Rect()
}

/**
 * Test double for a `WindowSurfacePlacer` that does not expose `requestTraversal()`.
 *
 * Used to verify that the status-bar refresh path falls back to waiting for a
 * natural layout instead of calling `performSurfacePlacement()` directly.
 */
private class NoRequestTraversalPlacer {
    var performSurfacePlacementCount = 0
        private set

    fun performSurfacePlacement() {
        performSurfacePlacementCount++
    }
}
