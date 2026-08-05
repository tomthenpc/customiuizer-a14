package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoMonitorStateTest {

    @Test
    fun startNewGenerationReturnsMonotonicIds() {
        val state = DeviceInfoMonitorState()
        val first = state.startNewGeneration()
        val second = state.startNewGeneration()
        val third = state.startNewGeneration()

        assertTrue(first < second)
        assertTrue(second < third)
        assertEquals(third, state.activeBgHandlerId)
        assertEquals(third, state.activeMainHandlerId)
    }

    @Test
    fun oldGenerationIsRejected() {
        val state = DeviceInfoMonitorState()
        val oldId = state.startNewGeneration()
        state.startNewGeneration()

        assertFalse(state.isActive(oldId))
        assertFalse(state.isActiveBg(oldId))
        assertFalse(state.isActiveMain(oldId))
    }

    @Test
    fun currentGenerationIsActive() {
        val state = DeviceInfoMonitorState()
        val id = state.startNewGeneration()

        assertTrue(state.isActive(id))
        assertTrue(state.isActiveBg(id))
        assertTrue(state.isActiveMain(id))
    }

    @Test
    fun stopRejectsAllGenerations() {
        val state = DeviceInfoMonitorState()
        val id = state.startNewGeneration()
        state.stop()

        assertFalse(state.isActive(id))
        assertEquals(-1, state.activeBgHandlerId)
        assertEquals(-1, state.activeMainHandlerId)
    }

    @Test
    fun startNewGenerationAfterStopResumesWithFreshId() {
        val state = DeviceInfoMonitorState()
        val first = state.startNewGeneration()
        state.stop()
        val second = state.startNewGeneration()

        assertTrue(second > first)
        assertTrue(state.isActive(second))
    }

    @Test
    fun consecutiveFailCountBacksOff() {
        val state = DeviceInfoMonitorState()

        state.resetFailCount()
        assertEquals(2000L, state.calculateDelay(2000L, 60000L))

        state.bumpFailCount()
        assertEquals(4000L, state.calculateDelay(2000L, 60000L))

        state.bumpFailCount()
        assertEquals(8000L, state.calculateDelay(2000L, 60000L))

        state.bumpFailCount()
        state.bumpFailCount()
        state.bumpFailCount()
        assertEquals(60000L, state.calculateDelay(2000L, 60000L))

        state.resetFailCount()
        assertEquals(2000L, state.calculateDelay(2000L, 60000L))
    }

    @Test
    fun screenOnDefaultsToTrue() {
        val state = DeviceInfoMonitorState()
        assertTrue(state.screenOn)
        state.screenOn = false
        assertFalse(state.screenOn)
    }
}
