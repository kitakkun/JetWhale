package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.ByteArrayInputStream
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoStreamTest {
    private val simulator = IntSize(1179, 2556)

    @Test
    fun `a raw stream is asked for a width that is a multiple of 16 so its rows carry no padding`() {
        val layout = rawBgraLayout(simulator, IntSize(407, 882))

        assertEquals(400, layout.frameSize.width)
        assertEquals(0, layout.frameSize.width % 16)
    }

    @Test
    fun `the scale lands on the frame size idb then writes`() {
        // idb writes floor(screen * scale) pixels on each side, as measured against a simulator.
        val layout = rawBgraLayout(simulator, IntSize(592, 1284))

        assertEquals(layout.frameSize.width, floor(simulator.width * layout.scale).toInt())
        assertEquals(layout.frameSize.height, floor(simulator.height * layout.scale).toInt())
    }

    @Test
    fun `a view as large as the screen keeps the screen width rounded down to a multiple of 16`() {
        val layout = rawBgraLayout(simulator, null)

        assertEquals(IntSize(1168, 2533), layout.frameSize)
        assertTrue(layout.scale < 1.0)
    }

    @Test
    fun `raw frames reach the surface one whole frame at a time`() {
        val size = IntSize(16, 2)
        val frameBytes = size.width * size.height * 4
        val stream = ByteArrayInputStream(ByteArray(frameBytes * 3) { (it / frameBytes).toByte() })
        MirrorSurface().use { surface ->
            var frames = 0

            readRawBgraInto(surface, stream, size) { frames++ }

            assertEquals(3, frames)
            surface.drawFrame { bitmap -> assertEquals(2.toByte(), bitmap.readPixels()?.first()) }
        }
    }

    @Test
    fun `a stream that ends partway through a frame hands over only the whole frames`() {
        val size = IntSize(16, 2)
        val frameBytes = size.width * size.height * 4
        MirrorSurface().use { surface ->
            var frames = 0

            readRawBgraInto(surface, ByteArrayInputStream(ByteArray(frameBytes + frameBytes / 2)), size) { frames++ }

            assertEquals(1, frames)
        }
    }
}
