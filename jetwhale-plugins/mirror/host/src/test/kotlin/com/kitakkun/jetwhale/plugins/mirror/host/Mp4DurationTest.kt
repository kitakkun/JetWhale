package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Mp4DurationTest {
    private val folder: File = Files.createTempDirectory("mp4-duration").toFile()

    @Test
    fun `the length comes from the movie header, after the media data`() {
        val file = mp4(box("ftyp", ByteArray(8)), box("mdat", ByteArray(64)), box("moov", box("mvhd", mvhdV0(timescale = 1000, duration = 4190))))

        assertEquals(4190L, mp4DurationMillis(file))
    }

    @Test
    fun `a version 1 header with its own timescale is converted to milliseconds`() {
        val file = mp4(box("moov", box("mvhd", mvhdV1(timescale = 90_000, duration = 456_300))))

        assertEquals(5070L, mp4DurationMillis(file))
    }

    @Test
    fun `a simulator recording that holds its last frame is as long as its movie, not its frames`() {
        // The layout simctl recordVideo writes: 5.41 s of movie over 2.82 s of frames at a 600 timescale.
        val track = box("trak", box("mdia", box("mdhd", mvhdV0(timescale = 600, duration = 1693))))
        val file = mp4(box("mdat", ByteArray(64)), box("moov", box("mvhd", mvhdV0(timescale = 600, duration = 3246)) + track))

        assertEquals(5410L, mp4DurationMillis(file))
    }

    @Test
    fun `a file whose recording never wrote a movie header has no length`() {
        val file = mp4(box("ftyp", ByteArray(8)), box("mdat", ByteArray(64)))

        assertNull(mp4DurationMillis(file))
    }

    @Test
    fun `a file cut off inside the header has no length`() {
        val whole = box("moov", box("mvhd", mvhdV0(timescale = 1000, duration = 4190)))
        val file = mp4(whole.copyOf(whole.size - 6))

        assertNull(mp4DurationMillis(file))
    }

    private fun mp4(vararg boxes: ByteArray): File = File(folder, "recording.mp4").apply { writeBytes(boxes.fold(ByteArray(0), ByteArray::plus)) }

    private fun box(type: String, content: ByteArray): ByteArray = bytes {
        writeInt(8 + content.size)
        writeBytes(type)
        write(content)
    }

    private fun mvhdV0(timescale: Int, duration: Int): ByteArray = bytes {
        writeInt(0)
        writeInt(0)
        writeInt(0)
        writeInt(timescale)
        writeInt(duration)
    }

    private fun mvhdV1(timescale: Int, duration: Long): ByteArray = bytes {
        writeInt(1 shl 24)
        writeLong(0)
        writeLong(0)
        writeInt(timescale)
        writeLong(duration)
    }

    private fun bytes(write: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { DataOutputStream(it).use(write) }.toByteArray()
}
