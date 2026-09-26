package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.swscale
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Decodes the raw H.264 that adb and idb write, frame by frame, into [surface]. [onFrame] is
 * called after each frame so the caller can watch the stream's health. Returns when the stream
 * ends; blocks the calling thread, so run it off the UI.
 *
 * ffmpeg decodes in software here: javacv's grabber has no hook for a hardware device context,
 * and the measured decode time of a phone-sized frame stays in single milliseconds.
 */
internal fun decodeH264Into(surface: MirrorSurface, stream: InputStream, outputSize: IntSize?, onFrame: () -> Unit) {
    val timedStream = WaitTimingInputStream(stream)
    val grabber = FFmpegFrameGrabber(timedStream, 0)
    // Shrinking here, with area averaging, keeps text crisp on every renderer and leaves a
    // fraction of the pixels to copy and upload; drawing a full-size frame small would sample it.
    outputSize?.let {
        grabber.imageWidth = it.width
        grabber.imageHeight = it.height
        grabber.imageScalingFlags = swscale.SWS_AREA
    }
    grabber.format = "h264"
    // ffmpeg's defaults probe for seconds before the first frame, which a live mirror shows as
    // lag. A raw H.264 stream needs almost no probing. `fflags=nobuffer` is left out on purpose:
    // with it, adb's screenrecord stream decodes to nothing at all.
    grabber.setOption("flags", "low_delay")
    grabber.setOption("probesize", "65536")
    grabber.setOption("analyzeduration", "500000")
    // BGRA is Skia's native layout on a little-endian host, so a frame is copied, never converted.
    grabber.pixelFormat = avutil.AV_PIX_FMT_BGRA
    try {
        grabber.start()
        while (true) {
            val frame = timedStream.timingWork(surface::recordDecode, grabber::grabImage) ?: break
            if (surface.writeFrame(frame)) onFrame()
        }
    } catch (e: FrameGrabber.Exception) {
        throw DeviceControlException("the video stream could not be decoded: ${e.message}", e)
    } finally {
        grabber.close()
    }
}

/** Copies [frame]'s BGRA pixels into [this] surface; false when the frame carried none. */
private fun MirrorSurface.writeFrame(frame: Frame): Boolean {
    val pixels = frame.image?.firstOrNull() as? ByteBuffer ?: return false
    writeFrame(frame.imageWidth, frame.imageHeight) { target ->
        val pixmap = target.peekPixels() ?: return@writeFrame false
        copyRows(pixels, sourceRowBytes = frame.imageStride, target = pixmap.addr, targetRowBytes = pixmap.rowBytes, height = frame.imageHeight)
        true
    }
    return true
}

private fun copyRows(pixels: ByteBuffer, sourceRowBytes: Int, target: Long, targetRowBytes: Int, height: Int) {
    val source = BytePointer(pixels)
    val destination = NativeAddress(target)
    if (sourceRowBytes == targetRowBytes) {
        Pointer.memcpy(destination, source, sourceRowBytes.toLong() * height)
        return
    }
    val rowBytes = minOf(sourceRowBytes, targetRowBytes).toLong()
    for (row in 0 until height) {
        Pointer.memcpy(destination.position(row.toLong() * targetRowBytes), source.position(row.toLong() * sourceRowBytes), rowBytes)
    }
}

/** Copies [pixels], rows of [sourceRowBytes], into the native memory at [target], rows of [targetRowBytes]. */
internal fun copyRows(pixels: ByteArray, sourceRowBytes: Int, target: Long, targetRowBytes: Int, height: Int) {
    val destination = NativeAddress(target)
    if (sourceRowBytes == targetRowBytes) {
        destination.put(pixels, 0, sourceRowBytes * height)
        return
    }
    val rowBytes = minOf(sourceRowBytes, targetRowBytes)
    for (row in 0 until height) {
        destination.position(row.toLong() * targetRowBytes).put(pixels, row * sourceRowBytes, rowBytes)
    }
}

/** A JavaCPP view of memory Skia owns, so a frame can be copied into it directly. */
private class NativeAddress(address: Long) : BytePointer() {
    init {
        this.address = address
    }
}
