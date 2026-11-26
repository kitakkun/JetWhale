package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.InputStream

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

/** An `ImageFormat` asking for the whole screen as a PNG. */
internal fun encodePngImageFormat(): ByteArray = Buffer().writeVarintField(field = 1, value = IMAGE_FORMAT_PNG).readByteArray()

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
        var pixelBytes = 0
        val size = readImageMessage(BoundedReader(input, readFixedInt())) { message, length ->
            if (length > MAX_FRAME_BYTES) throw deviceControlError("the emulator sent a frame of $length bytes, more than the mirror accepts")
            pixelBytes = length.toInt()
            if (pixels.size < pixelBytes) pixels = ByteArray(pixelBytes)
            message.readFully(pixels, pixelBytes)
        }
        val sizeInRange = size.width in 1..MAX_FRAME_SIDE && size.height in 1..MAX_FRAME_SIDE
        if (!sizeInRange || pixelBytes < size.width.toLong() * size.height * 4) throw deviceControlError("the emulator sent a frame of ${size.width}x${size.height} with $pixelBytes bytes of pixels")
        return EmulatorImage(size.width, size.height)
    }

    private fun readFixedInt(): Int = (0 until 4).fold(0) { value, _ -> (value shl 8) or input.readByteOrThrow() }
}

/** The picture in an `Image` message, as its format encodes it: its `image` field. */
internal fun parseImageBytes(message: ByteArray): ByteArray = BoundedReader(ByteArrayInputStream(message), message.size).use { reader ->
    var image: ByteArray? = null
    readImageMessage(reader) { field, length -> image = ByteArray(length.toInt()).also { field.readFully(it, it.size) } }
    image ?: throw deviceControlError("the emulator sent a screenshot without a picture")
}

/**
 * Reads one `Image` message from [message]: returns the frame size in its `format`, hands its
 * `image` field to [readImage] with the field's length, and skips the rest.
 */
private fun readImageMessage(message: BoundedReader, readImage: (message: BoundedReader, length: Long) -> Unit): IntSize {
    var size = IntSize.Zero
    while (message.hasMore()) {
        when (val key = message.readVarint()) {
            KEY_FORMAT -> size = message.readFrameSize()
            KEY_IMAGE -> readImage(message, message.readLength())
            else -> message.skipField(key)
        }
    }
    return size
}

/** The frame size inside an `Image.format` field: `ImageFormat.width` and `.height`. */
private fun BoundedReader.readFrameSize(): IntSize {
    val format = BoundedReader(this, readLength().toInt())
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

/** The longest side of a frame the mirror reads; an emulator asked for its shown size sends far less. */
private const val MAX_FRAME_SIDE = 4096

/** The largest frame the mirror reads: [MAX_FRAME_SIDE] squared, RGBA. */
private const val MAX_FRAME_BYTES = MAX_FRAME_SIDE.toLong() * MAX_FRAME_SIDE * 4

private const val IMAGE_FORMAT_PNG = 0L
private const val IMAGE_FORMAT_RGBA8888 = 1L

private val KEY_FORMAT = fieldKey(1, WIRE_LENGTH_DELIMITED)
private val KEY_IMAGE = fieldKey(4, WIRE_LENGTH_DELIMITED)
private val KEY_FORMAT_WIDTH = fieldKey(3, WIRE_VARINT)
private val KEY_FORMAT_HEIGHT = fieldKey(4, WIRE_VARINT)
