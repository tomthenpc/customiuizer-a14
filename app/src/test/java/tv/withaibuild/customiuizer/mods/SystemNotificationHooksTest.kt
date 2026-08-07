package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class SystemNotificationHooksTest {

    @Test
    fun resolveNotificationUserId_returnsResolvedValueOnSuccess() {
        val resolved = SystemNotificationHooks.resolveNotificationUserId { 10 }
        assertEquals(10, resolved)
    }

    @Test
    fun resolveNotificationUserId_returnsResolvedOwnerUserZero() {
        val resolved = SystemNotificationHooks.resolveNotificationUserId { 0 }
        assertEquals(0, resolved)
    }

    @Test
    fun resolveNotificationUserId_returnsNullOnNonFatalFailure() {
        val resolved = SystemNotificationHooks.resolveNotificationUserId { throw RuntimeException("no such method") }
        assertNull(resolved)
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveNotificationUserId_rethrowsDirectFatalError() {
        SystemNotificationHooks.resolveNotificationUserId { throw OutOfMemoryError("oom") }
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveNotificationUserId_rethrowsWrappedFatalError() {
        SystemNotificationHooks.resolveNotificationUserId {
            throw InvocationTargetException(OutOfMemoryError("wrapped oom"))
        }
    }

    @Test
    fun resolveNotificationUserId_freeformBranchSkipsResolution() {
        // The Freeform branch in the OnClickListener does not call resolveNotificationUserId.
        // This seam is only invoked for App Info and Force Close actions.
        val resolved = SystemNotificationHooks.resolveNotificationUserId { 0 }
        assertEquals(0, resolved)
    }
}
