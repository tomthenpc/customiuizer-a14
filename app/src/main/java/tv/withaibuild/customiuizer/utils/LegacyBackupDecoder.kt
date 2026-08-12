package tv.withaibuild.customiuizer.utils

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.util.HashMap
import java.util.HashSet

/**
 * Focused restricted decoder for historical Java-serialized CustoMIUIzer backups.
 *
 * This decoder does **not** use `ObjectInputStream` or `ObjectInputFilter`.
 * It parses only the ObjectOutputStream wire subset proven in the historical
 * backup graph and rejects everything else before any application class can be
 * instantiated or any custom `readObject` can run.
 *
 * Supported historical wire graph:
 * - root: `java.util.HashMap`
 * - values: `java.lang.Boolean`, `Integer`, `Long`, `Float`, `String`
 * - set values: `java.util.HashSet<String>`
 * - super classes: `java.lang.Number`, `java.lang.Object`
 *
 * The decoder enforces the same bounds as V2:
 * - `MAX_FILE_SIZE`
 * - `MAX_ENTRY_COUNT`
 * - `MAX_KEY_BYTES`
 * - `MAX_STRING_BYTES`
 * - `MAX_SET_ITEMS`
 * plus legacy graph limits:
 * - `LEGACY_MAX_DEPTH`
 * - `LEGACY_MAX_REFERENCES`
 * - `LEGACY_MAX_ARRAY_LENGTH`
 */
object LegacyBackupDecoder {

    // ObjectOutputStream content and class-descriptor type codes.
    private const val TC_NULL: Int = 0x70
    private const val TC_REFERENCE: Int = 0x71
    private const val TC_CLASSDESC: Int = 0x72
    private const val TC_OBJECT: Int = 0x73
    private const val TC_STRING: Int = 0x74
    private const val TC_ARRAY: Int = 0x75
    private const val TC_CLASS: Int = 0x76
    private const val TC_BLOCKDATA: Int = 0x77
    private const val TC_ENDBLOCKDATA: Int = 0x78
    private const val TC_RESET: Int = 0x79
    private const val TC_BLOCKDATALONG: Int = 0x7A
    private const val TC_EXCEPTION: Int = 0x7B
    private const val TC_LONGSTRING: Int = 0x7C
    private const val TC_PROXYCLASSDESC: Int = 0x7D
    private const val TC_ENUM: Int = 0x7E

    // ObjectStreamClass flags.
    private const val SC_WRITE_METHOD = 0x01
    private const val SC_SERIALIZABLE = 0x02
    private const val SC_EXTERNALIZABLE = 0x04
    private const val SC_BLOCK_DATA = 0x08
    private const val SC_ENUM = 0x10

    private const val BASE_WIRE_HANDLE = 0x7E0000

    private class ClassDesc(
        val name: String,
        val suid: Long,
        val flags: Int,
        val fields: List<Field>,
        val superClass: ClassDesc?,
    ) {
        val allFields: List<Field> = (superClass?.allFields ?: emptyList()) + fields
    }

    private class Field(val type: Char, val name: String, val className: String?)

