package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfig
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfigPublisher
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureDependenciesResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureDependenciesResult
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEffectExecutor
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureMachine
import tv.withaibuild.customiuizer.mods.utils.gesture.PhysicalGestureArbiter
import java.lang.reflect.Executable
import java.util.ArrayList

class ControlCenterPluginRuntimeTest {

    @After
    fun tearDown() {
        ControlCenterPluginRuntime.resetForTests()
    }

    @Test
    fun sameLoaderIsIdempotent() {
        val loader = testLoader
        ControlCenterPluginRuntime.installPluginHooks = { }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        ControlCenterPluginRuntime.bind(loader)
        val first = ControlCenterPluginRuntime.runtimeHolder().activeRuntime()
        val firstResult = ControlCenterPluginRuntime.bind(loader)
        val second = ControlCenterPluginRuntime.runtimeHolder().activeRuntime()

        assertSame(first, second)
        assertTrue(firstResult is ControlCenterBindResult.AlreadyInstalled)
    }

    @Test
    fun newLoaderClearsOldMachine() {
        val loader1 = newIsolatedClassLoader()
        val loader2 = newIsolatedClassLoader()
        ControlCenterPluginRuntime.installPluginHooks = { }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        ControlCenterPluginRuntime.bind(loader1)
        val first = ControlCenterPluginRuntime.runtimeHolder().activeRuntime()

        ControlCenterPluginRuntime.bind(loader2)
        val second = ControlCenterPluginRuntime.runtimeHolder().activeRuntime()

        assertNotSame(first, second)
        assertSame(loader2, second?.classLoader)
    }

    @Test
    fun newLoaderReleasesOldLoaderReference() {
        val loader1 = newIsolatedClassLoader()
        val loader2 = newIsolatedClassLoader()
        ControlCenterPluginRuntime.installPluginHooks = { }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        ControlCenterPluginRuntime.bind(loader1)
        ControlCenterPluginRuntime.bind(loader2)

        assertSame(loader2, ControlCenterPluginRuntime.activeLoader())
    }

    @Test
    fun explicitClearNullsAllReferences() {
        val loader = testLoader
        ControlCenterPluginRuntime.installPluginHooks = { }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        ControlCenterPluginRuntime.bind(loader)
        ControlCenterPluginRuntime.clear()

        assertNull(ControlCenterPluginRuntime.activeLoader())
        assertNull(ControlCenterPluginRuntime.runtimeHolder().activeRuntime())
        assertEquals(0, ControlCenterPluginRuntime.arbiter().heldTokenCount())
        assertFalse(ControlCenterPluginRuntime.activeLease()!!.active)
    }

    @Test
    fun createPluginHookInstalledOnlyOnce() {
        var hookCount = 0
        ControlCenterPluginRuntime.installCreatePluginHook = { _, _ -> hookCount++ }

        ControlCenterPluginRuntime.hookIfNeeded(testLoader)
        ControlCenterPluginRuntime.hookIfNeeded(testLoader)

        assertEquals(1, hookCount)
    }

    @Test(expected = OutOfMemoryError::class)
    fun fatalInstallFailureDoesNotPublishHalfState() {
        val loader = testLoader
        ControlCenterPluginRuntime.installPluginHooks = { throw OutOfMemoryError("fatal install") }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        try {
            ControlCenterPluginRuntime.bind(loader)
        } finally {
            assertNull(ControlCenterPluginRuntime.activeLoader())
            assertNull(ControlCenterPluginRuntime.runtimeHolder().activeRuntime())
        }
    }

    @Test
    fun ordinaryPartialFailureIsNotSilent() {
        val loader = testLoader
        val failure = RuntimeException("partial install")
        ControlCenterPluginRuntime.installPluginHooks = { throw failure }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        val result = ControlCenterPluginRuntime.bind(loader)

        assertTrue(result is ControlCenterBindResult.Failed)
        assertEquals(failure, (result as ControlCenterBindResult.Failed).reason)
        assertEquals(InstallState.FAILED_PARTIAL, ControlCenterPluginRuntime.installState())
        assertSame(failure, ControlCenterPluginRuntime.lastFailure())
    }

    @Test
    fun sameLoaderDoesNotRetryAfterFailedPartial() {
        val loader = testLoader
        var pluginHookCalls = 0
        ControlCenterPluginRuntime.installPluginHooks = {
            pluginHookCalls++
            throw RuntimeException("partial install")
        }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        val first = ControlCenterPluginRuntime.bind(loader)
        assertTrue(first is ControlCenterBindResult.Failed)

        val second = ControlCenterPluginRuntime.bind(loader)
        assertTrue(second is ControlCenterBindResult.NoRetry)

        assertEquals(1, pluginHookCalls)
    }

    @Test
    fun newLoaderCanStartNewTransaction() {
        val loader1 = newIsolatedClassLoader()
        val loader2 = newIsolatedClassLoader()

        ControlCenterPluginRuntime.installPluginHooks = { throw RuntimeException("partial install") }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }

        val first = ControlCenterPluginRuntime.bind(loader1)
        assertTrue(first is ControlCenterBindResult.Failed)

        ControlCenterPluginRuntime.installPluginHooks = { }
        val second = ControlCenterPluginRuntime.bind(loader2)

