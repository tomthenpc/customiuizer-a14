package tv.withaibuild.customiuizer.utils

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/**
 * M1 backup / restore reliability owner.
 *
 * This object is intentionally cold-path only: no startup cost, no resident state,
 * no observer, no background worker. The only mutable data it creates lives for the
 * duration of one user-initiated restore.
 */
object BackupRestore {

    /** M1 provisional legacy guard. NOT the M2 final MAX_FILE_SIZE contract. */
    const val M1_LEGACY_MAX_BYTES = 2L * 1024 * 1024

    /** Default backup filename prefix + MMddHHmmss local wall-clock timestamp. */
    private val BACKUP_FILENAME_FORMATTER =
        DateTimeFormatter.ofPattern("MMddHHmmss", Locale.US)

    /** SharedPreferences storage keys removed in the A14/HyperOS 1 line. */
    val DROPPED_KEYS = setOf(
        "pref_key_system_notif_disable_strong_toast",
        "pref_key_system_notif_disable_strong_toast_always",
        "pref_key_system_notif_disable_strong_toast_dnd",
        "pref_key_system_notif_strong_toast_width",
    )

    /** Source-device derived or runtime markers that must not be trusted on restore. */
    val NON_EXPORTABLE_KEYS = setOf(
        "pref_key_miuizer_locale_applied",
        "pref_key_miuizer_synced_from_lsposed",
    )

    enum class Status { SUCCESS, PARTIAL_FAILURE, FAILURE }

    data class RestoreResult(
        val status: Status,
        val restored: Int = 0,
        val deprecatedIgnored: Int = 0,
        val invalidSkipped: Int = 0,
        val appSelectionsSanitized: Int = 0,
        val migrated: Int = 0,
        val commitSucceeded: Boolean = false,
        val commitConfirmedDurable: Boolean = false,
        val deviceReconciled: Boolean = false,
        val rollbackAttempted: Boolean = false,
        val rollbackSucceeded: Boolean = false,
    ) {
        val isSuccess: Boolean get() = status == Status.SUCCESS
    }

    /**
     * Generates a backup filename using an immutable / thread-safe
     * [java.time.DateTimeFormatter] and the device-local wall clock.
     */
    @JvmStatic
    fun generateBackupFilename(): String =
        "r14bak_" + BACKUP_FILENAME_FORMATTER.format(LocalDateTime.now())

    /**
     * Captures a defensive copy of the current preferences for best-effort rollback.
     *
     * String sets are copied so later mutation of the snapshot cannot affect the
     * original and vice-versa. The snapshot lives only for the current restore call.
     */
    @JvmStatic
    fun capturePreRestoreSnapshot(prefs: SharedPreferences): Map<String, Any?> {
        val snapshot = HashMap<String, Any?>(prefs.all.size * 4 / 3 + 1)
        for ((key, value) in prefs.all) {
            snapshot[key] = when (value) {
                is Set<*> -> HashSet<String>(value.size).apply {
                    @Suppress("UNCHECKED_CAST")
                    addAll(value as Set<String>)
                }
                else -> value
            }
        }
        return snapshot
    }

    /**
     * Bounded read of a legacy backup input. Returns `null` if the input is too large.
     *
     * This is the M1 provisional legacy guard, not the M2 final format contract.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readBoundedInputStream(input: InputStream, maxBytes: Long = M1_LEGACY_MAX_BYTES): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /**
     * Decodes a legacy Java-serialized backup and validates the root object.
     *
     * The root must be a `Map<*, *>`. No unchecked `Map<String, Any?>` cast is
     * performed before this structural check.
     *
     * @throws BackupRestoreException if the stream is malformed or the root is not a map.
     */
    @JvmStatic
    @Throws(IOException::class, ClassNotFoundException::class)
    fun decodeLegacyBackup(bytes: ByteArray): Map<*, *> {
        ByteArrayInputStream(bytes).use { byteIn ->
            ObjectInputStream(byteIn).use { input ->
                val root = input.readObject()
                if (root !is Map<*, *>) {
                    throw BackupRestoreException("Legacy backup root is not a Map")
                }
                return root
            }
        }
    }

