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
import org.junit.Assert.fail
import org.junit.Test

class BackupRestoreTest {

    // Legacy ObjectOutputStream type codes used for raw malicious fixtures.
    private val TC_NULL = 0x70
    private val TC_CLASSDESC = 0x72
    private val TC_OBJECT = 0x73
    private val TC_ENDBLOCKDATA = 0x78

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
        val huge = ByteArray(BackupFormatV2.MAX_FILE_SIZE.toInt() + 1)
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
        map["pref_key_system_clock_app_user"] = "123"
        map["pref_key_system_blocktoasts_apps"] = HashSet<String>().apply {
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

    @Test
    fun decodeLegacyBackupRejectsLinkedHashMap() {
        val map = LinkedHashMap<String, Any?>()
        map["pref_key_test"] = true
        val bytes = serialize(map)
        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue(e.cause is java.io.InvalidClassException)
        }
    }

    @Test
    fun performRestoreDetectsV2AndSucceeds() {
        val entries = linkedMapOf(
            "pref_key_enabled" to true,
            "pref_key_miuizer_launchericon" to false,
        )
        val input = ByteArrayInputStream(BackupFormatV2.encode(entries))

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertEquals(2, result.restored)
        assertTrue(prefs.getBoolean("pref_key_enabled", false))
        assertFalse(prefs.getBoolean("pref_key_miuizer_launchericon", true))
    }

    @Test
    fun performRestoreDetectsLegacyJavaSerialization() {
        val map = HashMap<String, Any?>()
        map["pref_key_enabled"] = true
        val input = ByteArrayInputStream(serialize(map))

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertTrue(result.commitSucceeded)
        assertTrue(prefs.getBoolean("pref_key_enabled", false))
    }

    @Test
    fun performRestoreRejectsUnrecognizedFormat() {
        val input = ByteArrayInputStream(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06))

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
    fun performBackupWritesV2AndFiltersDroppedAndNonExportable() {
        val prefs = FakeSharedPreferences().apply {
            put("pref_key_enabled", true)
            put("pref_key_string", "value")
            put("pref_key_system_notif_disable_strong_toast", true)
            put("pref_key_miuizer_locale_applied", "zh")
        }

        val output = ByteArrayOutputStream()
        val success = BackupRestore.performBackup(prefs, output)

        assertTrue(success)

        val decoded = BackupFormatV2.decode(output.toByteArray())
        assertEquals(2, decoded.size)
        assertTrue(decoded.containsKey("pref_key_enabled"))
        assertTrue(decoded.containsKey("pref_key_string"))
        assertFalse(decoded.containsKey("pref_key_system_notif_disable_strong_toast"))
        assertFalse(decoded.containsKey("pref_key_miuizer_locale_applied"))
    }

    @Test
    fun performBackupFailsOnUnsupportedValueType() {
        val prefs = FakeSharedPreferences().apply {
            put("pref_key_double", 1.5)
        }

        try {
            BackupRestore.performBackup(prefs, ByteArrayOutputStream())
            fail("Expected BackupFormatV2.BackupFormatException")
        } catch (e: BackupFormatV2.BackupFormatException) {
            assertTrue(e.message?.contains("Unsupported") == true)
        }
    }

    @Test
    fun performRestoreV2IgnoresDroppedAndDeviceDerivedKeys() {
        val entries = linkedMapOf(
            "pref_key_enabled" to true,
            "pref_key_system_notif_disable_strong_toast" to true,
            "pref_key_miuizer_locale" to "en",
            "pref_key_miuizer_locale_applied" to "zh",
        )
        val input = ByteArrayInputStream(BackupFormatV2.encode(entries))

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            input,
            prefs,
            installedPackages = emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(2, result.deprecatedIgnored)
        assertEquals(2, result.restored)
        assertTrue(prefs.getBoolean("pref_key_enabled", false))
        assertEquals("en", prefs.getString("pref_key_miuizer_locale", null))
        assertNull(prefs.getString("pref_key_system_notif_disable_strong_toast", null))
        // Device-derived marker gets local reconcile marker, not source value.
        assertEquals("", prefs.getString("pref_key_miuizer_locale_applied", null))
    }

