package com.kitakkun.jetwhale.plugins.mirror.host

import okio.Buffer
import java.io.EOFException
import java.io.InputStream

internal const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
internal const val WIRE_LENGTH_DELIMITED = 2
internal const val WIRE_FIXED32 = 5

/** The key that starts protobuf field [fieldNumber] of [wireType]. */
internal fun fieldKey(fieldNumber: Int, wireType: Int): Long = (fieldNumber.toLong() shl 3) or wireType.toLong()

internal fun Buffer.writeVarint(value: Long) {
    var remaining = value
    while (remaining and 0x7fL.inv() != 0L) {
        writeByte(((remaining and 0x7f) or 0x80).toInt())
        remaining = remaining ushr 7
    }
    writeByte(remaining.toInt())
}

internal fun Buffer.writeVarintField(fieldNumber: Int, value: Long): Buffer = apply {
    writeVarint(fieldKey(fieldNumber, WIRE_VARINT))
    writeVarint(value)
}

internal fun Buffer.writeFloatField(fieldNumber: Int, value: Float): Buffer = apply {
    writeVarint(fieldKey(fieldNumber, WIRE_FIXED32))
    writeIntLe(value.toRawBits())
}

internal fun Buffer.writeMessageField(fieldNumber: Int, message: Buffer): Buffer = apply {
    writeVarint(fieldKey(fieldNumber, WIRE_LENGTH_DELIMITED))
    writeVarint(message.size)
    writeAll(message)
}

/** Reads at most [remainingBytes] bytes of [source], for one protobuf message or field. */
internal class BoundedReader(private val source: InputStream, private var remainingBytes: Int) : InputStream() {
    fun hasMore(): Boolean = remainingBytes > 0

    fun remaining(): Long = remainingBytes.toLong()

    override fun read(): Int {
        if (remainingBytes == 0) return -1
        remainingBytes--
        return source.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remainingBytes == 0) return -1
        val read = source.read(b, off, minOf(len, remainingBytes))
        if (read > 0) remainingBytes -= read
        return read
    }

    fun readVarint(): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val byte = readByteOrThrow()
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return value
            shift += 7
        }
    }

    /** The length that starts a length-delimited field, refused when it runs past what is left to read. */
    fun readLength(): Long {
        val length = readVarint()
        if (length > remaining()) throw deviceControlError("the emulator sent a message the mirror cannot parse")
        return length
    }

    fun readFloat(): Float = Float.fromBits(Buffer().write(ByteArray(4).also { readFully(it, it.size) }).readIntLe())

    fun readFully(target: ByteArray, length: Int) {
        var bytesRead = 0
        while (bytesRead < length) {
            val count = read(target, bytesRead, length - bytesRead)
            if (count < 0) throw EOFException("the emulator's message ended early")
            bytesRead += count
        }
    }

    fun skipField(key: Long) {
        when ((key and 0x7).toInt()) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> skipBytes(8)
            WIRE_LENGTH_DELIMITED -> skipBytes(readVarint().toInt())
            WIRE_FIXED32 -> skipBytes(4)
            else -> throw deviceControlError("the emulator sent a message the mirror cannot parse")
        }
    }

    private fun skipBytes(count: Int) {
        repeat(count) { readByteOrThrow() }
    }
}

internal fun InputStream.readByteOrThrow(): Int {
    val byte = read()
    if (byte == -1) throw EOFException("the emulator's message ended early")
    return byte
}
