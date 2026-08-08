package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException

class SystemLockScreenHooksTest {

    @Test
    fun resolveWallpaperUserId_returnsResolvedValueOnSuccess() {
        val resolved = SystemLockScreenHooks.resolveWallpaperUserId { 10 }
        assertEquals(10, resolved)
    }

    @Test
    fun resolveWallpaperUserId_returnsNullOnNonFatalFailure() {
        val resolved = SystemLockScreenHooks.resolveWallpaperUserId { throw RuntimeException("no such method") }
        assertNull(resolved)
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveWallpaperUserId_rethrowsDirectFatalError() {
        SystemLockScreenHooks.resolveWallpaperUserId { throw OutOfMemoryError("oom") }
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveWallpaperUserId_rethrowsWrappedFatalError() {
        SystemLockScreenHooks.resolveWallpaperUserId {
            throw InvocationTargetException(OutOfMemoryError("wrapped oom"))
        }
    }

    @Test
    fun noScreenLockHook_preflightsSixCoreMethodsBeforeAnyInstall() {
        val source = sourceFile(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt"
        ).readText()
        val fnStart = source.indexOf("fun NoScreenLockHook(")
        assertTrue("NoScreenLockHook must exist", fnStart >= 0)

        val fnBody = source.substring(fnStart)

        val h1 = fnBody.indexOf("findMethodExact(\n            \"com.android.systemui.keyguard.KeyguardViewMediator\",\n            lpparam.classLoader,\n            \"handleKeyguardDone\"")
        val h2 = fnBody.indexOf("findMethodExact(\n            \"com.android.keyguard.KeyguardUpdateMonitor\",\n            lpparam.classLoader,\n            \"onFingerprintAuthenticated\",\n            Int::class.javaPrimitiveType!!,\n            Boolean::class.javaPrimitiveType!!")
        val h3 = fnBody.indexOf("findMethodExact(\n            \"com.android.keyguard.KeyguardSecurityContainerController\",\n            lpparam.classLoader,\n            \"onInit\"")
        val h4 = fnBody.indexOf("findMethodExact(\n            \"com.android.systemui.keyguard.KeyguardViewMediator\",\n            lpparam.classLoader,\n            \"doKeyguardLocked\",\n            Bundle::class.java")
        val h5 = fnBody.indexOf("findMethodExact(\n            \"com.android.systemui.keyguard.KeyguardViewMediator\",\n            lpparam.classLoader,\n            \"setupLocked\"")
        val h6 = fnBody.indexOf("findMethodExact(\n            \"com.android.keyguard.KeyguardSecurityModel\",\n            lpparam.classLoader,\n            \"getSecurityMode\",\n            Int::class.javaPrimitiveType!!")

        assertTrue("Preflight H1 handleKeyguardDone must exist", h1 >= 0)
        assertTrue("Preflight H2 onFingerprintAuthenticated must exist", h2 >= 0)
        assertTrue("Preflight H3 onInit must exist", h3 >= 0)
        assertTrue("Preflight H4 doKeyguardLocked must exist", h4 >= 0)
        assertTrue("Preflight H5 setupLocked must exist", h5 >= 0)
        assertTrue("Preflight H6 getSecurityMode must exist", h6 >= 0)

        val firstHookInstall = fnBody.indexOf("ModuleHelper.findAndHookMethod(\"com.android.systemui.keyguard.KeyguardViewMediator\", lpparam.classLoader, \"handleKeyguardDone\"")
        assertTrue("First hook installation must exist", firstHookInstall >= 0)

        val preflightMax = maxOf(h1, h2, h3, h4, h5, h6)
        assertTrue(
            "All six core preflight method resolutions must happen before the first hook installation",
            preflightMax < firstHookInstall
        )

        // Preflight must not include auxiliary/optional symbols.
        assertFalse(
            "Preflight must NOT include BluetoothControllerImpl constructor hook",
            fnBody.substring(0, firstHookInstall).contains("BluetoothControllerImpl")
        )
        assertFalse(
            "Preflight must NOT include updateConnected hook",
            fnBody.substring(0, firstHookInstall).contains("updateConnected")
        )
        assertFalse(
            "Preflight must NOT include SecurityMode enum",
            fnBody.substring(0, firstHookInstall).contains("KeyguardSecurityModel\\\$SecurityMode")
        )
    }

    private fun sourceFile(relativePath: String): File {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
