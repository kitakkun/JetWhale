package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.SequenceInputStream
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceMirrorStreamingTest {
    private val root: File = Files.createTempDirectory("mirror-captures").toFile()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val notices = MirrorNotices(scope)
    private val mirror = DeviceMirror(
        discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), companions = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList())),
        developmentTeamSetting = emptyDevelopmentTeamSetting(),
        captures = MirrorCaptures(root, storage = InMemoryPluginStorage(emptyMap()), scope = scope, zone = ZoneOffset.UTC, notices = notices, ffmpegPath = CompletableDeferred(value = null), clipboard = CaptureClipboard(osascriptPath = null)),
        notices = notices,
        scope = scope,
    )

    private val tools: File = Files.createTempDirectory("mirror-tools").toFile()
    private val fakeFfmpeg = File(tools, "ffmpeg").apply {
        writeText("#!/bin/sh\ncat > /dev/null\n")
        setExecutable(true)
    }

    @AfterTest
    fun cleanUp() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        root.deleteRecursively()
        tools.deleteRecursively()
    }

    @Test
    fun `switching away from a device whose stream fails to read as it closes ends its session quietly`() = runBlocking {
        val controller = ClosingStream()
        val streaming = MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)
        val ended = CompletableDeferred<Throwable?>()
        val session = scope.launch { mirror.mirror(streaming) }
        session.invokeOnCompletion(ended::complete)

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.reading.await() }
        session.cancel()

        val cause = withTimeout(STREAM_END_TIMEOUT_MILLIS) { ended.await() }
        assertTrue(cause is CancellationException, "the session ended with $cause")
    }

    @Test
    fun `a simulator's whole images map taps through their own size and are not reopened for a resized view`() = runBlocking {
        val controller = WholeImagesSimulator()
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("sim-1", "iPhone 17", DeviceKind.IosSimulator, osVersion = "iOS 26.2"), controller)) }
        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.imageShown.await() }

        mirror.surface.viewSize = IntSize(270, 600)
        val reopened = withTimeoutOrNull(RESIZED_VIEW_SETTLE_MILLIS) { controller.reopened.await() }
        val deviceSize = mirror.surface.deviceSize
        session.cancel()

        assertNull(deviceSize)
        assertNull(reopened)
    }

    @Test
    fun `a stream that ends without a resize lets the mirror move on`() = runBlocking {
        val controller = EndingStream(power = null)
        val streaming = MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)
        val session = scope.launch { mirror.mirror(streaming) }

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.reopened.await() }
        session.cancel()
    }

    @Test
    fun `a stream opened before its view is laid out waits for the view instead of asking for the full screen`() = runBlocking {
        val controller = EndingStream(power = null)
        val streaming = MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)
        val session = scope.launch { mirror.mirror(streaming) }
        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.screenSizeRead.await() }
        mirror.surface.viewSize = IntSize(540, 1200)

        val wanted = withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.firstWanted.await() }
        session.cancel()

        assertEquals(IntSize(540, 1200), wanted)
    }

    @Test
    fun `an emulator's own stream is shown as it arrives with no screenshot patching a still screen`() = runBlocking {
        val controller = StillEmulator()
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        val screenshot = withTimeoutOrNull(STILL_SCREEN_PATCH_WINDOW_MILLIS) { controller.screenshotTaken.await() }
        session.cancel()

        assertNull(screenshot)
    }

    @Test
    fun `a mirrored Android device whose screen is off says so until mirroring stops`() = runBlocking {
        val asleep = ScreenPower(awake = false, locked = true)
        val controller = EndingStream(power = asleep)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.readTwice.await() }
        val whileMirroring = mirror.screenPower
        session.cancel()
        session.join()

        assertEquals(asleep, whileMirroring)
        assertNull(mirror.screenPower)
    }

    @Test
    fun `a foldable that folds while mirrored is streamed again at its new screen size`() = runBlocking {
        val controller = FoldingEmulator(stateNow = { mirror.state })
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel Fold", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        withTimeout(SCREEN_CHANGE_TIMEOUT_MILLIS) { controller.reopenedStreamRead.await() }
        val deviceSize = mirror.surface.deviceSize
        session.cancel()

        assertEquals(FOLDED_SCREEN, deviceSize)
    }

    @Test
    fun `a stream that ends is followed by the next without a connecting screen`() = runBlocking<Unit> {
        val controller = FoldingEmulator(stateNow = { mirror.state })
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel Fold", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        val stateWhenReopened = withTimeout(SCREEN_CHANGE_TIMEOUT_MILLIS) { controller.reopened.await() }
        session.cancel()

        assertEquals(MirrorState.Streaming, stateWhenReopened)
    }

    @Test
    fun `a screenshot of a screen turned since the stream opened opens a new stream instead of showing among its frames`() = runBlocking {
        assumeShellScriptsLaunch()
        val turned = Surface.makeRasterN32Premul(20, 10).use { surface -> surface.makeImageSnapshot().use { checkNotNull(it.encodeToData(EncodedImageFormat.PNG)).bytes } }
        val controller = ScreenrecordDevice(fakeFfmpeg.path, sent = ByteArray(4_096), screenshot = turned)
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller)) }

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.reopened.await() }
        val shown = mirror.surface.newestFramePng("device-1")
        session.cancel()

        assertNull(shown)
    }

    @Test
    fun `a screenrecord stream that sends nothing falls back to screenshots and says so`() = runBlocking {
        assumeShellScriptsLaunch()
        val controller = ScreenrecordDevice(fakeFfmpeg.path, sent = ByteArray(0), screenshot = null)
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller)) }

        withTimeout(SILENT_STREAM_TIMEOUT_MILLIS) { controller.polled.await() }
        val state = mirror.state
        session.cancel()

        assertEquals(MirrorState.Polling("the video stream sent no picture"), state)
    }

    @Test
    fun `screenshots shown in place of a stream map taps through their own size`() = runBlocking {
        assumeShellScriptsLaunch()
        val controller = ScreenrecordDevice(fakeFfmpeg.path, sent = ByteArray(0), screenshot = null)
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller)) }

        withTimeout(SILENT_STREAM_TIMEOUT_MILLIS) { controller.polled.await() }
        val deviceSize = mirror.surface.deviceSize
        session.cancel()

        assertNull(deviceSize)
    }

    @Test
    fun `a device on screenshots tries its stream again without leaving them for a connecting screen`() = runBlocking<Unit> {
        val controller = FlakyStreamDevice(stateNow = { mirror.state })
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        val stateWhenTriedAgain = withTimeout(STREAM_RETRY_TIMEOUT_MILLIS) { controller.triedAgain.await() }
        session.cancel()

        assertIs<MirrorState.Polling>(stateWhenTriedAgain)
    }

    @Test
    fun `a screenrecord stream that sends bytes but no finished frame keeps streaming`() = runBlocking {
        assumeShellScriptsLaunch()
        val controller = ScreenrecordDevice(fakeFfmpeg.path, sent = ByteArray(4_096), screenshot = null)
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller)) }

        val polled = withTimeoutOrNull(SILENT_STREAM_TIMEOUT_MILLIS) { controller.polled.await() }
        val state = mirror.state
        session.cancel()

        assertNull(polled)
        assertEquals(MirrorState.Streaming, state)
    }

    @Test
    fun `a screenshot that is not an image fails as a notice and leaves the mirror running`() = runBlocking {
        assumeShellScriptsLaunch()
        val warning = "[Warning] Multiple displays were found, but no display id was specified!".encodeToByteArray()
        val controller = ScreenrecordDevice(fakeFfmpeg.path, sent = ByteArray(0), screenshot = warning)
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller)) }

        withTimeout(SILENT_STREAM_TIMEOUT_MILLIS) { controller.polledAgain.await() }
        val state = mirror.state
        val running = session.isActive
        session.cancel()

        assertEquals(MirrorState.Failed("the screenshot could not be read as an image"), state)
        assertTrue(running)
    }

    @Test
    fun `a device without screen power is never asked for it`() = runBlocking {
        val controller = EndingStream(power = null)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5558", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.reopened.await() }
        session.cancel()

        assertEquals(0, controller.screenPowerReads.get())
        assertNull(mirror.screenPower)
    }
}