    /**
     * Decodes a legacy Java-serialized backup into a map suitable for the
     * shared M1 restore pipeline.
     *
     * @throws BackupRestore.BackupRestoreException on format/allowlist/bound errors.
     */
    @JvmStatic
    fun decode(bytes: ByteArray): Map<*, *> {
        if (bytes.size < 5) {
            throw BackupRestore.BackupRestoreException("Legacy backup too short")
        }
        if (bytes.size > BackupFormatV2.MAX_FILE_SIZE) {
            throw BackupRestore.BackupRestoreException("Legacy backup too large")
        }
        if (bytes[0] != 0xAC.toByte() || bytes[1] != 0xED.toByte() || bytes[2] != 0x00.toByte() || bytes[3] != 0x05.toByte()) {
            throw BackupRestore.BackupRestoreException("Legacy backup magic mismatch")
        }

        val state = DecoderState(bytes)
        return try {
            val root = state.readRootObject()
            if (root !is HashMap<*, *>) {
                throw BackupRestore.BackupRestoreException("Legacy backup root is not a Map")
            }
            root
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (e: IOException) {
            throw BackupRestore.BackupRestoreException("Legacy decode I/O error", e)
        }
    }

    private class DecoderState(private val bytes: ByteArray) {

        private val input = DataInputStream(ByteArrayInputStream(bytes))
        private val handles = HashMap<Int, Any?>()
        private var nextHandle = 0

        private var depth = 0
        private var references = 0

        init {
            // Stream header (magic + version) already verified by decode(); consume it.
            input.skipBytes(4)
        }

        fun readRootObject(): Any? {
            return readContent()
        }

        private fun readContent(): Any? {
            if (references >= BackupRestore.LEGACY_MAX_REFERENCES) {
                throw BackupRestore.BackupRestoreException("Legacy reference limit exceeded")
            }

            val tc = input.readUnsignedByte()
            references++

            return when (tc) {
                TC_NULL -> null
                TC_REFERENCE -> readReference()
                TC_CLASSDESC -> readNewClassDesc()
                TC_OBJECT -> readNewObject()
                TC_STRING -> readNewString()
                TC_LONGSTRING -> throw BackupRestore.BackupRestoreException("TC_LONGSTRING not supported")
                TC_ARRAY -> throw BackupRestore.BackupRestoreException("TC_ARRAY not supported in legacy backups")
                TC_CLASS -> throw BackupRestore.BackupRestoreException("TC_CLASS not supported in legacy backups")
                TC_PROXYCLASSDESC -> throw BackupRestore.BackupRestoreException("Proxy class descriptors not allowed")
                TC_ENUM -> throw BackupRestore.BackupRestoreException("Enums not allowed in legacy backups")
                TC_RESET, TC_EXCEPTION, TC_BLOCKDATA, TC_BLOCKDATALONG, TC_ENDBLOCKDATA -> {
                    throw BackupRestore.BackupRestoreException("Unexpected legacy type code at content: $tc")
                }
                else -> throw BackupRestore.BackupRestoreException("Unknown legacy type code: $tc")
            }
        }

        private fun readReference(): Any? {
            val handle = input.readInt()
            return handles[handle]
                ?: throw BackupRestore.BackupRestoreException("Unresolvable legacy reference: $handle")
        }

        private fun readClassDescToken(): ClassDesc? {
            val tc = input.readUnsignedByte()
            return when (tc) {
                TC_NULL -> null
                TC_REFERENCE -> {
                    val handle = input.readInt()
                    when (val existing = handles[handle]) {
                        is ClassDesc -> existing
                        else -> throw BackupRestore.BackupRestoreException("Legacy class reference does not resolve to a class: $handle")
                    }
                }
                TC_CLASSDESC -> readNewClassDesc()
                TC_PROXYCLASSDESC -> throw BackupRestore.BackupRestoreException("Proxy class descriptors not allowed")
                else -> throw BackupRestore.BackupRestoreException("Unexpected class-desc token: $tc")
            }
        }

        private fun readNewClassDesc(): ClassDesc {
            val handle = assignHandle()
            val name = input.readUTF()
            val suid = input.readLong()
            val flags = input.readUnsignedByte()
            val fieldCount = input.readUnsignedShort()

            if (fieldCount > 256) {
                throw BackupRestore.BackupRestoreException("Suspicious legacy field count: $fieldCount")
            }

            val fields = ArrayList<Field>(fieldCount)
            repeat(fieldCount) {
                val typeCode = input.readUnsignedByte().toChar()
                val fieldName = input.readUTF()
                val className = if (typeCode == 'L' || typeCode == '[') input.readUTF() else null
                fields.add(Field(typeCode, fieldName, className))
            }

            // Class annotations are not used by the proven graph; only TC_ENDBLOCKDATA is accepted.
            val annotationTc = input.readUnsignedByte()
            if (annotationTc != TC_ENDBLOCKDATA) {
                throw BackupRestore.BackupRestoreException("Legacy class annotation not supported")
            }

            val superClass = readClassDescToken()
            val classDesc = ClassDesc(name, suid, flags, fields, superClass)
            store(handle, classDesc)
            return classDesc
        }

        private fun readNewObject(): Any? {
            if (depth >= BackupRestore.LEGACY_MAX_DEPTH) {
                throw BackupRestore.BackupRestoreException("Legacy depth limit exceeded")
            }
            depth++
            return try {
                val classDesc = readClassDescToken()
                    ?: throw BackupRestore.BackupRestoreException("Legacy object has no class descriptor")
                // Class desc handle is assigned before the object handle to match ObjectOutputStream.
                val objHandle = assignHandle()
                val obj = readObjectData(classDesc)
                store(objHandle, obj)
                obj
            } finally {
                depth--
            }
        }

        private fun readNewString(): String {
            val handle = assignHandle()
            val s = input.readUTF()
            store(handle, s)
            return s
        }

        private fun readObjectData(classDesc: ClassDesc): Any? {
            return when (classDesc.name) {
                "java.util.HashMap" -> readHashMap(classDesc)
                "java.util.HashSet" -> readHashSet(classDesc)
                "java.lang.Boolean" -> readBoolean(classDesc)
                "java.lang.Integer" -> readInteger(classDesc)
                "java.lang.Long" -> readLong(classDesc)
                "java.lang.Float" -> readFloat(classDesc)
                "java.lang.Number", "java.lang.Object" -> {
                    throw java.io.InvalidClassException(classDesc.name, "Unsupported legacy root object")
                }
                else -> throw java.io.InvalidClassException(classDesc.name, "Legacy class not in allowlist")
            }
        }

        private fun readHashMap(classDesc: ClassDesc): Map<String, Any?> {
            // Default fields written by HashMap.defaultWriteObject(): loadFactor, threshold.
            input.readFloat() // loadFactor
            input.readInt()   // threshold

            // Custom writeObject block.
            val (capacity, size) = readMapBlockHeader()

            if (size < 0) {
                throw BackupRestore.BackupRestoreException("Negative HashMap size")
            }
            if (size > BackupFormatV2.MAX_ENTRY_COUNT) {
                throw BackupRestore.BackupRestoreException("HashMap size $size exceeds MAX_ENTRY_COUNT")
            }

            val effectiveCapacity = if (capacity >= 0) {
                capacity
            } else {
                // Older format only wrote size; bound the table that HashMap.readObject would allocate.
                Integer.highestOneBit((size / 0.75f).toInt()) * 2
            }
            if (effectiveCapacity > BackupRestore.LEGACY_MAX_ARRAY_LENGTH) {
                throw BackupRestore.BackupRestoreException("HashMap capacity $effectiveCapacity exceeds legacy array limit")
            }

            val map = HashMap<String, Any?>(size)
            repeat(size) {
                val key = readContent()
                val value = readContent()
                when {
                    key !is String -> {
                        // Non-String key: skip the entry. The value was already safely consumed.
                    }
                    else -> {
                        val keyBytes = key.toByteArray(Charsets.UTF_8)
                        if (keyBytes.size > BackupFormatV2.MAX_KEY_BYTES) {
                            throw BackupRestore.BackupRestoreException("Legacy key too long: ${keyBytes.size}")
                        }
                        if (isSupportedValue(value)) {
                            map[key] = value
                        }
                    }
                }
            }

            // End of HashMap custom writeObject.
            val endTc = input.readUnsignedByte()
            if (endTc != TC_ENDBLOCKDATA) {
                throw BackupRestore.BackupRestoreException("Expected TC_ENDBLOCKDATA after HashMap entries, got $endTc")
            }

            return map
        }

        private fun readMapBlockHeader(): Pair<Int, Int> {
            val blockType = input.readUnsignedByte()
            val blockLen = if (blockType == TC_BLOCKDATA) {
                input.readUnsignedByte()
            } else if (blockType == TC_BLOCKDATALONG) {
                input.readInt()
            } else {
                throw BackupRestore.BackupRestoreException("Expected HashMap custom block data, got $blockType")
            }

            return when (blockLen) {
                4 -> {
                    // Legacy format: only size.
                    Pair(-1, input.readInt())
                }
                8 -> {
                    // Newer format: capacity, size.
                    Pair(input.readInt(), input.readInt())
                }
                12 -> {
                    // capacity, loadFactor, size — tolerate by skipping the float.
                    val capacity = input.readInt()
                    input.readFloat()
                    Pair(capacity, input.readInt())
                }
                else -> throw BackupRestore.BackupRestoreException("Unsupported HashMap custom block length: $blockLen")
            }
        }

        private fun readHashSet(classDesc: ClassDesc): Set<String>? {
            // HashSet has no default fields; custom writeObject block follows.
            val (capacity, size) = readSetBlockHeader()

            if (size < 0) {
                throw BackupRestore.BackupRestoreException("Negative HashSet size")
            }
            if (size > BackupFormatV2.MAX_SET_ITEMS) {
                throw BackupRestore.BackupRestoreException("HashSet size $size exceeds MAX_SET_ITEMS")
            }

            val effectiveCapacity = if (capacity >= 0) {
                capacity
            } else {
                Integer.highestOneBit((size / 0.75f).toInt()) * 2
            }
            if (effectiveCapacity > BackupRestore.LEGACY_MAX_ARRAY_LENGTH) {
                throw BackupRestore.BackupRestoreException("HashSet capacity $effectiveCapacity exceeds legacy array limit")
            }

            val set = HashSet<String>(size)
            var allStrings = true
            repeat(size) {
                val element = readContent()
                if (element is String) {
                    val elementBytes = element.toByteArray(Charsets.UTF_8)
                    if (elementBytes.size > BackupFormatV2.MAX_STRING_BYTES) {
                        throw BackupRestore.BackupRestoreException("HashSet item too long: ${elementBytes.size}")
                    }
                    set.add(element)
                } else {
                    allStrings = false
                }
            }

            // End of HashSet custom writeObject.
            val endTc = input.readUnsignedByte()
            if (endTc != TC_ENDBLOCKDATA) {
                throw BackupRestore.BackupRestoreException("Expected TC_ENDBLOCKDATA after HashSet entries, got $endTc")
            }

            return if (allStrings) set else null
        }

        private fun readSetBlockHeader(): Pair<Int, Int> {
            val blockType = input.readUnsignedByte()
            val blockLen = if (blockType == TC_BLOCKDATA) {
                input.readUnsignedByte()
            } else if (blockType == TC_BLOCKDATALONG) {
                input.readInt()
            } else {
                throw BackupRestore.BackupRestoreException("Expected HashSet custom block data, got $blockType")
            }

            return when (blockLen) {
                4 -> {
                    // Legacy format: only size.
                    Pair(-1, input.readInt())
                }
                12 -> {
                    // Newer format: capacity, loadFactor, size.
                    val capacity = input.readInt()
                    input.readFloat()
                    Pair(capacity, input.readInt())
                }
                8 -> {
                    // capacity, size (no loadFactor)
                    Pair(input.readInt(), input.readInt())
                }
                else -> throw BackupRestore.BackupRestoreException("Unsupported HashSet custom block length: $blockLen")
            }
        }

        private fun readBoolean(classDesc: ClassDesc): Boolean {
            consumeSuperClassData(classDesc.superClass)
            for (field in classDesc.fields) {
                if (field.name == "value" && field.type == 'Z') {
                    return input.readBoolean()
                }
                consumeField(field)
            }
            if (classDesc.flags and SC_WRITE_METHOD != 0) {
                consumeCustomData()
            }
            throw BackupRestore.BackupRestoreException("Malformed Boolean class data")
        }

        private fun readInteger(classDesc: ClassDesc): Int {
            consumeSuperClassData(classDesc.superClass)
            for (field in classDesc.fields) {
                if (field.name == "value" && field.type == 'I') {
                    return input.readInt()
                }
                consumeField(field)
            }
            if (classDesc.flags and SC_WRITE_METHOD != 0) {
                consumeCustomData()
            }
            throw BackupRestore.BackupRestoreException("Malformed Integer class data")
        }

        private fun readLong(classDesc: ClassDesc): Long {
            consumeSuperClassData(classDesc.superClass)
            for (field in classDesc.fields) {
                if (field.name == "value" && field.type == 'J') {
                    return input.readLong()
                }
                consumeField(field)
            }
            if (classDesc.flags and SC_WRITE_METHOD != 0) {
                consumeCustomData()
            }
            throw BackupRestore.BackupRestoreException("Malformed Long class data")
        }

        private fun readFloat(classDesc: ClassDesc): Float {
            consumeSuperClassData(classDesc.superClass)
            for (field in classDesc.fields) {
                if (field.name == "value" && field.type == 'F') {
                    return input.readFloat()
                }
                consumeField(field)
            }
            if (classDesc.flags and SC_WRITE_METHOD != 0) {
                consumeCustomData()
            }
            throw BackupRestore.BackupRestoreException("Malformed Float class data")
        }

        private fun consumeSuperClassData(classDesc: ClassDesc?) {
            if (classDesc == null) return
            consumeSuperClassData(classDesc.superClass)
            for (field in classDesc.fields) {
                consumeField(field)
            }
            if (classDesc.flags and SC_WRITE_METHOD != 0) {
                consumeCustomData()
            }
        }

        private fun consumeField(field: Field) {
            when (field.type) {
                'B' -> input.readByte()
                'C' -> input.readChar()
                'D' -> input.readDouble()
                'F' -> input.readFloat()
                'I' -> input.readInt()
                'J' -> input.readLong()
                'S' -> input.readShort()
                'Z' -> input.readBoolean()
                'L', '[' -> readContent()
                else -> throw BackupRestore.BackupRestoreException("Unknown legacy field type: ${field.type}")
            }
        }

        private fun consumeCustomData() {
            val tc = input.readUnsignedByte()
            if (tc == TC_BLOCKDATA) {
                val len = input.readUnsignedByte()
                skipBytes(len)
            } else if (tc == TC_BLOCKDATALONG) {
                val len = input.readInt()
                skipBytes(len)
            } else if (tc != TC_ENDBLOCKDATA) {
                throw BackupRestore.BackupRestoreException("Unexpected custom data start: $tc")
            }
            // TC_ENDBLOCKDATA means no custom data.
        }

        private fun skipBytes(count: Int) {
            var remaining = count
            while (remaining > 0) {
                val skipped = input.skipBytes(remaining)
                if (skipped <= 0) {
                    throw BackupRestore.BackupRestoreException("Could not skip $count bytes in legacy stream")
                }
                remaining -= skipped
            }
        }

        private fun isSupportedValue(value: Any?): Boolean {
            return when (value) {
                is Boolean, is Int, is Long, is Float, is String -> true
                is Set<*> -> value.all { it is String }
                else -> false
            }
        }

        private fun assignHandle(): Int {
            return BASE_WIRE_HANDLE + nextHandle++
        }

        private fun store(handle: Int, obj: Any?) {
            handles[handle] = obj
        }
    }
}
