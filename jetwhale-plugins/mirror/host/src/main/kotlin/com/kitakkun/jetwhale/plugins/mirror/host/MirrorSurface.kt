package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.impl.BufferUtil

/** Devices whose last frame is kept for switching back; a frame is the size of the view. */
private const val MAX_KEPT_FRAMES = 4

/** How often the stats shown under the mirror are recomputed. */
private const val STATS_WINDOW_NANOS = 1_000_000_000L

/**
 * Where decoded frames meet the screen, the way a SurfaceView sits between a video decoder and a
 * window: the decoder writes each frame into a bitmap of its own, and drawing picks up the newest
 * complete frame without anything around it recomposing.
 *
 * A frame's bitmap is written before anyone else can see it, then made immutable and handed over as
 * `ready`; a draw moves `ready` to `front` and draws `front`. A frame the screen had no time to show
 * is replaced in `ready`, so the newest one wins. Since no bitmap is written once it is shown, the
 * decoder never waits for a draw, and Skia draws an immutable bitmap without copying its pixels.
 *
 * Every bitmap is closed as soon as it is replaced. Skia frees a bitmap's pixels only when it is
 * closed or after a garbage collection, and a heap of small wrapper objects rarely prompts one, so
 * a bitmap left to the collector per frame grows native memory without bound.
 */
@Stable
internal class MirrorSurface : AutoCloseable {
    private val lock = Any()
    private var ready: Bitmap? = null
    private var front: Bitmap? = null

    private var closedOnNextDraw: Bitmap? = null

    private var closed = false

    private var streamGeneration = 0L

    private var drawn: Bitmap? = null
    private var closeWhenDrawn = false

    private val lastFrames = LinkedHashMap<String, Bitmap>()

    /** The device whose frames this surface holds now; set by [switchTo]. */
    var deviceId: String? by mutableStateOf(null)
        private set

    /** True while the frame on screen is the one kept from the device's last visit, not a live one. */
    var showingKeptFrame: Boolean by mutableStateOf(false)
        private set

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
     * Starts the next stream of frames into this surface. From now on only the returned
     * [FrameStream] writes: a stream started before it may still be finishing a frame, of a screen
     * since turned or folded, and that frame must not land between the new stream's.
     */
    fun startStream(): FrameStream = FrameStream(synchronized(lock) { ++streamGeneration })

    /** One stream's frames into this surface; its writes are dropped once a newer stream has started. */
    inner class FrameStream internal constructor(private val generation: Long) {
        /**
         * Stores one frame of [width] by [height] pixels laid out as [colorType], which [write] puts
         * into the bitmap it is given; [write] returns false when it could not, and the frame is
         * dropped. Called from the decoding thread.
         */
        fun writeFrame(width: Int, height: Int, colorType: ColorType, write: (target: Bitmap) -> Boolean) = writeStreamFrame(generation, width, height, colorType, write)

        /**
         * Stores [frame], a bitmap the decoder filled for this surface alone, as the next frame. The
         * surface owns it from now on and closes it once it is replaced.
         */
        fun publishFrame(frame: Bitmap) = publishStreamFrame(generation, frame, System.nanoTime())

        /**
         * Records the time the decoder took for one frame, for [stats]. Leave out the time spent
         * waiting for the device to send it: a still screen sends nothing for seconds.
         */
        fun recordDecode(nanos: Long) {
            window.recordDecode(nanos)
            updateStatsIfDue()
        }
    }

    private fun writeStreamFrame(generation: Long, width: Int, height: Int, colorType: ColorType, write: (target: Bitmap) -> Boolean) {
        val started = System.nanoTime()
        val frame = newBitmap(width, height, colorType)
        var written = false
        try {
            written = write(frame)
        } finally {
            if (!written) frame.close()
        }
        if (!written) return
        publishStreamFrame(generation, frame, copyStartedNanos = started)
    }

    private fun publishStreamFrame(generation: Long, frame: Bitmap, copyStartedNanos: Long) {
        frame.setImmutable()
        // Published under the lock together with showingKeptFrame and frameCounter, so a device
        // switch lands wholly before or after this frame.
        val discarded = synchronized(lock) {
            if (closed || generation != streamGeneration) {
                frame
            } else {
                val previous = ready
                ready = frame
                showingKeptFrame = false
                window.recordCopy(System.nanoTime() - copyStartedNanos)
                frameCounter++
                previous
            }
        }
        discarded?.close()
    }

