package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** How long a stream may stay silent before the mirror says why it might be. */
private const val FIRST_FRAME_TIMEOUT_MILLIS = 7_000L

/** How often the device list is refreshed. */
private const val DISCOVERY_INTERVAL_MILLIS = 3_000L

/** A device that could not stream is asked again after this long. */
private const val STREAM_RETRY_MILLIS = 5_000L

/** How long an Android stream must be quiet before a screenshot shows its true last frame. */
private const val SETTLE_AFTER_NANOS = 300_000_000L

private const val SETTLE_CHECK_MILLIS = 100L

/** How long the view must keep a new size before the stream is reopened for it. */
private const val RESIZE_SETTLE_MILLIS = 500L

/** The share by which the view must change before a stream is reopened for it. */
private const val RESIZE_THRESHOLD = 0.15f

/** How often a mirrored Android device's screen state is read; a screen turned off shows as black. */
private const val SCREEN_POWER_POLL_MILLIS = 2_000L

/** How fast the still-image fallback polls, for a device whose stream never started. */
private const val SCREENSHOT_POLL_MILLIS = 250L

/** What the mirror is doing with the selected device. */
internal sealed interface MirrorState {
    data object Idle : MirrorState

    data object Connecting : MirrorState

    data object Streaming : MirrorState

    /** The device cannot stream, so screenshots stand in for video. */
    data object Polling : MirrorState

    /** The stream opened but sent nothing; [hints] say what to check. */
    data class NoFrames(val hints: List<String>) : MirrorState

    data class Failed(val message: String) : MirrorState
}

internal data class MirrorStatus(val message: String, val isError: Boolean)

/** What the mirror's UI can ask of it. */
internal interface MirrorActions {
    fun select(deviceId: String)

    fun tap(x: Int, y: Int)

    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int)

    fun pressButton(button: DeviceButton)

    fun inputText(text: String)

    fun saveScreenshot()

    fun toggleRecording()

    fun wake()

    fun sleep()
}

/**
 * The devices on this machine and the one being mirrored. Only the device on screen streams, and
 * only while the mirror is shown ([mirror] runs for as long as its caller does), so a hidden
 * mirror costs nothing and an iOS device's companion runs only while it is watched.
 */
