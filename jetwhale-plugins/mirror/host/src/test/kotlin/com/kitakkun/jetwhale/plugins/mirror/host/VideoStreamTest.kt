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
        val layout = rawBgraLayout(simulator, IntSize(598, 1296), maxFps = MAX_RAW_FPS)

        assertEquals(598, layout.frameSize.width)
    }

    @Test
    fun `the layout matches what idb writes, rows padded to 64 bytes`() {
        // Measured against a simulator: width 777 comes as 1685 rows of 3136 bytes.
        val layout = rawBgraLayout(simulator, IntSize(777, 1685), maxFps = MAX_RAW_FPS)

        assertEquals(IntSize(777, 1685), layout.frameSize)
        assertEquals(3136, layout.rowBytes)
        assertEquals(777, floor(simulator.width * layout.scale).toInt())
    }

    @Test
    fun `larger frames come at a lower rate so the stream never falls behind`() {
        val small = rawBgraLayout(simulator, IntSize(400, 868), maxFps = MAX_RAW_FPS)
        val large = rawBgraLayout(simulator, IntSize(800, 1734), maxFps = MAX_RAW_FPS)

        assertTrue(small.fps >= 40, "small ${small.fps}")
        assertTrue(large.fps < small.fps / 2, "large ${large.fps}")
        assertTrue(large.rowBytes.toLong() * large.frameSize.height * large.fps <= 100_000_000L)
    }

    @Test
    fun `a view as large as the screen streams the whole screen`() {
        val layout = rawBgraLayout(simulator, null, maxFps = MAX_RAW_FPS)

        assertEquals(IntSize(1179, 2556), layout.frameSize)
        assertEquals(1.0, layout.scale)
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
private fun rawStream(input: InputStream, size: IntSize, rowBytes: Int) = VideoStream.RawBgra(StreamProcess(input), size, rowBytes, fps = 30, onFellBehind = {})

/** A process whose output is [input]. */
private class StreamProcess(private val input: InputStream) : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = input

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
