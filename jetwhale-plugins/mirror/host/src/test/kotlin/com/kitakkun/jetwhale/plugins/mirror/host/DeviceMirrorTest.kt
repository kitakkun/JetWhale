package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
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
        discovery = DeviceDiscovery(
            CompletableDeferred(
                MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null),
            ),
            companions = CompletableDeferred(value = null),
            emulatorScreens = EmulatorScreens(runningDirectories = emptyList()),
        ),
        captures = MirrorCaptures(
            root,
            storage = InMemoryPluginStorage(emptyMap()),
            scope = scope,
            zone = ZoneOffset.UTC,
            notices = notices,
            ffmpegPath = CompletableDeferred(value = null),
            clipboard = CaptureClipboard(osascriptPath = null),
        ),
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

        val outcomes = List(SIMULTANEOUS_CALLS) {
            async(Dispatchers.Default) { runCatching { mirror.stopRecording(deviceId = null) } }
        }.awaitAll()

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
            discovery = DeviceDiscovery(
                CompletableDeferred(
                    MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null),
                ),
                companions = CompletableDeferred(value = null),
                emulatorScreens = EmulatorScreens(runningDirectories = emptyList()),
            ),
            captures = MirrorCaptures(
                root,
                storage = InMemoryPluginStorage(emptyMap()),
                scope = clicks,
                zone = ZoneOffset.UTC,
                notices = notices,
                ffmpegPath = CompletableDeferred(value = null),
                clipboard = CaptureClipboard(osascriptPath = null),
            ),
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
        assertEquals(
            setOf(device.id, other.id),
            saved.filterIsInstance<RecordingResult.Saved>().map(RecordingResult.Saved::deviceId).toSet(),
        )
        assertEquals(2, mirror.listCaptures(deviceId = null, kind = CaptureKind.Recording, sinceEpochMillis = null).size)
    }

    @Test
    fun `a device that fails to start leaves the others recording`() = runBlocking {
        val failing =
            MirrorDevice(
                DeviceListing("emulator-5556", "Pixel 8", DeviceKind.AndroidEmulator, osVersion = null),
                EndingStream(power = null),
            )

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
        val cannotRecord =
            MirrorDevice(
                DeviceListing("emulator-5556", "Pixel 8", DeviceKind.AndroidEmulator, osVersion = null),
                EndingStream(power = null),
            )

        val targets = recordAllTargets(listOf(device, idle, cannotRecord), recordingDeviceIds = setOf(device.id))

        assertEquals(listOf(idle.id), targets.map(MirrorDevice::id))
    }

    @Test
    fun `a recording running when the mirror is disposed is kept in the library`() = runBlocking {
        mirror.startRecording(device)

        mirror.dispose()

        assertEquals(
            listOf(CaptureKind.Recording),
            mirror.listCaptures(deviceId = null, kind = null, sinceEpochMillis = null).map {
                it.info.kind
            },
        )
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
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller))
        }

        val screenshot = withTimeoutOrNull(STILL_SCREEN_PATCH_WINDOW_MILLIS) { controller.screenshotTaken.await() }
        session.cancel()

        assertNull(screenshot)
    }

    @Test
    fun `a mirrored Android device whose screen is off says so until mirroring stops`() = runBlocking {
        val asleep = ScreenPower(awake = false, locked = true)
        val controller = EndingStream(power = asleep)
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller))
        }

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
        val session = scope.launch {
            mirror.mirror(
                MirrorDevice(DeviceListing("emulator-5556", "Pixel Fold", DeviceKind.AndroidEmulator, osVersion = null), controller),
            )
        }

        withTimeout(SCREEN_CHANGE_TIMEOUT_MILLIS) { controller.reopened.await() }
        val deviceSize = mirror.surface.deviceSize
        session.cancel()

        assertEquals(FOLDED_SCREEN, deviceSize)
    }

    @Test
    fun `a stream that ends is followed by the next without a connecting screen`() = runBlocking<Unit> {
        val controller = FoldingEmulator(stateNow = { mirror.state })
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch {
            mirror.mirror(
                MirrorDevice(DeviceListing("emulator-5556", "Pixel Fold", DeviceKind.AndroidEmulator, osVersion = null), controller),
            )
        }

        val stateWhenReopened = withTimeout(SCREEN_CHANGE_TIMEOUT_MILLIS) { controller.reopened.await() }
        session.cancel()

        assertEquals(MirrorState.Streaming, stateWhenReopened)
    }

    @Test
    fun `a screenshot of a screen turned since the stream opened opens a new stream instead of showing among its frames`() = runBlocking {
        assumeShellScriptsLaunch()
        val turned = Surface.makeRasterN32Premul(20, 10).use { surface ->
            surface.makeImageSnapshot().use { checkNotNull(it.encodeToData(EncodedImageFormat.PNG)).bytes }
        }
        val controller = ScreenrecordDevice(fakeFfmpeg.path, sent = ByteArray(4_096), screenshot = turned)
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller))
        }

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
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller))
        }

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
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller))
        }

        withTimeout(SILENT_STREAM_TIMEOUT_MILLIS) { controller.polled.await() }
        val deviceSize = mirror.surface.deviceSize
        session.cancel()

        assertNull(deviceSize)
    }

    @Test
    fun `a device on screenshots tries its stream again without leaving them for a connecting screen`() = runBlocking<Unit> {
        val controller = FlakyStreamDevice(stateNow = { mirror.state })
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller))
        }

        val stateWhenTriedAgain = withTimeout(STREAM_RETRY_TIMEOUT_MILLIS) { controller.triedAgain.await() }
        session.cancel()

        assertIs<MirrorState.Polling>(stateWhenTriedAgain)
    }

    @Test
    fun `a screenrecord stream that sends bytes but no finished frame keeps streaming`() = runBlocking {
        assumeShellScriptsLaunch()
        val controller = ScreenrecordDevice(fakeFfmpeg.path, sent = ByteArray(4_096), screenshot = null)
        mirror.surface.viewSize = IntSize(540, 1200)
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller))
        }

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
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("device-1", "Pixel Fold", DeviceKind.AndroidDevice, osVersion = null), controller))
        }

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
        val session = scope.launch {
            mirror.mirror(MirrorDevice(DeviceListing("emulator-5558", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller))
        }

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

/** Longer than the mirror waits before patching a still screenrecord stream with a screenshot. */
private const val STILL_SCREEN_PATCH_WINDOW_MILLIS = 1_500L

/** Bounds a sequence of queued Record and Stop clicks, so a hang fails the test instead of stalling it. */
private const val QUEUED_CLICKS_TIMEOUT_MILLIS = 5_000L

/** Long enough for two recorders that start together, far shorter than one waiting for the other forever. */
private const val PARALLEL_START_TIMEOUT_MILLIS = 5_000L
