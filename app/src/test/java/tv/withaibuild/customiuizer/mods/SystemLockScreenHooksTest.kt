package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
}
