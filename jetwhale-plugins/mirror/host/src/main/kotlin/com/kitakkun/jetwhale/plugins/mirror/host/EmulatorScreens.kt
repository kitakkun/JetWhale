package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.skia.ColorType
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * The emulator's own screen stream: the gRPC `streamScreenshot` call Android Studio's Running
 * Devices uses. It sends a frame as soon as the screen changes, already scaled to the size asked
 * for, as raw pixels, so there is no encoder latency, no decoding, and no frame held back until the
 * next change, which screenrecord's H.264 has.
 */
internal class EmulatorScreens(private val grpcEndpoints: EmulatorGrpcEndpoints) {
    // A stream stays open for as long as it is watched, so reads never time out.
    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Opens the screen stream of the emulator with [serial] (e.g. `emulator-5554`), frames about
     * [wanted] in size, or null when that emulator has no gRPC endpoint this host can reach.
     */
    fun open(serial: String, wanted: IntSize?): VideoStream.EmulatorRgba? {
        val endpoint = grpcEndpoints.findEndpoint(serial) ?: return null
        val call = client.newCall(grpcEndpoints.buildRequest(endpoint, "streamScreenshot", grpcMessage(encodeImageFormat(wanted)).toRequestBody(GRPC_MEDIA_TYPE)))
        val response = try {
            call.execute()
        } catch (_: IOException) {
            return null
        }
        if (grpcEndpoints.refusalInHeaders(response) != null) {
            response.close()
            return null
        }
        return VideoStream.EmulatorRgba(frames = response.body.byteStream(), cancel = call::cancel)
    }
}

/**
 * Reads the `Image` messages of a `streamScreenshot` response from [frames] into [target] until the
 * stream ends. The pixels of each frame are read straight into one buffer kept for the whole
 * stream; it grows only when a frame is larger than any before it.
 */
internal fun readEmulatorFramesInto(target: MirrorSurface.FrameStream, frames: InputStream, onFrame: () -> Unit) {
    val timed = WaitTimingInputStream(frames)
    val reader = EmulatorImageReader(timed)
    try {
        while (true) {
            val image = timed.timingWork(target::recordDecode, reader::next) ?: return
            target.writeRgbaFrame(reader.pixels, image)
            onFrame()
        }
    } catch (_: IOException) {
        // A call cut off mid-frame, or cancelled because the mirror closed it, ends the stream like
        // any other end: the mirror opens the next one.
    }
}

private fun MirrorSurface.FrameStream.writeRgbaFrame(pixels: ByteArray, image: EmulatorImage) {
    writeFrame(image.width, image.height, ColorType.RGBA_8888) { target ->
        val pixmap = target.peekPixels() ?: return@writeFrame false
        pixmap.writeRows(pixels, sourceRowBytes = image.width * 4, height = image.height)
        true
    }
}
