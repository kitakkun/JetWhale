package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.SequenceInputStream
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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

        assertEquals(found.path, findToolPath("ffmpeg", listOf(first.path, second.path)))
    }

    @Test
    fun `a file that is not executable is not taken for the tool`() {
        File(folder, "ffmpeg").writeText("not a program")

        assertNull(findToolPath("ffmpeg", listOf(folder.path)))
    }

    @Test
    fun `Homebrew's directories are searched even when PATH lacks them`() {
        val directories = toolDirectories(pathVariable = null)

        assertTrue("/opt/homebrew/bin" in directories && "/usr/local/bin" in directories)
    }

    @Test
    fun `an Android device without ffmpeg opens no stream and says how to install it`() = runBlocking {
        val controller = AndroidDeviceController(adbPath = File(folder, "adb").path, serial = "device-1", emulatorScreens = null, ffmpegPath = null)

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

        assertEquals(IntSize(360, 640), parseStreamSize(line, codec = "rawvideo"))
        assertNull(parseStreamSize("  Stream #0:0: Video: h264 (High), yuv420p(progressive), 360x640, 30 fps", codec = "rawvideo"))
    }

    @Test
    fun `a picture's new size is read from ffmpeg's note that it changed, in the current and the earlier wording`() {
        val current = "[vf#0:0 @ 0x12c00d800] Reconfiguring filter graph because video parameters changed to yuv420p(unknown, unknown), 240x320, unspecified alpha"
        val earlier = "Input stream #0:0 frame changed from size:320x240 fmt:yuv420p to size:240x320 fmt:yuv420p"

        assertEquals(IntSize(240, 320), parseChangedSize(current))
        assertEquals(IntSize(240, 320), parseChangedSize(earlier))
        assertNull(parseChangedSize("  Stream #0:0: Video: h264 (High), yuv420p(progressive), 320x240, 25 fps"))
    }

    @Test
    fun `a stream whose picture changes size partway ends so it can be opened again at the new size`() {
        val ffmpegPath = installedFfmpegPath()
        val resized = h264Sample(ffmpegPath, size = IntSize(360, 640)) + h264Sample(ffmpegPath, size = IntSize(640, 360))
        // The device keeps its stream open, so only the decoder can end it.
        val held = PipedOutputStream()
        val stream = SequenceInputStream(ByteArrayInputStream(resized), PipedInputStream(held))

        val decoding = CompletableFuture.runAsync {
            MirrorSurface().use { surface -> decodeH264Into(surface, VideoStream.H264(DeviceToolProcess(stream), ffmpegPath), IntSize(180, 320), onInput = {}) {} }
        }

        val ended = try {
            decoding.get(RESIZE_END_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            true
        } catch (_: TimeoutException) {
            false
        } finally {
            held.close()
        }
        assertTrue(ended, "decoding went on waiting for a stream whose picture changed size")
    }

    @Test
    fun `an H264 stream decoded through ffmpeg arrives as BGRA frames of the size asked for`() {
        val ffmpegPath = installedFfmpegPath()

        val sizes = decode(ffmpegPath, h264Sample(ffmpegPath, size = IntSize(360, 640)), outputSize = IntSize(180, 320))

        assertEquals(SAMPLE_FRAMES, sizes.size)
        assertEquals(setOf(IntSize(180, 320)), sizes.toSet())
    }

    @Test
    fun `an H264 stream decoded through ffmpeg keeps its own size when none is asked for`() {
        val ffmpegPath = installedFfmpegPath()

        val sizes = decode(ffmpegPath, h264Sample(ffmpegPath, size = IntSize(360, 640)), outputSize = null)

        assertEquals(setOf(IntSize(360, 640)), sizes.toSet())
    }

    @Test
    fun `a stream ffmpeg cannot decode fails with ffmpeg's reason when no size is asked for`() {
        val ffmpegPath = installedFfmpegPath()
        val notH264 = "not an H.264 stream ".repeat(500).encodeToByteArray()

        val failure = assertFailsWith<DeviceControlException> { decode(ffmpegPath, notH264, outputSize = null) }

        assertContains(failure.message.orEmpty(), "could not be decoded")
    }

    private fun installedFfmpegPath(): String {
        val ffmpegPath = findToolPath("ffmpeg", toolDirectories(System.getenv("PATH")))
        assumeTrue("ffmpeg is not installed", ffmpegPath != null)
        return checkNotNull(ffmpegPath)
    }

    /**
     * A short test pattern encoded as raw H.264 by ffmpeg itself, without B-frames as a phone's
     * encoder writes it, so each frame decodes as it arrives rather than once the stream ends.
     */
    private fun h264Sample(ffmpegPath: String, size: IntSize): ByteArray {
        val file = File(folder, "sample-${size.width}x${size.height}.h264")
        val encode = ProcessBuilder(
            ffmpegPath, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=size=${size.width}x${size.height}:rate=30",
            "-frames:v", "$SAMPLE_FRAMES", "-pix_fmt", "yuv420p", "-bf", "0", "-f", "h264", file.path,
        ).redirectErrorStream(true).start()
        val log = encode.inputStream.use(InputStream::readAllBytes).decodeToString()
        assumeTrue("ffmpeg cannot encode H.264 here: $log", encode.waitFor() == 0)
        return file.readBytes()
    }

    /** Decodes [h264] and returns the size of every frame that reached the surface. */
    private fun decode(ffmpegPath: String, h264: ByteArray, outputSize: IntSize?): List<IntSize> = MirrorSurface().use { surface ->
        val sizes = mutableListOf<IntSize>()
        decodeH264Into(surface, VideoStream.H264(DeviceToolProcess(ByteArrayInputStream(h264)), ffmpegPath), outputSize, onInput = {}) {
            surface.drawFrame { sizes += IntSize(it.width, it.height) }
        }
        sizes
    }
}

private const val SAMPLE_FRAMES = 10

private const val RESIZE_END_TIMEOUT_SECONDS = 10L

/** A device tool whose stdout is [output]. */
private class DeviceToolProcess(private val output: InputStream) : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = output

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
