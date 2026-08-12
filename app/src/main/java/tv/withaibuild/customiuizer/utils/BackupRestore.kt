package tv.withaibuild.customiuizer.utils

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/**
 * M2 backup / restore owner.
 *
 * This object is intentionally cold-path only: no startup cost, no resident state,
 * no observer, no background worker. The only mutable data it creates lives for the
 * duration of one user-initiated backup or restore.
 *
 * M2 final format:
 * - Backup writes V2 (CUI2 / typed entries / CRC32).
 * - Restore auto-detects V2 or legacy Java serialization.
 * - Legacy reader is restricted by an `ObjectInputFilter` allowlist.
 */
object BackupRestore {

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

    /** Legacy Java serialization stream header: 0xAC 0xED 0x00 0x05. */
    private val LEGACY_MAGIC = byteArrayOf(0xAC.toByte(), 0xED.toByte(), 0x00, 0x05)

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
     * Copies and filters the current preference entries for backup output.
     *
     * Dropped / non-exportable keys are removed. Unsupported value types cause
     * a [BackupFormatV2.BackupFormatException] rather than silent partial output.
     */
    @JvmStatic
    fun filterBackupEntries(prefs: SharedPreferences): Map<String, Any?> {
        val source = prefs.all
        val filtered = LinkedHashMap<String, Any?>(source.size)
        for ((key, value) in source) {
            if (key in DROPPED_KEYS || key in NON_EXPORTABLE_KEYS) continue
            if (value != null && !isSupportedValue(value)) {
                throw BackupFormatV2.BackupFormatException(
                    "Unsupported backup value type for key '$key': ${value.javaClass}"
                )
            }
            filtered[key] = when (value) {
                is Set<*> -> HashSet<String>(value.size).apply {
                    @Suppress("UNCHECKED_CAST")
                    addAll(value as Set<String>)
                }
                else -> value
            }
        }
        return filtered
    }

    @JvmStatic
    private fun isSupportedValue(value: Any?): Boolean {
        return value == null ||
            value is Boolean ||
            value is Int ||
            value is Long ||
            value is Float ||
            value is String ||
            (value is Set<*> && value.all { it is String })
    }

    /**
     * Writes the current preferences to [output] in the M2 V2 format.
     *
     * The output stream is closed by this function.
     *
     * @return true if the encoded bytes were fully written.
     * @throws BackupFormatV2.BackupFormatException if a bound is exceeded or a
     *     value is unsupported.
     */
    @JvmStatic
    fun performBackup(
        prefs: SharedPreferences,
        output: OutputStream,
    ): Boolean {
        val entries = filterBackupEntries(prefs)
        val encoded = BackupFormatV2.encode(entries)
        output.use { out ->
            out.write(encoded)
            out.flush()
        }
        return true
    }

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
     * Bounded read of a backup input. Returns `null` if the input exceeds
     * [BackupFormatV2.MAX_FILE_SIZE].
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readBoundedInputStream(input: InputStream, maxBytes: Long = BackupFormatV2.MAX_FILE_SIZE): ByteArray? {
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
     * Decodes a V2 or restricted legacy Java-serialized backup.
     *
     * - First 4 bytes `CUI2` → V2.
     * - First 4 bytes `AC ED 00 05` → legacy.
     * - Anything else → structural failure.
     *
     * The legacy path uses an `ObjectInputFilter` allowlist and validates the root
     * object. No unchecked `Map<String, Any?>` cast is performed before format
     * detection.
     *
     * @throws BackupRestoreException if the file is not a recognized backup.
     */
    @JvmStatic
    @Throws(IOException::class, ClassNotFoundException::class)
    fun decodeBackup(bytes: ByteArray): Map<*, *> {
        if (bytes.size < 4) {
            throw BackupRestoreException("Backup file too short")
        }

        val firstFour = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

        if (firstFour == BackupFormatV2.MAGIC) {
            return BackupFormatV2.decode(bytes)
        }

        if (bytes.size >= LEGACY_MAGIC.size &&
            bytes[0] == LEGACY_MAGIC[0] &&
            bytes[1] == LEGACY_MAGIC[1] &&
            bytes[2] == LEGACY_MAGIC[2] &&
            bytes[3] == LEGACY_MAGIC[3]
        ) {
            return decodeLegacyBackup(bytes)
        }

        throw BackupRestoreException("Unrecognized backup format")
    }

    /**
     * Decodes a legacy Java-serialized backup with a restricted `ObjectInputStream`.
     *
     * The wire allowlist is the minimum proven historical class graph:
     * - `java.util.HashMap`
     * - `java.util.HashSet`
     * - `java.lang.String`
     * - `java.lang.Boolean`, `Integer`, `Long`, `Float`
     *
     * Subclasses, `LinkedHashMap`, `LinkedHashSet`, `ArrayList`, `Date`, `Double`,
     * and any application classes are rejected by overriding `resolveClass`.
     */
    @JvmStatic
    fun decodeLegacyBackup(bytes: ByteArray): Map<*, *> {
        try {
            ByteArrayInputStream(bytes).use { byteIn ->
                RestrictedObjectInputStream(byteIn).use { input ->
                    val root = input.readObject()
                    if (root !is Map<*, *>) {
                        throw BackupRestoreException("Legacy backup root is not a Map")
                    }
                    return root
                }
            }
        } catch (e: BackupRestoreException) {
            throw e
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            throw BackupRestoreException("Legacy decode failed", t)
        }
    }

    /**
     * `ObjectInputStream` subclass that refuses to resolve any class outside the
     * proven legacy backup allowlist. Proxy classes are always rejected.
     */
    private class RestrictedObjectInputStream(input: ByteArrayInputStream) : ObjectInputStream(input) {

        private val allowedClassNames = setOf(
            "java.util.HashMap",
            "java.util.HashSet",
            "java.lang.Boolean",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.String",
            "[Ljava.lang.String;",
        )

        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            val name = desc.name
            if (!isAllowed(name)) {
                throw java.io.InvalidClassException(name, "Legacy class not in allowlist: $name")
            }
            return super.resolveClass(desc)
        }

        override fun resolveProxyClass(interfaces: Array<String>): Class<*> {
            throw java.io.InvalidClassException("proxy", "Proxy classes not allowed in legacy backups")
        }

        private fun isAllowed(name: String): Boolean {
            return name in allowedClassNames ||
                name == "java.lang.Object" ||
                name.startsWith("java.util.HashMap$") ||
                name.startsWith("java.util.HashSet$")
        }
    }

    /**
     * Validates and normalizes every entry of a decoded backup map.
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
     * Validates and normalizes a single value.
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
     * @param enabled the desired enabled state; `null` falls back to enabled.
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
     * Performs the full M2 restore pipeline.
     *
     * - bounded input read (final M2 `MAX_FILE_SIZE`);
     * - V2 or legacy format detection;
     * - V2 decode with CRC32 and bounds;
     * - restricted legacy `ObjectInputStream` with allowlist;
     * - entry validation and tombstone filtering;
     * - app-selection sanitization;
     * - pre-restore snapshot capture;
     * - `SharedPreferences` clear + put + commit;
     * - best-effort rollback on commit failure;
     * - launcher and locale side-effect reconcile only after durable commit.
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
            decodeBackup(bytes)
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
     * Performs the full M2 restore pipeline using a `PackageManager`.
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

    class BackupRestoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
