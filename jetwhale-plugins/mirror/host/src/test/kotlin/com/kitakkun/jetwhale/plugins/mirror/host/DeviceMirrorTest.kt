package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceMirrorTest {
    private val root: File = Files.createTempDirectory("mirror-recordings").toFile()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val recorder = SlowRecorder()
    private val device = MirrorDevice(DeviceListing("emulator-5554", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), recorder)
    private val notices = MirrorNotices(scope)
    private val mirror = DeviceMirror(
        discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = null, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList())),
        developmentTeamSetting = emptyDevelopmentTeamSetting(),
        captures = MirrorCaptures(root, storage = InMemoryPluginStorage(emptyMap()), scope = scope, zone = ZoneOffset.UTC, notices = notices, ffmpegPath = CompletableDeferred(value = null), clipboard = CaptureClipboard(osascriptPath = null)),
        notices = notices,
        scope = scope,
    )

    @AfterTest
    fun cleanUp() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        root.deleteRecursively()
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
            discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = null, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList())),
            developmentTeamSetting = emptyDevelopmentTeamSetting(),
            captures = MirrorCaptures(root, storage = InMemoryPluginStorage(emptyMap()), scope = clicks, zone = ZoneOffset.UTC, notices = notices, ffmpegPath = CompletableDeferred(value = null), clipboard = CaptureClipboard(osascriptPath = null)),
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
}

private const val SIMULTANEOUS_CALLS = 8

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

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = true, screenPower = false)

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
}
