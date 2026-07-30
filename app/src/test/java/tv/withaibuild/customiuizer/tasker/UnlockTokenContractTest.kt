package tv.withaibuild.customiuizer.tasker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTokenContractTest {

    private val unlockTokenProvider = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockTokenProvider.kt")
    private val unlockSettings = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockSettings.kt")
    private val unlockReceiver = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockReceiver.kt")
    private val constants = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/Constants.kt")

    @Test
    fun tokenIsNotHardCoded() {
        assertFalse(
            "Token must be generated at runtime, not a constant string",
            unlockTokenProvider.contains(Regex("const val TOKEN\\b")) ||
                unlockTokenProvider.contains("\"fixed_token\"") ||
                unlockTokenProvider.contains("= \"tv.withaibuild.customiuizer")
        )
    }

    @Test
    fun tokenUsesSecureRandom() {
        assertTrue(
            "Token generation must use SecureRandom",
            unlockTokenProvider.contains("SecureRandom()")
        )
        assertTrue(
            "Token length should be at least 16 bytes",
            unlockTokenProvider.contains("ByteArray(") &&
                (unlockTokenProvider.contains("TOKEN_BYTES = 32") || unlockTokenProvider.contains("ByteArray(32)"))
        )
    }

    @Test
    fun tokenIsStoredInPrivateSharedPreferences() {
        assertTrue(
            "Token must be stored in a private SharedPreferences file",
            unlockTokenProvider.contains("getSharedPreferences(")
        )
        assertTrue(
            "SharedPreferences must be MODE_PRIVATE",
            unlockTokenProvider.contains("Context.MODE_PRIVATE")
        )
    }

    @Test
    fun unlockSettingsEmbedsTokenInBundle() {
        assertTrue(
            "UnlockSettings must include the token in the saved Bundle",
            unlockSettings.contains("putString(UnlockTokenProvider.BUNDLE_KEY_TOKEN,") ||
                unlockSettings.contains("UnlockTokenProvider.BUNDLE_KEY_TOKEN")
        )
        assertTrue(
            "UnlockSettings must generate or reuse the token when saving",
            unlockSettings.contains("getOrCreateToken(")
        )
    }

    @Test
    fun unlockReceiverValidatesToken() {
        assertTrue(
            "UnlockReceiver must call UnlockTokenProvider.verify",
            unlockReceiver.contains("UnlockTokenProvider().verify(")
        )
        assertTrue(
            "UnlockReceiver must reject missing or invalid token",
            unlockReceiver.contains("if (!UnlockTokenProvider().verify(")
        )
        assertTrue(
            "UnlockReceiver must not expose the token in logs",
            !unlockReceiver.contains("provided") && !unlockReceiver.contains("token.toString()")
        )
    }

    @Test
    fun unlockReceiverForwardsOnlyToSystemUI() {
        assertTrue(
            "UnlockReceiver must send UnlockSetForced only to com.android.systemui",
            unlockReceiver.contains("setPackage(\"com.android.systemui\")")
        )
    }

    @Test
    fun tokenNeverLoggedOrExported() {
        assertFalse(
            "Token value must never be written to logs",
            unlockReceiver.contains("XposedHelpers.log(") && unlockReceiver.contains("\$token")
        )
        assertFalse(
            "Token value must never be shown in a Toast",
            unlockSettings.contains("Toast.makeText") && unlockSettings.contains("\$token")
        )
        assertFalse(
            "Token value must never be written to logcat directly",
            unlockTokenProvider.contains("Log.") && unlockTokenProvider.contains("\$token")
        )
    }

    @Test
    fun constantsDoNotDefineTokenValue() {
        assertFalse(
            "Constants should not contain a hard-coded token",
            constants.contains("token") || constants.contains("Token")
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
