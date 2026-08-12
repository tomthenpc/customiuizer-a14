package tv.withaibuild.customiuizer.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreTest {

    @Test
    fun generateBackupFilenameHasCorrectPrefixAndTimestamp() {
        val filename = BackupRestore.generateBackupFilename()
        assertTrue("Filename should start with r14bak_", filename.startsWith("r14bak_"))
        assertEquals("r14bak_".length + 10, filename.length)
        val timestamp = filename.substring("r14bak_".length)
        assertTrue("Timestamp should be 10 digits", timestamp.matches(Regex("\\d{10}")))
    }

    @Test
    fun capturePreRestoreSnapshotDefensiveCopiesStringSet() {
        val prefs = FakeSharedPreferences()
        val original = HashSet<String>().apply { add("a") }
        prefs.put("key1", original)

        val snapshot = BackupRestore.capturePreRestoreSnapshot(prefs)
        @Suppress("UNCHECKED_CAST")
        val copied = snapshot["key1"] as HashSet<String>

        assertEquals(original, copied)
        copied.add("b")

        @Suppress("UNCHECKED_CAST")
        val live = prefs.getStringSet("key1", emptySet()) as Set<String>
        assertFalse(live.contains("b"))
    }

    @Test
    fun decodeLegacyBackupAcceptsHashMap() {
        val map = HashMap<String, Any?>()
        map["pref_key_enabled"] = true
        val bytes = serialize(map)

        val decoded = BackupRestore.decodeLegacyBackup(bytes)
        assertNotNull(decoded)
        assertEquals(true, decoded["pref_key_enabled"])
    }

    @Test
    fun decodeLegacyBackupRejectsNonMapRoot() {
        val bytes = serialize("not a map")
        try {
            BackupRestore.decodeLegacyBackup(bytes)
            org.junit.Assert.fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue(e.message?.contains("not a Map") == true)
        }
    }

    @Test
    fun validateAndNormalizeEntriesSkipsTombstones() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_system_notif_disable_strong_toast"] = true
        map["pref_key_system_notif_strong_toast_width"] = 100
        map["pref_key_valid"] = "keep"

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertFalse(normalized.containsKey("pref_key_system_notif_disable_strong_toast"))
        assertFalse(normalized.containsKey("pref_key_system_notif_strong_toast_width"))
        assertEquals("keep", normalized["pref_key_valid"])
        assertEquals(2, counts.deprecatedIgnored)
        // validateAndNormalizeEntries does not yet know sanitization; restored is adjusted later.
        assertEquals(1, counts.restored)
    }

    @Test
    fun validateAndNormalizeEntriesSkipsNonStringKey() {
        val map = LinkedHashMap<Any?, Any?>()
        map[123] = "value"
        map["pref_key_valid"] = true

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertEquals(1, counts.invalidSkipped)
        assertEquals(1, counts.restored)
        assertTrue(normalized.containsKey("pref_key_valid"))
    }

    @Test
    fun validateAndNormalizeEntriesSkipsUnsupportedValue() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_double"] = 1.5
        map["pref_key_valid"] = 42

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertEquals(1, counts.invalidSkipped)
        assertEquals(1, counts.restored)
        assertEquals(42, normalized["pref_key_valid"])
    }

    @Test
    fun validateAndNormalizeEntriesSkipsMalformedStringSet() {
        val map = LinkedHashMap<Any?, Any?>()
        map["pref_key_apps"] = LinkedHashSet<Any?>().apply { add("com.example"); add(123) }
        map["pref_key_valid"] = "keep"

        val (normalized, counts) = BackupRestore.validateAndNormalizeEntries(map)

        assertEquals(1, counts.invalidSkipped)
        assertEquals(1, counts.restored)
        assertNull(normalized["pref_key_apps"])
        assertEquals("keep", normalized["pref_key_valid"])
    }

    @Test
    fun performRestoreReturnsSuccessForValidLegacyBackup() {
        val map = HashMap<String, Any?>()
        map["pref_key_enabled"] = true
        map["pref_key_miuizer_launchericon"] = false
        map["pref_key_miuizer_locale"] = "en"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_old", "value")
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertTrue(result.deviceReconciled)
        assertEquals(3, result.restored)
        assertEquals(true, prefs.getBoolean("pref_key_enabled", false))
        assertFalse(prefs.getBoolean("pref_key_miuizer_launchericon", true))
    }

    @Test
    fun performRestorePrimaryCommitFalseAndRollbackTrueUpdatesAndRestoresVisibleState() {
        val map = HashMap<String, Any?>()
        map["pref_key_new"] = "restored in memory"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_old", "original")
            commitSequence = listOf(false, true).iterator()
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
        assertTrue(result.rollbackAttempted)
        assertTrue(result.rollbackSucceeded)

        // Primary commit applies to in-memory map even though durability result is false.
        val primaryState = prefs.commitSnapshot(0)
        assertNull(primaryState["pref_key_old"])
        assertEquals("restored in memory", primaryState["pref_key_new"])

        // Rollback restores original snapshot.
        val rollbackState = prefs.commitSnapshot(1)
        assertEquals("original", rollbackState["pref_key_old"])
        assertNull(rollbackState["pref_key_new"])

        // Final live state is original.
        assertEquals("original", prefs.getString("pref_key_old", null))
        assertNull(prefs.getString("pref_key_new", null))
    }

    @Test
    fun performRestorePrimaryCommitFalseAndRollbackFalseLeavesRestoredStateThenRollsBack() {
        val map = HashMap<String, Any?>()
        map["pref_key_new"] = "restored in memory"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_old", "original")
            commitSequence = listOf(false, false).iterator()
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
        assertTrue(result.rollbackAttempted)
        assertFalse(result.rollbackSucceeded)

        // Both commits applied to in-memory map; rollback attempted even if durable result false.
        assertEquals("original", prefs.getString("pref_key_old", null))
        assertNull(prefs.getString("pref_key_new", null))
    }

    @Test
    fun performRestoreDoesNotReconcileWhenCommitFails() {
        val map = HashMap<String, Any?>()
        map["pref_key_new"] = "should not persist"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            put("pref_key_old", "value")
            commitSequence = listOf(false, true).iterator()
        }

        var reconcileCalled = false
        BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = {
                reconcileCalled = true
                true
            },
        )

        assertFalse(reconcileCalled)
    }

    @Test
    fun performRestoreReturnsPartialFailureWhenReconcileFails() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_launchericon"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { false },
        )

        assertEquals(BackupRestore.Status.PARTIAL_FAILURE, result.status)
        assertTrue(result.commitSucceeded)
        assertFalse(result.deviceReconciled)
    }

    @Test
    fun performRestoreReturnsPartialFailureWhenLocaleReconcileFails() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences().apply {
            applyShouldThrow = true
        }

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.PARTIAL_FAILURE, result.status)
        assertTrue(result.commitSucceeded)
        assertFalse(result.deviceReconciled)
        // Locale choice is still persisted.
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
    }

    @Test
    fun performRestoreReconcilesBothLocaleAndLauncherWhenBothSucceed() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        map["pref_key_miuizer_launchericon"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        var launcherCalled = false
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { enabled ->
                launcherCalled = true
                assertEquals(true, enabled)
                true
            },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertTrue(result.deviceReconciled)
        assertTrue(launcherCalled)
    }

    @Test
    fun performRestoreRejectsTruncatedInput() {
        val map = HashMap<String, Any?>()
        map["pref_key_enabled"] = true
        val full = serialize(map)
        val truncated = full.copyOfRange(0, full.size / 2)
        val input = ByteArrayInputStream(truncated)

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
    }

    @Test
    fun performRestoreRejectsOversizedInput() {
        val huge = ByteArray(BackupRestore.M1_LEGACY_MAX_BYTES.toInt() + 1)
        val input = ByteArrayInputStream(huge)

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.FAILURE, result.status)
        assertFalse(result.commitSucceeded)
    }

    @Test
    fun performRestoreSanitizesAppSelectionsAndReportsRestoredCount() {
        val map = HashMap<String, Any?>()
        map["pref_key_system_clock_app"] = "com.missing|com.missing.Clock"
        map["pref_key_system_clock_app_user"] = 123
        map["pref_key_system_blocktoasts_apps"] = LinkedHashSet<String>().apply {
            add("com.present")
            add("com.missing")
        }
        map["pref_key_valid"] = "keep"
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = setOf("com.present"),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(2, result.appSelectionsSanitized)
        // Only pref_key_system_blocktoasts_apps and pref_key_valid survive.
        assertEquals(2, result.restored)
        assertEquals("keep", prefs.getString("pref_key_valid", null))
        assertNull(prefs.getString("pref_key_system_clock_app", null))
        assertNull(prefs.getString("pref_key_system_clock_app_user", null))
        @Suppress("UNCHECKED_CAST")
        val set = prefs.getStringSet("pref_key_system_blocktoasts_apps", emptySet()) as Set<String>
        assertEquals(setOf("com.present"), set)
    }

    @Test
    fun performRestoreIgnoresDeviceDerivedKeys() {
        val map = HashMap<String, Any?>()
        map["pref_key_miuizer_locale"] = "en"
        map["pref_key_miuizer_locale_applied"] = "zh"
        map["pref_key_miuizer_synced_from_lsposed"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()

        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(2, result.deprecatedIgnored)
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
        // Source marker ignored; invalidateFastPath writes the local reconcile marker.
        assertEquals("", prefs.getString("pref_key_miuizer_locale_applied", null))
        assertNull(prefs.getString("pref_key_miuizer_synced_from_lsposed", null))
    }

    @Test
    fun putSupportedPreferenceEntriesWritesAllSupportedTypes() {
        val prefs = FakeSharedPreferences()
        val editor = prefs.edit()
        val entries = LinkedHashMap<String, Any?>()
        entries["bool"] = true
        entries["int"] = 1
        entries["long"] = 2L
        entries["float"] = 1.5f
        entries["string"] = "value"
        entries["set"] = LinkedHashSet<String>().apply { add("a") }

        BackupRestore.putSupportedPreferenceEntries(editor, entries)
        assertTrue((editor as FakeSharedPreferences.FakeEditor).commit())

        assertTrue(prefs.getBoolean("bool", false))
        assertEquals(1, prefs.getInt("int", 0))
        assertEquals(2L, prefs.getLong("long", 0L))
        assertEquals(1.5f, prefs.getFloat("float", 0f), 0.001f)
        assertEquals("value", prefs.getString("string", null))
        @Suppress("UNCHECKED_CAST")
        assertEquals(setOf("a"), prefs.getStringSet("set", emptySet()) as Set<String>)
    }

    private fun serialize(obj: Any): ByteArray {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { it.writeObject(obj) }
        return output.toByteArray()
    }
}
