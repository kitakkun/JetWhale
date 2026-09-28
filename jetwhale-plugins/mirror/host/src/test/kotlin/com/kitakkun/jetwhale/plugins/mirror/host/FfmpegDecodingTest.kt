package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FfmpegDecodingTest {
    private val folder: File = Files.createTempDirectory("mirror-ffmpeg").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a tool is found in the first directory that has it as an executable`() {
        val first = File(folder, "first").apply { mkdirs() }
        val second = File(folder, "second").apply { mkdirs() }
        val found = File(second, "ffmpeg").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }

        assertEquals(found.path, findTool("ffmpeg", listOf(first.path, second.path)))
    }

    @Test
    fun `a file that is not executable is not taken for the tool`() {
        File(folder, "ffmpeg").writeText("not a program")

        assertNull(findTool("ffmpeg", listOf(folder.path)))
    }

    @Test
    fun `Homebrew's directories are searched even when PATH lacks them`() {
        val directories = toolDirectories(path = null)

        assertTrue("/opt/homebrew/bin" in directories && "/usr/local/bin" in directories)
    }

    @Test
    fun `an Android device without ffmpeg opens no stream and says how to install it`() = runBlocking {
        val controller = AndroidDeviceController(adb = File(folder, "adb").path, serial = "device-1", emulatorScreens = null, ffmpeg = null)

        val failure = assertFailsWith<DeviceControlException> { controller.openVideoStream(wanted = null) }

        assertContains(failure.message.orEmpty(), "brew install ffmpeg")
    }

    @Test
    fun `the decode command shrinks frames only when a size is asked for`() {
        assertContains(ffmpegDecodeCommand("ffmpeg", IntSize(540, 1200)), "scale=540:1200:flags=area")
        assertTrue(ffmpegDecodeCommand("ffmpeg", outputSize = null).none { it.startsWith("scale=") })
    }

    @Test
    fun `the frame size is read from ffmpeg's description of its rawvideo output`() {
        val line = "  Stream #0:0: Video: rawvideo (BGRA / 0x41524742), bgra(pc, gbr/unknown/unknown, progressive), 360x640 [SAR 1:1 DAR 9:16], q=2-31, 30 fps"

        assertEquals(IntSize(360, 640), parseRawvideoSize(line))
        assertNull(parseRawvideoSize("  Stream #0:0: Video: h264 (High), yuv420p(progressive), 360x640, 30 fps"))
    }

    @Test
    fun `an H264 stream decoded through ffmpeg arrives as BGRA frames of the size asked for`() {
        val ffmpeg = installedFfmpeg()

        val sizes = decode(ffmpeg, h264Sample(ffmpeg), outputSize = IntSize(180, 320))

        assertEquals(SAMPLE_FRAMES, sizes.size)
        assertEquals(setOf(IntSize(180, 320)), sizes.toSet())
    }

    @Test
    fun `an H264 stream decoded through ffmpeg keeps its own size when none is asked for`() {
        val ffmpeg = installedFfmpeg()

        val sizes = decode(ffmpeg, h264Sample(ffmpeg), outputSize = null)

        assertEquals(setOf(IntSize(360, 640)), sizes.toSet())
    }

    @Test
    fun `a stream ffmpeg cannot decode fails with ffmpeg's reason when no size is asked for`() {
        val ffmpeg = installedFfmpeg()
        val notH264 = "not an H.264 stream ".repeat(500).encodeToByteArray()

        val failure = assertFailsWith<DeviceControlException> { decode(ffmpeg, notH264, outputSize = null) }

        assertContains(failure.message.orEmpty(), "could not be decoded")
    }

    private fun installedFfmpeg(): String {
        val ffmpeg = findTool("ffmpeg", toolDirectories(System.getenv("PATH")))
        assumeTrue("ffmpeg is not installed", ffmpeg != null)
        return checkNotNull(ffmpeg)
    }

    /** A short test pattern encoded as raw H.264 by ffmpeg itself. */
    private fun h264Sample(ffmpeg: String): ByteArray {
        val file = File(folder, "sample.h264")
        val encode = ProcessBuilder(
            ffmpeg, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=size=360x640:rate=30",
            "-frames:v", "$SAMPLE_FRAMES", "-pix_fmt", "yuv420p", "-f", "h264", file.path,
        ).redirectErrorStream(true).start()
        val log = encode.inputStream.use(InputStream::readAllBytes).decodeToString()
        assumeTrue("ffmpeg cannot encode H.264 here: $log", encode.waitFor() == 0)
        return file.readBytes()
    }

    /** Decodes [h264] and returns the size of every frame that reached the surface. */
    private fun decode(ffmpeg: String, h264: ByteArray, outputSize: IntSize?): List<IntSize> = MirrorSurface().use { surface ->
        val sizes = mutableListOf<IntSize>()
        decodeH264Into(surface, VideoStream.H264(ByteProcess(h264), ffmpeg), outputSize) {
            surface.drawFrame { sizes += IntSize(it.width, it.height) }
        }
        sizes
    }
}

private const val SAMPLE_FRAMES = 10

/** A device tool that has written [output] and ended. */
private class ByteProcess(private val output: ByteArray) : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = ByteArrayInputStream(output)

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