    @Test
    fun decodeLegacyBackupRejectsCustomReadObjectBeforeExecution() {
        EvilSerializable.readObjectCalled = false

        val map = HashMap<String, Any?>()
        map["pref_key_evil"] = EvilSerializable()
        val bytes = serialize(map)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertFalse("Custom readObject must not run", EvilSerializable.readObjectCalled)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsOversizedHashMapCapacity() {
        val map = HashMap<String, String>()
        repeat(4) { map["k$it"] = "v" }
        val bytes = serialize(map).toMutableList()

        // Locate the HashMap custom block: TC_BLOCKDATA (0x77) followed by length 8 (0x08).
        val blockIndex = (0 until bytes.size - 1)
            .first { i -> bytes[i] == 0x77.toByte() && bytes[i + 1] == 0x08.toByte() } + 2

        // Patch the declared capacity to exceed LEGACY_MAX_ARRAY_LENGTH.
        val cap = (BackupRestore.LEGACY_MAX_ARRAY_LENGTH + 1).toInt()
        bytes[blockIndex] = (cap ushr 24).toByte()
        bytes[blockIndex + 1] = (cap ushr 16 and 0xFF).toByte()
        bytes[blockIndex + 2] = (cap ushr 8 and 0xFF).toByte()
        bytes[blockIndex + 3] = (cap and 0xFF).toByte()

        try {
            BackupRestore.decodeLegacyBackup(bytes.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected capacity bound", e.message?.contains("capacity") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsDeepGraph() {
        val root = HashMap<String, Any?>()
        var current = root
        repeat(20) {
            val next = HashMap<String, Any?>()
            current["x"] = next
            current = next
        }
        current["x"] = true

        try {
            BackupRestore.decodeLegacyBackup(serialize(root))
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected depth bound", e.message?.contains("depth") == true)
        }
    }

    @Test
    fun decodeLegacyBackupContainsAllSupportedTypes() {
        val map = HashMap<String, Any?>()
        map["bool"] = true
        map["int"] = 42
        map["long"] = 1234567890123L
        map["float"] = 3.14f
        map["string"] = "hello"
        map["set"] = HashSet<String>().apply { add("a"); add("b") }

        val decoded = BackupRestore.decodeLegacyBackup(serialize(map)) as Map<String, Any?>

        assertEquals(true, decoded["bool"])
        assertEquals(42, decoded["int"])
        assertEquals(1234567890123L, decoded["long"])
        assertEquals(3.14f, decoded["float"])
        assertEquals("hello", decoded["string"])
        @Suppress("UNCHECKED_CAST")
        val set = decoded["set"] as Set<String>
        assertEquals(setOf("a", "b"), set)
    }

    @Test
    fun decodeLegacyBackupRejectsTrailingByte() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = serialize(map).toMutableList()
        bytes.add(0x00)

        try {
            BackupRestore.decodeLegacyBackup(bytes.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected trailing bytes rejection", e.message?.contains("trailing") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsSecondRootObject() {
        val map = HashMap<String, Any?>()
        map["key"] = true

        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { oos ->
            oos.writeObject(map)
            oos.writeObject(map)
        }

        try {
            BackupRestore.decodeLegacyBackup(output.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected trailing bytes rejection", e.message?.contains("trailing") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsDeepClassDescriptorChainWithoutStackOverflow() {
        val output = ByteArrayOutputStream()
        val data = java.io.DataOutputStream(output)

        // Stream header.
        data.writeByte(0xAC)
        data.writeByte(0xED)
        data.writeByte(0x00)
        data.writeByte(0x05)

        // Root TC_OBJECT.
        data.writeByte(TC_OBJECT)

        // Build a 20-level class-descriptor chain using java.lang.Number (an
        // allowlisted descriptor with no instance data). Each descriptor's
        // superclass is another TC_CLASSDESC, forcing superclass recursion.
        val chainDepth = 20
        val numberName = "java.lang.Number"
        val numberSuid = -8742448824652078965L
        val numberFlags = 0x02

        repeat(chainDepth) { index ->
            data.writeByte(TC_CLASSDESC)
            data.writeUTF(numberName)
            data.writeLong(numberSuid)
            data.writeByte(numberFlags)
            data.writeShort(0)
            data.writeByte(TC_ENDBLOCKDATA)
            if (index == chainDepth - 1) {
                data.writeByte(TC_NULL)
            }
        }

        try {
            BackupRestore.decodeLegacyBackup(output.toByteArray())
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected descriptor depth bound", e.message?.contains("descriptor depth") == true)
        }
    }

    @Test
    fun decodeLegacyBackupRejectsHandleLimit() {
        val map = HashMap<String, HashSet<String>>()
        repeat(100) { i ->
            val set = HashSet<String>()
            repeat(BackupFormatV2.MAX_SET_ITEMS) { j ->
                set.add(String.format("s%05d", i * BackupFormatV2.MAX_SET_ITEMS + j))
            }
            map["set$i"] = set
        }

        try {
            BackupRestore.decodeLegacyBackup(serialize(map))
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected handle bound", e.message?.contains("handle") == true)
        }
    }

    @Test
    fun performRestoreLegacyCountsNonStringKeyAsInvalidSkipped() {
        val map = HashMap<Any?, Any?>()
        map[123] = "bad-key"
        map["valid"] = true

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(serialize(map)),
            prefs,
            emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.invalidSkipped)
        assertEquals(1, result.restored)
        assertTrue(prefs.getBoolean("valid", false))
        assertNull(prefs.getString("123", null))
    }

    @Test
    fun performRestoreLegacyCountsMalformedSetAsInvalidSkipped() {
        val map = HashMap<Any?, Any?>()
        val set = HashSet<Any?>()
        set.add("com.ok")
        set.add(123)
        map["bad_set"] = set
        map["valid"] = true

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(serialize(map)),
            prefs,
            emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.invalidSkipped)
        assertEquals(1, result.restored)
        assertNull(prefs.getStringSet("bad_set", null))
        assertTrue(prefs.getBoolean("valid", false))
    }

    @Test
    fun performRestoreLegacyCountsNestedMapAsInvalidSkipped() {
        val map = HashMap<Any?, Any?>()
        val nested = HashMap<String, Any?>()
        nested["x"] = true
        map["bad_value"] = nested
        map["valid"] = true

        val prefs = FakeSharedPreferences()
        val result = BackupRestore.performRestore(
            ByteArrayInputStream(serialize(map)),
            prefs,
            emptySet(),
            launcherReconciler = { true },
        )

        assertEquals(BackupRestore.Status.SUCCESS, result.status)
        assertEquals(1, result.invalidSkipped)
        assertEquals(1, result.restored)
        assertNull(prefs.getString("bad_value", null))
        assertTrue(prefs.getBoolean("valid", false))
    }

    @Test
    fun legacyHashMapRejectsNaNLoadFactor() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapLoadFactor(serialize(map), Float.NaN)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashMapRejectsZeroOrNegativeLoadFactor() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapLoadFactor(serialize(map), -0.5f)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashMapRejectsPositiveInfinityLoadFactor() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapLoadFactor(serialize(map), Float.POSITIVE_INFINITY)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashMapRejectsNegativeExplicitCapacity() {
        val map = HashMap<String, Any?>()
        map["key"] = true
        val bytes = patchHashMapCapacity(serialize(map), -1)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected negative capacity rejection", e.message?.contains("capacity") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsNegativeCapacity() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetCapacity(serialize(set), -1)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected negative capacity rejection", e.message?.contains("capacity") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsNaNLoadFactor() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetLoadFactor(serialize(set), Float.NaN)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsZeroOrNegativeLoadFactor() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetLoadFactor(serialize(set), 0f)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    @Test
    fun legacyHashSetRejectsPositiveInfinityLoadFactor() {
        val set = HashSet<String>()
        set.add("x")
        val bytes = patchHashSetLoadFactor(serialize(set), Float.POSITIVE_INFINITY)

        try {
            BackupRestore.decodeLegacyBackup(bytes)
            fail("Expected BackupRestoreException")
        } catch (e: BackupRestore.BackupRestoreException) {
            assertTrue("Expected loadFactor validation", e.message?.contains("loadFactor") == true)
        }
    }

    private fun findFirstClassDataEnd(bytes: List<Byte>): Int {
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == TC_ENDBLOCKDATA.toByte() && bytes[i + 1] == TC_NULL.toByte()) {
                return i + 2
            }
        }
        throw IllegalArgumentException("Class data end not found in serialized fixture")
    }

    private fun patchHashMapLoadFactor(bytes: ByteArray, newLoadFactor: Float): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        val bits = java.lang.Float.floatToIntBits(newLoadFactor)
        list[end] = (bits ushr 24).toByte()
        list[end + 1] = (bits ushr 16).toByte()
        list[end + 2] = (bits ushr 8).toByte()
        list[end + 3] = bits.toByte()
        return list.toByteArray()
    }

    private fun patchHashMapCapacity(bytes: ByteArray, newCapacity: Int): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        // JDK 17 HashMap custom block: TC_BLOCKDATA(1) + len(1) + capacity(4) + size(4)
        val capIndex = end + 8 + 2
        list[capIndex] = (newCapacity ushr 24).toByte()
        list[capIndex + 1] = (newCapacity ushr 16).toByte()
        list[capIndex + 2] = (newCapacity ushr 8).toByte()
        list[capIndex + 3] = newCapacity.toByte()
        return list.toByteArray()
    }

    private fun patchHashSetCapacity(bytes: ByteArray, newCapacity: Int): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        // JDK 17 HashSet custom block: TC_BLOCKDATA(1) + len(1) + capacity(4) + loadFactor(4) + size(4)
        val capIndex = end + 2
        list[capIndex] = (newCapacity ushr 24).toByte()
        list[capIndex + 1] = (newCapacity ushr 16).toByte()
        list[capIndex + 2] = (newCapacity ushr 8).toByte()
        list[capIndex + 3] = newCapacity.toByte()
        return list.toByteArray()
    }

    private fun patchHashSetLoadFactor(bytes: ByteArray, newLoadFactor: Float): ByteArray {
        val list = bytes.toMutableList()
        val end = findFirstClassDataEnd(list)
        // JDK 17 HashSet custom block: TC_BLOCKDATA(1) + len(1) + capacity(4) + loadFactor(4) + size(4)
        val lfIndex = end + 6
        val bits = java.lang.Float.floatToIntBits(newLoadFactor)
        list[lfIndex] = (bits ushr 24).toByte()
        list[lfIndex + 1] = (bits ushr 16).toByte()
        list[lfIndex + 2] = (bits ushr 8).toByte()
        list[lfIndex + 3] = bits.toByte()
        return list.toByteArray()
    }

    private fun serialize(obj: Any): ByteArray {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { it.writeObject(obj) }
        return output.toByteArray()
    }

    class EvilSerializable : java.io.Serializable {
        companion object {
            @JvmField
            var readObjectCalled = false
        }

        private fun readObject(s: java.io.ObjectInputStream) {
            readObjectCalled = true
        }
    }
}