    /**
     * Runs [draw] with the frame to show now, swapping in the newest complete one; does nothing
     * when there is no frame. Called from the draw phase. The bitmap stays open until [draw]
     * returns, even when [close] runs meanwhile.
     */
    fun drawFrame(draw: (Bitmap) -> Unit) {
        val bitmap = synchronized(lock) {
            if (closed) return
            closedOnNextDraw?.close()
            closedOnNextDraw = null
            ready?.let { newer ->
                front?.close()
                front = newer
                ready = null
                window.recordDisplayed(System.nanoTime())
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

    /**
     * Runs [draw] with the frame kept from [keptDeviceId]'s last visit, for while the view already
     * shows that device and this surface still holds another one's; does nothing without such a frame.
     */
    fun drawKeptFrame(keptDeviceId: String, draw: (Bitmap) -> Unit) {
        val bitmap = synchronized(lock) {
            if (closed) return
            lastFrames[keptDeviceId]?.also { drawn = it }
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

    fun hasKeptFrame(keptDeviceId: String): Boolean = synchronized(lock) { keptDeviceId in lastFrames }

    /**
     * The newest frame streamed from [streamingDeviceId], encoded as PNG at the size it was decoded,
     * or null when the surface shows another device or only a frame kept from an earlier visit.
     */
    @VisibleForTesting
    fun newestFramePng(streamingDeviceId: String): ByteArray? {
        // The image keeps the frame's pixels alive even once the bitmap is closed, so the PNG
        // encode runs outside the lock and holds up neither the decoder nor a draw.
        val newest = synchronized(lock) {
            if (closed || deviceId != streamingDeviceId || showingKeptFrame) return null
            Image.makeFromBitmap(ready ?: front ?: return null)
        }
        return newest.use { image -> image.encodeToData(EncodedImageFormat.PNG)?.use(Data::bytes) }
    }

    fun recordDraw(nanos: Long) = window.recordDraw(nanos)

    /**
     * Starts showing [nextDeviceId]: the frame of the device shown until now is kept for when it is
     * shown again, and [nextDeviceId]'s own kept frame, if any, is shown until its stream sends one.
     * At most [MAX_KEPT_FRAMES] frames are kept; the device shown longest ago loses its frame first.
     */
    fun switchTo(nextDeviceId: String) {
        synchronized(lock) {
            val newest = takeNewestFrame()
            val previous = deviceId
            when {
                newest == null -> Unit

                previous == null -> closeOnNextDraw(newest)

                previous == nextDeviceId -> front = newest

                else -> {
                    lastFrames.remove(previous)?.closeUnlessDrawn()
                    lastFrames[previous] = newest
                }
            }
            if (previous != nextDeviceId) front = lastFrames.remove(nextDeviceId)
            deviceId = nextDeviceId
            streamGeneration++
            while (lastFrames.size > MAX_KEPT_FRAMES) {
                val oldest = lastFrames.keys.first()
                lastFrames.remove(oldest)?.closeUnlessDrawn()
            }
            showingKeptFrame = front != null && previous != nextDeviceId
            stats = MirrorStats.Empty
            frameCounter++
        }
    }

    /** Drops the kept frames of devices other than [present], which are no longer connected. */
    fun keepFramesOf(present: Set<String>) {
        synchronized(lock) {
            val gone = lastFrames.keys - present
            gone.forEach { lastFrames.remove(it)?.closeUnlessDrawn() }
        }
    }

    /**
     * Frees every bitmap; for when nothing will draw from this surface again. A frame being drawn
     * is freed when its draw ends.
     */
    override fun close() {
        synchronized(lock) {
            closed = true
            ready?.close()
            closedOnNextDraw?.closeUnlessDrawn()
            front?.closeUnlessDrawn()
            lastFrames.values.forEach { it.closeUnlessDrawn() }
            lastFrames.clear()
            ready = null
            closedOnNextDraw = null
            front = null
        }
        showingKeptFrame = false
        stats = MirrorStats.Empty
        frameCounter++
    }

    private fun takeNewestFrame(): Bitmap? {
        val newest = ready ?: front
        if (front !== newest) front?.let(::closeOnNextDraw)
        ready = null
        front = null
        return newest
    }

    private fun closeOnNextDraw(bitmap: Bitmap) {
        closedOnNextDraw?.closeUnlessDrawn()
        closedOnNextDraw = bitmap
    }

    private fun Bitmap.closeUnlessDrawn() {
        if (this === drawn) closeWhenDrawn = true else close()
    }

    private fun updateStatsIfDue() {
        window.takeIfDue(STATS_WINDOW_NANOS)?.let { stats = it }
    }

    private fun newBitmap(width: Int, height: Int, colorType: ColorType): Bitmap = Bitmap().apply {
        allocPixels(ImageInfo(width, height, colorType, ColorAlphaType.OPAQUE))
    }
}

/**
 * The cost of the mirror over the last second: frames received from the device and frames the
 * screen showed, the average time of each stage for one frame, and the longest gap between two
 * shown frames, which is what a viewer sees as a stutter.
 */
internal data class MirrorStats(
    val receivedFps: Int,
    val displayedFps: Int,
    val decodeMillis: Double,
    val copyMillis: Double,
    val drawMillis: Double,
    val longestGapMillis: Double,
) {
    companion object {
        val Empty = MirrorStats(receivedFps = 0, displayedFps = 0, decodeMillis = 0.0, copyMillis = 0.0, drawMillis = 0.0, longestGapMillis = 0.0)
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
    private var lastDisplayedAt = 0L
    private var longestGapNanos = 0L

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
    fun recordDisplayed(atNanos: Long) {
        displayed++
        if (lastDisplayedAt != 0L) longestGapNanos = maxOf(longestGapNanos, atNanos - lastDisplayedAt)
        lastDisplayedAt = atNanos
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
            longestGapMillis = longestGapNanos / 1e6,
        )
        windowStart = now
        received = 0
        displayed = 0
        decodeNanos = 0
        copyNanos = 0
        draws = 0
        drawNanos = 0
        longestGapNanos = 0
        return stats
    }
}

/**
 * Copies [pixels], [height] rows of [sourceRowBytes], into this pixmap's memory, whose rows may be
 * padded longer. The pixmap's memory is written in place through a direct buffer over it, so a
 * frame is copied once on its way to the screen.
 */
internal fun Pixmap.writeRows(pixels: ByteArray, sourceRowBytes: Int, height: Int) {
    val target = BufferUtil.getByteBufferFromPointer(addr, rowBytes * height)
    if (sourceRowBytes == rowBytes) {
        target.put(pixels, 0, sourceRowBytes * height)
        return
    }
    val copied = minOf(sourceRowBytes, rowBytes)
    for (row in 0 until height) {
        target.put(row * rowBytes, pixels, row * sourceRowBytes, copied)
    }
}
