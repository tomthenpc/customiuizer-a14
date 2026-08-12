package tv.withaibuild.customiuizer.mods.utils

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import java.io.File

class UpdaterServicesBridgeContractTest {
    @Test
    fun onlyRestorableComponentStatesAreAccepted() {
        assertTrue(GlobalActionSystemServerHooks.isAllowedComponentState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))
        assertTrue(GlobalActionSystemServerHooks.isAllowedComponentState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
        assertTrue(GlobalActionSystemServerHooks.isAllowedComponentState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
        assertTrue(GlobalActionSystemServerHooks.isAllowedComponentState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER))
        assertTrue(GlobalActionSystemServerHooks.isAllowedComponentState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED))
        assertFalse(GlobalActionSystemServerHooks.isAllowedComponentState(99))
    }

    @Test
    fun updaterBridgeIsAllowlistedBoundedAndRollbackCapable() {
        val bridge = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
        val screen = source("app/src/main/java/tv/withaibuild/customiuizer/subs/Various.kt")
        assertTrue(bridge.contains("XIAOMI_UPDATER_PACKAGE = \"com.android.updater\""))
        assertTrue(bridge.contains("names.size > 32"))
        assertTrue(bridge.contains("it !in declared"))
        assertTrue(bridge.contains("original[index]"))
        assertTrue(screen.contains("UPDATE_SERVICE_STATES_SNAPSHOT"))
        assertTrue(screen.contains("setPackage(\"android\")"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