@Stable
internal class DeviceMirror(
    private val discovery: DeviceDiscovery,
    val captures: MirrorCaptures,
    private val scope: CoroutineScope,
) : MirrorActions,
    MirrorDevices {
    var devices: List<MirrorDevice> by mutableStateOf(emptyList())
        private set

    var missingTools: List<String> by mutableStateOf(emptyList())
        private set

    override var selectedId: String? by mutableStateOf(null)
        private set

    var state: MirrorState by mutableStateOf(MirrorState.Idle)
        private set

    var status: MirrorStatus? by mutableStateOf(null)
        private set

    var recordingDeviceId: String? by mutableStateOf(null)
        private set

    /** The mirrored device's screen state, for a device whose [DeviceCapabilities.screenPower] is true. */
    var screenPower: ScreenPower? by mutableStateOf(null)
        private set

    private var recording: ActiveRecording? = null

    private class ActiveRecording(val device: MirrorDevice, val handle: DeviceRecording, val file: File, val startedAt: Instant)

    val surface = MirrorSurface()

    // Switching devices starts the new session while the old one is still stopping; one at a time
    // keeps the old decoder from writing into the surface the new one has cleared.
    private val sessions = Mutex()

    // The UI and MCP start and stop recordings concurrently; each start, stop or disposal runs to
    // completion before the next looks at [recording].
    private val recordings = Mutex()

    val selectedDevice: MirrorDevice? get() = devices.firstOrNull { it.id == selectedId }

    /** Keeps [devices] current until the caller is cancelled; the mirror runs it while it is shown. */
    suspend fun keepDevicesCurrent() {
        while (coroutineContext.isActive) {
            refresh()
            delay(DISCOVERY_INTERVAL_MILLIS)
        }
    }

    override suspend fun refresh(): List<MirrorDevice> {
        val found = discovery.discover()
        devices = found.devices
        missingTools = found.missingTools
        if (devices.none { it.id == selectedId }) selectedId = devices.firstOrNull()?.id
        return found.devices
    }

    /** Mirrors [device] into [surface] until the caller is cancelled. */
    suspend fun mirror(device: MirrorDevice) = sessions.withLock {
        surface.clear()
        try {
            coroutineScope {
                if (device.controller.capabilities.screenPower) launch { watchScreenPower(device) }
                streamUntilCancelled(device)
            }
        } finally {
            withContext(NonCancellable) { device.controller.release() }
            state = MirrorState.Idle
            screenPower = null
        }
    }

    private suspend fun streamUntilCancelled(device: MirrorDevice) {
        while (coroutineContext.isActive) {
            state = MirrorState.Connecting
            when (val outcome = streamOnce(device)) {
                is StreamOutcome.Ended -> Unit

                // A physical iOS device has nothing to fall back on, so the mirror says why it
                // is blank and tries again; the others still show something through screenshots.
                is StreamOutcome.Silent -> if (device.kind == DeviceKind.IosDevice) {
                    state = MirrorState.NoFrames(noFramesHints(device.kind))
                    delay(STREAM_RETRY_MILLIS)
                } else {
                    pollScreenshots(device)
                }

                is StreamOutcome.Unavailable -> if (device.kind == DeviceKind.IosDevice) {
                    state = MirrorState.Failed(outcome.message)
                    delay(STREAM_RETRY_MILLIS)
                } else {
                    pollScreenshots(device)
                }
            }
        }
    }

    private suspend fun watchScreenPower(device: MirrorDevice) {
        while (coroutineContext.isActive) {
            // An unreadable state hides the screen-off notice rather than showing a stale one.
            screenPower = try {
                device.controller.screenPower()
            } catch (_: DeviceControlException) {
                null
            }
            delay(SCREEN_POWER_POLL_MILLIS)
        }
    }

    private sealed interface StreamOutcome {
        /** The stream showed frames, then ended; a new one continues it. */
        data object Ended : StreamOutcome

        /** The stream opened but no frame came. */
        data object Silent : StreamOutcome

        data class Unavailable(val message: String) : StreamOutcome
    }

    private suspend fun streamOnce(device: MirrorDevice): StreamOutcome {
        // Without the screen's size the frames stay whole and taps map through them instead.
        val screen = try {
            device.controller.screenSize()
        } catch (_: DeviceControlException) {
            null
        }
        surface.deviceSize = screen
        val outputSize = screen?.let { decodingSize(it, surface.viewSize) }
        val stream = try {
            device.controller.openVideoStream(outputSize)
        } catch (e: DeviceControlException) {
            return StreamOutcome.Unavailable(e.message.orEmpty())
        }
        // Whatever the tool logs goes unread otherwise, and a full pipe would stall it.
        thread(isDaemon = true, name = "mirror-stream-stderr") { stream.process.errorStream.use(InputStream::readAllBytes) }
        val watchdog = FirstFrameWatchdog(FIRST_FRAME_TIMEOUT_MILLIS)
        val lastFrameAt = AtomicLong(System.nanoTime())
        return try {
            coroutineScope<StreamOutcome> {
                // The decoder's failure comes back as a value, so a stream this side closed on
                // purpose is not mistaken for one that broke.
                val decoding = async(Dispatchers.IO) {
                    try {
                        decode(stream, outputSize) {
                            lastFrameAt.set(System.nanoTime())
                            watchdog.frameArrived()
                            state = MirrorState.Streaming
                        }
                        StreamOutcome.Ended
                    } catch (e: DeviceControlException) {
                        StreamOutcome.Unavailable(e.message.orEmpty())
                    }
                }
                // Decoding blocks inside ffmpeg and ignores cancellation: only the stream closing
                // lets it return, and this scope waits for it, so the stream is closed here the
                // moment the body ends or is cancelled. The resize watch only ends on a resize, so
                // it is cancelled there too, or this scope would wait for it after the stream ended.
                val resizing = screen?.let { launch { reopenWhenResized(it, outputSize, stream.process) } }
                try {
                    if (device.kind.platform == DevicePlatform.Android) {
                        state = MirrorState.Streaming
                        val settling = launch { settleStillScreen(device, lastFrameAt) }
                        decoding.await().also { settling.cancel() }
                    } else if (!watchdog.awaitFirstFrame()) {
                        StreamOutcome.Silent
                    } else {
                        decoding.await()
                    }
                } finally {
                    resizing?.cancel()
                    stream.process.destroyForcibly()
                }
            }
        } catch (e: DeviceControlException) {
            StreamOutcome.Unavailable(e.message.orEmpty())
        } finally {
            stream.process.destroyForcibly()
        }
    }

    private fun decode(stream: VideoStream, outputSize: IntSize?, onFrame: () -> Unit) = when (stream) {
        is VideoStream.H264 -> decodeH264Into(surface, stream.process.inputStream, outputSize, onFrame)
        is VideoStream.RawBgra -> readRawBgraInto(surface, stream.process.inputStream, stream.frameSize, onFrame)
    }

    /**
     * Ends the stream once the view has settled at a size the frames no longer fit, so the next one
     * is decoded at the new size. Small changes are ignored: reopening a stream takes a second.
     */
    private suspend fun reopenWhenResized(screen: IntSize, decodedAt: IntSize?, process: Process) {
        val current = decodedAt ?: screen
        var candidate: IntSize? = null
        while (coroutineContext.isActive) {
            delay(RESIZE_SETTLE_MILLIS)
            val wanted = decodingSize(screen, surface.viewSize) ?: screen
            val changed = abs(wanted.width - current.width) > current.width * RESIZE_THRESHOLD
            if (changed && wanted == candidate) {
                process.destroyForcibly()
                return
            }
            candidate = if (changed) wanted else null
        }
    }

    /**
     * Keeps an Android mirror true to a still screen. screenrecord sends a frame only when the
     * screen changes, and the decoder can finish a frame only once the next one starts, so a
     * still screen would show nothing at first and, after motion, the frame before the last.
     * A screenshot fills in at the start and again whenever the stream has gone quiet.
     */
    private suspend fun settleStillScreen(device: MirrorDevice, lastFrameAt: AtomicLong) {
        var settledAt = 0L
        while (coroutineContext.isActive) {
            val last = lastFrameAt.get()
            if (last > settledAt && System.nanoTime() - last >= SETTLE_AFTER_NANOS) {
                settledAt = System.nanoTime()
                // A missed screenshot only leaves the stream's own picture up until the next one.
                try {
                    showScreenshot(device)
                } catch (_: DeviceControlException) {
                }
            }
            delay(SETTLE_CHECK_MILLIS)
        }
    }

    private suspend fun pollScreenshots(device: MirrorDevice) {
        state = MirrorState.Polling
        while (coroutineContext.isActive) {
            try {
                showScreenshot(device)
            } catch (e: DeviceControlException) {
                state = MirrorState.Failed(e.message.orEmpty())
            }
            delay(SCREENSHOT_POLL_MILLIS)
        }
    }

    private suspend fun showScreenshot(device: MirrorDevice) {
        val png = device.controller.captureScreenshot()
        withContext(Dispatchers.IO) {
            Image.makeFromEncoded(png).use { image ->
                surface.writeFrame(image.width, image.height) { image.readPixels(it, 0, 0) }
            }
        }
    }

    override fun select(deviceId: String) {
        selectedId = deviceId
    }

    override fun tap(x: Int, y: Int) = control { it.tap(x, y) }

    override fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int) = control { it.swipe(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = 250) }

    override fun pressButton(button: DeviceButton) = control { it.pressButton(button) }

    override fun inputText(text: String) = control { it.inputText(text) }

    override fun saveScreenshot() = control {
        val capture = saveScreenshot(checkNotNull(selectedDevice))
        status = MirrorStatus("Saved ${capture.file.name}", isError = false)
    }

    override fun toggleRecording() = control {
        recordings.withLock {
            if (recording != null) {
                status = MirrorStatus("Saved ${stopRunningRecording().file.name}", isError = false)
            } else {
                startRecordingOf(checkNotNull(selectedDevice))
            }
        }
    }

    override fun wake() = control { controller ->
        controller.wake()
        val power = controller.screenPower()
        screenPower = power
        if (power.locked) status = MirrorStatus("The device is still locked. Unlock it to continue.", isError = false)
    }

    override fun sleep() = control { controller ->
        controller.sleep()
        screenPower = controller.screenPower()
    }

    override fun resolve(deviceId: String?): MirrorDevice {
        if (deviceId == null) {
            return selectedDevice ?: throw deviceControlError("no device is connected; start an emulator or simulator, then call $TOOL_PREFIX.listDevices")
        }
        return devices.firstOrNull { it.id == deviceId } ?: throw deviceControlError("no device has the id '$deviceId'; call $TOOL_PREFIX.listDevices")
    }

    override suspend fun saveScreenshot(device: MirrorDevice): Capture = captures.addScreenshot(device.listing, device.controller.captureScreenshot())

    override suspend fun startRecording(device: MirrorDevice) = recordings.withLock { startRecordingOf(device) }

    private suspend fun startRecordingOf(device: MirrorDevice) {
        if (recording != null) throw deviceControlError("a recording is already running; stop it first")
        val file = captures.recordingFile(device.listing)
        val handle = try {
            device.controller.startRecording(file)
        } catch (e: DeviceControlException) {
            file.delete()
            throw e
        }
        recording = ActiveRecording(device, handle, file, Instant.now())
        recordingDeviceId = device.id
    }

    override suspend fun stopRecording(): Capture = recordings.withLock { stopRunningRecording() }

    private suspend fun stopRunningRecording(): Capture {
        val running = recording ?: throw deviceControlError("no recording is running")
        recording = null
        recordingDeviceId = null
        val file = running.handle.stop()
        val size = try {
            running.device.controller.screenSize()
        } catch (_: DeviceControlException) {
            null
        }
        return captures.addRecording(running.device.listing, file, running.startedAt, size)
    }

    override fun listCaptures(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture> = captures.library.list(deviceId, kind, sinceEpochMillis)

    /** Stops what outlives the UI: a recording in progress, which is kept in the library. */
    suspend fun dispose() {
        recordings.withLock {
            if (recording != null) stopRunningRecording()
        }
        surface.close()
    }

    // Runs [action] on the selected device, reporting a failure in [status] instead of throwing.
    private fun control(action: suspend (DeviceController) -> Unit) {
        val device = selectedDevice ?: return
        scope.launch {
            try {
                action(device.controller)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeviceControlException) {
                status = MirrorStatus(e.message.orEmpty(), isError = true)
            }
        }
    }
}
