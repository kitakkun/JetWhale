package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MirrorViewingTest {
    @Test
    fun `a tall frame in a wide view is letterboxed at the sides and maps back to device pixels`() {
        // 100x200 fitted into 400x200: scaled by 1, drawn from x=150 to x=250.
        val fitted = FittedFrame(frameWidth = 100, frameHeight = 200, viewWidth = 400f, viewHeight = 200f)

        assertEquals(150f, fitted.bounds.left)
        assertEquals(IntOffset(50, 100), fitted.toDevicePixel(Offset(200f, 100f), IntSize(100, 200)))
        assertNull(fitted.toDevicePixel(Offset(100f, 100f), IntSize(100, 200)))
    }

    @Test
    fun `a point on a frame decoded smaller than the screen maps to the screen's own pixels`() {
        // A 540x1200 frame of a 1080x2400 screen, drawn at 540x1200: each view pixel is two device pixels.
        val fitted = FittedFrame(frameWidth = 540, frameHeight = 1200, viewWidth = 540f, viewHeight = 1200f)

        assertEquals(IntOffset(1000, 2000), fitted.toDevicePixel(Offset(500f, 1000f), IntSize(1080, 2400)))
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
        val surface = MirrorSurface()
        surface.writeFrame(width = 4, height = 4, write = fill(Color.RED))
        surface.writeFrame(width = 4, height = 4, write = fill(Color.BLUE))

        val drawn = surface.drawnFrame()

        assertEquals(Color.BLUE, drawn?.getColor(0, 0))
    }

    @Test
    fun `drawing again without a new frame keeps the frame on screen`() {
        val surface = MirrorSurface()
        surface.writeFrame(width = 4, height = 4, write = fill(Color.RED))

        val first = surface.drawnFrame()

        assertSame(first, surface.drawnFrame())
    }

    @Test
    fun `a bitmap that leaves the rotation is closed instead of left to the collector`() {
        val surface = MirrorSurface()
        val written = mutableListOf<Bitmap>()
        val record: (Bitmap) -> Boolean = { bitmap ->
            written += bitmap
            true
        }
        repeat(2) { surface.writeFrame(width = 4, height = 4, write = record) }

        // A new size replaces the bitmaps of the old one.
        repeat(2) { surface.writeFrame(width = 8, height = 8, write = record) }
        surface.close()

        assertTrue(written.all(Bitmap::isClosed))
    }

    @Test
    fun `switching devices waits for a frame being written instead of closing its bitmap mid-write`() {
        val surface = MirrorSurface()
        val clearStarted = CountDownLatch(1)
        val cleared = CountDownLatch(1)
        var closedDuringWrite = true
        var clearedDuringWrite = true
        surface.writeFrame(width = 4, height = 4) { target ->
            thread {
                clearStarted.countDown()
                surface.switchTo("device-2")
                cleared.countDown()
            }
            clearStarted.await()
            clearedDuringWrite = cleared.await(CLEAR_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            closedDuringWrite = target.isClosed
            true
        }
        cleared.await()

        assertFalse(clearedDuringWrite)
        assertFalse(closedDuringWrite)
    }

    @Test
    fun `nothing is written or drawn once the surface is closed`() {
        val surface = MirrorSurface()
        surface.close()

        surface.writeFrame(width = 4, height = 4, write = fill(Color.BLUE))

        assertNull(surface.drawnFrame())
    }

    @Test
    fun `closing during a draw leaves the drawn frame open until the draw ends`() {
        val surface = MirrorSurface()
        surface.writeFrame(width = 4, height = 4, write = fill(Color.RED))
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
        surface.writeFrame(width = 4, height = 4, write = fill(Color.RED))
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
            val decoder = thread {
                repeat(FRAMES_PER_ROUND) { frame ->
                    // Alternating sizes allocates a new bitmap on most frames.
                    val side = if (frame % 2 == 0) 4 else 8
                    surface.writeFrame(width = side, height = side) { bitmap ->
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
        val surface = MirrorSurface()
        surface.writeFrame(width = 4, height = 4, write = fill(Color.RED))
        val onScreen = surface.drawnFrame()
        val written = mutableListOf<Any>()

        repeat(3) {
            surface.writeFrame(width = 4, height = 4) { bitmap ->
                written += bitmap
                fill(Color.GREEN)(bitmap)
            }
        }

        assertTrue(written.none { it === onScreen })
        assertEquals(Color.RED, onScreen?.getColor(0, 0))
    }

    @Test
    fun `switching back to a device shows its last frame until its stream sends a new one`() {
        MirrorSurface().use { surface ->
            surface.showLive("phone", Color.RED)
            surface.showLive("tablet", Color.BLUE)

            surface.switchTo("phone")

            assertEquals(Color.RED, surface.drawnFrame()?.getColor(0, 0))
            assertTrue(surface.showingKeptFrame)

            surface.writeFrame(width = 4, height = 4, write = fill(Color.GREEN))
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
            surface.writeFrame(width = 4, height = 4, write = fill(Color.GREEN))

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
            surface.writeFrame(width = 4, height = 4) { bitmap ->
                written += bitmap
                true
            }
            surface.drawnFrame()
        }

        surface.close()

        assertTrue(written.all(Bitmap::isClosed))
    }
}

// The bitmap a draw was given; it stays open after the draw as long as the surface does.
private fun MirrorSurface.drawnFrame(): Bitmap? {
    var drawn: Bitmap? = null
    drawFrame { drawn = it }
    return drawn
}

/** Mirrors [device] until a frame of [color] is on screen. */
private fun MirrorSurface.showLive(device: String, color: Int) {
    switchTo(device)
    writeFrame(width = 4, height = 4, write = fill(color))
    drawnFrame()
}

private fun fill(color: Int): (Bitmap) -> Boolean = { bitmap ->
    bitmap.erase(color)
    true
}

private const val CLOSE_RACE_ROUNDS = 200
private const val FRAMES_PER_ROUND = 50

/** Long enough for an unguarded switch to finish while the write is still running. */
private const val CLEAR_GRACE_MILLIS = 200L
