package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MirrorViewingTest {
    @Test
    fun `a tall frame in a wide view is letterboxed at the sides and maps back to device pixels`() {
        val fitted = FittedFrame(frameWidth = 100, frameHeight = 200, viewWidth = 400f, viewHeight = 200f)

        assertEquals(150f, fitted.bounds.left)
        assertEquals(IntOffset(50, 100), fitted.toDevicePixel(Offset(200f, 100f), IntSize(100, 200)))
        assertNull(fitted.toDevicePixel(Offset(100f, 100f), IntSize(100, 200)))
    }

    @Test
    fun `a phone in a narrow view is fitted inside it and taps map to the right device pixels`() {
        val fitted = FittedFrame(frameWidth = 1080, frameHeight = 2424, viewWidth = 760f, viewHeight = 1200f)
        assertTrue(fitted.bounds.right <= 760f)
        assertEquals(1200f, fitted.bounds.bottom)
        assertEquals(fitted.bounds.left, 760f - fitted.bounds.right, absoluteTolerance = 0.01f)

        val narrow = FittedFrame(frameWidth = 1080, frameHeight = 2424, viewWidth = 300f, viewHeight = 1200f)
        assertEquals(0f, narrow.bounds.left)
        assertEquals(300f, narrow.bounds.right)
        assertEquals(IntOffset(1079, 1212), narrow.toDevicePixel(Offset(299.9f, 600f), IntSize(1080, 2424)))
        assertEquals(IntOffset(0, 1212), narrow.toDevicePixel(Offset(0f, 600f), IntSize(1080, 2424)))
    }

    @Test
    fun `a point on a frame decoded smaller than the screen maps to the screen's own pixels`() {
        val fitted = FittedFrame(frameWidth = 540, frameHeight = 1200, viewWidth = 540f, viewHeight = 1200f)

        assertEquals(IntOffset(1000, 2000), fitted.toDevicePixel(Offset(500f, 1000f), IntSize(1080, 2400)))
    }

    @Test
    fun `a point on a capped simulator frame enlarged to its view maps to the screen's own pixels`() {
        val fitted = FittedFrame(frameWidth = 600, frameHeight = 1301, viewWidth = 800f, viewHeight = 1734f)

        assertEquals(IntOffset(589, 1278), fitted.toDevicePixel(Offset(400f, 867f), IntSize(1179, 2556)))
        assertEquals(IntOffset(1178, 2555), fitted.toDevicePixel(Offset(799.8f, 1733.9f), IntSize(1179, 2556)))
    }

    @Test
    fun `a screen is decoded at the size it is shown and whole when the view is larger`() {
        assertEquals(IntSize(588, 1282), decodingSize(source = IntSize(1206, 2622), view = IntSize(1400, 1282)))
        assertNull(decodingSize(source = IntSize(1080, 2400), view = IntSize(2000, 3000)))
        assertNull(decodingSize(source = IntSize(1080, 2400), view = IntSize.Zero))
    }

    @Test
    fun `a stream that sends a frame in time is live`() = runTest {
        val watchdog = FirstFrameWatchdog(timeoutMillis = 7_000)
        launch {
            delay(6_000)
            watchdog.frameArrived()
        }

        assertTrue(watchdog.awaitFirstFrame())
    }

    @Test
    fun `a stream that stays silent past the timeout is reported as sending nothing`() = runTest {
        val watchdog = FirstFrameWatchdog(timeoutMillis = 7_000)
        launch {
            delay(8_000)
            watchdog.frameArrived()
        }

        assertFalse(watchdog.awaitFirstFrame())
    }

    @Test
    fun `a silent iPhone is explained by the lock screen and the camera permission`() {
        val hints = noFramesHints(DeviceKind.IosDevice).joinToString(" ")

        assertTrue("Unlock" in hints)
        assertTrue("Camera" in hints)
    }

    @Test
    fun `the newest frame is drawn and frames the screen had no time for are skipped`() {
        MirrorSurface().use { surface ->
            val frames = surface.startStream()
            frames.writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))
            frames.writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.BLUE))

            val drawn = surface.drawnFrame()

            assertEquals(Color.BLUE, drawn?.getColor(0, 0))
        }
    }

    @Test
    fun `a stream that keeps writing after the next one started never reaches the screen`() {
        MirrorSurface().use { surface ->
            val earlier = surface.startStream()
            val later = surface.startStream()
            later.writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.BLUE))

            earlier.writeFrame(width = 8, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))
            val drawn = surface.drawnFrame()

            assertEquals(Color.BLUE, drawn?.getColor(0, 0))
            assertEquals(4, drawn?.width)
        }
    }

    @Test
    fun `a stream of the device shown before a switch never reaches the screen after it`() {
        MirrorSurface().use { surface ->
            surface.switchTo("pixel")
            val pixel = surface.startStream()
            surface.switchTo("iphone")

            pixel.writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))

            assertNull(surface.drawnFrame())
        }
    }

    @Test
    fun `drawing again without a new frame keeps the frame on screen`() {
        MirrorSurface().use { surface ->
            surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))

            val first = surface.drawnFrame()

            assertSame(first, surface.drawnFrame())
        }
    }

    @Test
    fun `a frame bitmap replaced by a newer one is closed instead of left to the collector`() {
        val surface = MirrorSurface()
        val written = mutableListOf<Bitmap>()
        val record: (Bitmap) -> Boolean = { bitmap ->
            written += bitmap
            true
        }
        val frames = surface.startStream()
        repeat(2) { frames.writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = record) }

        repeat(2) { frames.writeFrame(width = 8, height = 8, colorType = ColorType.BGRA_8888, write = record) }
        surface.close()

        assertTrue(written.all(Bitmap::isClosed))
    }

    @Test
    fun `a frame whose write throws has its bitmap closed`() {
        var target: Bitmap? = null

        assertFailsWith<IllegalStateException> {
            MirrorSurface().startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888) { bitmap ->
                target = bitmap
                error("malformed frame")
            }
        }

        assertTrue(target?.isClosed ?: false)
    }

    @Test
    fun `a device switch during a frame's write neither waits for it nor closes the bitmap being written`() {
        MirrorSurface().use { surface ->
            val switched = CountDownLatch(1)
            var switchedDuringWrite = false
            var closedDuringWrite = true
            var written: Bitmap? = null
            surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888) { target ->
                written = target
                thread {
                    surface.switchTo("device-2")
                    switched.countDown()
                }
                switchedDuringWrite = switched.await(SWITCH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                closedDuringWrite = target.isClosed
                fill(Color.RED)(target)
            }

            assertTrue(switchedDuringWrite)
            assertFalse(closedDuringWrite)
            assertTrue(written?.isClosed ?: false)
            assertNull(surface.drawnFrame())
        }
    }

    @Test
    fun `a frame is drawn from an immutable bitmap so Skia shares its pixels instead of copying them`() {
        MirrorSurface().use { surface ->
            surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))

            assertTrue(surface.drawnFrame()?.isImmutable ?: false)
        }
    }

    @Test
    fun `nothing is drawn once the surface is closed`() {
        val surface = MirrorSurface()
        surface.close()

        surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.BLUE))

        assertNull(surface.drawnFrame())
    }

    @Test
    fun `closing during a draw leaves the drawn frame open until the draw ends`() {
        val surface = MirrorSurface()
        surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))
        var openAfterClose = false
        var drawn: Bitmap? = null

        surface.drawFrame { bitmap ->
            drawn = bitmap
            thread(block = surface::close).join()
            openAfterClose = !bitmap.isClosed
        }

        assertTrue(openAfterClose)
        assertTrue(drawn?.isClosed ?: false)
    }

    @Test
    fun `a device switch and a close during a draw leave the drawn frame open until the draw ends`() {
        val surface = MirrorSurface()
        surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))
        var openAfterClose = false
        var drawn: Bitmap? = null

        surface.drawFrame { bitmap ->
            drawn = bitmap
            thread {
                surface.switchTo("device-2")
                surface.close()
            }.join()
            openAfterClose = !bitmap.isClosed
        }

        assertTrue(openAfterClose)
        assertTrue(drawn?.isClosed ?: false)
    }

    @Test
    fun `every bitmap a decoder writes is freed however its last frame races the close`() {
        repeat(CLOSE_RACE_ROUNDS) {
            val surface = MirrorSurface()
            val written = mutableListOf<Bitmap>()
            val started = CountDownLatch(1)
            val frames = surface.startStream()
            val decoder = thread {
                repeat(FRAMES_PER_ROUND) { frame ->
                    val side = if (frame % 2 == 0) 4 else 8
                    frames.writeFrame(width = side, height = side, ColorType.BGRA_8888) { bitmap ->
                        written += bitmap
                        started.countDown()
                        true
                    }
                }
            }
            started.await()
            surface.close()
            decoder.join()

            assertTrue(written.all(Bitmap::isClosed))
        }
    }

    @Test
    fun `the decoder never writes into the bitmap being drawn`() {
        MirrorSurface().use { surface ->
            val frames = surface.startStream()
            frames.writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.RED))
            val onScreen = surface.drawnFrame()
            val written = mutableListOf<Any>()

            repeat(3) {
                frames.writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888) { bitmap ->
                    written += bitmap
                    fill(Color.GREEN)(bitmap)
                }
            }

            assertTrue(written.none { it === onScreen })
            assertEquals(Color.RED, onScreen?.getColor(0, 0))
        }
    }

    @Test
    fun `switching back to a device shows its last frame until its stream sends a new one`() {
        MirrorSurface().use { surface ->
            surface.showLive("phone", Color.RED)
            surface.showLive("tablet", Color.BLUE)

            surface.switchTo("phone")

            assertEquals(Color.RED, surface.drawnFrame()?.getColor(0, 0))
            assertTrue(surface.showingKeptFrame)

            surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.GREEN))
            assertEquals(Color.GREEN, surface.drawnFrame()?.getColor(0, 0))
            assertFalse(surface.showingKeptFrame)
        }
    }

    @Test
    fun `a device selected before its stream starts shows its own kept frame and never the previous device's`() {
        MirrorSurface().use { surface ->
            surface.showLive("phone", Color.RED)
            surface.showLive("tablet", Color.BLUE)

            var kept: Int? = null
            surface.drawKeptFrame("phone") { kept = it.getColor(0, 0) }

            assertEquals(Color.RED, kept)
            assertTrue(surface.hasKeptFrame("phone"))
            assertFalse(surface.hasKeptFrame("tablet"))
        }
    }

    @Test
    fun `the newest frame is kept even when the screen had no time to show it`() {
        MirrorSurface().use { surface ->
            surface.showLive("phone", Color.RED)
            surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(Color.GREEN))

            surface.switchTo("tablet")
            surface.switchTo("phone")

            assertEquals(Color.GREEN, surface.drawnFrame()?.getColor(0, 0))
        }
    }

    @Test
    fun `only the devices shown most recently keep a frame`() {
        MirrorSurface().use { surface ->
            listOf("a", "b", "c", "d", "e", "f").forEach { surface.showLive(it, Color.RED) }

            surface.switchTo("a")
            assertNull(surface.drawnFrame())

            surface.switchTo("e")
            assertEquals(Color.RED, surface.drawnFrame()?.getColor(0, 0))
        }
    }

    @Test
    fun `a device that is no longer connected loses its kept frame`() {
        MirrorSurface().use { surface ->
            surface.showLive("phone", Color.RED)
            surface.showLive("tablet", Color.BLUE)

            surface.keepFramesOf(setOf("tablet"))
            surface.switchTo("phone")

            assertNull(surface.drawnFrame())
        }
    }

    @Test
    fun `closing frees the frames kept for every device`() {
        val surface = MirrorSurface()
        val written = mutableListOf<Bitmap>()
        listOf("a", "b", "c").forEach { device ->
            surface.switchTo(device)
            surface.startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888) { bitmap ->
                written += bitmap
                true
            }
            surface.drawnFrame()
        }

        surface.close()

        assertTrue(written.all(Bitmap::isClosed))
    }
}

private fun MirrorSurface.drawnFrame(): Bitmap? {
    var drawn: Bitmap? = null
    drawFrame { drawn = it }
    return drawn
}

/** Mirrors [device] until a frame of [color] is on screen. */
private fun MirrorSurface.showLive(device: String, color: Int) {
    switchTo(device)
    startStream().writeFrame(width = 4, height = 4, colorType = ColorType.BGRA_8888, write = fill(color))
    drawnFrame()
}

private fun fill(color: Int): (Bitmap) -> Boolean = { bitmap ->
    bitmap.erase(color)
    true
}

private const val CLOSE_RACE_ROUNDS = 200
private const val FRAMES_PER_ROUND = 50

/** Far longer than a switch that does not wait for the write takes. */
private const val SWITCH_TIMEOUT_MILLIS = 5_000L
