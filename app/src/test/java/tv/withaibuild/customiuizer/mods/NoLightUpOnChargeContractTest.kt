package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.feature.NoLightUpOnChargeFeature
import tv.withaibuild.customiuizer.mods.utils.feature.NoLightUpOnChargeSystemUiFeature
import tv.withaibuild.customiuizer.utils.PrefMap

class NoLightUpOnChargeContractTest {

    @Test
    fun systemServerInstallsForBothNonDefaultOptions() {
        assertFalse(NoLightUpOnChargeFeature.evaluateEnabled(PrefMap()))
        assertFalse(NoLightUpOnChargeFeature.evaluateEnabled(prefs(1)))
        assertTrue(NoLightUpOnChargeFeature.evaluateEnabled(prefs(2)))
        assertTrue(NoLightUpOnChargeFeature.evaluateEnabled(prefs(3)))
    }

    @Test
    fun systemUiInstallsForBothNonDefaultOptions() {
        assertFalse(NoLightUpOnChargeSystemUiFeature.evaluateEnabled(PrefMap()))
        assertTrue(NoLightUpOnChargeSystemUiFeature.evaluateEnabled(prefs(2)))
        assertTrue(NoLightUpOnChargeSystemUiFeature.evaluateEnabled(prefs(3)))
    }

    @Test
    fun option2BlocksPowerPluggedAndChargeAnimReasons() {
        assertTrue(SystemDisplayHooks.shouldSkipChargeWake(2, "android.server.power:POWER"))
        assertTrue(SystemDisplayHooks.shouldSkipChargeWake(2, "android.server.power:PLUGGED:USB"))
        assertTrue(SystemDisplayHooks.shouldSkipChargeWake(2, "com.android.systemui:RAPID_CHARGE"))
        assertTrue(SystemDisplayHooks.shouldSkipChargeWake(2, "com.android.systemui:WIRELESS_CHARGE"))
        assertTrue(SystemDisplayHooks.shouldSkipChargeWake(2, "com.android.systemui:WIRELESS_RAPID_CHARGE"))
        assertFalse(SystemDisplayHooks.shouldSkipChargeWake(2, "android.server.power:DREAM"))
    }

    @Test
    fun option3BlocksPowerAndPluggedOnly() {
        assertTrue(SystemDisplayHooks.shouldSkipChargeWake(3, "android.server.power:POWER"))
        assertTrue(SystemDisplayHooks.shouldSkipChargeWake(3, "android.server.power:PLUGGED:USB"))
        assertFalse(SystemDisplayHooks.shouldSkipChargeWake(3, "com.android.systemui:RAPID_CHARGE"))
        assertFalse(SystemDisplayHooks.shouldSkipChargeWake(3, "com.android.systemui:WIRELESS_CHARGE"))
    }

    @Test
    fun defaultOptionNeverSkips() {
        assertFalse(SystemDisplayHooks.shouldSkipChargeWake(1, "android.server.power:POWER"))
        assertFalse(SystemDisplayHooks.shouldSkipChargeWake(1, "android.server.power:PLUGGED:USB"))
    }

    private fun prefs(option: Int): PrefMap =
        PrefMap().apply { put("system_nolightuponcharges", option) }
}
