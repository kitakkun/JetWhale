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
        pixmap.writeRows(pixels, sourceRowBytes = image.width * 4, height = image.height)
        true
    }
}

private val GRPC_MEDIA_TYPE = "application/grpc".toMediaType()