private const val STREAM_END_TIMEOUT_MILLIS = 5_000L

/** Longer than the mirror takes to read a mirrored Android device's screen size again. */
private const val SCREEN_CHANGE_TIMEOUT_MILLIS = 5_000L

/** Longer than the mirror waits for a first sign of life from a stream. */
private const val SILENT_STREAM_TIMEOUT_MILLIS = 9_000L

/** Longer than screenshots stand in after a first failed stream before it is tried again. */
private const val STREAM_RETRY_TIMEOUT_MILLIS = 8_000L

private val UNFOLDED_SCREEN = IntSize(2208, 1840)

private val FOLDED_SCREEN = IntSize(1080, 2092)

/** Longer than the mirror waits before patching a still screenrecord stream with a screenshot. */
private const val STILL_SCREEN_PATCH_WINDOW_MILLIS = 1_500L

/** Longer than a resized view takes to settle and reopen a stream that follows the view's size. */
private const val RESIZED_VIEW_SETTLE_MILLIS = 2_000L

/**
 * An emulator whose stream blocks in its read until the mirror closes it, and then throws "Stream
 * closed", as a socket closed under a blocked read does.
 */
private class ClosingStream : DeviceController {
    /** Completes once the mirror is blocked reading the stream. */
    val reading = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val frames = ClosedUnderReadFrames(reading)
        return VideoStream.EmulatorRgba(frames = frames, cancel = frames::close)
    }

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/** A simulator whose runner sends one whole image of its screen and then nothing, as a still screen does. */
private class WholeImagesSimulator : DeviceController {
    private val opened = AtomicInteger()

