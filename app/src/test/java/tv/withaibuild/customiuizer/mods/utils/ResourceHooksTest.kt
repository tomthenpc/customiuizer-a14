package tv.withaibuild.customiuizer.mods.utils

import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.Configuration
import android.content.res.XmlResourceParser
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ResourceHooksTest {

    private lateinit var hooks: ResourceHooks

    @Before
    fun setUp() {
        hooks = ResourceHooks()
    }

    @After
    fun tearDown() {
        hooks.hookInstallerForTest = null
        hooks.themeHookInstallerForTest = null
        hooks.logSinkForTest = null
        hooks.testModuleRes = null
    }

    // ---- getFakeResId ----

    @Test
    fun getFakeResIdIsStablePerName() {
        assertEquals(ResourceHooks.getFakeResId("test_tag"), ResourceHooks.getFakeResId("test_tag"))
    }

    @Test
    fun getFakeResIdDiffersByName() {
        assertNotEquals(ResourceHooks.getFakeResId("foo"), ResourceHooks.getFakeResId("bar"))
    }

    // ---- ResourceGetterKind dispatch ----

    @Test
    fun getterKindDispatchesToCorrectResourcesCall() {
        val calls = mutableListOf<String>()
        val fakeRes = FakeResources(calls)
        assertEquals("text", ResourceHooks.ResourceGetterKind.GET_TEXT.getValue(fakeRes, 100))
        assertEquals("string", ResourceHooks.ResourceGetterKind.GET_STRING.getValue(fakeRes, 200))
        assertNotNull(ResourceHooks.ResourceGetterKind.GET_LAYOUT.getValue(fakeRes, 300))
        val drawable = ResourceHooks.ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY.getValue(fakeRes, 400, 2, null)
        assertTrue(drawable is Drawable)
        assertEquals(
            listOf("getText:100", "getString:200", "getLayout:300", "getDrawableForDensity:400:2:null"),
            calls
        )
    }

    @Test
    fun layoutGetterDoesNotSupportIdReplacement() {
        assertFalse(ResourceHooks.ResourceGetterKind.GET_LAYOUT.supportsIdReplacement)
    }

    // ---- ReplaceHook: zero argument materialization and fixed getter kind ----

    @Test
    fun replaceHookUsesGetArgAndDoesNotTouchGetArgsOrExecutableName() {
        val calls = mutableListOf<String>()
        val chain = fakeChain(resId = 42, proceedValue = "proceed", callLog = calls, throwOnGetArgs = true, throwOnExecutable = true)
        hooks.setResourceIdReplacementForTest(42, ResourceHooks.ReplacementType.ID, 100)
        hooks.testModuleRes = FakeResources(mapOf(100 to "replaced"))

        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_STRING)
        val result = hook.intercept(chain)

        assertEquals("replaced", result)
        assertTrue("getArg used", calls.any { it.startsWith("getArg:") })
        assertFalse("getArgs must not be used", calls.any { it == "getArgs" })
        assertFalse("executable must not be used", calls.any { it == "getExecutable" })
    }

    @Test
    fun replaceHookReturnsObjectReplacementDirectly() {
        val chain = fakeChain(resId = 10, proceedValue = "proceed")
        val expected = Any()
        hooks.setResourceIdReplacementForTest(10, ResourceHooks.ReplacementType.OBJECT, expected)

        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)
        val result = hook.intercept(chain)

        assertSame(expected, result)
    }

    @Test
    fun replaceHookRespectsDrawableExtraArgs() {
        val expectedDrawable = ColorDrawable()
        val chain = fakeChain(resId = 7, density = 3, proceedValue = "proceed")
        hooks.setResourceIdReplacementForTest(7, ResourceHooks.ReplacementType.ID, 200)
        hooks.testModuleRes = FakeResources(mapOf(200 to expectedDrawable))

        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY)
        val result = hook.intercept(chain)

        assertSame(expectedDrawable, result)
    }

    @Test
    fun replaceHookSkipsIdReplacementForLayout() {
        val chain = fakeChain(resId = 8, proceedValue = "proceed")
        hooks.setResourceIdReplacementForTest(8, ResourceHooks.ReplacementType.ID, 300)
        hooks.testModuleRes = FakeResources(mapOf(300 to "layout:300"))

        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_LAYOUT)
        val result = hook.intercept(chain)

        assertEquals("proceed", result)
    }

    @Test
    fun replaceHookFallsBackToFakesAndProceedsOnce() {
        val calls = mutableListOf<String>()
        val fakeResId = ResourceHooks.getFakeResId("my_fake")
        val chain = fakeChain(resId = fakeResId, proceedValue = "proceed", callLog = calls)
        hooks.setFakeForTest(fakeResId, 400)
        hooks.testModuleRes = FakeResources(mapOf(400 to "fake_value"))

        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_STRING)
        val result = hook.intercept(chain)

        assertEquals("fake_value", result)
        assertEquals(0, calls.count { it == "proceed" })
    }

    @Test
    fun replaceHookProceedsOnceWhenNoMatch() {
        val calls = mutableListOf<String>()
        val chain = fakeChain(resId = 99, proceedValue = "proceed", callLog = calls)

        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_STRING)
        val result = hook.intercept(chain)

        assertEquals("proceed", result)
        assertEquals(1, calls.count { it == "proceed" })
    }

    @Test(expected = OutOfMemoryError::class)
    fun replaceHookRethrowsOutOfMemoryError() {
        val chain = fakeChain(resId = 0, throwOnGetArg = true)
        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)
        hook.intercept(chain)
    }

    @Test
    fun replaceHookRateLimitsExceptionLogging() {
        val logged = AtomicInteger(0)
        hooks.logSinkForTest = { logged.incrementAndGet() }

        val chain = fakeChain(resId = 0, throwRuntimeOnGetArg = true)
        val hook = hooks.createReplaceHookForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)

        // First exception should log.
        try { hook.intercept(chain) } catch (_: RuntimeException) {}
        assertEquals(1, logged.get())

        // Second exception within throttle window should not.
        try { hook.intercept(chain) } catch (_: RuntimeException) {}
        assertEquals(1, logged.get())

        // After clearing throttling, the next exception should log again.
        hooks.clearThrottlingForTest()
        try { hook.intercept(chain) } catch (_: RuntimeException) {}
        assertEquals(2, logged.get())
    }

    // ---- installGetter: partial / concurrent / retry ----

    @Test
    fun installGetterRecordsPartialSuccessForStringGetters() {
        hooks.hookInstallerForTest = { kind, _ ->
            kind == ResourceHooks.ResourceGetterKind.GET_STRING
        }

        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)
        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_STRING)

        assertEquals(ResourceHooks.HookStatus.FAILED, hooks.getterStatusForTest(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertEquals(1, hooks.getterAttemptCountForTest(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertEquals(ResourceHooks.HookStatus.HOOKED, hooks.getterStatusForTest(ResourceHooks.ResourceGetterKind.GET_STRING))
    }

    @Test
    fun installGetterRetriesOnlyFailedGetter() {
        val shouldSucceed = mutableMapOf(
            ResourceHooks.ResourceGetterKind.GET_TEXT to false,
            ResourceHooks.ResourceGetterKind.GET_STRING to true,
        )
        hooks.hookInstallerForTest = { kind, _ ->
            shouldSucceed[kind] == true
        }

        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)
        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_STRING)

        // Retry GET_TEXT with success.
        shouldSucceed[ResourceHooks.ResourceGetterKind.GET_TEXT] = true
        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)

        assertEquals(ResourceHooks.HookStatus.HOOKED, hooks.getterStatusForTest(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertEquals(0, hooks.getterAttemptCountForTest(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertEquals(ResourceHooks.HookStatus.HOOKED, hooks.getterStatusForTest(ResourceHooks.ResourceGetterKind.GET_STRING))
    }

    @Test
    fun installGetterBoundedByMaxAttempts() {
        hooks.hookInstallerForTest = { _, _ -> false }

        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)
        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)
        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)
        hooks.installGetterForTest(ResourceHooks.ResourceGetterKind.GET_TEXT)

        assertEquals(ResourceHooks.HookStatus.FAILED, hooks.getterStatusForTest(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertEquals(3, hooks.getterAttemptCountForTest(ResourceHooks.ResourceGetterKind.GET_TEXT))
    }

    @Test
    fun installGetterInstallsOnlyOnceConcurrently() {
        val installCount = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)

        hooks.hookInstallerForTest = { _, _ ->
            entered.countDown()
            proceed.await(2, TimeUnit.SECONDS)
            installCount.incrementAndGet() > 0
        }

        val kind = ResourceHooks.ResourceGetterKind.GET_TEXT
        val t1 = Thread { hooks.installGetterForTest(kind) }
        t1.start()
        entered.await(2, TimeUnit.SECONDS)

        val t2 = Thread { hooks.installGetterForTest(kind) }
        t2.start()
        t2.join(500)

        proceed.countDown()
        t1.join(2000)
        t2.join(2000)

        assertEquals(1, installCount.get())
        assertEquals(ResourceHooks.HookStatus.HOOKED, hooks.getterStatusForTest(kind))
    }

    // ---- theme hook retry ----

    @Test
    fun themeHookRetriesUntilSuccessThenStops() {
        val calls = AtomicInteger(0)
        hooks.themeHookInstallerForTest = {
            calls.incrementAndGet() >= 2
        }

        hooks.tryInitThemeHookForTest()
        assertEquals(ResourceHooks.HookStatus.FAILED, hooks.themeHookStatusForTest())
        assertEquals(1, hooks.themeHookAttemptCountForTest())

        hooks.tryInitThemeHookForTest()
        assertEquals(ResourceHooks.HookStatus.HOOKED, hooks.themeHookStatusForTest())
        assertEquals(0, hooks.themeHookAttemptCountForTest())
    }

    @Test
    fun themeHookBoundedByMaxAttempts() {
        hooks.themeHookInstallerForTest = { false }

        hooks.tryInitThemeHookForTest()
        hooks.tryInitThemeHookForTest()
        hooks.tryInitThemeHookForTest()
        hooks.tryInitThemeHookForTest()

        assertEquals(ResourceHooks.HookStatus.FAILED, hooks.themeHookStatusForTest())
        assertEquals(3, hooks.themeHookAttemptCountForTest())
    }

    // ---- helpers ----

    private fun fakeChain(
        resId: Int,
        density: Int = 0,
        theme: Resources.Theme? = null,
        proceedValue: Any? = null,
        callLog: MutableList<String>? = null,
        throwOnGetArgs: Boolean = false,
        throwOnExecutable: Boolean = false,
        throwOnGetArg: Boolean = false,
        throwRuntimeOnGetArg: Boolean = false,
    ): XposedInterface.Chain {
        val handler = java.lang.reflect.InvocationHandler { _, method, methodArgs ->
            val name = method.name
            if ((name == "getArgs" || name == "getArguments") && throwOnGetArgs) {
                throw IllegalStateException("getArgs must not be called")
            }
            if ((name == "getExecutable" || name == "executable") && throwOnExecutable) {
                throw IllegalStateException("executable must not be called")
            }
            when (name) {
                "getArg", "getArgument" -> {
                    when {
                        throwOnGetArg -> throw OutOfMemoryError("oom")
                        throwRuntimeOnGetArg -> throw RuntimeException("runtime boom")
                    }
                    val index = methodArgs?.get(0) as? Int ?: 0
                    callLog?.add("getArg:$index")
                    when (index) {
                        0 -> resId
                        1 -> density
                        2 -> theme
                        else -> null
                    }
                }
                "getArgs", "getArguments" -> {
                    callLog?.add("getArgs")
                    listOf(resId, density, theme)
                }
                "proceed" -> {
                    callLog?.add("proceed")
                    proceedValue
                }
                "thisObject", "getThisObject" -> null
                "executable", "getExecutable" -> null
                "toString" -> "FakeChain"
                "hashCode" -> resId
                "equals" -> methodArgs?.get(0) === this
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            ResourceHooksTest::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java),
            handler,
        ) as XposedInterface.Chain
    }

    private class FakeResources : Resources {
        private val callLog: MutableList<String>
        private val values = mutableMapOf<Int, Any?>()

        constructor(callLog: MutableList<String>) : super(newAssetManager(), DisplayMetrics(), Configuration()) {
            this.callLog = callLog
            values[100] = "text"
            values[200] = "string"
            values[300] = newXmlResourceParser()
            values[400] = ColorDrawable()
        }

        constructor(map: Map<Int, Any>) : super(newAssetManager(), DisplayMetrics(), Configuration()) {
            this.callLog = mutableListOf()
            values.putAll(map)
        }

        override fun getText(id: Int): CharSequence {
            callLog.add("getText:$id")
            return values[id] as? CharSequence ?: throw NotFoundException()
        }

        override fun getString(id: Int): String {
            callLog.add("getString:$id")
            return values[id] as? String ?: throw NotFoundException()
        }

        override fun getLayout(id: Int): XmlResourceParser {
            callLog.add("getLayout:$id")
            @Suppress("UNCHECKED_CAST")
            return values[id] as? XmlResourceParser ?: throw NotFoundException()
        }

        override fun getDrawableForDensity(id: Int, density: Int, theme: Resources.Theme?): Drawable? {
            callLog.add("getDrawableForDensity:$id:$density:${theme}")
            return values[id] as? Drawable
        }
    }

    companion object {
        private fun newAssetManager(): AssetManager {
            val constructor = AssetManager::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        }

        private fun newXmlResourceParser(): XmlResourceParser {
            return Proxy.newProxyInstance(
                ResourceHooksTest::class.java.classLoader,
                arrayOf(XmlResourceParser::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "toString" -> "FakeXmlResourceParser"
                    else -> null
                }
            } as XmlResourceParser
        }
    }
}
