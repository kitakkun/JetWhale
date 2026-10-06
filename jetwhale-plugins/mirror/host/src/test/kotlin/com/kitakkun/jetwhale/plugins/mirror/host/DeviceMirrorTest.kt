package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.ByteArrayInputStream
import java.io.File
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

class DeviceMirrorTest {
    private val root: File = Files.createTempDirectory("mirror-recordings").toFile()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val recorder = SlowRecorder()
    private val device = MirrorDevice(DeviceListing("emulator-5554", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), recorder)
    private val notices = MirrorNotices(scope)
    private val mirror = DeviceMirror(
        discovery = DeviceDiscovery(MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList())),
        captures = MirrorCaptures(root, storage = null, scope = scope, zone = ZoneOffset.UTC, notices = notices, ffmpegPath = null, clipboard = CaptureClipboard(osascriptPath = null)),
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
    fun `a screenshot that fails becomes a notice that retries that device`() = runBlocking {
        val notice = MirrorNotice.screenshotsSaved(listOf(mirror.screenshotResultOf(device)))

        assertTrue(notice.isError)
        assertEquals(listOf<NoticeAction>(NoticeAction.RetryScreenshots(listOf("emulator-5554"))), notice.actions)
    }

    @Test
    fun `recordings started at once leave one recorder running and refuse the others`() = runBlocking {
        val outcomes = List(SIMULTANEOUS_CALLS) { async(Dispatchers.Default) { runCatching { mirror.startRecording(device) } } }.awaitAll()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, recorder.started.get())
    }

    @Test
    fun `stops sent at once finish the one recording once and refuse the rest`() = runBlocking {
        mirror.startRecording(device)

        val outcomes = List(SIMULTANEOUS_CALLS) { async(Dispatchers.Default) { runCatching { mirror.stopRecording(deviceId = null) } } }.awaitAll()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, recorder.stopped.get())
    }

    @Test
    fun `a recording started while another stops waits for the stop and then runs`() = runBlocking {
        mirror.startRecording(device)

        val stop = async(Dispatchers.Default) { mirror.stopRecording(deviceId = null) }
        recorder.stopping.await()
        mirror.startRecording(device)
        stop.await()
        mirror.stopRecording(deviceId = null)

        assertEquals(2, recorder.started.get())
        assertEquals(2, recorder.stopped.get())
    }

    @Test
    fun `a stop clicked twice while the first is still saving stops once and starts nothing`() = runBlocking {
        val clicks = CoroutineScope(Job(scope.coroutineContext.job) + Dispatchers.Unconfined)
        val clicked = DeviceMirror(
            discovery = DeviceDiscovery(MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList())),
            captures = MirrorCaptures(root, storage = null, scope = clicks, zone = ZoneOffset.UTC, notices = notices, ffmpegPath = null, clipboard = CaptureClipboard(osascriptPath = null)),
            notices = notices,
            scope = clicks,
        )
        clicked.select(device.id)
        clicked.startRecording(device)

        withTimeout(QUEUED_CLICKS_TIMEOUT_MILLIS) {
            clicked.stopSelectedRecording()
            clicked.stopSelectedRecording()
            clicked.startRecording(device)
            clicked.stopRecording(deviceId = null)
        }

        assertEquals(2, recorder.started.get())
        assertEquals(2, recorder.stopped.get())
    }

    @Test
    fun `two devices record at once and stopping them all keeps each in its device's captures`() = runBlocking {
        val bothStarting = StartBarrier(parties = 2)
        recorder.startGate = bothStarting
        val otherRecorder = SlowRecorder().apply { startGate = bothStarting }
        val other = MirrorDevice(DeviceListing("sim-1", "iPhone 16", DeviceKind.IosSimulator, osVersion = null), otherRecorder)

        val started = withTimeout(PARALLEL_START_TIMEOUT_MILLIS) { mirror.startRecordingsOf(listOf(device, other)) }
        val saved = mirror.stopRecordings(deviceIds = null)

        assertEquals(2, started.filterIsInstance<RecordingResult.Started>().size)
        assertEquals(1, recorder.started.get())
        assertEquals(1, otherRecorder.started.get())
        assertEquals(setOf(device.id, other.id), saved.filterIsInstance<RecordingResult.Saved>().map(RecordingResult.Saved::deviceId).toSet())
        assertEquals(2, mirror.listCaptures(deviceId = null, kind = CaptureKind.Recording, sinceEpochMillis = null).size)
    }

    @Test
    fun `a device that fails to start leaves the others recording`() = runBlocking {
        val failing = MirrorDevice(DeviceListing("emulator-5556", "Pixel 8", DeviceKind.AndroidEmulator, osVersion = null), EndingStream(power = null))

        val results = mirror.startRecordingsOf(listOf(device, failing))

        assertEquals(listOf(device.id), results.filterIsInstance<RecordingResult.Started>().map(RecordingResult.Started::deviceId))
        assertEquals(listOf(failing.id), results.filterIsInstance<RecordingResult.Failed>().map(RecordingResult.Failed::deviceId))
        assertEquals(setOf(device.id), mirror.recordingsStartedAtMillis.keys)
    }

    @Test
    fun `with several recordings running, a stop that names no device is refused and names them`() = runBlocking {
        val other = MirrorDevice(DeviceListing("sim-1", "iPhone 16", DeviceKind.IosSimulator, osVersion = null), SlowRecorder())
        mirror.startRecordingsOf(listOf(device, other))

        val failure = runCatching { mirror.stopRecording(deviceId = null) }.exceptionOrNull()

        assertTrue(failure is DeviceControlException && "iPhone 16" in failure.message.orEmpty(), "failed with $failure")
        assertEquals(2, mirror.recordingsStartedAtMillis.size)
    }

    @Test
    fun `a device whose recording file cannot be reserved fails alone and the others still record`() = runBlocking {
        assumePosixPermissions()
        // A first recording makes this device's day folder, so once the root is read-only only the
        // other device's file cannot be reserved.
        mirror.startRecording(device)
        mirror.stopRecording(deviceId = null)
        val other = MirrorDevice(DeviceListing("sim-1", "iPhone 16", DeviceKind.IosSimulator, osVersion = null), SlowRecorder())
        root.setWritable(false)
        try {
            val results = mirror.startRecordingsOf(listOf(device, other))

            assertEquals(listOf(device.id), results.filterIsInstance<RecordingResult.Started>().map(RecordingResult.Started::deviceId))
            assertEquals(listOf(other.id), results.filterIsInstance<RecordingResult.Failed>().map(RecordingResult.Failed::deviceId))
        } finally {
            root.setWritable(true)
        }
    }

    @Test
    fun `a recording still starting when the mirror is disposed is stopped with the others`() = runBlocking {
        val gate = StartBarrier(parties = 2)
        recorder.startGate = gate
        val starting = scope.async { mirror.startRecording(device) }
        withTimeout(PARALLEL_START_TIMEOUT_MILLIS) { recorder.entered.await() }

        val disposing = scope.async { mirror.dispose() }
        gate.arrive()
        starting.await()
        withTimeout(PARALLEL_START_TIMEOUT_MILLIS) { disposing.await() }

        assertEquals(1, recorder.stopped.get())
        assertTrue(mirror.recordingsStartedAtMillis.isEmpty())
        assertTrue(runCatching { mirror.startRecording(device) }.exceptionOrNull() is DeviceControlException)
    }

    @Test
    fun `Record all targets only the devices that can record and are not recording yet`() {
        val idle = MirrorDevice(DeviceListing("sim-1", "iPhone 16", DeviceKind.IosSimulator, osVersion = null), SlowRecorder())
        val cannotRecord = MirrorDevice(DeviceListing("emulator-5556", "Pixel 8", DeviceKind.AndroidEmulator, osVersion = null), EndingStream(power = null))

        val targets = recordAllTargets(listOf(device, idle, cannotRecord), recordingDeviceIds = setOf(device.id))

        assertEquals(listOf(idle.id), targets.map(MirrorDevice::id))
    }

    @Test
    fun `a recording running when the mirror is disposed is kept in the library`() = runBlocking {
        mirror.startRecording(device)

        mirror.dispose()

        assertEquals(listOf(CaptureKind.Recording), mirror.listCaptures(deviceId = null, kind = null, sinceEpochMillis = null).map { it.info.kind })
    }

    @Test
    fun `a recording whose stop fails leaves no reserved file behind and a new one can start`() = runBlocking {
        mirror.startRecording(device)
        recorder.stopFailure = deviceControlError("pull failed")

        runCatching { mirror.stopRecording(deviceId = null) }
        recorder.stopFailure = null
        mirror.startRecording(device)
        mirror.stopRecording(deviceId = null)

        assertEquals(1, root.walk().count { it.isFile && it.extension == CaptureKind.Recording.extension })
    }

    @Test
    fun `switching away from a device whose stream fails to read as it closes ends its session quietly`() = runBlocking {
        val controller = ClosingStream()
        val streaming = MirrorDevice(DeviceListing("sim-1", "iPhone 15 Pro", DeviceKind.IosSimulator, osVersion = null), controller)
        val ended = CompletableDeferred<Throwable?>()
        val session = scope.launch { mirror.mirror(streaming) }
        session.invokeOnCompletion(ended::complete)

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.reading.await() }
        session.cancel()

        val cause = withTimeout(STREAM_END_TIMEOUT_MILLIS) { ended.await() }
        assertTrue(cause is CancellationException, "the session ended with $cause")
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

        withTimeout(SCREEN_CHANGE_TIMEOUT_MILLIS) { controller.reopened.await() }
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