    /** Completes if the mirror opens a second stream. */
    val reopened = CompletableDeferred<Unit>()

    /** Completes once the mirror has shown the image. */
    val imageShown = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenSize(): IntSize = IntSize(1206, 2622)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        if (opened.incrementAndGet() == 2) reopened.complete(Unit)
        val image = Surface.makeRasterN32Premul(12, 26).use { surface -> surface.makeImageSnapshot().use { checkNotNull(it.encodeToData(EncodedImageFormat.JPEG)).bytes } }
        return VideoStream.EncodedImages(
            flow {
                emit(image)
                imageShown.complete(Unit)
                awaitCancellation()
            },
        )
    }

    override suspend fun captureScreenshot(): ByteArray = awaitCancellation()

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/** An emulator whose gRPC stream sends one frame and then nothing, as a still screen does. */
private class StillEmulator : DeviceController {
    /** Completes if the mirror asks for a screenshot. */
    val screenshotTaken = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun captureScreenshot(): ByteArray {
        screenshotTaken.complete(Unit)
        throw deviceControlError("no screenshots in tests")
    }

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val writer = PipedOutputStream()
        val frames = PipedInputStream(writer, ONE_FRAME_MESSAGE.size)
        writer.write(ONE_FRAME_MESSAGE)
        writer.flush()
        return VideoStream.EmulatorRgba(frames = frames, cancel = writer::close)
    }

    override suspend fun release() = Unit
}

/** An emulator that folds once its first stream is open, whose streams send nothing until closed. */
private class FoldingEmulator(private val stateNow: () -> MirrorState) : DeviceController {
    private val opened = AtomicInteger()

    /** Completes with the mirror's state once it opens a second stream. */
    val reopened = CompletableDeferred<MirrorState>()

