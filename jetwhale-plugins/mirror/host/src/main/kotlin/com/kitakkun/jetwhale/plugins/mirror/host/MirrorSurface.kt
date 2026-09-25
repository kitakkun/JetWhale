package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** How often the stats shown under the mirror are recomputed. */
private const val STATS_WINDOW_NANOS = 1_000_000_000L

/**
 * Where decoded frames meet the screen, the way a SurfaceView sits between a video decoder and a
 * window: the decoder writes pixels into a bitmap it owns, and drawing picks up the newest
 * complete frame without anything around it recomposing.
 *
 * Three bitmaps rotate so neither side waits on the other: the decoder fills `back`, then swaps it
 * with `ready`; a draw swaps `ready` into `front` when a newer frame is there, and draws `front`.
 * A frame the screen had no time to show is simply overwritten in `ready` — the newest one wins.
 * The bitmaps are reused for as long as the frame size stays the same.
 *
 * Every bitmap is closed as soon as it leaves the rotation. Skia frees a bitmap's pixels only when
 * it is closed or after a garbage collection, and a heap of small wrapper objects rarely prompts
 * one, so a bitmap left to the collector per frame grows native memory without bound.
 */
@Stable
internal class MirrorSurface : AutoCloseable {
    private val lock = Any()
    private var back: Bitmap? = null
    private var ready: Bitmap? = null
    private var front: Bitmap? = null
    private var readyIsNewer = false

    // A frame that was on screen when [clear] ran; the draw may still be using it, so the next
    // draw closes it instead.
    private var retired: Bitmap? = null

    // Set by [close]; a decoder still finishing its last frame then writes nothing.
    private var closed = false

    // The bitmap [drawFrame] is drawing outside the lock. Whatever retires it meanwhile — a
    // [clear], a [close] — sets [closeWhenDrawn] and leaves the closing to the draw's end.
    private var drawn: Bitmap? = null
    private var closeWhenDrawn = false

    /** The size the frames are drawn at, in pixels; the decoder shrinks frames to it. Set by the view. */
    @Volatile
    var viewSize: IntSize = IntSize.Zero

    /** The size of the mirrored device's screen, for mapping a point on a frame back to it. */
    @Volatile
    var deviceSize: IntSize? = null

    /** Bumped for every new frame. Read it inside a draw lambda only, so a frame redraws and nothing more. */
    var frameCounter: Long by mutableLongStateOf(0)
        private set

    var stats: MirrorStats by mutableStateOf(MirrorStats.Empty)
        private set

    private val window = StatsWindow()

    /**
     * Stores one frame of [width] by [height] BGRA pixels, which [write] puts into the bitmap it is
     * given; [write] returns false when it could not, and the frame is dropped. Called from the
     * decoding thread.
     */
    fun writeFrame(width: Int, height: Int, write: (target: Bitmap) -> Boolean) {
        val started = System.nanoTime()
        // The write happens under the lock too, so [clear] can never close the bitmap being written.
        synchronized(lock) {
            if (closed) return
            val reusable = back?.takeIf { it.width == width && it.height == height }
            val target = reusable ?: newBitmap(width, height).also {
                back?.close()
                back = it
            }
            if (!write(target)) return
            target.notifyPixelsChanged()
            back = ready
            ready = target
            readyIsNewer = true
        }
        window.recordCopy(System.nanoTime() - started)
        frameCounter++
    }

    /** Records the time the decoder took for one frame, for [stats]. */
    fun recordDecode(nanos: Long) {
        window.recordDecode(nanos)
        publishStatsIfDue()
    }

