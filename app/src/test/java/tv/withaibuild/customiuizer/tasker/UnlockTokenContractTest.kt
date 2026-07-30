package tv.withaibuild.customiuizer.tasker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTokenContractTest {

    private val unlockTokenProvider = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockTokenProvider.kt")
    private val unlockSettings = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockSettings.kt")
    private val unlockReceiver = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockReceiver.kt")
    private val taskerFiles = listOf(
        unlockTokenProvider,
        unlockSettings,
        unlockReceiver
    )

    @Test
    fun tokensArePerHostNotGlobal() {
        assertTrue(
            "Token storage must be keyed by host package",
            unlockTokenProvider.contains("host_token_") &&
                unlockTokenProvider.contains("host_certs_") &&
                unlockTokenProvider.contains("HostInfo")
        )
        assertFalse(
            "There must not be a single global token key",
            unlockTokenProvider.contains(Regex("const val PREF_KEY_TOKEN")) &&
                unlockTokenProvider.contains("= \"unlock_token\"")
        )
    }

    @Test
    fun hostInfoIncludesPackageLabelAndCerts() {
        assertTrue(
            "HostInfo must capture package, label and certificate fingerprints",
            unlockTokenProvider.contains("data class HostInfo") &&
                unlockTokenProvider.contains("packageName") &&
                unlockTokenProvider.contains("applicationLabel") &&
                unlockTokenProvider.contains("certFingerprints")
        )
        assertTrue(
            "Certificate extraction must use SHA-256",
            unlockTokenProvider.contains("MessageDigest.getInstance(\"SHA-256\")") &&
                unlockTokenProvider.contains("signingCertificateHistory")
        )
    }

    @Test
    fun unlockSettingsRequiresCallingPackage() {
        assertTrue(
            "UnlockSettings must read callingPackage",
            unlockSettings.contains("callingPackage")
        )
        assertTrue(
            "Missing calling package must cancel",
            unlockSettings.contains("Activity.RESULT_CANCELED") &&
                unlockSettings.contains("if (callingPackage == null)")
        )
        assertTrue(
            "Host summary must be shown to the user",
            unlockSettings.contains("R.id.host_info") &&
                unlockSettings.contains("R.string.unlock_host_summary")
        )
    }

    @Test
    fun unlockReceiverValidatesSenderAndToken() {
        assertTrue(
            "UnlockReceiver must read getSentFromPackage",
            unlockReceiver.contains("getSentFromPackage()")
        )
        assertTrue(
            "UnlockReceiver must call verifyBundle with the actual sender",
            unlockReceiver.contains("verifyBundle(context, bundle, sender)")
        )
        assertTrue(
            "UnlockReceiver must require sender identity sharing",
            unlockReceiver.contains("broadcast sender identity not shared")
        )
    }

    @Test
    fun bundleContainsHostPackageAndToken() {
        assertTrue(
            "Bundle must carry the host package",
            unlockSettings.contains("BUNDLE_KEY_HOST_PACKAGE") &&
                unlockSettings.contains("putString(UnlockTokenProvider.BUNDLE_KEY_HOST_PACKAGE")
        )
        assertTrue(
            "Bundle must carry the per-host token",
            unlockSettings.contains("putString(UnlockTokenProvider.BUNDLE_KEY_TOKEN")
        )
        assertTrue(
            "UnlockTokenProvider must expose host package and token bundle keys",
            unlockTokenProvider.contains("BUNDLE_KEY_HOST_PACKAGE") &&
                unlockTokenProvider.contains("BUNDLE_KEY_TOKEN")
        )
    }

    @Test
    fun tokenNeverLoggedOrExported() {
        assertFalse(
            "Token value must never be written to logs",
            unlockReceiver.contains("XposedHelpers.log(") ||
                unlockReceiver.contains("android.util.Log") && unlockReceiver.contains("\$token")
        )
        assertFalse(
            "Token value must never be shown in a Toast",
            unlockSettings.contains("Toast.makeText") && unlockSettings.contains("\$token")
        )
        assertFalse(
            "Token value must not be shared via settings export",
            unlockTokenProvider.contains("appPrefs") ||
                unlockTokenProvider.contains("MainModule.mPrefs")
        )
    }

    @Test
    fun taskerComponentsDoNotReferenceHookOrLibxposed() {
        val joined = taskerFiles.joinToString("")
        assertFalse(
            "Tasker plugin files must not reference XposedHelpers",
            joined.contains("XposedHelpers")
        )
        assertFalse(
            "Tasker plugin files must not reference MainModule",
            joined.contains("MainModule")
        )
        assertFalse(
            "Tasker plugin files must not reference libxposed / XposedBridge",
            joined.contains("libxposed") ||
                joined.contains("XposedBridge") ||
                joined.contains("de.robv.android.xposed")
        )
        assertFalse(
            "UnlockReceiver must not use mods/utils ModuleHelper after this round",
            unlockReceiver.contains("ModuleHelper")
        )
    }

    @Test
    fun legacyGlobalTokenIsCleared() {
        assertTrue(
            "Legacy global token must be cleared",
            unlockTokenProvider.contains("LEGACY_PREFS_NAME") &&
                unlockTokenProvider.contains("LEGACY_TOKEN_KEY") &&
                unlockSettings.contains("clearLegacyGlobalToken")
        )
    }

    @Test
    fun constantsDoNotDefineTokenValue() {
        val constants = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/Constants.kt")
        assertFalse(
            "Constants should not contain a hard-coded token",
            constants.contains(Regex("const\\s+val\\s+.*TOKEN"))
        )
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
}
