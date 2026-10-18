package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class VideoStreamTest {
    @Test
    fun `an encoded frame reaches the surface at its own size`() = runTest {
        MirrorSurface().use { surface ->
            var frames = 0

            readEncodedImagesInto(surface.startStream(), VideoStream.EncodedImages(flowOf(readResourceBytes("/runner-frames/portrait.jpg")))) { frames++ }

            assertEquals(1, frames)
            surface.drawFrame { bitmap -> assertEquals(4 to 8, bitmap.width to bitmap.height) }
        }
    }

    @Test
    fun `a JPEG whose EXIF orientation turns it reaches the surface turned as the screen is`() = runTest {
        MirrorSurface().use { surface ->
            readEncodedImagesInto(surface.startStream(), VideoStream.EncodedImages(flowOf(readResourceBytes("/runner-frames/portrait-pixels-turned-to-landscape.jpg")))) {}

            surface.drawFrame { bitmap -> assertEquals(8 to 4, bitmap.width to bitmap.height) }
        }
    }

    @Test
    fun `a frame that is not an image ends the stream with the reason`() = runTest {
        MirrorSurface().use { surface ->
            val failure = assertFailsWith<DeviceControlException> {
                readEncodedImagesInto(surface.startStream(), VideoStream.EncodedImages(flowOf("<html>".encodeToByteArray()))) {}
            }

            assertEquals("a frame of the video stream could not be read as an image", failure.message)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `closing an encoded stream ends its reading while it waits for the next frame`() = runTest {
        val stream = VideoStream.EncodedImages(flow { awaitCancellation() })
        MirrorSurface().use { surface ->
            val reading = launch { readEncodedImagesInto(surface.startStream(), stream) {} }
            runCurrent()

            stream.close()
            withTimeout(1.seconds) { reading.join() }

            assertFalse(reading.isCancelled)
        }
    }

    private fun readResourceBytes(path: String): ByteArray = checkNotNull(VideoStreamTest::class.java.getResource(path)) { "$path is missing" }.readBytes()
}
