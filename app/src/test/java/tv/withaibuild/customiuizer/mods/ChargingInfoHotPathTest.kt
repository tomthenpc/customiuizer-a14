package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedModuleInterface
import java.io.FileNotFoundException
import java.lang.reflect.Modifier
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallRegistry
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallState
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.feature.ChargingInfoFeature
import tv.withaibuild.customiuizer.utils.PrefMap

class ChargingInfoHotPathTest {

    @Test
    fun chargingInfo_allDetailsDisabled_returnsBeforeCallerClassification() {
        var callerCalls = 0
        var providerCalls = 0
        val prefs = allDisabledPrefs()

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { callerCalls++; true },
            batteryPropsProvider = { providerCalls++; Properties() }
        )

        assertNull(result)
        assertEquals("caller classification must not run when all details are disabled", 0, callerCalls)
        assertEquals("battery props provider must not run when all details are disabled", 0, providerCalls)
    }

    @Test
    fun chargingInfo_enabled_nonKeyguard_returnsBeforeSysfs() {
        var callerCalls = 0
        var providerCalls = 0
        val prefs = PrefMap().apply { put("system_charginginfo_current", true) }

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { callerCalls++; false },
            batteryPropsProvider = { providerCalls++; Properties() }
        )

        assertNull(result)
        assertEquals("caller classification must run exactly once", 1, callerCalls)
        assertEquals("battery props provider must not run for non-keyguard callers", 0, providerCalls)
    }

    @Test
    fun chargingInfo_disabledReadsStopAtAllDetailsOff() {
        val prefs = allDisabledPrefs()
        // prefs.getBoolean on missing keys returns false, so every switch is false.
        assertFalse(prefs.getBoolean("system_charginginfo_current"))
        assertFalse(prefs.getBoolean("system_charginginfo_voltage"))
        assertFalse(prefs.getBoolean("system_charginginfo_wattage"))
        assertFalse(prefs.getBoolean("system_charginginfo_temp"))

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { throw AssertionError("caller classification must not be reached") },
            batteryPropsProvider = { throw AssertionError("sysfs read must not be reached") }
        )
        assertNull(result)
    }

    @Test
    fun chargingInfo_malformedSingleProperty_skipsOnlyThatProperty() {
        val props = Properties().apply {
            setProperty("POWER_SUPPLY_CURRENT_NOW", "not_a_number")
            setProperty("POWER_SUPPLY_VOLTAGE_NOW", "4200000")
        }
        val prefs = PrefMap().apply {
            put("system_charginginfo_current", true)
            put("system_charginginfo_voltage", true)
        }

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { props }
        )

        assertNotNull(result)
        assertTrue("voltage should still be rendered", result!!.contains("V"))
        assertFalse("malformed current value should be skipped", result.contains("A"))
    }

    @Test
    fun chargingInfo_oomFromBusinessPath_isRethrown() {
        val prefs = PrefMap().apply {
            put("system_charginginfo_current", true)
            put("system_charginginfo_voltage", true)
        }

        try {
            SystemLockScreenHooks.buildChargingInfoDetails(
                charge = 50,
                hint = "50%",
                prefs = prefs,
                isKeyguardCaller = { true },
                batteryPropsProvider = { throw OutOfMemoryError("oom in sysfs") }
            )
            fail("OutOfMemoryError from the business path must propagate")
        } catch (e: OutOfMemoryError) {
            assertEquals("oom in sysfs", e.message)
        }
    }

    @Test
    fun chargingInfo_nonFatalReadFailure_returnsOriginalHint() {
        val prefs = PrefMap().apply { put("system_charginginfo_current", true) }

        val result = SystemLockScreenHooks.buildChargingInfoDetails(
            charge = 50,
            hint = "50%",
            prefs = prefs,
            isKeyguardCaller = { true },
            batteryPropsProvider = { throw FileNotFoundException("/sys/class/power_supply/battery/uevent") }
        )

        // A non-fatal I/O error must not crash and must allow the original hint to be preserved.
        assertNull(result)
    }

    @Test
    fun chargingInfo_requiredHookFailure_isNotInstalled() {
        val result = SystemLockScreenHooks.ChargingInfoHook(fakePackageReadyParam())
        assertEquals(
            "missing ChargeUtils class must produce a transient failure, not INSTALLED",
            FeatureInstallResult.FAILED_TRANSIENT,
            result
        )
    }

    @Test
    fun chargingInfo_featureHasNoLocalInstallOwner() {
        // The previous implementation kept a private boolean isChargingInfoHooked that duplicated
        // the registry's once-guard. It must be gone.
        for (field in SystemLockScreenHooks::class.java.declaredFields) {
            assertFalse(
                "isChargingInfoHooked local owner must not exist",
                field.name == "isChargingInfoHooked"
            )
        }
    }

    @Test
    fun chargingInfo_installIsIdempotentThroughFeatureInstallRegistry() {
        FeatureInstallState.reset()
        val registry = FeatureInstallRegistry()
        var installCount = 0

        val lpparam = fakePackageReadyParam()
        val prefs = PrefMap().apply { put("system_charginginfo", true) }
        val realFeature = ChargingInfoFeature(lpparam)

        // Wrap the real feature so the test does not depend on a live Xposed bridge,
        // but preserves the same feature id and registry identity.
        val countingFeature = object : FeatureDefinition by realFeature {
            override fun create(): FeatureDefinition = this
            override fun install(): FeatureInstallResult {
                installCount++
                return FeatureInstallResult.INSTALLED
            }
        }

        registry.register(countingFeature)
        registry.register(countingFeature)
        val first = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, prefs, collectResults = true)
        val second = registry.installAll(FeatureTarget.SYSTEM_UI, InstallPhase.PACKAGE_READY, prefs, collectResults = true)

        assertEquals("ChargingInfo install() must be called exactly once by the registry", 1, installCount)
        assertEquals(listOf(FeatureInstallResult.INSTALLED), first)
        assertEquals(listOf(FeatureInstallResult.ALREADY_INSTALLED), second)
    }

    @Test
    fun baseSystemUiFeature_installHookRemainsAbstract() {
        val base = Class.forName("tv.withaibuild.customiuizer.mods.utils.feature.BaseSystemUiFeature")
        val installHook = base.getDeclaredMethod("installHook")
        assertTrue("BaseSystemUiFeature.installHook() must be abstract", Modifier.isAbstract(installHook.modifiers))
        assertTrue("BaseSystemUiFeature.installHook() must be protected", Modifier.isProtected(installHook.modifiers))
    }

    @Test
    fun baseSystemUiFeature_installRemainsFinal() {
        val base = Class.forName("tv.withaibuild.customiuizer.mods.utils.feature.BaseSystemUiFeature")
        val install = base.getMethod("install")
        assertTrue("BaseSystemUiFeature.install() must be final", Modifier.isFinal(install.modifiers))
        assertTrue("BaseSystemUiFeature.install() must be public", Modifier.isPublic(install.modifiers))
    }

    @Test
    fun chargingInfoFeature_directlyImplementsFeatureDefinition() {
        assertTrue(
            "ChargingInfoFeature must directly implement FeatureDefinition",
            FeatureDefinition::class.java.isAssignableFrom(ChargingInfoFeature::class.java)
        )
        assertFalse(
            "ChargingInfoFeature must not inherit BaseSystemUiFeature",
            Class.forName("tv.withaibuild.customiuizer.mods.utils.feature.BaseSystemUiFeature")
                .isAssignableFrom(ChargingInfoFeature::class.java)
        )
        val method = ChargingInfoFeature::class.java.getMethod("install")
        assertEquals(FeatureInstallResult::class.java, method.returnType)
    }

    private fun allDisabledPrefs(): PrefMap = PrefMap().apply {
        put("system_charginginfo_current", false)
        put("system_charginginfo_voltage", false)
        put("system_charginginfo_wattage", false)
        put("system_charginginfo_temp", false)
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return java.lang.reflect.Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }
}
