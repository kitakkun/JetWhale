package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.InputStream
import kotlin.math.floor

/** A process writing the device's screen to its stdout, and how to read what it writes. */
internal sealed interface VideoStream {
    val process: Process

    /** Raw H.264, decoded on the host. */
    class H264(override val process: Process) : VideoStream

    /** Uncompressed BGRA frames of [frameSize], back to back, with no header or row padding. */
    class RawBgra(override val process: Process, val frameSize: IntSize) : VideoStream
}

/**
 * The scale factor to ask `idb video-stream --format rbga` for, and the size of the frames it then
 * writes, for a screen of [screen] pixels shown at about [wanted].
 *
 * idb writes frames `floor(screen * scale)` pixels in size, with each row padded to a multiple of 64
 * bytes. The stream carries no header saying either, so the width is kept a multiple of 16 pixels,
 * which leaves no padding, and the scale is aimed at the middle of that pixel so rounding cannot
 * land on its neighbor.
 */
internal fun rawBgraLayout(screen: IntSize, wanted: IntSize?): RawBgraLayout {
    val width = maxOf(RAW_WIDTH_STEP, minOf(wanted?.width ?: screen.width, screen.width) / RAW_WIDTH_STEP * RAW_WIDTH_STEP)
    val scale = (width + 0.5) / screen.width
    return RawBgraLayout(scale = scale, frameSize = IntSize(width, floor(screen.height * scale).toInt()))
}

internal class RawBgraLayout(val scale: Double, val frameSize: IntSize)

private const val RAW_WIDTH_STEP = 16

/**
 * Copies BGRA frames of [frameSize] from [stream] into [surface] until the stream ends. Blocks the
 * calling thread, so run it off the UI. One buffer holds a frame between the pipe and the bitmap
 * for the whole stream.
 */
internal fun readRawBgraInto(surface: MirrorSurface, stream: InputStream, frameSize: IntSize, onFrame: () -> Unit) {
    val frame = ByteArray(frameSize.width * 4 * frameSize.height)
    val timedStream = WaitTimingInputStream(stream)
    while (timedStream.timingWork(surface::recordDecode) { timedStream.readNBytes(frame, 0, frame.size) } == frame.size) {
        surface.writeBgraFrame(frame, frameSize)
        onFrame()
    }
}

private fun MirrorSurface.writeBgraFrame(frame: ByteArray, frameSize: IntSize) {
    writeFrame(frameSize.width, frameSize.height) { target ->
        val pixmap = target.peekPixels() ?: return@writeFrame false
        copyRows(frame, sourceRowBytes = frameSize.width * 4, target = pixmap.addr, targetRowBytes = pixmap.rowBytes, height = frameSize.height)
        true
    }
}

/**
 * [source], counting the time its reads spend blocked waiting for the device, so the stats can tell
 * a still screen (long waits) from slow decoding.
 */
internal class WaitTimingInputStream(private val source: InputStream) : InputStream() {
    @Volatile
    var waitedNanos: Long = 0
        private set

    override fun read(): Int = timed { source.read() }

    override fun read(b: ByteArray, off: Int, len: Int): Int = timed { source.read(b, off, len) }

    /** Runs [read] and gives [record] the time it took apart from waiting for the device. */
    inline fun <T> timingWork(record: (Long) -> Unit, read: () -> T): T {
        val started = System.nanoTime()
        val waitedBefore = waitedNanos
        return read().also { record(System.nanoTime() - started - (waitedNanos - waitedBefore)) }
    }

    override fun available(): Int = source.available()

    override fun close() = source.close()

    private inline fun timed(read: () -> Int): Int {
        val started = System.nanoTime()
        try {
            return read()
        } finally {
            waitedNanos += System.nanoTime() - started
        }
    }
}