        assertTrue(second is ControlCenterBindResult.Installed)
        assertSame(loader2, ControlCenterPluginRuntime.activeLoader())
        assertEquals(InstallState.INSTALLED, ControlCenterPluginRuntime.installState())
    }

    @Test
    fun clearInvalidatesLeaseAndCallbackNoOps() {
        var pluginHookCalls = 0
        var capturedHook: HookerClassHelper.MethodHook? = null
        ControlCenterPluginRuntime.installPluginHooks = { pluginHookCalls++ }
        ControlCenterPluginRuntime.installHooks = { _, _ -> }
        ControlCenterPluginRuntime.installCreatePluginHook = { _, hook ->
            capturedHook = hook
        }

        ControlCenterPluginRuntime.hookIfNeeded(testLoader)
        ControlCenterPluginRuntime.bind(testLoader)
        assertEquals(1, pluginHookCalls)

        ControlCenterPluginRuntime.clear()

        val hook = capturedHook ?: error("createPlugin hook was not installed")
        val callback = fakeBeforeHookCallback(thisObject = Any(), args = arrayOf(Any(), true))
        hook.beforeHook(callback)

        // The stale hook must not trigger a new bind after clear().
        assertEquals(1, pluginHookCalls)
        assertNull(ControlCenterPluginRuntime.activeLoader())
    }

    @Test
    fun installHooksSuccessButInstallPluginHooksFailsDoesNotRepeatInstall() {
        val loader = testLoader
        var gestureInstallCount = 0
        var pluginHookCalls = 0

        ControlCenterPluginRuntime.installHooks = { _, _ -> gestureInstallCount++ }
        ControlCenterPluginRuntime.installPluginHooks = {
            pluginHookCalls++
            throw RuntimeException("ui hooks failed")
        }

        ControlCenterPluginRuntime.bind(loader)

        assertEquals(1, gestureInstallCount)
        assertEquals(1, pluginHookCalls)
        assertEquals(InstallState.FAILED_PARTIAL, ControlCenterPluginRuntime.installState())
        assertNull(ControlCenterPluginRuntime.runtimeHolder().activeRuntime())
    }

    @Test
    fun hookIfNeededOrdinaryFailureSetsFailedPartial() {
        val failure = RuntimeException("hook install failed")
        ControlCenterPluginRuntime.installCreatePluginHook = { _, _ -> throw failure }

        ControlCenterPluginRuntime.hookIfNeeded(testLoader)

        assertEquals(InstallState.FAILED_PARTIAL, ControlCenterPluginRuntime.installState())
        assertSame(failure, ControlCenterPluginRuntime.lastFailure())
    }

    @Test(expected = OutOfMemoryError::class)
    fun hookIfNeededFatalFailureRethrows() {
        ControlCenterPluginRuntime.installCreatePluginHook = { _, _ -> throw OutOfMemoryError("fatal") }
        ControlCenterPluginRuntime.hookIfNeeded(testLoader)
    }

    @Test
    fun handleMotionEventGuardsArgsAndTypes() {
        val machine = newGestureMachine()
        val lease = RuntimeLease()
        val hooks = ControlCenterPluginRuntime.installControlCenterGestureHooks(
            testLoader,
            machine,
            lease,
        )

        // No args.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf()))
        // Missing second arg.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any())))
        // First arg wrong type, second arg boolean true.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any(), true)))
        // Second arg boolean true must short-circuit.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any(), true)))
        // Second arg wrong type (not Boolean) should not crash and continue until event cast fails.
        hooks.handleMotionEvent.beforeHook(fakeBeforeHookCallback(args = arrayOf(Any(), "not a boolean")))

        // None of the invalid cases should reach the machine.
        assertTrue(lease.active)
    }

    @Test
    fun onAttachedAndDetachedCallbacksCheckLease() {
        val machine = newGestureMachine()
        val activeLease = RuntimeLease()
        val inactiveLease = RuntimeLease().apply { invalidate() }

        val activeHooks = ControlCenterPluginRuntime.installControlCenterGestureHooks(
            testLoader,
            machine,
            activeLease,
        )
        val inactiveHooks = ControlCenterPluginRuntime.installControlCenterGestureHooks(
            testLoader,
            machine,
            inactiveLease,
        )

        // With an inactive lease, callbacks must be no-ops.
        inactiveHooks.onAttachedToWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))
        inactiveHooks.onDetachedFromWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))

        // Active lease + non-View thisObject returns early.
        activeHooks.onAttachedToWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))
        activeHooks.onDetachedFromWindow.beforeHook(fakeBeforeHookCallback(thisObject = Any()))

        assertTrue(activeLease.active)
    }

    private val testLoader: ClassLoader
        get() = this::class.java.classLoader!!

    private fun newIsolatedClassLoader(): ClassLoader {
        return java.net.URLClassLoader(arrayOf(), testLoader)
    }

    private fun newGestureMachine(): GestureMachine = GestureMachine(
        classLoaderIdentity = "test",
        configResolver = { GestureConfig() },
        depsResolver = object : GestureDependenciesResolver {
            override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any): GestureDependenciesResult {
                return GestureDependenciesResult.NotReady
            }
        },
        effectExecutor = GestureEffectExecutor { _, _, _, _ -> },
        arbiter = PhysicalGestureArbiter(),
    )

    private fun fakeBeforeHookCallback(
        thisObject: Any? = null,
        args: Array<Any?> = arrayOf(),
    ): BeforeHookCallback {
        val chain = object : XposedInterface.Chain {
            override fun getExecutable(): Executable = error("not used in test")
            override fun getThisObject(): Any? = thisObject
            override fun getArgs(): List<Any?> = ArrayList(args.toList())
            override fun getArg(index: Int): Any? = args[index]
            override fun proceed(): Any? = error("not used in test")
            override fun proceed(p0: Array<Any>): Any? = error("not used in test")
            override fun proceedWith(p0: Any): Any? = error("not used in test")
            override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
        }
        return BeforeHookCallback(chain)
    }
}
