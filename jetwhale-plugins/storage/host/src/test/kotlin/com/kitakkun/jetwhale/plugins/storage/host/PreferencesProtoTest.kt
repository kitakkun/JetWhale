package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreferencesProtoTest {
    @Test
    fun `every value type of preferences proto decodes with its type name`() {
        val file = preferenceMap(
            "darkMode" to value(1) { varint(1) },
            "scale" to value(2) { fixed32(1.5f.toRawBits()) },
            "launches" to value(3) { varint(42) },
            "installedAt" to value(4) { varint(1_700_000_000_000) },
            "userName" to value(5) { string("kitakkun") },
            "tags" to value(6) {
                lengthDelimited {
                    string(1, "a")
                    string(1, "b")
                }
            },
            "ratio" to value(7) { fixed64(0.25.toRawBits()) },
            "token" to value(8) { bytes(byteArrayOf(0x0A, 0xFF.toByte())) },
        )

        assertEquals(
            listOf(
                KeyValueEntry(key = "darkMode", value = "true", type = "Boolean"),
                KeyValueEntry(key = "installedAt", value = "1700000000000", type = "Long"),
                KeyValueEntry(key = "launches", value = "42", type = "Int"),
                KeyValueEntry(key = "ratio", value = "0.25", type = "Double"),
                KeyValueEntry(key = "scale", value = "1.5", type = "Float"),
                KeyValueEntry(key = "tags", value = "a, b", type = "Set<String>"),
                KeyValueEntry(key = "token", value = "0aff", type = "ByteArray"),
                KeyValueEntry(key = "userName", value = "kitakkun", type = "String"),
            ),
            decodePreferencesDataStore(file),
        )
    }

    @Test
    fun `an empty file is an empty store`() {
        assertEquals(emptyList(), decodePreferencesDataStore(ByteArray(0)))
    }

    @Test
    fun `a file cut in the middle of an entry is rejected`() {
        val file = preferenceMap("userName" to value(5) { string("kitakkun") })

        assertFailsWith<IllegalArgumentException> { decodePreferencesDataStore(file.copyOf(file.size - 3)) }
    }
}

/** Writes protobuf wire format, just enough of it to build Preferences DataStore files. */
private class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(value: Long) {
        var remaining = value
        while (remaining and 0x7FL.inv() != 0L) {
            out.write(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        out.write(remaining.toInt())
    }

    fun tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

    fun fixed32(bits: Int) = (0 until 4).forEach { out.write((bits ushr (8 * it)) and 0xFF) }

    fun fixed64(bits: Long) = (0 until 8).forEach { out.write(((bits ushr (8 * it)) and 0xFF).toInt()) }

    fun bytes(value: ByteArray) {
        varint(value.size.toLong())
        out.write(value)
    }

    fun string(value: String) = bytes(value.encodeToByteArray())

    fun string(field: Int, value: String) {
        tag(field, 2)
        string(value)
    }

    fun lengthDelimited(build: ProtoWriter.() -> Unit) = bytes(ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** A `Value` whose oneof is [field]; [write] writes the field's payload after its tag. */
private fun value(field: Int, write: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply {
    val wireType = when (field) {
        1, 3, 4 -> 0
        2 -> 5
        7 -> 1
        else -> 2
    }
    tag(field, wireType)
    write()
}.toByteArray()

private fun preferenceMap(vararg entries: Pair<String, ByteArray>): ByteArray = ProtoWriter().apply {
    entries.forEach { (key, value) ->
        tag(1, 2)
        lengthDelimited {
            string(1, key)
            tag(2, 2)
            bytes(value)
        }
    }
}.toByteArray()