private const val SIMULTANEOUS_CALLS = 8

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

/** Bounds a sequence of queued Record and Stop clicks, so a hang fails the test instead of stalling it. */
private const val QUEUED_CLICKS_TIMEOUT_MILLIS = 5_000L

/** Long enough that every concurrent call reaches the recorder while the first is still inside it. */
private const val RECORDER_LATENCY_MILLIS = 100L

/** Long enough for two recorders that start together, far shorter than one waiting for the other forever. */
private const val PARALLEL_START_TIMEOUT_MILLIS = 5_000L

/** Holds each caller of [arrive] until [parties] callers have arrived. */
private class StartBarrier(private val parties: Int) {
    private val arrived = AtomicInteger()
    private val open = CompletableDeferred<Unit>()

    suspend fun arrive() {
        if (arrived.incrementAndGet() >= parties) open.complete(Unit)
        open.await()
    }
}

private class SlowRecorder : DeviceController {
    val started = AtomicInteger()
    val stopped = AtomicInteger()

    /** Completes when a recording has begun stopping. */
    val stopping = CompletableDeferred<Unit>()

    /** Thrown by the next stop, as a pull that fails would. */
    var stopFailure: DeviceControlException? = null

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = true, screenPower = false)

    /** Completes when a start has begun. */
    val entered = CompletableDeferred<Unit>()

    /** When set, a start waits at it until every party has arrived. */
    var startGate: StartBarrier? = null

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        entered.complete(Unit)
        startGate?.arrive()
        delay(RECORDER_LATENCY_MILLIS)
        started.incrementAndGet()
        return object : DeviceRecording {
            override suspend fun stop(): File {
                stopping.complete(Unit)
                delay(RECORDER_LATENCY_MILLIS)
                stopFailure?.let { throw it }
                stopped.incrementAndGet()
                return outputFile
            }
        }
    }

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = throw deviceControlError("no stream in tests")

    override suspend fun release() = Unit
}

