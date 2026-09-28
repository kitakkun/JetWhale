package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoStreamTest {
    private val simulator = IntSize(1179, 2556)

    @Test
    fun `a raw stream is asked for frames exactly as wide as they are shown`() {
        // Drawing a frame even slightly larger than it is blurs its text.
        val layout = rawBgraLayout(simulator, IntSize(598, 1296), maxFps = MAX_RAW_FPS, maxWidth = Int.MAX_VALUE)

        assertEquals(598, layout.frameSize.width)
    }

    @Test
    fun `the layout matches what idb writes, rows padded to 64 bytes`() {
        // Measured against a simulator: width 777 comes as 1685 rows of 3136 bytes.
        val layout = rawBgraLayout(simulator, IntSize(777, 1685), maxFps = MAX_RAW_FPS, maxWidth = Int.MAX_VALUE)

        assertEquals(IntSize(777, 1685), layout.frameSize)
        assertEquals(3136, layout.rowBytes)
        assertEquals(777, floor(simulator.width * layout.scale).toInt())
    }

    @Test
    fun `larger frames come at a lower rate so the stream never falls behind`() {
        val small = rawBgraLayout(simulator, IntSize(400, 868), maxFps = MAX_RAW_FPS, maxWidth = Int.MAX_VALUE)
        val large = rawBgraLayout(simulator, IntSize(800, 1734), maxFps = MAX_RAW_FPS, maxWidth = Int.MAX_VALUE)

        assertTrue(small.fps >= 40, "small ${small.fps}")
        assertTrue(large.fps < small.fps / 2, "large ${large.fps}")
        assertTrue(large.rowBytes.toLong() * large.frameSize.height * large.fps <= 100_000_000L)
    }

    @Test
    fun `a view as large as the screen streams one pixel narrower so idb never sends its page-rounded buffer`() {
        // At a scale of exactly 1, idb measured 12,107,776 bytes a frame: 2,556 rows of 4,736 bytes
        // rounded up to a whole page, which drifts a reader counting rows. At 1,178 pixels wide the
        // frames were exactly their rows.
        val layout = rawBgraLayout(simulator, null, maxFps = MAX_RAW_FPS, maxWidth = Int.MAX_VALUE)

        assertEquals(IntSize(1178, 2554), layout.frameSize)
        assertTrue(layout.scale < 1.0, "scale ${layout.scale}")
        assertEquals(4736, layout.rowBytes)
    }

    @Test
    fun `a width cap narrows the frames even when the view is wider`() {
        val layout = rawBgraLayout(simulator, IntSize(800, 1734), maxFps = MAX_RAW_FPS, maxWidth = 600)

        assertEquals(600, layout.frameSize.width)
        assertEquals(600, floor(simulator.width * layout.scale).toInt())
    }

    @Test
    fun `a stream that fell behind is lowered in rate first`() {
        val layout = rawBgraLayout(simulator, IntSize(800, 1734), maxFps = MAX_RAW_FPS, maxWidth = Int.MAX_VALUE)

        val caps = lighterThan(layout, arrivedFps = layout.fps / 2)

        assertEquals(RawBgraCaps(maxFps = maxOf(MIN_RAW_FPS, layout.fps / 2 * 3 / 4), maxWidth = 800), caps)
    }

    @Test
    fun `a stream at the lowest rate that still fell behind is narrowed instead of reopened unchanged`() {
        val layout = rawBgraLayout(simulator, null, maxFps = MIN_RAW_FPS, maxWidth = Int.MAX_VALUE)

        val caps = lighterThan(layout, arrivedFps = 3)

        assertEquals(RawBgraCaps(maxFps = MIN_RAW_FPS, maxWidth = 1178 * 3 / 4), caps)
    }

    @Test
    fun `falling behind again and again ends at a floor instead of reopening forever`() {
        var caps = RawBgraCaps(maxFps = MAX_RAW_FPS, maxWidth = Int.MAX_VALUE)
        var reopens = 0
        var previousLoad = Long.MAX_VALUE
        while (true) {
            val layout = rawBgraLayout(simulator, null, maxFps = caps.maxFps, maxWidth = caps.maxWidth)
            val load = layout.rowBytes.toLong() * layout.frameSize.height * layout.fps
            assertTrue(load < previousLoad || reopens == 0, "each reopen asks for less: $load after $previousLoad")
            previousLoad = load
            caps = lighterThan(layout, arrivedFps = 1) ?: break
            reopens++
            assertTrue(reopens < 50, "still reopening after $reopens")
        }
        assertTrue(reopens > 0)
    }

    @Test
    fun `raw frames reach the surface one whole frame at a time`() {
        val size = IntSize(16, 2)
        val frameBytes = size.width * size.height * 4
        val stream = ByteArrayInputStream(ByteArray(frameBytes * 3) { (it / frameBytes).toByte() })
        MirrorSurface().use { surface ->
            var frames = 0

            readRawBgraInto(surface, rawStream(stream, size, rowBytes = size.width * 4)) { frames++ }

            assertEquals(3, frames)
            surface.drawFrame { bitmap -> assertEquals(2.toByte(), bitmap.readPixels()?.first()) }
        }
    }

    @Test
    fun `padding at the end of each row is left out of the picture`() {
        val size = IntSize(2, 2)
        // Rows of 16 bytes: 8 of pixels, then 8 of padding filled with a marker.
        val stream = ByteArrayInputStream(ByteArray(32) { if (it % 16 < 8) 1 else 9 })
        MirrorSurface().use { surface ->
            readRawBgraInto(surface, rawStream(stream, size, rowBytes = 16)) {}

            surface.drawFrame { bitmap -> assertTrue(bitmap.readPixels()?.all { it == 1.toByte() } ?: false) }
        }
    }

    @Test
    fun `an odd width padded to 64 bytes reads each frame from its own start`() {
        val size = IntSize(3, 2)
        // 12 bytes of pixels then 52 of padding per row; each frame is filled with its index.
        val frameBytes = 64 * size.height
        val stream = ByteArrayInputStream(ByteArray(frameBytes * 4) { i -> if (i % 64 < 12) (i / frameBytes).toByte() else 99 })
        MirrorSurface().use { surface ->
            var frames = 0

            readRawBgraInto(surface, rawStream(stream, size, rowBytes = 64)) { frames++ }

            assertEquals(4, frames)
            surface.drawFrame { bitmap -> assertTrue(bitmap.readPixels()?.all { it == 3.toByte() } ?: false) }
        }
    }

    @Test
    fun `a stream that ends partway through a frame hands over only the whole frames`() {
        val size = IntSize(16, 2)
        val frameBytes = size.width * size.height * 4
        MirrorSurface().use { surface ->
            var frames = 0

            readRawBgraInto(surface, rawStream(ByteArrayInputStream(ByteArray(frameBytes + frameBytes / 2)), size, rowBytes = size.width * 4)) { frames++ }

            assertEquals(1, frames)
        }
    }

    @Test
    fun `a stream that keeps its pace is left alone`() {
        val pace = ArrivalPace(requestedFps = 30, windowNanos = SECOND)

        val reports = (0..90).map { pace.fellBehind(it * SECOND / 30) }

        assertTrue(reports.all { it == null })
    }

    @Test
    fun `a stream whose frames arrive at half the rate asked for is reported with the rate it got`() {
        val pace = ArrivalPace(requestedFps = 30, windowNanos = SECOND)

        val reports = (0..30).mapNotNull { pace.fellBehind(it * SECOND / 15) }

        assertEquals(15, reports.first())
    }
}

private const val SECOND = 1_000_000_000L

/** A stream that keeps its pace, for reading frames out of [input]. */
private fun rawStream(input: InputStream, size: IntSize, rowBytes: Int) = VideoStream.RawBgra(StreamProcess(input), size, rowBytes, fps = 30, onFellBehind = { false })

/** A process whose output is [input]. */
private class StreamProcess(private val input: InputStream) : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = input

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
