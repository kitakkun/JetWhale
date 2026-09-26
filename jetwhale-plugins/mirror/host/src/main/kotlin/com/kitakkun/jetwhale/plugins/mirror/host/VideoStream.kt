package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.InputStream
import kotlin.math.floor

/** A process writing the device's screen to its stdout, and how to read what it writes. */
internal sealed interface VideoStream {
    val process: Process

    /** Raw H.264, decoded on the host. */
    class H264(override val process: Process) : VideoStream

    /**
     * Uncompressed BGRA frames of [frameSize], back to back with no header, each row [rowBytes]
     * long, asked for at [fps]. The source cannot drop frames it has not sent yet, so a reader that
     * sees them arrive slower than [fps] reports the rate it got to [onFellBehind]. When that asks
     * for a lighter stream (returns true) the reader ends this one, and the next starts from the
     * device's current screen; when nothing is left to lower (false) it keeps reading.
     */
    class RawBgra(
        override val process: Process,
        val frameSize: IntSize,
        val rowBytes: Int,
        val fps: Int,
        val onFellBehind: (arrivedFps: Int) -> Boolean,
    ) : VideoStream
}

/**
 * How to ask `idb video-stream --format rbga` for frames of a screen of [screen] pixels shown at
 * [wanted] pixels, and what it then writes.
 *
 * idb writes frames `floor(screen * scale)` pixels in size with each row padded to a multiple of
 * 64 bytes, and says neither in the stream, so the scale is aimed a quarter pixel past the wanted
 * width, where rounding cannot land on a neighbor. The frames are the size they are shown, since
 * drawing a frame even slightly larger than it is blurs its text.
 *
 * A scale of exactly 1 is never asked for: idb then passes on the simulator's own buffer, whose
 * size is rounded up to a whole memory page, so every frame carries a few kilobytes more than its
 * rows, and a reader counting rows drifts through the stream. One pixel narrower, the scaler writes
 * frames that are exactly their rows.
 *
 * idb's client passes on about 100 MB a second, shared by every stream of the simulator; a stream
 * asking for more falls further behind with every frame. The frame rate is what fits
 * [RAW_BYTES_PER_SECOND] at that size, and no more than [maxFps]; the width is no more than
 * [maxWidth]. A stream that fell behind lowers both, see [lighterThan].
 */
internal fun rawBgraLayout(screen: IntSize, wanted: IntSize?, maxFps: Int, maxWidth: Int): RawBgraLayout {
    // One pixel narrower than the screen at most: see above.
    val widest = minOf(screen.width - 1, maxWidth)
    val width = minOf(wanted?.width ?: screen.width, widest).coerceAtLeast(1)
    val scale = (width + 0.25) / screen.width
    val height = floor(screen.height * scale).toInt()
    val rowBytes = (width * 4 + RAW_ROW_ALIGNMENT - 1) / RAW_ROW_ALIGNMENT * RAW_ROW_ALIGNMENT
    val fps = (RAW_BYTES_PER_SECOND / (rowBytes.toLong() * height)).toInt().coerceIn(MIN_RAW_FPS, minOf(MAX_RAW_FPS, maxFps))
    return RawBgraLayout(scale = scale, frameSize = IntSize(width, height), rowBytes = rowBytes, fps = fps)
}

internal class RawBgraLayout(val scale: Double, val frameSize: IntSize, val rowBytes: Int, val fps: Int)

/** The caps for the next stream after one laid out as [layout] fell behind. */
internal data class RawBgraCaps(val maxFps: Int, val maxWidth: Int)

/**
 * Caps for a lighter stream than [layout], whose frames arrived at [arrivedFps]: first a lower
 * rate, and once the rate is at [MIN_RAW_FPS], narrower frames. Null when both are at their floor,
 * where reopening would only bring the same stream back.
 */
internal fun lighterThan(layout: RawBgraLayout, arrivedFps: Int): RawBgraCaps? {
    val fps = maxOf(MIN_RAW_FPS, arrivedFps * 3 / 4)
    if (fps < layout.fps) return RawBgraCaps(maxFps = fps, maxWidth = layout.frameSize.width)
    val width = layout.frameSize.width * 3 / 4
    if (width >= MIN_RAW_WIDTH) return RawBgraCaps(maxFps = layout.fps, maxWidth = width)
    return null
}

private const val RAW_ROW_ALIGNMENT = 64

/** What idb's client keeps up with alone, measured at 85–110 MB/s, with room to spare. */
private const val RAW_BYTES_PER_SECOND = 64_000_000L

internal const val MIN_RAW_FPS = 5

internal const val MAX_RAW_FPS = 60

/** Narrower than this, a phone's screen is too small to read. */
internal const val MIN_RAW_WIDTH = 240

/**
 * Copies BGRA frames of [frameSize] from [stream] into [surface] until the stream ends. Blocks the
 * calling thread, so run it off the UI. One buffer holds a frame between the pipe and the bitmap
 * for the whole stream.
 */
internal fun readRawBgraInto(surface: MirrorSurface, stream: VideoStream.RawBgra, onFrame: () -> Unit) {
    val frame = ByteArray(stream.rowBytes * stream.frameSize.height)
    val input = WaitTimingInputStream(stream.process.inputStream)
    val pace = ArrivalPace(requestedFps = stream.fps, windowNanos = PACE_WINDOW_NANOS)
    while (input.timingWork(surface::recordDecode) { input.readNBytes(frame, 0, frame.size) } == frame.size) {
        surface.writeBgraFrame(frame, stream.frameSize, stream.rowBytes)
        onFrame()
        val arrivedFps = pace.fellBehind(System.nanoTime()) ?: continue
        if (stream.onFellBehind(arrivedFps)) return
    }
}

/** How long a stream is watched before it counts as falling behind. */
private const val PACE_WINDOW_NANOS = 2_000_000_000L

/**
 * Tells a stream that keeps its pace from one that falls behind: frames arriving at under
 * [KEPT_PACE_SHARE] of [requestedFps] over [windowNanos] mean the source is producing more than can
 * be passed on, and the backlog only grows.
 */
internal class ArrivalPace(private val requestedFps: Int, private val windowNanos: Long) {
    private var windowStart = -1L
    private var arrived = 0

    /** Counts a frame arriving at [nowNanos]; returns the rate frames arrived at when it is too low. */
    fun fellBehind(nowNanos: Long): Int? {
        if (windowStart < 0) {
            windowStart = nowNanos
            return null
        }
        arrived++
        val elapsed = nowNanos - windowStart
        if (elapsed < windowNanos) return null
        val arrivedFps = (arrived * 1_000_000_000L / elapsed).toInt()
        windowStart = nowNanos
        arrived = 0
        return arrivedFps.takeIf { it < requestedFps * KEPT_PACE_SHARE }
    }
}

private const val KEPT_PACE_SHARE = 0.85

private fun MirrorSurface.writeBgraFrame(frame: ByteArray, frameSize: IntSize, rowBytes: Int) {
    writeFrame(frameSize.width, frameSize.height) { target ->
        val pixmap = target.peekPixels() ?: return@writeFrame false
        copyRows(frame, sourceRowBytes = rowBytes, target = pixmap.addr, targetRowBytes = pixmap.rowBytes, height = frameSize.height)
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
