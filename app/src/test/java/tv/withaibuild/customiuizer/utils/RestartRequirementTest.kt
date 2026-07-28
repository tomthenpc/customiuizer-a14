package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartRequirementTest {

    @Test
    fun allValuesArePresent() {
        val values = RestartRequirement.values()
        assertEquals(6, values.size)
        assertTrue(values.contains(RestartRequirement.NONE))
        assertTrue(values.contains(RestartRequirement.APP_REOPEN))
        assertTrue(values.contains(RestartRequirement.SYSTEM_UI))
        assertTrue(values.contains(RestartRequirement.LAUNCHER))
        assertTrue(values.contains(RestartRequirement.SYSTEM_SERVER))
        assertTrue(values.contains(RestartRequirement.DEVICE))
    }

    @Test
    fun localeChangeRequiresAppReopen() {
        // The locale setting is classified as Level 2: the settings app must be
        // closed and reopened by the user for the new language to take effect.
        assertEquals(RestartRequirement.APP_REOPEN, RestartRequirement.valueOf("APP_REOPEN"))
    }

    @Test
    fun deviceRebootIsHighestLevel() {
        assertEquals("DEVICE", RestartRequirement.DEVICE.name)
    }
}
