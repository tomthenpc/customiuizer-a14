package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

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
        ControlCenterPluginRuntime.bind(loader)
        val second = ControlCenterPluginRuntime.runtimeHolder().activeRuntime()

        assertSame(first, second)
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
        }
    }

    private val testLoader: ClassLoader
        get() = this::class.java.classLoader!!

    private fun newIsolatedClassLoader(): ClassLoader {
        return java.net.URLClassLoader(arrayOf(), testLoader)
    }
}
