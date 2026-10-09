package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class IosPhysicalDeviceCapturesTest {
    private val folder: File = Files.createTempDirectory("mirror-iphone-captures").toFile()
    private val companionScope = CoroutineScope(Job())
    private val launched = mutableListOf<List<String>>()

    @AfterTest
    fun cleanUp() {
        companionScope.cancel()
        folder.deleteRecursively()
    }

    @Test
    fun `a physical iOS device can record only when ffmpeg is installed`() {
        assertFalse(iosDevice(ffmpegPath = null).capabilities.recording)
        assertTrue(iosDevice(ffmpegPath = "/usr/local/bin/ffmpeg").capabilities.recording)
    }

    @Test
    fun `without ffmpeg a physical iOS device's screenshot says how to install it and starts no companion`() = runBlocking {
        val failure = assertFailsWith<DeviceControlException> { iosDevice(ffmpegPath = null).captureScreenshot() }

        assertContains(failure.message.orEmpty(), "brew install ffmpeg")
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `without ffmpeg a physical iOS device's recording says how to install it and starts no companion`() = runBlocking {
        val failure = assertFailsWith<DeviceControlException> { iosDevice(ffmpegPath = null).startRecording(File(folder, "clip.mp4")) }

        assertContains(failure.message.orEmpty(), "brew install ffmpeg")
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `the recording command copies the H264 into an mp4 without encoding it again`() {
        val command = ffmpegRemuxCommand("ffmpeg", "/captures/clip.mp4")

        assertContains(command.joinToString(" "), "-f h264 -i pipe:0 -c copy -movflags +faststart")
        assertContains(command.joinToString(" "), "-use_wallclock_as_timestamps 1")
        assertEquals("/captures/clip.mp4", command.last())
    }

    @Test
    fun `a screenshot from an H264 stream is its first frame as a PNG at the stream's own size`() {
        val ffmpegPath = installedFfmpegPath()

        val png = firstH264FrameAsPng(ByteArrayInputStream(h264Sample(ffmpegPath)), ffmpegPath, timeoutMillis = 10_000)

        assertEquals(SAMPLE_WIDTH to SAMPLE_HEIGHT, Image.makeFromEncoded(png).use { it.width to it.height })
    }

    @Test
    fun `a screenshot from a stream that is not H264 fails instead of returning nothing`() {
        val ffmpegPath = installedFfmpegPath()
        val notH264 = ByteArrayInputStream("not an H.264 stream ".repeat(500).encodeToByteArray())

        val failure = assertFailsWith<DeviceControlException> { firstH264FrameAsPng(notH264, ffmpegPath, timeoutMillis = 10_000) }

        assertContains(failure.message.orEmpty(), "sent no frame")
    }

    @Test
    fun `a recording stopped by ending its source is a finished mp4 with a length`() {
        val ffmpegPath = installedFfmpegPath()
        val output = File(folder, "clip.mp4")
        val source = PacedStreamProcess(PacedInputStream(h264Sample(ffmpegPath), chunks = SAMPLE_FRAMES, pauseMillis = 30))
        val recorder = H264FileRecorder(source, ffmpegPath, output)
        source.awaitDrained()

        val recorded = recorder.stop()

        val durationMillis = assertNotNull(mp4DurationMillis(recorded))
        assertTrue(durationMillis > 0, "duration was $durationMillis ms")
    }

    @Test
    fun `a recording started without a live view holds the companion no longer than the recording`() = runBlocking {
        val ffmpegPath = installedFfmpegPath()
        val idbPath = fakeIdbPath(h264Sample(ffmpegPath))
        val companionProcesses = mutableListOf<ReadyCompanionProcess>()
        val companions = IdbCompanions(
            idbCompanionPath = "idb_companion",
            idbPath = idbPath,
            launcher = { ReadyCompanionProcess().also(companionProcesses::add) },
            commands = { },
            ports = { 10_000 },
            idleTimeout = Duration.ZERO,
            scope = companionScope,
        )
        val iphone = IosPhysicalDeviceController(udid = "udid-1", iosMajorVersion = 26, companions = companions, ffmpegPath = ffmpegPath, runnerInput = null)

        // Record, stop, then ask the size, as a recording started from the grid does. The stand-in
        // stream can stop before ffmpeg reads any of it, which fails the file; the companion must
        // be released either way.
        try {
            iphone.startRecording(File(folder, "clip.mp4")).stop()
        } catch (_: DeviceControlException) {
        }
        iphone.screenSize()

        assertTrue(companionProcesses.last().stopped.await(COMPANION_STOP_WAIT_SECONDS, TimeUnit.SECONDS), "the companion was still held after the recording")
    }

    @Test
    fun `the device's stream asks the encoder for a quality that holds up while the screen moves`() = runBlocking {
        val ffmpegPath = installedFfmpegPath()
        val idbPath = fakeIdbPath(h264Sample(ffmpegPath))
        val companions = IdbCompanions(
            idbCompanionPath = "idb_companion",
            idbPath = idbPath,
            launcher = { ReadyCompanionProcess() },
            commands = { },
            ports = { 10_000 },
            idleTimeout = Duration.ZERO,
            scope = companionScope,
        )
        val iphone = IosPhysicalDeviceController(udid = "udid-1", iosMajorVersion = 26, companions = companions, ffmpegPath = ffmpegPath, runnerInput = null)

        iphone.captureScreenshot()

        val streamCall = File(folder, IDB_CALLS).readLines().single { it.startsWith("video-stream") }
        assertContains(streamCall, "--compression-quality 0.8")
    }

    @Test
    fun `the streaming device's newest frame is encoded as a PNG at the size it was decoded`() {
        MirrorSurface().use { surface ->
            surface.switchTo("iphone")
            surface.startStream().writeFrame(width = 6, height = 12, colorType = ColorType.BGRA_8888) {
                it.erase(Color.RED)
                true
            }

            val png = assertNotNull(surface.newestFramePng("iphone"))

            assertEquals(6 to 12, Image.makeFromEncoded(png).use { it.width to it.height })
            assertNull(surface.newestFramePng("another-device"))
        }
    }

    private fun iosDevice(ffmpegPath: String?) = IosPhysicalDeviceController(
        udid = "udid-1",
        iosMajorVersion = 26,
        companions = IdbCompanions(
            idbCompanionPath = "idb_companion",
            idbPath = "idb",
            launcher = { command ->
                launched += command
                throw deviceControlError("no processes in tests")
            },
            commands = { },
            ports = { 10_000 },
            idleTimeout = 3.minutes,
            scope = companionScope,
        ),
        ffmpegPath = ffmpegPath,
        runnerInput = null,
    )

    /** An idb stand-in that describes a 360x640 screen, streams [h264] once, and notes each call in [IDB_CALLS]. */
    private fun fakeIdbPath(h264: ByteArray): String {
        val sample = File(folder, "stream.h264").apply { writeBytes(h264) }
        val calls = File(folder, IDB_CALLS)
        val script = File(folder, "idb").apply {
            writeText(
                """
                #!/bin/sh
                echo "${'$'}*" >> '${calls.path}'
                case "${'$'}1" in
                  describe) echo '{"screen_dimensions":{"width":$SAMPLE_WIDTH,"height":$SAMPLE_HEIGHT,"density":3}}' ;;
                  video-stream) exec cat '${sample.path}' ;;
                esac
                """.trimIndent() + "\n",
            )
            setExecutable(true)
        }
        return script.path
    }

    private fun installedFfmpegPath(): String {
        val ffmpegPath = findToolPath("ffmpeg", toolDirectories(loginShellPathVariable = null, pathVariable = System.getenv("PATH")))
        assumeTrue("ffmpeg is not installed", ffmpegPath != null)
        return checkNotNull(ffmpegPath)
    }

    /** A short test pattern encoded as raw H.264 by ffmpeg itself. */
    private fun h264Sample(ffmpegPath: String): ByteArray {
        val file = File(folder, "sample.h264")
        val encode = ProcessBuilder(
            ffmpegPath, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=size=${SAMPLE_WIDTH}x$SAMPLE_HEIGHT:rate=30",
            "-frames:v", "$SAMPLE_FRAMES", "-pix_fmt", "yuv420p", "-f", "h264", "-y", file.path,
        ).redirectErrorStream(true).start()
        val log = encode.inputStream.use(InputStream::readAllBytes).decodeToString()
        assumeTrue("ffmpeg cannot encode H.264 here: $log", encode.waitFor() == 0)
        return file.readBytes()
    }
}

private const val SAMPLE_WIDTH = 360

private const val SAMPLE_HEIGHT = 640

private const val SAMPLE_FRAMES = 10

private const val COMPANION_STOP_WAIT_SECONDS = 5L

private const val IDB_CALLS = "idb-calls.txt"

/** An idb companion that reports its port at once and ends when destroyed. */
internal class ReadyCompanionProcess : Process() {
    private val pipe = PipedOutputStream()
    private val output = PipedInputStream(pipe)
    private var destroyed = false

    /** Counts down once the companion is stopped, which happens when nothing holds it any more. */
    val stopped = CountDownLatch(1)

    init {
        pipe.write("{\"grpc_port\":10000}\n".toByteArray())
        pipe.flush()
    }

    override fun getInputStream(): InputStream = output

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun waitFor(): Int = 0

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = destroyed

    override fun exitValue(): Int = 0

    override fun destroy() {
        destroyed = true
        pipe.close()
        stopped.countDown()
    }

    override fun destroyForcibly(): Process = apply { destroy() }
}

/** Hands out [bytes] in [chunks] pieces, pausing between them the way a live stream arrives. */
private class PacedInputStream(bytes: ByteArray, chunks: Int, private val pauseMillis: Long) : InputStream() {
    private val source = ByteArrayInputStream(bytes)
    private val chunkSize = (bytes.size + chunks - 1) / chunks
    private var leftInChunk = chunkSize

    override fun read(): Int {
        val buffer = ByteArray(1)
        return if (read(buffer, 0, 1) == -1) -1 else buffer[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (leftInChunk == 0) {
            Thread.sleep(pauseMillis)
            leftInChunk = chunkSize
        }
        val read = source.read(buffer, offset, minOf(length, leftInChunk))
        if (read > 0) leftInChunk -= read
        return read
    }
}

/** A device tool whose stdout is [output]; destroying it does nothing once the output is read. */
private class PacedStreamProcess(private val output: InputStream) : Process() {
    private val drained = CountDownLatch(1)
    private val tracked = object : InputStream() {
        override fun read(): Int = output.read().also { if (it == -1) drained.countDown() }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = output.read(buffer, offset, length).also { if (it == -1) drained.countDown() }
    }

    fun awaitDrained() {
        drained.await()
    }

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = tracked

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
