package tv.withaibuild.customiuizer.mods.utils

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks as Insets
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable

class StatusBarInsetsDecisionTest {

    @After
    fun tearDown() {
        Insets.resetDiagnosticsForTest()
        StatusBarHeightConfig.resetForTest()
    }

    @Test
    fun modernPublicStatusTypeHits() {
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 104))

        val decision = Insets.makeSetFrameDecision(
            chain,
            modernPublicInfo(),
            configuredPx = 110,
            enabled = true,
        )

        assertTrue(decision is Insets.ProceedWithArgs)
        val args = (decision as Insets.ProceedWithArgs).args
        assertEquals(1, args.size)
        val adjusted = args[0] as Rect
        assertEquals(110, adjusted.bottom)
    }

    @Test
    fun modernNavigationDoesNotHit() {
        val chain = fakeChain(source(type = 2), fakeRect(bottom = 104))

        val decision = Insets.makeSetFrameDecision(
            chain,
            modernPublicInfo(),
            configuredPx = 110,
            enabled = true,
        )

        assertTrue(decision is Insets.ProceedOriginal)
    }

    @Test
    fun modernDisplayCutoutDoesNotHit() {
        val chain = fakeChain(source(type = 128), fakeRect(bottom = 104))

        val decision = Insets.makeSetFrameDecision(
            chain,
            modernPublicInfo(),
            configuredPx = 110,
            enabled = true,
        )

        assertTrue(decision is Insets.ProceedOriginal)
    }

    @Test
    fun legacyInternalStatusType0Hits() {
        val chain = fakeChain(source(type = 0), fakeRect(bottom = 104))

        val decision = Insets.makeSetFrameDecision(
            chain,
            legacyInternalInfo(),
            configuredPx = 110,
            enabled = true,
        )

        assertTrue(decision is Insets.ProceedWithArgs)
    }

    @Test
    fun legacyInternalNavigationType1DoesNotHit() {
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 104))

        val decision = Insets.makeSetFrameDecision(
            chain,
            legacyInternalInfo(),
            configuredPx = 110,
            enabled = true,
        )

        assertTrue(decision is Insets.ProceedOriginal)
    }

    @Test
    fun legacyNavigationOldFrameEmptyTop0StillNotMisclassified() {
        // A navigation source with an empty old frame whose top==0 must not be treated as
        // a status bar. Type encoding is the single source of truth.
        val chain = fakeChain(source(type = 1, frame = Rect()), fakeRect(bottom = 104))

        val decision = Insets.makeSetFrameDecision(
            chain,
            legacyInternalInfo(),
            configuredPx = 110,
            enabled = true,
        )

        assertTrue(decision is Insets.ProceedOriginal)
    }

    @Test
    fun unsupportedEncodingReturnsProceedOriginal() {
        val chain = fakeChain(source(type = 0), fakeRect(bottom = 104))

        val decision = Insets.makeSetFrameDecision(
            chain,
            unsupportedInfo(),
            configuredPx = 110,
            enabled = true,
        )

        assertTrue(decision is Insets.ProceedOriginal)
    }

    @Test
    fun rectInputIsNotModified() {
        val original = fakeRect(bottom = 104)
        val chain = fakeChain(source(type = 1), original)

        val decision = Insets.makeSetFrameDecision(
            chain,
            modernPublicInfo(),
            configuredPx = 110,
            enabled = true,
        )

        val args = (decision as Insets.ProceedWithArgs).args
        val adjusted = args[0] as Rect
        assertNotSame(original, adjusted)
        assertEquals(104, original.bottom)
        assertEquals(110, adjusted.bottom)
        assertEquals(original.left, adjusted.left)
        assertEquals(original.top, adjusted.top)
        assertEquals(original.right, adjusted.right)
    }

    @Test
    fun fourArgOrderIsCorrect() {
        val chain = fakeChain(source(type = 0), 0, 0, 1080, 104)

        val decision = Insets.makeSetFrameDecision(
            chain,
            legacyInternalInfo(),
            configuredPx = 110,
            enabled = true,
        )

        val args = (decision as Insets.ProceedWithArgs).args
        assertEquals(4, args.size)
        assertEquals(0, args[0])
        assertEquals(0, args[1])
        assertEquals(1080, args[2])
        assertEquals(110, args[3])
    }

    @Test
    fun preprocessingReflectionFailureProceedsOnce() {
        val chain = fakeChain(throwingSource(), fakeRect(bottom = 104))
        val callback = Insets.SetFrameCallback(modernPublicInfo(), hasGetId = false, hasGetFrame = false)

        callback.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun logExceptionProceedsOnce() {
        // getArg(0) succeeds once for decision computation, then throws on the second
        // access inside maybeLogFirstHit. The real decision must still execute once.
        configureHeight(40, 440) // -> 110 px
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 0), throwOnSecondArg = true)
        val callback = Insets.SetFrameCallback(modernPublicInfo(), hasGetId = false, hasGetFrame = false)

        callback.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue(chain.calledWithArgs)
    }

    @Test
    fun originalMethodNormalExceptionPropagatesAndProceedsOnce() {
        configureHeight(40, 440) // -> 110 px
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 0), proceedThrow = RuntimeException("setFrame failed"))
        val callback = Insets.SetFrameCallback(modernPublicInfo(), hasGetId = false, hasGetFrame = false)

        try {
            callback.intercept(chain)
            assertFalse("Expected proceed exception to propagate", true)
        } catch (t: Throwable) {
            assertTrue(t is RuntimeException)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun originalMethodFatalPropagatesAndProceedsOnce() {
        configureHeight(40, 440) // -> 110 px
        val error = OutOfMemoryError("setFrame OOM")
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 0), proceedThrow = error)
        val callback = Insets.SetFrameCallback(modernPublicInfo(), hasGetId = false, hasGetFrame = false)

        try {
            callback.intercept(chain)
            assertFalse("Expected OutOfMemoryError to propagate", true)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun changedFalseProceedsOnce() {
        // originalTop=0, originalBottom=104, configuredPx=104 -> newBottom=104, no change
        configureHeight(104, 160) // -> 104 px
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 104))
        val callback = Insets.SetFrameCallback(modernPublicInfo(), hasGetId = false, hasGetFrame = false)

        callback.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun disabledProceedsOnce() {
        configureHeight(11, 160) // disabled

        val chain = fakeChain(source(type = 1), fakeRect(bottom = 104))
        val callback = Insets.SetFrameCallback(modernPublicInfo(), hasGetId = false, hasGetFrame = false)

        callback.intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun diagnosticKeyCapStopsAtMax() {
        configureHeight(40, 160) // -> 40 px

        val callback = Insets.SetFrameCallback(modernPublicInfo(), hasGetId = true, hasGetFrame = false)

        repeat(40) { index ->
            val chain = fakeChain(source(type = 1, id = index), fakeRect(bottom = 104))
            callback.intercept(chain)
        }

        assertEquals(32, Insets.diagnosticKeyCountForTest())
    }

    private fun modernPublicInfo() = Insets.InsetsTypeInfo(
        Insets.InsetsTypeEncoding.MODERN_PUBLIC,
        statusBarType = 1,
        navigationType = 2,
        displayCutoutType = 128,
    )

    private fun legacyInternalInfo() = Insets.InsetsTypeInfo(
        Insets.InsetsTypeEncoding.LEGACY_INTERNAL,
        statusBarType = 0,
        navigationType = 1,
        displayCutoutType = -1,
    )

    private fun unsupportedInfo() = Insets.InsetsTypeInfo(
        Insets.InsetsTypeEncoding.UNSUPPORTED,
        statusBarType = -1,
        navigationType = -1,
        displayCutoutType = -1,
    )

    private fun source(type: Int, id: Int = 0, frame: Rect = Rect()) = FakeSource(type, id, frame)

    private fun throwingSource() = ThrowingFakeSource()

    private fun fakeRect(
        left: Int = 0,
        top: Int = 0,
        right: Int = 1080,
        bottom: Int = 104,
    ): Rect = Rect().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    private fun configureHeight(rawDp: Int, densityDpi: Int) {
        StatusBarHeightConfig.configure(PrefMap().apply { put("system_statusbarheight", rawDp) }, fakeResources(densityDpi))
    }

    private fun fakeChain(source: Any, firstArg: Rect, throwOnSecondArg: Boolean = false, proceedThrow: Throwable? = null): FakeChain {
        return FakeChain(
            source = source,
            args = arrayOf<Any?>(firstArg),
            throwOnSecondArg = throwOnSecondArg,
            proceedThrow = proceedThrow,
        )
    }

    private fun fakeChain(source: Any, left: Int, top: Int, right: Int, bottom: Int, proceedThrow: Throwable? = null): FakeChain {
        return FakeChain(
            source = source,
            args = arrayOf<Any?>(left, top, right, bottom),
            proceedThrow = proceedThrow,
        )
    }

    private fun fakeResources(densityDpi: Int): Resources {
        val metrics = DisplayMetrics().apply { this.densityDpi = densityDpi }
        val constructor = AssetManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val assetManager = constructor.newInstance()
        return FakeResources(assetManager, metrics)
    }

    private class FakeResources(assetManager: AssetManager, private val metrics: DisplayMetrics) :
        Resources(assetManager, metrics, Configuration()) {
        override fun getDisplayMetrics(): DisplayMetrics = metrics
    }

    class FakeSource(private val _type: Int, private val _id: Int, private val _frame: Rect) {
        fun getType(): Int = _type
        fun getId(): Int = _id
        fun getFrame(): Rect = _frame
    }

    class ThrowingFakeSource {
        fun getType(): Int = throw RuntimeException("getType failed")
    }

    class FakeChain(
        val source: Any,
        val args: Array<Any?>,
        val throwOnSecondArg: Boolean = false,
        val proceedThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        var calledWithArgs = false
            private set

        private var argAccessCount = 0

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? = source
        override fun getArgs(): List<Any?> = args.toList()

        override fun getArg(index: Int): Any? {
            if (throwOnSecondArg) {
                argAccessCount++
                if (argAccessCount >= 2) throw RuntimeException("getArg log failure")
            }
            return args[index]
        }

        override fun proceed(): Any? {
            proceedCount++
            calledWithArgs = false
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            calledWithArgs = true
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}
