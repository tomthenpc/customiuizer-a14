package tv.withaibuild.customiuizer.utils

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InvalidClassException
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
 * - super classes: `java.lang.Number`
 *
 * The decoder enforces the same bounds as V2:
 * - `MAX_FILE_SIZE`
 * - `MAX_ENTRY_COUNT`
 * - `MAX_KEY_BYTES`
 * - `MAX_STRING_BYTES`
 * - `MAX_SET_ITEMS`
 * plus legacy graph limits:
 * - `LEGACY_MAX_DEPTH` (nested object graph)
 * - `LEGACY_MAX_DESCRIPTOR_DEPTH` (superclass descriptor recursion)
 * - `LEGACY_MAX_REFERENCES` (allocated wire handles)
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

    private data class DescriptorSchema(
        val name: String,
        val suid: Long,
        val flags: Int,
        val fields: List<FieldSchema>,
        val superClassName: String?,
    )

    private data class FieldSchema(val type: Char, val name: String, val className: String?)

    // Exact historical class-descriptor schemas (host JDK 17 / API 34 evidence).
    // Values are frozen by ObjectStreamClass fixture; any deviation is rejected.
    private val HASHMAP_SCHEMA = DescriptorSchema(
        name = "java.util.HashMap",
        suid = 362498820763181265L,
        flags = SC_SERIALIZABLE or SC_WRITE_METHOD,
        fields = listOf(
            FieldSchema('F', "loadFactor", null),
            FieldSchema('I', "threshold", null),
        ),
        superClassName = null,
    )

    private val HASHSET_SCHEMA = DescriptorSchema(
        name = "java.util.HashSet",
        suid = -5024744406713321676L,
        flags = SC_SERIALIZABLE or SC_WRITE_METHOD,
        fields = emptyList(),
        superClassName = null,
    )

    private val BOOLEAN_SCHEMA = DescriptorSchema(
        name = "java.lang.Boolean",
        suid = -3665804199014368530L,
        flags = SC_SERIALIZABLE,
        fields = listOf(FieldSchema('Z', "value", null)),
        superClassName = null,
    )

    private val INTEGER_SCHEMA = DescriptorSchema(
        name = "java.lang.Integer",
        suid = 1360826667806852920L,
        flags = SC_SERIALIZABLE,
        fields = listOf(FieldSchema('I', "value", null)),
        superClassName = "java.lang.Number",
    )

    private val LONG_SCHEMA = DescriptorSchema(
        name = "java.lang.Long",
        suid = 4290774380558885855L,
        flags = SC_SERIALIZABLE,
        fields = listOf(FieldSchema('J', "value", null)),
        superClassName = "java.lang.Number",
    )

    private val FLOAT_SCHEMA = DescriptorSchema(
        name = "java.lang.Float",
        suid = -2671257302660747028L,
        flags = SC_SERIALIZABLE,
        fields = listOf(FieldSchema('F', "value", null)),
        superClassName = "java.lang.Number",
    )

    private val NUMBER_SCHEMA = DescriptorSchema(
        name = "java.lang.Number",
        suid = -8742448824652078965L,
        flags = SC_SERIALIZABLE,
        fields = emptyList(),
        superClassName = null,
    )

    private val DESCRIPTOR_ALLOWLIST: Map<String, DescriptorSchema> = mapOf(
        HASHMAP_SCHEMA.name to HASHMAP_SCHEMA,
        HASHSET_SCHEMA.name to HASHSET_SCHEMA,
        BOOLEAN_SCHEMA.name to BOOLEAN_SCHEMA,
        INTEGER_SCHEMA.name to INTEGER_SCHEMA,
        LONG_SCHEMA.name to LONG_SCHEMA,
        FLOAT_SCHEMA.name to FLOAT_SCHEMA,
        NUMBER_SCHEMA.name to NUMBER_SCHEMA,
    )

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
            if (state.hasTrailingBytes()) {
                throw BackupRestore.BackupRestoreException("Legacy backup has trailing bytes after root object")
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
        private var descriptorDepth = 0

        init {
            // Stream header (magic + version) already verified by decode(); consume it.
            input.skipBytes(4)
        }

        fun readRootObject(): Any? {
            return readContent()
        }

        fun hasTrailingBytes(): Boolean {
            return input.available() != 0
        }

        private fun readContent(): Any? {
            val tc = input.readUnsignedByte()

            return when (tc) {
                TC_NULL -> null
                TC_REFERENCE -> readReference()
                TC_OBJECT -> readNewObject()
                TC_STRING -> readNewString()
                TC_LONGSTRING -> throw BackupRestore.BackupRestoreException("TC_LONGSTRING not supported")
                TC_ARRAY -> throw BackupRestore.BackupRestoreException("TC_ARRAY not supported in legacy backups")
                TC_CLASS -> throw BackupRestore.BackupRestoreException("TC_CLASS not supported in legacy backups")
                TC_CLASSDESC -> throw BackupRestore.BackupRestoreException("Unexpected class descriptor in legacy content")
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
            if (!handles.containsKey(handle)) {
                throw BackupRestore.BackupRestoreException("Unresolvable legacy reference: $handle")
            }
            return handles[handle]
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
            if (descriptorDepth >= BackupRestore.LEGACY_MAX_DESCRIPTOR_DEPTH) {
                throw BackupRestore.BackupRestoreException("Legacy descriptor depth limit exceeded")
            }
            descriptorDepth++
            return try {
                val handle = assignHandle()
                val name = input.readUTF()

                // Fail-closed: reject unknown classes before any further parsing.
                val schema = DESCRIPTOR_ALLOWLIST[name]
                    ?: throw InvalidClassException(name, "Legacy class descriptor not in allowlist")

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
                validateClassDescriptor(classDesc, schema)
                store(handle, classDesc)
                classDesc
            } finally {
                descriptorDepth--
            }
        }

        private fun readNewObject(): Any? {
            if (depth >= BackupRestore.LEGACY_MAX_DEPTH) {
                throw BackupRestore.BackupRestoreException("Legacy object depth limit exceeded")
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
                "java.lang.Number" -> {
                    throw InvalidClassException(classDesc.name, "Unsupported legacy root object")
                }
                else -> throw InvalidClassException(classDesc.name, "Legacy class not in allowlist")
            }
        }

        private fun readHashMap(classDesc: ClassDesc): Map<String, Any?> {
            consumeSuperClassData(classDesc.superClass)

            // Default fields written by HashMap.defaultWriteObject(): loadFactor, threshold.
            for (field in classDesc.allFields) {
                when (field.name) {
                    "loadFactor" -> input.readFloat()
                    "threshold" -> input.readInt()
                    else -> consumeField(field)
                }
            }

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
            consumeSuperClassData(classDesc.superClass)

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
            return readPrimitiveWrapper(classDesc, 'Z') { input.readBoolean() } as Boolean
        }

        private fun readInteger(classDesc: ClassDesc): Int {
            return readPrimitiveWrapper(classDesc, 'I') { input.readInt() } as Int
        }

        private fun readLong(classDesc: ClassDesc): Long {
            return readPrimitiveWrapper(classDesc, 'J') { input.readLong() } as Long
        }

        private fun readFloat(classDesc: ClassDesc): Float {
            return readPrimitiveWrapper(classDesc, 'F') { input.readFloat() } as Float
        }

        private fun readPrimitiveWrapper(classDesc: ClassDesc, typeCode: Char, read: () -> Any): Any {
            consumeSuperClassData(classDesc.superClass)
            for (field in classDesc.allFields) {
                if (field.name == "value" && field.type == typeCode) {
                    return read()
                }
                consumeField(field)
            }
            if (classDesc.flags and SC_WRITE_METHOD != 0) {
                consumeCustomData()
            }
            throw BackupRestore.BackupRestoreException("Malformed ${classDesc.name} class data")
        }

        private fun consumeSuperClassData(classDesc: ClassDesc?) {
            if (classDesc == null) return
            consumeSuperClassData(classDesc.superClass)
            for (field in classDesc.allFields) {
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

        private fun validateClassDescriptor(classDesc: ClassDesc, schema: DescriptorSchema) {
            if (classDesc.name != schema.name) {
                throw InvalidClassException(classDesc.name, "Class descriptor name mismatch")
            }
            if (classDesc.suid != schema.suid) {
                throw InvalidClassException(classDesc.name, "SerialVersionUID mismatch for ${classDesc.name}")
            }
            if (classDesc.flags != schema.flags) {
                throw InvalidClassException(classDesc.name, "Class flags mismatch for ${classDesc.name}")
            }
            if (classDesc.fields.size != schema.fields.size) {
                throw InvalidClassException(classDesc.name, "Field count mismatch for ${classDesc.name}")
            }
            for (i in classDesc.fields.indices) {
                val actual = classDesc.fields[i]
                val expected = schema.fields[i]
                if (actual.type != expected.type ||
                    actual.name != expected.name ||
                    actual.className != expected.className
                ) {
                    throw InvalidClassException(classDesc.name, "Field mismatch for ${classDesc.name}: ${actual.name}")
                }
            }

            val expectedSuper = schema.superClassName
            if (expectedSuper == null) {
                if (classDesc.superClass != null) {
                    throw InvalidClassException(classDesc.name, "Unexpected superclass for ${classDesc.name}")
                }
            } else {
                val superClass = classDesc.superClass
                    ?: throw InvalidClassException(classDesc.name, "Missing expected superclass $expectedSuper for ${classDesc.name}")
                if (superClass.name != expectedSuper) {
                    throw InvalidClassException(classDesc.name, "Unexpected superclass ${superClass.name} for ${classDesc.name}")
                }
                val superSchema = DESCRIPTOR_ALLOWLIST[superClass.name]
                    ?: throw InvalidClassException(superClass.name, "Superclass not in descriptor allowlist")
                validateClassDescriptor(superClass, superSchema)
            }
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
            if (nextHandle >= BackupRestore.LEGACY_MAX_REFERENCES) {
                throw BackupRestore.BackupRestoreException("Legacy handle limit exceeded")
            }
            return BASE_WIRE_HANDLE + nextHandle++
        }

        private fun store(handle: Int, obj: Any?) {
            handles[handle] = obj
        }
    }
}