    /**
     * Runs [draw] with the frame to show now, swapping in the newest complete one; does nothing
     * when there is no frame. Called from the draw phase. The bitmap stays open until [draw]
     * returns, even when [close] runs meanwhile.
     */
    fun drawFrame(draw: (Bitmap) -> Unit) {
        val bitmap = synchronized(lock) {
            if (closed) return
            retired?.close()
            retired = null
            if (readyIsNewer) {
                val shown = front
                front = ready
                ready = shown
                readyIsNewer = false
                window.recordDisplayed()
            }
            front?.also { drawn = it }
        } ?: return
        try {
            draw(bitmap)
        } finally {
            synchronized(lock) {
                if (closeWhenDrawn) bitmap.close()
                closeWhenDrawn = false
                drawn = null
            }
        }
    }

    fun recordDraw(nanos: Long) = window.recordDraw(nanos)

    /** Forgets every frame, for when the mirror switches to another device or stops. */
    fun clear() {
        synchronized(lock) {
            back?.close()
            ready?.close()
            retired?.closeUnlessDrawn()
            retired = front
            back = null
            ready = null
            front = null
            readyIsNewer = false
        }
        stats = MirrorStats.Empty
        frameCounter++
    }

    /**
     * Frees every bitmap; for when nothing will draw from this surface again. A frame being drawn
     * is freed when its draw ends.
     */
    override fun close() {
        synchronized(lock) {
            closed = true
            back?.close()
            ready?.close()
            retired?.closeUnlessDrawn()
            front?.closeUnlessDrawn()
            back = null
            ready = null
            retired = null
            front = null
            readyIsNewer = false
        }
        stats = MirrorStats.Empty
        frameCounter++
    }

    // Call with [lock] held.
    private fun Bitmap.closeUnlessDrawn() {
        if (this === drawn) closeWhenDrawn = true else close()
    }

    private fun publishStatsIfDue() {
        window.takeIfDue(STATS_WINDOW_NANOS)?.let { stats = it }
    }

    private fun newBitmap(width: Int, height: Int): Bitmap = Bitmap().apply {
        allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
    }
}

/**
 * The cost of the mirror over the last second: frames received from the device and frames the
 * screen showed, and the average time of each stage for one frame.
 */
internal data class MirrorStats(
    val receivedFps: Int,
    val displayedFps: Int,
    val decodeMillis: Double,
    val copyMillis: Double,
    val drawMillis: Double,
) {
    companion object {
        val Empty = MirrorStats(receivedFps = 0, displayedFps = 0, decodeMillis = 0.0, copyMillis = 0.0, drawMillis = 0.0)
    }
}

/** Accumulates per-frame timings from the decoding and drawing threads between two readings. */
internal class StatsWindow {
    private var windowStart = System.nanoTime()
    private var received = 0
    private var displayed = 0
    private var decodeNanos = 0L
    private var copyNanos = 0L
    private var draws = 0
    private var drawNanos = 0L

    @Synchronized
    fun recordDecode(nanos: Long) {
        received++
        decodeNanos += nanos
    }

    @Synchronized
    fun recordCopy(nanos: Long) {
        copyNanos += nanos
    }

    @Synchronized
    fun recordDisplayed() {
        displayed++
    }

    @Synchronized
    fun recordDraw(nanos: Long) {
        draws++
        drawNanos += nanos
    }

    /** The stats since the last reading, once [windowNanos] have passed; null before that. */
    @Synchronized
    fun takeIfDue(windowNanos: Long): MirrorStats? {
        val now = System.nanoTime()
        val elapsed = now - windowStart
        if (elapsed < windowNanos) return null
        val perSecond = 1_000_000_000.0 / elapsed
        val stats = MirrorStats(
            receivedFps = (received * perSecond).toInt(),
            displayedFps = (displayed * perSecond).toInt(),
            decodeMillis = if (received == 0) 0.0 else decodeNanos / received / 1e6,
            copyMillis = if (received == 0) 0.0 else copyNanos / received / 1e6,
            drawMillis = if (draws == 0) 0.0 else drawNanos / draws / 1e6,
        )
        windowStart = now
        received = 0
        displayed = 0
        decodeNanos = 0
        copyNanos = 0
        draws = 0
        drawNanos = 0
        return stats
    }
}