    /**
     * Validates and normalizes every entry of a decoded legacy backup map.
     *
     * - Root is already known to be a `Map<*, *>`.
     * - Non-`String` keys and unsupported values are treated as **entry-level**
     *   invalid input: the entire entry is skipped and `invalidSkipped` is incremented.
     * - `StringSet` members must all be non-null `String`s; otherwise the entire
     *   `StringSet` entry is skipped.
     * - `DROPPED_KEYS` and `NON_EXPORTABLE_KEYS` are not written back; they are
     *   counted in `deprecatedIgnored`.
     *
     * This function performs no `SharedPreferences` mutation.
     */
    @JvmStatic
    fun validateAndNormalizeEntries(
        raw: Map<*, *>,
    ): Pair<Map<String, Any?>, RestoreResult> {
        val normalized = LinkedHashMap<String, Any?>(raw.size)
        var invalidSkipped = 0
        var deprecatedIgnored = 0
        var restored = 0

        for ((rawKey, rawValue) in raw) {
            if (rawKey !is String) {
                invalidSkipped++
                continue
            }

            if (rawKey in DROPPED_KEYS || rawKey in NON_EXPORTABLE_KEYS) {
                deprecatedIgnored++
                continue
            }

            val value = normalizeValue(rawValue)
            if (value == null) {
                invalidSkipped++
                continue
            }

            normalized[rawKey] = value
            restored++
        }

        val counts = RestoreResult(
            status = Status.FAILURE,
            restored = restored,
            deprecatedIgnored = deprecatedIgnored,
            invalidSkipped = invalidSkipped,
        )
        return Pair(normalized, counts)
    }

    /**
     * Validates and normalizes a single legacy value.
     *
     * Returns `null` if the value is unsupported or a `StringSet` contains an
     * invalid member.
     */
    @JvmStatic
    private fun normalizeValue(rawValue: Any?): Any? {
        return when (rawValue) {
            is Boolean, is Int, is Long, is Float, is String -> rawValue
            is Set<*> -> {
                val set = LinkedHashSet<String>(rawValue.size)
                for (element in rawValue) {
                    if (element !is String) return null
                    set.add(element)
                }
                set
            }
            else -> null
        }
    }

