package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemUsbDefaultHooks.ContextReason
import tv.withaibuild.customiuizer.mods.SystemUsbDefaultHooks.UsbDefaultContext

class SystemUsbDefaultHooksTest {

    @After
    fun tearDown() {
        MainModule.mPrefs.remove(SystemUsbDefaultHooks.PREF_KEY)
        UsbDefaultContext.clear()
    }

    // --- PrefMap / getStringAsInt semantics ---

    @Test
    fun getStringAsInt_readsValidStringModes() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")
        assertEquals(2, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))

        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "3")
        assertEquals(3, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    @Test
    fun getStringAsInt_defaultsToFollowSystemWhenMissing() {
        assertEquals(0, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    @Test
    fun getStringAsInt_defaultsToFollowSystemForInvalidString() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "invalid")
        assertEquals(0, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    @Test
    fun getStringAsInt_acceptsNumberStorage() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, 1)
        assertEquals(1, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    // --- Pure mapping ---

    @Test
    fun resolveEffective_followSystemPreservesNativeDefault() {
        assertEquals(0L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, 0L))
        assertEquals(4L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, 4L))
        assertEquals(16L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, 16L))
    }

    @Test
    fun resolveEffective_chargingMapsToNone() {
        assertEquals(0L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_CHARGING, 16L))
    }

    @Test
    fun resolveEffective_mtpMapsToMtp() {
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_MTP, 0L))
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_MTP, 16L))
    }

    @Test
    fun resolveEffective_ptpMapsToPtp() {
        assertEquals(SystemUsbDefaultHooks.FUNCTION_PTP, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_PTP, 4L))
    }

    @Test
    fun resolveEffective_invalidModeFallsBackToNativeDefault() {
        assertEquals(99L, SystemUsbDefaultHooks.resolveEffective(7, 99L))
    }

    // --- Policy / transfer guards ---

    @Test
    fun computeEffectiveUsbFunctions_followSystemReturnsNull() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM,
                nativeDefault = 16L,
                currentFunctions = 16L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_honoursScreenLocked() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = true,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_honoursForceRestart() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = false,
                forceRestart = true,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_allowsMtpWhenTransferAllowed() {
        assertEquals(
            SystemUsbDefaultHooks.FUNCTION_MTP,
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_blocksMtpWhenTransferDisallowed() {
        assertEquals(
            0L,
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 16L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = false,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_noRewriteWhenAlreadyCorrect() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = SystemUsbDefaultHooks.FUNCTION_MTP,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_noRewriteWhenTransferBlockedAndAlreadyNone() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = false,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_chargingOverridesPtp() {
        assertEquals(
            0L,
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_CHARGING,
                nativeDefault = 16L,
                currentFunctions = 16L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    // --- Thread-local ownership guard ---

    @Test
    fun usbDefaultContext_pushAndPeek() {
        assertNull(UsbDefaultContext.peek())
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 13)
        val frame = UsbDefaultContext.peek()
        assertNotNull(frame)
        assertEquals(ContextReason.HANDLE_MESSAGE, frame!!.reason)
        assertEquals(13, frame.messageWhat)
        UsbDefaultContext.pop()
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun usbDefaultContext_isThreadLocal() {
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 0)
        var otherPeek: SystemUsbDefaultHooks.ContextFrame? = null
        val thread = Thread {
            otherPeek = UsbDefaultContext.peek()
        }
        thread.start()
        thread.join()
        assertNull(otherPeek)
        assertNotNull(UsbDefaultContext.peek())
        UsbDefaultContext.pop()
    }

    @Test
    fun usbDefaultContext_nestedPushPop() {
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 0)
        UsbDefaultContext.push(ContextReason.SET_SCREEN_UNLOCKED_FUNCTIONS)
        val top = UsbDefaultContext.peek()
        assertEquals(ContextReason.SET_SCREEN_UNLOCKED_FUNCTIONS, top!!.reason)
        UsbDefaultContext.pop()
        val outer = UsbDefaultContext.peek()
        assertEquals(ContextReason.HANDLE_MESSAGE, outer!!.reason)
        UsbDefaultContext.pop()
        assertNull(UsbDefaultContext.peek())
    }

    // --- Constants ---

    @Test
    fun usbFunctionConstantsMatchExpectedValues() {
        assertEquals(0L, SystemUsbDefaultHooks.FUNCTION_NONE)
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, SystemUsbDefaultHooks.FUNCTION_MTP)
        assertEquals(SystemUsbDefaultHooks.FUNCTION_PTP, SystemUsbDefaultHooks.FUNCTION_PTP)
    }
}
