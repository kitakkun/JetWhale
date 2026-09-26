package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.jetbrains.skia.ColorType
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * An `ImageFormat` message asking for RGBA8888 frames about [wanted] in size; the emulator keeps
 * the screen's aspect ratio and fits the frame inside it. Without a size it sends the full screen.
 */
internal fun encodeImageFormat(wanted: IntSize?): ByteArray = Buffer().apply {
    writeVarintField(field = 1, value = IMAGE_FORMAT_RGBA8888)
    wanted?.let {
        writeVarintField(field = 3, value = it.width.toLong())
        writeVarintField(field = 4, value = it.height.toLong())
    }
}.readByteArray()

/** [message] with gRPC's five-byte prefix: not compressed, then its length. */
internal fun grpcMessage(message: ByteArray): ByteArray = Buffer().writeByte(0).writeInt(message.size).write(message).readByteArray()

/** The size of one frame an [EmulatorImageReader] read; its pixels are in [EmulatorImageReader.pixels]. */
internal class EmulatorImage(val width: Int, val height: Int)

/**
 * Parses the length-prefixed `Image` messages of a gRPC response. Only the fields the mirror needs
 * are read: the frame's size inside its `ImageFormat`, and the pixels; the rest is skipped.
 */
internal class EmulatorImageReader(private val input: InputStream) {
    /** The pixels of the frame [next] returned last, RGBA, rows without padding. */
    var pixels: ByteArray = ByteArray(0)
        private set

    /** The next frame, or null when the stream ended between messages. */
    fun next(): EmulatorImage? {
        val compressed = input.read()
        if (compressed == -1) return null
        if (compressed != 0) throw deviceControlError("the emulator sent a compressed frame, which the mirror cannot read")
        val message = BoundedReader(input, readFixedInt())
        var size = IntSize.Zero
        var pixelBytes = 0
        while (message.hasMore()) {
            when (val key = message.readVarint()) {
                KEY_FORMAT -> size = message.readFrameSize()
                KEY_IMAGE -> pixelBytes = readPixels(message)
                else -> message.skipField(key)
            }
        }
        val sizeInRange = size.width in 1..MAX_FRAME_SIDE && size.height in 1..MAX_FRAME_SIDE
        if (!sizeInRange || pixelBytes < size.width.toLong() * size.height * 4) throw deviceControlError("the emulator sent a frame of ${size.width}x${size.height} with $pixelBytes bytes of pixels")
        return EmulatorImage(size.width, size.height)
    }

    /** Reads an `Image.image` field's pixels into [pixels]; returns how many bytes they took. */
    private fun readPixels(message: BoundedReader): Int {
        val length = message.readVarint()
        // Checked before anything is allocated: a garbled length must not ask for gigabytes.
        if (length > message.remaining() || length > MAX_FRAME_BYTES) throw deviceControlError("the emulator sent a frame of $length bytes, more than the mirror accepts")
        val bytes = length.toInt()
        if (pixels.size < bytes) pixels = ByteArray(bytes)
        message.readFully(pixels, bytes)
        return bytes
    }

    private fun readFixedInt(): Int = (0 until 4).fold(0) { value, _ -> (value shl 8) or input.readByteOrThrow() }
}

/** Reads at most [limit] bytes of [source], for one protobuf message or field. */
private class BoundedReader(private val source: InputStream, private var limit: Int) : InputStream() {
    fun hasMore(): Boolean = limit > 0

    fun remaining(): Long = limit.toLong()

    override fun read(): Int {
        if (limit == 0) return -1
        limit--
        return source.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (limit == 0) return -1
        val read = source.read(b, off, minOf(len, limit))
        if (read > 0) limit -= read
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

    fun readFully(target: ByteArray, length: Int) {
        var read = 0
        while (read < length) {
            val count = read(target, read, length - read)
            if (count < 0) throw EOFException("the emulator's frame ended early")
            read += count
        }
    }

    fun skipField(key: Long) {
        when ((key and 0x7).toInt()) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> skipBytes(8)
            WIRE_LENGTH_DELIMITED -> skipBytes(readVarint().toInt())
            WIRE_FIXED32 -> skipBytes(4)
            else -> throw deviceControlError("the emulator sent a frame the mirror cannot parse")
        }
    }

    private fun skipBytes(count: Int) {
        repeat(count) { readByteOrThrow() }
    }
}

/** The frame size inside an `Image.format` field: `ImageFormat.width` and `.height`. */
private fun BoundedReader.readFrameSize(): IntSize {
    val length = readVarint()
    if (length > remaining()) throw deviceControlError("the emulator sent a frame the mirror cannot parse")
    val format = BoundedReader(this, length.toInt())
    var width = 0
    var height = 0
    while (format.hasMore()) {
        when (val key = format.readVarint()) {
            KEY_FORMAT_WIDTH -> width = format.readVarint().toInt()
            KEY_FORMAT_HEIGHT -> height = format.readVarint().toInt()
            else -> format.skipField(key)
        }
    }
    return IntSize(width, height)
}

private fun InputStream.readByteOrThrow(): Int {
    val byte = read()
    if (byte == -1) throw EOFException("the emulator's frame ended early")
    return byte
}

private fun Buffer.writeVarintField(field: Int, value: Long) {
    writeVarint((field shl 3).toLong() or WIRE_VARINT.toLong())
    writeVarint(value)
}

private fun Buffer.writeVarint(value: Long) {
    var remaining = value
    while (remaining and 0x7fL.inv() != 0L) {
        writeByte(((remaining and 0x7f) or 0x80).toInt())
        remaining = remaining ushr 7
    }
    writeByte(remaining.toInt())
}

/** The longest side of a frame the mirror reads; an emulator asked for its shown size sends far less. */
private const val MAX_FRAME_SIDE = 4096

/** The largest frame the mirror reads: [MAX_FRAME_SIDE] squared, RGBA. */
private const val MAX_FRAME_BYTES = MAX_FRAME_SIDE.toLong() * MAX_FRAME_SIDE * 4

private const val IMAGE_FORMAT_RGBA8888 = 1L

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5

// Image.format (1, a message), Image.image (4, bytes); ImageFormat.width (3) and .height (4).
private const val KEY_FORMAT = (1L shl 3) or WIRE_LENGTH_DELIMITED.toLong()
private const val KEY_IMAGE = (4L shl 3) or WIRE_LENGTH_DELIMITED.toLong()
private const val KEY_FORMAT_WIDTH = (3L shl 3) or WIRE_VARINT.toLong()
private const val KEY_FORMAT_HEIGHT = (4L shl 3) or WIRE_VARINT.toLong()
