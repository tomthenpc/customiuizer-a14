package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkTintRegistrationStateTest {

    @Test
    fun registersExactlyOnce() {
        val state = DarkTintRegistrationState("owner", "test")
        var registerCalls = 0
        val ok = state.register(registerFn = { registerCalls++; true })
        assertTrue(ok)
        assertEquals(1, registerCalls)

        val second = state.register(registerFn = { registerCalls++; true })
        assertFalse(second)
        assertEquals(1, registerCalls)
        assertTrue(state.isActive)
    }

    @Test
    fun releasesAreIdempotent() {
        val state = DarkTintRegistrationState("owner", "test")
        var releaseCalls = 0
        state.register(registerFn = { true })

        val first = state.release { releaseCalls++ }
        assertTrue(first)
        assertEquals(1, releaseCalls)
        assertFalse(state.isActive)
        assertTrue(state.isReleased)

        val second = state.release { releaseCalls++ }
        assertFalse(second)
        assertEquals(1, releaseCalls)
    }

    @Test
    fun releaseWithoutRegisterDoesNotCallReleaseFn() {
        val state = DarkTintRegistrationState("owner", "test")
        var releaseCalls = 0
        val released = state.release { releaseCalls++ }
        assertFalse(released)
        assertEquals(0, releaseCalls)
    }

    @Test
    fun registerAppliesInitialTintOnlyOnSuccess() {
        val state = DarkTintRegistrationState("owner", "test")
        var tintCalls = 0
        var registerCalls = 0

        val first = state.register(
            registerFn = { registerCalls++; false },
            applyInitialTint = { tintCalls++; true }
        )
        assertFalse(first)
        assertEquals(1, registerCalls)
        assertEquals(0, tintCalls)
        assertFalse(state.isActive)

        val second = state.register(
            registerFn = { registerCalls++; true },
            applyInitialTint = { tintCalls++; true }
        )
        assertTrue(second)
        assertEquals(2, registerCalls)
        assertEquals(1, tintCalls)
        assertTrue(state.isActive)
    }

    @Test
    fun releasedStateCannotRegisterAgainWithoutReset() {
        val state = DarkTintRegistrationState("owner", "test")
        state.register(registerFn = { true })
        state.release { }

        val reRegister = state.register(registerFn = { true })
        assertFalse(reRegister)
        assertFalse(state.isActive)
    }

    @Test
    fun resetAllowsReRegistrationAfterRelease() {
        val state = DarkTintRegistrationState("owner", "test")
        var releaseCalls = 0
        var registerCalls = 0

        state.register(registerFn = { registerCalls++; true })
        state.release { releaseCalls++ }
        state.reset()

        val ok = state.register(registerFn = { registerCalls++; true })
        assertTrue(ok)
        assertEquals(2, registerCalls)
        assertEquals(1, releaseCalls)
        assertTrue(state.isActive)
    }

    @Test
    fun ownerAndRouteAreStored() {
        val state = DarkTintRegistrationState("owner-42", "left")
        assertEquals("owner-42", state.owner)
        assertEquals("left", state.route)
    }

    @Test
    fun isActiveReflectsRegistrationAndRelease() {
        val state = DarkTintRegistrationState("owner", "test")
        assertFalse(state.isActive)

        state.register(registerFn = { true })
        assertTrue(state.isActive)

        state.release { }
        assertFalse(state.isActive)
    }
}