    /**
     * Writes [entries] into a [SharedPreferences.Editor].
     *
     * Only supported types are written; unsupported types are silently skipped.
     */
    @JvmStatic
    fun putSupportedPreferenceEntries(
        editor: SharedPreferences.Editor,
        entries: Map<String, Any?>,
    ) {
        for ((key, value) in entries) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    val set = LinkedHashSet<String>(value.size)
                    @Suppress("UNCHECKED_CAST")
                    for (element in value as Set<String>) {
                        set.add(element)
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
    }

    /**
     * Reconciles the launcher icon enabled state after a successful restore commit.
     *
     * @param enabled the desired enabled state; `null` falls back to disabled.
     * @return `true` if the `PackageManager` call succeeds.
     */
    @JvmStatic
    fun reconcileLauncherIcon(
        packageManager: PackageManager,
        componentName: ComponentName,
        enabled: Boolean?,
    ): Boolean {
        return try {
            val target = when (enabled) {
                null, true -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                componentName,
                target,
                PackageManager.DONT_KILL_APP,
            )
            true
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            false
        }
    }

    /**
     * Performs the full M1 restore pipeline.
     *
     * This overload is suitable for unit tests and any caller that already has a
     * package set and a launcher reconcile lambda. It does not touch the
     * `PackageManager` itself, so no destructive query can happen after the
     * snapshot is captured.
     */
    @JvmStatic
    fun performRestore(
        inputStream: InputStream,
        prefs: SharedPreferences,
        installedPackages: Set<String>,
        launcherReconciler: ((Boolean?) -> Boolean)? = null,
    ): RestoreResult = inputStream.use { stream ->
        val bytes = try {
            readBoundedInputStream(stream) ?: return@use RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return@use RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        val rawRoot = try {
            decodeLegacyBackup(bytes)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return@use RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        val (entriesAfterValidation, validationCounts) = validateAndNormalizeEntries(rawRoot)
        val invalidSkipped = validationCounts.invalidSkipped
        val deprecatedIgnored = validationCounts.deprecatedIgnored
        var restored = validationCounts.restored

        val (sanitizedEntries, appSelectionsSanitized) = try {
            val sanitized = AppSelectionSanitizer.sanitizeRestoredEntries(
                entriesAfterValidation,
                installedPackages,
            )
            Pair(sanitized.entries, sanitized.changedPrimaryCount)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return@use RestoreResult(
                status = Status.FAILURE,
                invalidSkipped = invalidSkipped,
                deprecatedIgnored = deprecatedIgnored,
                appSelectionsSanitized = 0,
                restored = restored,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        restored = sanitizedEntries.size

        val snapshot = capturePreRestoreSnapshot(prefs)

        val primaryEditor = prefs.edit()
        primaryEditor.clear()
        putSupportedPreferenceEntries(primaryEditor, sanitizedEntries)
        val primaryCommit = primaryEditor.commit()

        if (!primaryCommit) {
            // Best-effort rollback from snapshot. The in-memory map may already have
            // changed, so we try to restore the previous durable state.
            val rollbackEditor = prefs.edit()
            rollbackEditor.clear()
            putSupportedPreferenceEntries(rollbackEditor, snapshot)
            val rollbackCommit = rollbackEditor.commit()

            return@use RestoreResult(
                status = Status.FAILURE,
                restored = restored,
                deprecatedIgnored = deprecatedIgnored,
                invalidSkipped = invalidSkipped,
                appSelectionsSanitized = appSelectionsSanitized,
                commitSucceeded = false,
                commitConfirmedDurable = false,
                deviceReconciled = false,
                rollbackAttempted = true,
                rollbackSucceeded = rollbackCommit,
            )
        }

        // Primary commit succeeded. Reconcile locale and launcher.
        val localeReconciled = try {
            AppLocaleController.invalidateFastPath(prefs)
            true
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            false
        }

        val launcherEnabled = prefs.getBoolean("pref_key_miuizer_launchericon", true)
        val launcherReconciled = launcherReconciler?.invoke(launcherEnabled) ?: true
        val deviceReconciled = localeReconciled && launcherReconciled

        val status = if (deviceReconciled) Status.SUCCESS else Status.PARTIAL_FAILURE

        RestoreResult(
            status = status,
            restored = restored,
            deprecatedIgnored = deprecatedIgnored,
            invalidSkipped = invalidSkipped,
            appSelectionsSanitized = appSelectionsSanitized,
            commitSucceeded = true,
            commitConfirmedDurable = true,
            deviceReconciled = deviceReconciled,
            rollbackAttempted = false,
            rollbackSucceeded = false,
        )
    }

    /**
     * Performs the full M1 restore pipeline using a `PackageManager`.
     *
     * Package query and launcher reconcile are performed outside the destructive
     * transaction. This is the production overload used by [PreferenceFragmentBase].
     */
    @JvmStatic
    fun performRestore(
        inputStream: InputStream,
        packageManager: PackageManager,
        prefs: SharedPreferences,
        componentName: ComponentName? = null,
    ): RestoreResult {
        val installedPackages = try {
            AppSelectionSanitizer.queryInstalledPackageNames(packageManager)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return RestoreResult(
                status = Status.FAILURE,
                commitSucceeded = false,
                commitConfirmedDurable = false,
            )
        }

        val reconciler: ((Boolean?) -> Boolean)? = if (componentName != null) { enabled ->
            reconcileLauncherIcon(packageManager, componentName, enabled)
        } else null

        return performRestore(inputStream, prefs, installedPackages, reconciler)
    }

    class BackupRestoreException(message: String) : Exception(message)
}
