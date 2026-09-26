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

/** Where a running emulator's gRPC endpoint listens, and the token it accepts. */
internal class EmulatorEndpoint(val port: Int, val token: String?)

/**
 * The emulator's own screen stream: the gRPC `streamScreenshot` call Android Studio's Running
 * Devices uses. It sends a frame as soon as the screen changes, already scaled to the size asked
 * for, as raw pixels, so there is no encoder latency, no decoding, and no frame held back until the
 * next change, which screenrecord's H.264 has.
 */
internal class EmulatorScreens(private val runningDirectories: List<File>) {
    // One client for every emulator: gRPC runs over one HTTP/2 connection per endpoint. A stream
    // stays open for as long as it is watched, so reads never time out.
    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Opens the screen stream of the emulator with [serial] (e.g. `emulator-5554`), frames about
     * [wanted] in size, or null when that emulator has no gRPC endpoint this host can reach.
     */
    fun open(serial: String, wanted: IntSize?): VideoStream.EmulatorRgba? {
        val endpoint = findEmulatorEndpoint(serial, runningDirectories) ?: return null
        val request = Request.Builder()
            .url("http://127.0.0.1:${endpoint.port}/android.emulation.control.EmulatorController/streamScreenshot")
            .header("te", "trailers")
            .apply { endpoint.token?.let { header("authorization", "Bearer $it") } }
            .post(grpcMessage(encodeImageFormat(wanted)).toRequestBody(GRPC_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (_: IOException) {
            return null
        }
        // A refused call answers with only headers, its status among them.
        val refused = response.code != 200 || response.header("grpc-status")?.let { it != "0" } == true
        if (refused) {
            response.close()
            return null
        }
        return VideoStream.EmulatorRgba(frames = response.body.byteStream(), cancel = call::cancel)
    }
}

/**
 * The directories where running emulators leave a `pid_<n>.ini` naming their ports and gRPC token,
 * per OS. [home] and [temp] are the user's home and temporary directories; [runtimeDir] is
 * `XDG_RUNTIME_DIR` on Linux.
 */
internal fun emulatorRunningDirectories(osName: String, home: File, temp: File, runtimeDir: String?): List<File> = when {
    osName.startsWith("Mac") -> listOf(File(home, "Library/Caches/TemporaryItems/avd/running"))
    osName.startsWith("Windows") -> listOf(File(temp, "avd/running"))
    else -> listOfNotNull(runtimeDir?.let { File(it, "avd/running") }, File(temp, "android-${home.name}/avd/running"))
}

/** The gRPC endpoint of the emulator with [serial], from the files in [directories], or null. */
internal fun findEmulatorEndpoint(serial: String, directories: List<File>): EmulatorEndpoint? {
    val consolePort = serial.removePrefix("emulator-").toIntOrNull() ?: return null
    return directories.asSequence()
        .flatMap { it.listFiles { file -> file.name.startsWith("pid_") && file.name.endsWith(".ini") }.orEmpty().asSequence() }
        // An emulator that is shutting down removes its file between the listing and the read.
        .mapNotNull { file -> file.readTextOrNull()?.let(::parseEmulatorDiscovery) }
        .firstOrNull { it.first == consolePort }
        ?.second
}

private fun File.readTextOrNull(): String? = try {
    readText()
} catch (_: IOException) {
    null
}

/** The console port and gRPC endpoint in the text of an emulator's discovery file, or null without a gRPC port. */
internal fun parseEmulatorDiscovery(text: String): Pair<Int, EmulatorEndpoint>? {
    val values = text.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
    val consolePort = values["port.serial"]?.toIntOrNull() ?: return null
    val grpcPort = values["grpc.port"]?.toIntOrNull() ?: return null
    return consolePort to EmulatorEndpoint(port = grpcPort, token = values["grpc.token"]?.takeIf(String::isNotEmpty))
}

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

/**
 * Reads the `Image` messages of a `streamScreenshot` response from [frames] into [surface] until the
 * stream ends. The pixels of each frame are read straight into one buffer kept for the whole
 * stream; it grows only when a frame is larger than any before it.
 */
internal fun readEmulatorFramesInto(surface: MirrorSurface, frames: InputStream, onFrame: () -> Unit) {
    val timed = WaitTimingInputStream(frames)
    val reader = EmulatorImageReader(timed)
    try {
        while (true) {
            val image = timed.timingWork(surface::recordDecode, reader::next) ?: return
            surface.writeRgbaFrame(reader.pixels, image)
            onFrame()
        }
    } catch (_: IOException) {
        // A call cut off mid-frame, or cancelled because the mirror closed it, ends the stream like
        // any other end: the mirror opens the next one.
    }
}

private fun MirrorSurface.writeRgbaFrame(pixels: ByteArray, image: EmulatorImage) {
    writeFrame(image.width, image.height, ColorType.RGBA_8888) { target ->
        val pixmap = target.peekPixels() ?: return@writeFrame false
        copyRows(pixels, sourceRowBytes = image.width * 4, target = pixmap.addr, targetRowBytes = pixmap.rowBytes, height = image.height)
        true
    }
}

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
        if (size.width <= 0 || size.height <= 0 || pixelBytes < size.width.toLong() * size.height * 4) throw deviceControlError("the emulator sent a frame of ${size.width}x${size.height} with $pixelBytes bytes of pixels")
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

private val GRPC_MEDIA_TYPE = "application/grpc".toMediaType()

/** The largest frame the mirror reads, 4096 by 4096 RGBA; an emulator asked for its shown size sends far less. */
private const val MAX_FRAME_BYTES = 4096L * 4096 * 4

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
