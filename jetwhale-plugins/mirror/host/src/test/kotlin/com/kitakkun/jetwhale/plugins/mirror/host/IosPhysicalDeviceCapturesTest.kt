package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class IosPhysicalDeviceCapturesTest {
    private val folder: File = Files.createTempDirectory("mirror-iphone-captures").toFile()
    private val captureScope = CoroutineScope(Job())
    private val helpers = CopyOnWriteArrayList<FakeCaptureHelperProcess>()

    @AfterTest
    fun cleanUp() {
        helpers.forEach { it.exit(0) }
        captureScope.cancel()
        folder.deleteRecursively()
    }

    @Test
    fun `a physical iOS device can record only when ffmpeg is installed`() {
        assertFalse(iosDevice(ffmpegPath = null).capabilities.recording)
        assertTrue(iosDevice(ffmpegPath = "/usr/local/bin/ffmpeg").capabilities.recording)
    }

    @Test
    fun `without ffmpeg a physical iOS device's screenshot says how to install it and starts no capture`() = runBlocking {
        val failure = assertFailsWith<DeviceControlException> { iosDevice(ffmpegPath = null).captureScreenshot() }

        assertContains(failure.message.orEmpty(), "brew install ffmpeg")
        assertTrue(helpers.isEmpty())
    }

    @Test
    fun `without ffmpeg a physical iOS device's recording says how to install it and starts no capture`() = runBlocking {
        val failure = assertFailsWith<DeviceControlException> { iosDevice(ffmpegPath = null).startRecording(File(folder, "clip.mp4")) }

        assertContains(failure.message.orEmpty(), "brew install ffmpeg")
        assertTrue(helpers.isEmpty())
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
        val source = DrainTrackingInputStream(PacedInputStream(h264Sample(ffmpegPath), chunks = SAMPLE_FRAMES, pauseMillis = 30))
        // The paced stream ends by itself, so there is nothing more to end.
        val recorder = H264FileRecorder(source, ffmpegPath, output) {}
        source.awaitDrained()

        val recorded = recorder.stop()

        val durationMillis = assertNotNull(mp4DurationMillis(recorded))
        assertTrue(durationMillis > 0, "duration was $durationMillis ms")
    }

    @Test
    fun `a screenshot is the key frame a new reader of the capture asks for, at the iPhone's own size`() = runBlocking {
        val ffmpegPath = installedFfmpegPath()
        val sample = h264Sample(ffmpegPath)
        // The sample is shorter than what ffmpeg probes before it decodes, so the capture ends after
        // it rather than leave ffmpeg waiting for more.
        val iphone = iosDevice(ffmpegPath = ffmpegPath, idleTimeout = 1.minutes) { command ->
            if (command == "keyframe") {
                send(sample)
                exit(IphoneCaptureExit.Ended.code)
            }
        }

        val png = iphone.captureScreenshot()

        assertEquals(SAMPLE_WIDTH to SAMPLE_HEIGHT, Image.makeFromEncoded(png).use { it.width to it.height })
        assertEquals(listOf("keyframe"), helpers.single().commands)
    }

    @Test
    fun `a recording started without a live view holds the capture no longer than the recording`() = runBlocking {
        val ffmpegPath = installedFfmpegPath()
        val sample = h264Sample(ffmpegPath)
        val iphone = iosDevice(ffmpegPath = ffmpegPath, idleTimeout = Duration.ZERO) { command -> if (command == "keyframe") send(sample) }

        // The stand-in capture can end before ffmpeg reads any of it, which fails the file; the
        // capture must be released either way.
        try {
            iphone.startRecording(File(folder, "clip.mp4")).stop()
        } catch (_: DeviceControlException) {
        }

        assertTrue(helpers.single().awaitStdinClosed(CAPTURE_STOP_TIMEOUT), "the capture was still held after the recording")
    }

    @Test
    fun `the iPhone's screen size is the size of the frames its capture sends`() = runBlocking {
        val iphone = iosDevice(ffmpegPath = null, idleTimeout = 1.minutes) {}

        assertEquals(IntSize(SAMPLE_WIDTH, SAMPLE_HEIGHT), iphone.screenSize())
    }

    /** An iPhone whose capture reports frames of the sample's size at once and answers [onCommand]. */
    private fun iosDevice(ffmpegPath: String?, idleTimeout: Duration, onCommand: FakeCaptureHelperProcess.(String) -> Unit) = IosPhysicalDeviceController(
        udid = "udid-1",
        deviceName = "Test iPhone",
        iosMajorVersion = 26,
        iphoneScreenCaptures = IphoneScreenCaptures(
            processLauncher = { command ->
                FakeCaptureHelperProcess(command, onCommand).also { helper ->
                    helper.reportCapturing(IntSize(SAMPLE_WIDTH, SAMPLE_HEIGHT))
                    helpers += helper
                }
            },
            idleTimeout = idleTimeout,
            failureReusePeriod = 1.minutes,
            timeSource = TimeSource.Monotonic,
            scope = captureScope,
            helperExecutable = CompletableDeferred(File("jetwhale-iphone-capture")),
        ),
        ffmpegPath = ffmpegPath,
        runnerInput = null,
    )

    private fun iosDevice(ffmpegPath: String?) = iosDevice(ffmpegPath, idleTimeout = 1.minutes) {}

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
            // An access unit delimiter starts each frame, so the capture's reader can tell them apart.
            "-frames:v", "$SAMPLE_FRAMES", "-pix_fmt", "yuv420p", "-bsf:v", "h264_metadata=aud=insert", "-f", "h264", "-y", file.path,
        ).redirectErrorStream(true).start()
        val log = encode.inputStream.use(InputStream::readAllBytes).decodeToString()
        assumeTrue("ffmpeg cannot encode H.264 here: $log", encode.waitFor() == 0)
        return file.readBytes()
    }
}

private const val SAMPLE_WIDTH = 360

private const val SAMPLE_HEIGHT = 640

private const val SAMPLE_FRAMES = 10

private val CAPTURE_STOP_TIMEOUT = 5.seconds

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

/** [source], noting when it has been read to its end. */
private class DrainTrackingInputStream(private val source: InputStream) : InputStream() {
    private val drained = CountDownLatch(1)

    fun awaitDrained() {
        drained.await()
    }

    override fun read(): Int = source.read().also { if (it == -1) drained.countDown() }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source.read(buffer, offset, length).also { if (it == -1) drained.countDown() }
}