    /** Completes once the mirror reads the second stream, which it does after taking that stream's screen size. */
    val reopenedStreamRead = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenSize(): IntSize = if (opened.get() == 0) UNFOLDED_SCREEN else FOLDED_SCREEN

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val isReopened = opened.incrementAndGet() == 2
        if (isReopened) reopened.complete(stateNow())
        val writer = PipedOutputStream()
        val frames = PipedInputStream(writer)
        if (!isReopened) return VideoStream.EmulatorRgba(frames = frames, cancel = writer::close)
        return VideoStream.EmulatorRgba(frames = FirstReadSignallingInputStream(frames, reopenedStreamRead), cancel = writer::close)
    }

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/** [stream], completing [firstRead] once something reads from it. */
private class FirstReadSignallingInputStream(stream: InputStream, private val firstRead: CompletableDeferred<Unit>) : FilterInputStream(stream) {
    override fun read(): Int {
        firstRead.complete(Unit)
        return super.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        firstRead.complete(Unit)
        return super.read(b, off, len)
    }
}

/**
 * A device whose screenrecord stream sends [sent] and then stays open until closed, decoded by the
 * ffmpeg at [ffmpegPath]. Its screenshots are [screenshot], or never finish when it is null, so the
 * mirror's state stays as it set it.
 */
private class ScreenrecordDevice(private val ffmpegPath: String, private val sent: ByteArray, private val screenshot: ByteArray?) : DeviceController {
    private val screenshots = AtomicInteger()
    private val opened = AtomicInteger()

    /** Completes once the mirror opens a second stream. */
    val reopened = CompletableDeferred<Unit>()

    /**
     * Completes when the mirror asks for a second screenshot. The first fills in a still screen;
     * another comes only from falling back to screenshots.
     */
    val polled = CompletableDeferred<Unit>()

    /** Completes when the mirror asks for a third screenshot, once the second one's outcome is shown. */
    val polledAgain = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        if (opened.incrementAndGet() == 2) reopened.complete(Unit)
        return VideoStream.H264(HeldOpenProcess(sent), ffmpegPath)
    }

    override suspend fun screenSize(): IntSize = FOLDED_SCREEN

    override suspend fun captureScreenshot(): ByteArray {
        when (screenshots.incrementAndGet()) {
            2 -> polled.complete(Unit)
            3 -> polledAgain.complete(Unit)
        }
        return screenshot ?: awaitCancellation()
    }

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/**
 * An emulator whose first stream fails to open and whose next one sends a frame. Its screenshots
 * never finish, so the mirror's state stays as it set it.
 */
private class FlakyStreamDevice(private val stateNow: () -> MirrorState) : DeviceController {
    private val opened = AtomicInteger()

    /** Completes with the mirror's state when it opens a second stream. */
    val triedAgain = CompletableDeferred<MirrorState>()

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        if (opened.incrementAndGet() == 1) throw deviceControlError("adb is restarting")
        triedAgain.complete(stateNow())
        val writer = PipedOutputStream()
        val frames = PipedInputStream(writer, ONE_FRAME_MESSAGE.size)
        writer.write(ONE_FRAME_MESSAGE)
        writer.flush()
        return VideoStream.EmulatorRgba(frames = frames, cancel = writer::close)
    }

    override suspend fun captureScreenshot(): ByteArray = awaitCancellation()

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/** A process that writes [output] and then keeps its stdout open until it is destroyed. */
private class HeldOpenProcess(output: ByteArray) : Process() {
    private val destroyed = CompletableDeferred<Unit>()

    private val stdout = SequenceInputStream(
        ByteArrayInputStream(output),
        object : InputStream() {
            override fun read(): Int {
                runBlocking { destroyed.await() }
                return -1
            }
        },
    )

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() {
        destroyed.complete(Unit)
    }
}

/** A gRPC `Image` message of one 1x1 RGBA frame. */
private val ONE_FRAME_MESSAGE = grpcMessage(byteArrayOf(0x0a, 0x04, 0x18, 0x01, 0x20, 0x01, 0x22, 0x04, 1, 2, 3, 4))

/** Frames whose read blocks until they are closed, and then fails. */
private class ClosedUnderReadFrames(private val reading: CompletableDeferred<Unit>) : InputStream() {
    private val closed = CompletableDeferred<Unit>()

    override fun read(): Int {
        reading.complete(Unit)
        runBlocking { closed.await() }
        throw IOException("Stream closed")
    }

    override fun close() {
        closed.complete(Unit)
    }
}
