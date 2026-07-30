package tv.withaibuild.customiuizer.tasker

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Per-host binding tokens for the Tasker / Locale UnlockReceiver plugin.
 *
 * When [UnlockSettings] is opened by a host app, it fetches the host's package name,
 * application label and signing certificate fingerprints. The host is bound on first
 * use by generating a random token and storing the host's certificate lineage.
 * Later invocations from the same package are accepted only if at least one current
 * certificate matches a previously recorded certificate, which allows legitimate
 * certificate rotation while preventing package-name spoofing with a mismatched
 * signature.
 *
 * The token is never written to logs, toasts or exported settings. It is returned
 * in the plugin Bundle together with the host package name. [UnlockReceiver] checks
 * both the actual broadcast sender ([getSentFromPackage]) and the stored token for
 * that specific host before forwarding the [UnlockSetForced] command.
 */
class UnlockTokenProvider {

    data class HostInfo(
        val packageName: String,
        val applicationLabel: String?,
        val certFingerprints: Set<String>
    ) {
        val currentFingerprint: String?
            get() = certFingerprints.firstOrNull()

        val historySummary: String
            get() = certFingerprints.joinToString("\n") { "  - $it" }
    }

    data class HostToken(
        val hostPackage: String,
        val token: String
    )

    /**
     * Bind or re-bind a host and return its per-host token.
     *
     * - First call for a package: generate a new token and store the certificate lineage.
     * - Re-binding: return the stored token if the current certificates share at least one
     *   fingerprint with the stored history. The stored history is updated with any new
     *   fingerprints.
     * - Same package but no common certificate: return null (reject, do not reissue).
     */
    fun getOrCreateToken(context: Context, hostInfo: HostInfo): HostToken? {
        return getOrCreateToken(getPrefs(context), hostInfo)
    }

    internal fun getOrCreateToken(prefs: SharedPreferences, hostInfo: HostInfo): HostToken? {
        val storedToken = prefs.getString(tokenKey(hostInfo.packageName), null)
        val storedCerts = getStoredCerts(prefs, hostInfo.packageName)

        return if (storedToken.isNullOrEmpty() || storedCerts.isEmpty()) {
            // First binding for this package.
            val token = generateToken()
            saveHostToken(prefs, hostInfo.packageName, token, hostInfo.certFingerprints)
            HostToken(hostInfo.packageName, token)
        } else {
            // Re-binding: at least one current cert must be in the stored lineage.
            val current = hostInfo.certFingerprints
            if (current.any { it in storedCerts }) {
                // Update lineage with any new fingerprints, then return existing token.
                saveCerts(prefs, hostInfo.packageName, current union storedCerts)
                HostToken(hostInfo.packageName, storedToken)
            } else {
                null
            }
        }
    }

    fun getToken(context: Context, hostPackage: String): HostToken? {
        return getToken(getPrefs(context), hostPackage)
    }

    internal fun getToken(prefs: SharedPreferences, hostPackage: String): HostToken? {
        val token = prefs.getString(tokenKey(hostPackage), null)
        return if (token.isNullOrEmpty()) null else HostToken(hostPackage, token)
    }

    fun verify(context: Context, hostPackage: String, token: String?): Boolean {
        return verify(getPrefs(context), hostPackage, token)
    }

    internal fun verify(prefs: SharedPreferences, hostPackage: String, token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        val stored = prefs.getString(tokenKey(hostPackage), null)
        return !stored.isNullOrEmpty() && stored == token
    }

    fun verifyBundle(context: Context, bundle: Bundle?, actualSender: String?): Boolean {
        return verifyBundle(getPrefs(context), bundle, actualSender)
    }

    internal fun verifyBundle(prefs: SharedPreferences, bundle: Bundle?, actualSender: String?): Boolean {
        if (bundle == null) return false
        val hostPackage = try {
            bundle.getString(BUNDLE_KEY_HOST_PACKAGE)
        } catch (t: Throwable) {
            null
        }
        val token = try {
            bundle.getString(BUNDLE_KEY_TOKEN)
        } catch (t: Throwable) {
            null
        }
        if (hostPackage.isNullOrEmpty() || token.isNullOrEmpty()) return false
        if (hostPackage != actualSender) return false
        return verify(prefs, hostPackage, token)
    }

    /**
     * Look up host information for [packageName]. Returns null if the package is not
     * installed or signing information cannot be read.
     */
    fun getHostInfo(context: Context, packageName: String): HostInfo? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            val label = pm.getApplicationLabel(appInfo).toString()
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            val certs = extractCertFingerprints(packageInfo)
            if (certs.isEmpty()) null else HostInfo(packageName, label, certs)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Clears any legacy global token so that old Tasker / Locale tasks fail safely.
     */
    fun clearLegacyGlobalToken(context: Context) {
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_TOKEN_KEY)
            .apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getStoredCerts(prefs: SharedPreferences, packageName: String): Set<String> {
        return prefs.getStringSet(certsKey(packageName), emptySet()) ?: emptySet()
    }

    private fun saveHostToken(prefs: SharedPreferences, packageName: String, token: String, certs: Set<String>) {
        prefs.edit()
            .putString(tokenKey(packageName), token)
            .putStringSet(certsKey(packageName), certs)
            .apply()
    }

    private fun saveCerts(prefs: SharedPreferences, packageName: String, certs: Set<String>) {
        prefs.edit()
            .putStringSet(certsKey(packageName), certs)
            .apply()
    }

    private fun tokenKey(packageName: String) = "host_token_$packageName"
    private fun certsKey(packageName: String) = "host_certs_$packageName"

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun extractCertFingerprints(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        return signatures?.map { sha256Hex(it.toByteArray()) }?.toSet() ?: emptySet()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PREFS_NAME = "customiuizer_unlock_hosts"
        const val LEGACY_PREFS_NAME = "customiuizer_unlock_token"
        const val LEGACY_TOKEN_KEY = "unlock_token"
        const val BUNDLE_KEY_TOKEN = "customiuizer.unlock_token"
        const val BUNDLE_KEY_HOST_PACKAGE = "customiuizer.unlock_host_package"
        const val TOKEN_BYTES = 32
    }
}
