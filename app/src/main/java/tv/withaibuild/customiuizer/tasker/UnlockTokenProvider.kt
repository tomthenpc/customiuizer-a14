package tv.withaibuild.customiuizer.tasker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import java.security.SecureRandom
import java.util.Base64

/**
 * Per-install random token used to authenticate Tasker / Locale plugin broadcasts.
 *
 * The token is generated once and stored in the module app's private SharedPreferences.
 * [UnlockSettings] embeds it into the plugin Bundle when the user saves a task, and
 * [UnlockReceiver] verifies it before re-sending the [UnlockSetForced] broadcast.
 *
 * The token is not derived from the package name, not hard-coded, and never written
 * to any log or exported setting. A token missing from the Bundle (old tasks) or not
 * matching the stored value (forged Bundles) causes an immediate, silent reject.
 */
class UnlockTokenProvider {

    fun getOrCreateToken(context: Context): String = getOrCreateToken(getPrefs(context))

    internal fun getOrCreateToken(prefs: SharedPreferences): String {
        val existing = prefs.getString(PREF_KEY_TOKEN, null)
        if (!existing.isNullOrEmpty()) return existing
        val token = generateToken()
        prefs.edit().putString(PREF_KEY_TOKEN, token).apply()
        return token
    }

    fun getToken(context: Context): String? = getToken(getPrefs(context))

    internal fun getToken(prefs: SharedPreferences): String? {
        return prefs.getString(PREF_KEY_TOKEN, null)
    }

    fun verify(context: Context, bundle: Bundle?): Boolean = verifyBundle(getPrefs(context), bundle)

    internal fun verifyBundle(prefs: SharedPreferences, bundle: Bundle?): Boolean {
        if (bundle == null) return false
        val provided = try {
            bundle.getString(BUNDLE_KEY_TOKEN)
        } catch (t: Throwable) {
            null
        }
        return verify(prefs, provided)
    }

    internal fun verify(prefs: SharedPreferences, provided: String?): Boolean {
        val stored = getToken(prefs)
        return !provided.isNullOrEmpty() && provided == stored
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val PREFS_NAME = "customiuizer_unlock_token"
        const val PREF_KEY_TOKEN = "unlock_token"
        const val BUNDLE_KEY_TOKEN = "customiuizer.unlock_token"
        const val TOKEN_BYTES = 32
    }
}
