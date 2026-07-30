package tv.withaibuild.customiuizer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastSecurityContractTest {

    private val manifest = source("app/src/main/AndroidManifest.xml")
    private val globalActions = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt")
    private val moduleHelper = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt")
    private val systemServerHooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
    private val preferences = source("app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")

    @Test
    fun broadcastPermissionIsDeclaredWithSignatureOrPrivilegedProtection() {
        assertTrue(
            "Expected a permission declaration for BROADCAST",
            manifest.contains("android:name=\"tv.withaibuild.customiuizer.r14.permission.BROADCAST\"")
        )
        assertTrue(
            "Expected signatureOrSystem or signature|privileged protection",
            manifest.contains("signatureOrSystem") || manifest.contains("signature|privileged")
        )
        assertTrue(
            "Module app must request its own permission",
            manifest.contains("uses-permission android:name=\"tv.withaibuild.customiuizer.r14.permission.BROADCAST\"")
        )
    }

    @Test
    fun globalActionsExportsTheBroadcastPermissionConstant() {
        assertTrue(
            "GlobalActions should expose BROADCAST_PERMISSION",
            globalActions.contains("BROADCAST_PERMISSION = \"tv.withaibuild.customiuizer.r14.permission.BROADCAST\"")
        )
    }

    @Test
    fun moduleHelperCanPassBroadcastPermissionWhenRegisteringReceivers() {
        assertTrue(
            "registerModuleReceiver should accept an optional permission parameter",
            moduleHelper.contains("fun registerModuleReceiver(")
        )
        assertTrue(
            "registerOwnedReceiver should accept an optional permission parameter",
            moduleHelper.contains("fun registerOwnedReceiver(")
        )
        assertTrue(
            "Receiver registration must forward permission to Context.registerReceiver",
            moduleHelper.contains("context.registerReceiver(receiver, filter, permission, null, flags)")
        )
    }

    @Test
    fun highPrivilegeReceiversAreRegisteredWithTheSharedPermission() {
        assertTrue(
            "fastRebootReceiver must be registered with the broadcast permission",
            systemServerHooks.contains("fastRebootReceiver") &&
                systemServerHooks.contains("GlobalActions.BROADCAST_PERMISSION")
        )
        val expectedProtectedKeys = listOf(
            "phoneWindowManagerActionReceiver",
            "statusBarActionReceiver",
            "freeformModeReceiver",
            "soScSplitScreenReceiver",
            "autoBrightnessReceiver"
        )
        for (key in expectedProtectedKeys) {
            assertTrue(
                "$key must be in the same file and the file must reference the shared permission",
                systemServerHooks.contains(key)
            )
        }
        val permissionCount = systemServerHooks.windowed("GlobalActions.BROADCAST_PERMISSION".length)
            .count { it == "GlobalActions.BROADCAST_PERMISSION" }
        assertEquals(
            "All six privileged receivers must reference the broadcast permission",
            6,
            permissionCount
        )
    }

    @Test
    fun softRebootBroadcastIsAddressedExplicitlyToSystemUI() {
        val sendSoftReboot = preferences.section(
            "private fun sendSoftReboot()",
            "private fun showFastRebootFailure("
        )
        assertTrue(sendSoftReboot.contains("intent.setPackage(\"com.android.systemui\")"))
        assertTrue(sendSoftReboot.contains("GlobalActions.ACTION_PREFIX + \"FastReboot\""))
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return substring(startIndex, endIndex)
    }
}
