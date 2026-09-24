package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry

/** The file name suffix Jetpack DataStore gives a Preferences DataStore file. */
internal const val PREFERENCES_DATASTORE_SUFFIX = ".preferences_pb"

/**
 * Decodes a Preferences DataStore file into its entries, sorted by key.
 *
 * The schema is androidx.datastore's `preferences.proto`: a `PreferenceMap` whose field 1 is a
 * `map<string, Value>`, and a `Value` that is a oneof of boolean (1), float (2), integer (3),
 * long (4), string (5), string_set (6), double (7) and bytes (8).
 *
 * @throws IllegalArgumentException when [bytes] is not a well-formed `PreferenceMap`.
 */
internal fun decodePreferencesDataStore(bytes: ByteArray): List<KeyValueEntry> {
    val entries = mutableListOf<KeyValueEntry>()
    ProtoReader(bytes).forEachField { field, reader ->
        if (field == 1) entries += decodeMapEntry(reader.lengthDelimited())
    }
    return entries.sortedBy(KeyValueEntry::key)
}

private fun decodeMapEntry(reader: ProtoReader): KeyValueEntry {
    var key: String? = null
    var value: Pair<String, String>? = null
    reader.forEachField { field, fieldReader ->
        when (field) {
            1 -> key = fieldReader.lengthDelimited().remainingString()
            2 -> value = decodeValue(fieldReader.lengthDelimited())
        }
    }
    val (type, text) = requireNotNull(value) { "the entry '${key.orEmpty()}' has no value" }
    return KeyValueEntry(key = requireNotNull(key) { "a map entry has no key" }, value = text, type = type)
}

/** The value's type and its text, as a [KeyValueEntry] carries them. */
private fun decodeValue(reader: ProtoReader): Pair<String, String>? {
    var decoded: Pair<String, String>? = null
    reader.forEachField { field, fieldReader ->
        decoded = when (field) {
            1 -> "Boolean" to (fieldReader.varint() != 0L).toString()
            2 -> "Float" to Float.fromBits(fieldReader.fixed32()).toString()
            3 -> "Int" to fieldReader.varint().toInt().toString()
            4 -> "Long" to fieldReader.varint().toString()
            5 -> "String" to fieldReader.lengthDelimited().remainingString()
            6 -> "Set<String>" to decodeStringSet(fieldReader.lengthDelimited()).joinToString()
            7 -> "Double" to Double.fromBits(fieldReader.fixed64()).toString()
            8 -> "ByteArray" to fieldReader.lengthDelimited().remainingBytes().toHexString()
            else -> decoded
        }
    }
    return decoded
}

private fun decodeStringSet(reader: ProtoReader): List<String> {
    val strings = mutableListOf<String>()
    reader.forEachField { field, fieldReader ->
        if (field == 1) strings += fieldReader.lengthDelimited().remainingString()
    }
    return strings
}

/** Reads protobuf wire format from [bytes] between [start] and [end]. */
private class ProtoReader(
    private val bytes: ByteArray,
    private var position: Int = 0,
    private val end: Int = bytes.size,
) {
    private var pendingWireType = -1

    /**
     * Calls [onField] for each field in turn. [onField] may read the field's value with the reader
     * it is given; a value it leaves unread is skipped.
     */
    fun forEachField(onField: (field: Int, reader: ProtoReader) -> Unit) {
        while (position < end) {
            val tag = varintAt()
            pendingWireType = (tag and 0x7).toInt()
            val before = position
            onField((tag ushr 3).toInt(), this)
            if (position == before) skip()
            pendingWireType = -1
        }
    }

    fun varint(): Long {
        expect(WIRE_VARINT)
        return varintAt()
    }

    fun fixed32(): Int {
        expect(WIRE_FIXED32)
        return (0 until 4).fold(0) { acc, i -> acc or ((nextByte().toInt() and 0xFF) shl (8 * i)) }
    }

    fun fixed64(): Long {
        expect(WIRE_FIXED64)
        return (0 until 8).fold(0L) { acc, i -> acc or ((nextByte().toLong() and 0xFF) shl (8 * i)) }
    }

    fun lengthDelimited(): ProtoReader {
        expect(WIRE_LENGTH_DELIMITED)
        val length = varintAt().toInt()
        require(length >= 0 && position + length <= end) { "a length-delimited field runs past the end of its message" }
        return ProtoReader(bytes, position, position + length).also { position += length }
    }

    fun remainingBytes(): ByteArray = bytes.copyOfRange(position, end).also { position = end }

    fun remainingString(): String = remainingBytes().decodeToString()

    private fun skip() {
        when (pendingWireType) {
            WIRE_VARINT -> varintAt()
            WIRE_FIXED64 -> advance(8)
            WIRE_LENGTH_DELIMITED -> advance(varintAt().toInt())
            WIRE_FIXED32 -> advance(4)
            else -> throw IllegalArgumentException("wire type $pendingWireType is not supported")
        }
    }

    private fun advance(count: Int) {
        require(count >= 0 && position + count <= end) { "a field runs past the end of its message" }
        position += count
    }

    private fun expect(wireType: Int) {
        require(pendingWireType == wireType) { "expected wire type $wireType, found $pendingWireType" }
    }

    private fun varintAt(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = nextByte().toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            require(shift < 64) { "a varint is longer than 10 bytes" }
        }
    }

    private fun nextByte(): Byte {
        require(position < end) { "the message ends in the middle of a field" }
        return bytes[position++]
    }
}

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5