/**
 * A device whose video stream ends at once, as screenrecord's does at its time limit. With a
 * [power], its screen state can be read, and stays that.
 */
private class EndingStream(private val power: ScreenPower?) : DeviceController {
    private val opened = AtomicInteger()

    val screenPowerReads = AtomicInteger()

    /** Completes once the mirror, done with the first stream, opens the next. */
    val reopened = CompletableDeferred<Unit>()

    /** The size the first stream was asked for. */
    val firstWanted = CompletableDeferred<IntSize?>()

    /** Completes when the mirror first reads the screen's size. */
    val screenSizeRead = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = power != null)

    /** Completes when the screen state is read a second time. */
    val readTwice = CompletableDeferred<Unit>()

    override suspend fun screenPower(): ScreenPower {
        if (screenPowerReads.incrementAndGet() == 2) readTwice.complete(Unit)
        return power ?: throw deviceControlError(NO_SCREEN_POWER)
    }

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun screenSize(): IntSize {
        screenSizeRead.complete(Unit)
        return IntSize(1080, 2400)
    }

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        firstWanted.complete(wanted)
        if (opened.incrementAndGet() == 2) reopened.complete(Unit)
        return VideoStream.RawBgra(EmptyProcess(), frameSize = IntSize(1, 1), rowBytes = 64, fps = 30, onFellBehind = { false })
    }

    override suspend fun release() = Unit
}

/**
 * A simulator whose raw stream blocks in its read until the mirror closes it, and then throws
 * "Stream closed", as a process pipe closed under a blocked read does.
 */
private class ClosingStream : DeviceController {
    /** Completes once the mirror is blocked reading the stream. */
    val reading = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = VideoStream.RawBgra(ClosedUnderReadProcess(reading), frameSize = IntSize(1, 1), rowBytes = 64, fps = 30, onFellBehind = { false })

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

/** An emulator whose gRPC stream sends one frame and then nothing, as a still screen does. */
private class StillEmulator : DeviceController {
    /** Completes if the mirror asks for a screenshot. */
    val screenshotTaken = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

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

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenSize(): IntSize = if (opened.get() == 0) UNFOLDED_SCREEN else FOLDED_SCREEN

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        if (opened.incrementAndGet() == 2) reopened.complete(stateNow())
        val writer = PipedOutputStream()
        return VideoStream.EmulatorRgba(frames = PipedInputStream(writer), cancel = writer::close)
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

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

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

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

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

/** A process whose output blocks until it is destroyed, and then fails the blocked read. */
private class ClosedUnderReadProcess(private val reading: CompletableDeferred<Unit>) : Process() {
    private val destroyed = CompletableDeferred<Unit>()

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            reading.complete(Unit)
            runBlocking { destroyed.await() }
            throw IOException("Stream closed")
        }
    }

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() {
        destroyed.complete(Unit)
    }
}

private class EmptyProcess : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
