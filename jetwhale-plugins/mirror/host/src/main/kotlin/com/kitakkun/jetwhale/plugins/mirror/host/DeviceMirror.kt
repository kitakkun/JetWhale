package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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

/** How often a mirrored Android device's screen size is read, to follow a foldable folding. */
private const val SCREEN_CHANGE_POLL_MILLIS = 2_000L

/** How fast the still-image fallback polls, for a device whose stream never started. */
private const val SCREENSHOT_POLL_MILLIS = 250L

/**
 * How long screenshots stand in before the stream is tried again, after one, two, and three or more
 * failures in a row. A failure that passes (adb restarting, a fold in progress) costs a few seconds;
 * one that lasts (no ffmpeg) costs one attempt a minute.
 */
private val SCREENSHOT_FALLBACK_MILLIS = listOf(5_000L, 15_000L, 60_000L)

/** How long a stream waits for its view to be laid out; a first layout takes a frame or two. */
private const val VIEW_SIZE_WAIT_MILLIS = 1_500L

private const val VIEW_SIZE_POLL_MILLIS = 20L

/** What the mirror is doing with the selected device. */
internal sealed interface MirrorState {
    data object Idle : MirrorState

    data object Connecting : MirrorState

    data object Streaming : MirrorState

    /** The device cannot stream, for [reason], so screenshots stand in for video. */
    data class Polling(val reason: String) : MirrorState

    /** The stream opened but sent nothing; [hints] say what to check. */
    data class NoFrames(val hints: List<String>) : MirrorState

    data class Failed(val message: String) : MirrorState
}

/** What the mirror's UI can ask of it. */
internal interface MirrorActions {
    fun select(deviceId: String)

    fun tap(x: Int, y: Int)

    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int)

    fun pressButton(button: DeviceButton)

    fun inputText(text: String)

    fun saveScreenshot()

    fun recordSelectedDevice()

    fun stopSelectedRecording()

    fun wake()

    fun sleep()

    /** The Apple development team that signs the XCTest runner driving iPhones; null until set. */
    val developmentTeam: String?

    /** Sets the development team, or forgets it when [team] is null. */
    fun updateDevelopmentTeam(team: String?)
}

/**
 * The devices on this machine and the one being mirrored. Only the device on screen streams, and
 * only while the mirror is shown ([mirror] runs for as long as its caller does), so a hidden
 * mirror costs nothing and an iOS device's companion runs only while it is watched.
 */
@Stable
internal class DeviceMirror(
    private val discovery: DeviceDiscovery,
    private val developmentTeamSetting: DevelopmentTeamSetting,
    val captures: MirrorCaptures,
    val notices: MirrorNotices,
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

    /** When each running recording started, in epoch milliseconds, by device id. */
    var recordingsStartedAtMillis: Map<String, Long> by mutableStateOf(emptyMap())
        private set

    /** The mirrored device's screen state, for a device whose [DeviceCapabilities.screenPower] is true. */
    var screenPower: ScreenPower? by mutableStateOf(null)
        private set

    private val activeRecordings = ConcurrentHashMap<String, ActiveRecording>()

    @Volatile
    private var isDisposing = false

    private class ActiveRecording(val device: MirrorDevice, val handle: DeviceRecording, val file: File, val startedAt: Instant)

    val surface = MirrorSurface()

    private val sessions = Mutex()

    private val recordingLocks = ConcurrentHashMap<String, Mutex>()

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
        surface.keepFramesOf(found.devices.map(MirrorDevice::id).toSet())
        missingTools = found.missingTools
        if (devices.none { it.id == selectedId }) selectedId = devices.firstOrNull()?.id
        return found.devices
    }

    /** Mirrors [device] into [surface] until the caller is cancelled. */
    suspend fun mirror(device: MirrorDevice) = sessions.withLock {
        surface.switchTo(device.id)
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
        state = MirrorState.Connecting
        var failuresInARow = 0
        while (coroutineContext.isActive) {
            val fallbackMillis = SCREENSHOT_FALLBACK_MILLIS[failuresInARow.coerceAtMost(SCREENSHOT_FALLBACK_MILLIS.lastIndex)]
            when (val outcome = streamOnce(device)) {
                is StreamOutcome.Ended -> failuresInARow = 0

                // A physical iOS device has nothing to fall back on, so the mirror says why it
                // is blank and tries again; the others still show something through screenshots.
                is StreamOutcome.Silent -> if (device.kind == DeviceKind.IosDevice) {
                    state = MirrorState.NoFrames(noFramesHints(device.kind))
                    delay(STREAM_RETRY_MILLIS)
                } else {
                    pollScreenshots(device, reason = "the video stream sent no picture", forMillis = fallbackMillis)
                    failuresInARow++
                }

                is StreamOutcome.Unavailable -> if (device.kind == DeviceKind.IosDevice) {
                    state = MirrorState.Failed(outcome.message)
                    delay(STREAM_RETRY_MILLIS)
                } else {
                    pollScreenshots(device, reason = outcome.message, forMillis = fallbackMillis)
                    failuresInARow++
                }
            }
        }
    }

    private suspend fun watchScreenPower(device: MirrorDevice) {
        while (coroutineContext.isActive) {
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
        val frames = surface.startStream()
        val screen = try {
            device.controller.screenSize()
        } catch (_: DeviceControlException) {
            null
        }
        withTimeoutOrNull(VIEW_SIZE_WAIT_MILLIS) {
            while (surface.viewSize == IntSize.Zero) delay(VIEW_SIZE_POLL_MILLIS)
        }
        val outputSize = screen?.let { decodingSize(it, surface.viewSize) }
        val stream = try {
            device.controller.openVideoStream(outputSize)
        } catch (e: DeviceControlException) {
            return StreamOutcome.Unavailable(e.message.orEmpty())
        }
        // Whole images come at the screen's own size, turned as its interface is, whatever size was
        // asked: a point maps onto the screen through each image's size, and a resized view has
        // nothing to reopen for.
        surface.deviceSize = screen.takeUnless { stream is VideoStream.EncodedImages }
        val isAndroid = device.kind.platform == DevicePlatform.Android
        return try {
            coroutineScope<StreamOutcome> {
                val lastFrameAt = AtomicLong(System.nanoTime())
                val watches = listOfNotNull(
                    screen?.takeUnless { stream is VideoStream.EncodedImages }?.let { launch { reopenWhenResized(it, outputSize, stream) } },
                    screen?.takeIf { isAndroid }?.let { launch { reopenWhenScreenChanges(device, it, stream) } },
                    // Only screenrecord holds a still screen's last frame back; the emulator's own
                    // stream sends every change as it happens.
                    stream.takeIf { isAndroid && it is VideoStream.H264 }?.let { launch { settleStillScreen(device, frames, screen, it, lastFrameAt) } },
                )
                val watchdog = FirstFrameWatchdog(FIRST_FRAME_TIMEOUT_MILLIS)
                val decoding = async(Dispatchers.IO) {
                    // screenrecord's bytes are its sign of life: a still screen's only frame
                    // decodes once the next one starts, which may be never.
                    decode(stream, frames, outputSize, onInput = if (isAndroid) watchdog::frameArrived else ({})) {
                        lastFrameAt.set(System.nanoTime())
                        watchdog.frameArrived()
                        state = MirrorState.Streaming
                    }
                }
                // Decoding blocks inside ffmpeg and ignores cancellation; only the stream closing
                // lets it return, and this scope waits for it, so the stream is closed here once
                // the body ends or is cancelled. The watches may never end on their own, so they
                // are cancelled here too.
                try {
                    if (isAndroid) {
                        // A first stream shows as live at once, with screenshots filling in a still
                        // screen; one tried again from behind screenshots keeps them up until it is heard from.
                        if (state == MirrorState.Connecting) state = MirrorState.Streaming
                        val silent = stream is VideoStream.H264 && !watchdog.awaitFirstFrame()
                        if (!silent) state = MirrorState.Streaming
                        if (silent) StreamOutcome.Silent else decoding.await()
                    } else if (!watchdog.awaitFirstFrame()) {
                        StreamOutcome.Silent
                    } else {
                        decoding.await()
                    }
                } finally {
                    // Both end here, inside the scope, which waits for its children: decoding
                    // blocks on the stream until it is closed, and the watches may never end on
                    // their own.
                    watches.forEach { it.cancel() }
                    stream.close()
                }
            }
        } catch (e: DeviceControlException) {
            StreamOutcome.Unavailable(e.message.orEmpty())
        } finally {
            stream.close()
        }
    }

    /**
     * Shows [stream] until it ends. A failure comes back as a value, so a stream this side closed on
     * purpose is not mistaken for one that broke.
     */
    private suspend fun decode(stream: VideoStream, frames: MirrorSurface.FrameStream, outputSize: IntSize?, onInput: () -> Unit, onFrame: () -> Unit): StreamOutcome = try {
        when (stream) {
            is VideoStream.H264 -> decodeH264Into(frames, stream, outputSize, onInput, onFrame)
            is VideoStream.EmulatorRgba -> readEmulatorFramesInto(frames, stream.frames, onFrame)
            is VideoStream.EncodedImages -> readEncodedImagesInto(frames, stream, onFrame)
        }
        StreamOutcome.Ended
    } catch (e: DeviceControlException) {
        StreamOutcome.Unavailable(e.message.orEmpty())
    } catch (e: IOException) {
        // Closing the stream under a blocked read (a device switch, a resize, the tool quitting)
        // can surface as "Stream closed" rather than as its end. It must not leave the decoding
        // coroutine: the mirror runs in the plugin's composition, and an exception escaping it stops
        // the whole screen from updating.
        StreamOutcome.Unavailable(e.message.orEmpty())
    }

    /**
     * Ends the stream once the view has settled at a size the frames no longer fit, so the next one
     * is decoded at the new size. Small changes are ignored: reopening a stream takes a second.
     */
    private suspend fun reopenWhenResized(screen: IntSize, decodedAt: IntSize?, stream: VideoStream) {
        val current = decodedAt ?: screen
        var candidate: IntSize? = null
        while (coroutineContext.isActive) {
            delay(RESIZE_SETTLE_MILLIS)
            val wanted = decodingSize(screen, surface.viewSize) ?: screen
            val changed = abs(wanted.width - current.width) > current.width * RESIZE_THRESHOLD
            if (changed && wanted == candidate) {
                stream.close()
                return
            }
            candidate = if (changed) wanted else null
        }
    }

    /**
     * Ends an Android stream once the device's screen changes size: a foldable's moves to the other
     * panel when it folds, and any screen turns its size around when it rotates. The next stream
     * then shows the panel that is on, in the shape it has now; screenrecord keeps the shape it
     * started with and would letterbox a turned screen inside it.
     */
    private suspend fun reopenWhenScreenChanges(device: MirrorDevice, screen: IntSize, stream: VideoStream) {
        while (coroutineContext.isActive) {
            delay(SCREEN_CHANGE_POLL_MILLIS)
            val current = try {
                device.controller.screenSize()
            } catch (_: DeviceControlException) {
                continue
            }
            if (current != screen) {
                stream.close()
                return
            }
        }
    }

    /**
     * Keeps an Android mirror true to a still screen. screenrecord sends a frame only when the
     * screen changes, and the decoder can finish a frame only once the next one starts, so a
     * still screen would show nothing at first and, after motion, the frame before the last.
     * A screenshot fills in at the start and again whenever the stream has gone quiet.
     */
    private suspend fun settleStillScreen(device: MirrorDevice, frames: MirrorSurface.FrameStream, screen: IntSize?, stream: VideoStream, lastFrameAt: AtomicLong) {
        var settledAt = 0L
        while (coroutineContext.isActive) {
            val last = lastFrameAt.get()
            if (last > settledAt && System.nanoTime() - last >= SETTLE_AFTER_NANOS) {
                settledAt = System.nanoTime()
                try {
                    if (!showScreenshot(device, frames, streamShape = screen)) {
                        stream.close()
                        return
                    }
                } catch (_: DeviceControlException) {
                    // A failed screenshot only leaves the stream's own frame in view until the next
                    // one.
                }
            }
            delay(SETTLE_CHECK_MILLIS)
        }
    }

    private suspend fun pollScreenshots(device: MirrorDevice, reason: String, forMillis: Long) {
        state = MirrorState.Polling(reason)
        surface.deviceSize = null
        val frames = surface.startStream()
        withTimeoutOrNull(forMillis) {
            while (true) {
                try {
                    showScreenshot(device, frames, streamShape = null)
                } catch (e: DeviceControlException) {
                    state = MirrorState.Failed(e.message.orEmpty())
                }
                delay(SCREENSHOT_POLL_MILLIS)
            }
        }
    }

    /**
     * Shows a screenshot of [device] through [frames], unless it is turned the other way than
     * [streamShape], the shape of the stream it would show between; returns whether it was shown.
     * The stream's frames keep the shape it opened with, so a turned screenshot among them would
     * flip the view between two shapes.
     */
    private suspend fun showScreenshot(device: MirrorDevice, frames: MirrorSurface.FrameStream, streamShape: IntSize?): Boolean {
        val png = device.controller.captureScreenshot()
        return withContext(Dispatchers.IO) {
            // Skia refuses bytes that are not an image with an IllegalArgumentException, which would
            // end the mirror; as a failed screenshot it only costs this one.
            val decoded = try {
                Image.makeFromEncoded(png)
            } catch (_: IllegalArgumentException) {
                throw deviceControlError("the screenshot could not be read as an image")
            }
            decoded.use { image ->
                val turned = streamShape != null && (image.width > image.height) != (streamShape.width > streamShape.height)
                if (!turned) frames.writeFrame(image.width, image.height, ColorType.BGRA_8888) { image.readPixels(it, 0, 0) }
                !turned
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

    override val developmentTeam: String? get() = developmentTeamSetting.developmentTeam

    override fun updateDevelopmentTeam(team: String?) {
        developmentTeamSetting.updateDevelopmentTeam(team)
        // The shown iPhone's stream opened before it had a team, so it started no runner then.
        (selectedDevice?.controller as? IosPhysicalDeviceController)?.startRunnerInBackground()
    }

    override fun saveScreenshot() {
        val device = selectedDevice ?: return
        scope.launch { notices.show(MirrorNotice.screenshotsSaved(listOf(screenshotResultOf(device)))) }
    }

    /** Saves a screenshot of [device], turning a failure into its reason instead of throwing. */
    suspend fun screenshotResultOf(device: MirrorDevice): ScreenshotResult {
        val reason = try {
            return ScreenshotResult.Saved(saveScreenshot(device))
        } catch (e: DeviceControlException) {
            e.message.orEmpty()
        } catch (e: IllegalArgumentException) {
            e.message ?: "the screenshot could not be read as an image"
        } catch (e: IOException) {
            e.message ?: "the screenshot could not be saved"
        }
        return ScreenshotResult.Failed(deviceId = device.id, deviceName = device.listing.name, reason = reason)
    }

    override fun recordSelectedDevice() {
        val device = selectedDevice ?: return
        scope.launch { recordFromUi(device) }
    }

    override fun stopSelectedRecording() {
        val deviceId = selectedId ?: return
        scope.launch { stopFromUi(deviceId) }
    }

    /** Starts recording [deviceId] again after a failed start, unless a recording of it has started since. */
    fun retryRecording(deviceId: String) {
        val device = devices.firstOrNull { it.id == deviceId } ?: return
        scope.launch { recordFromUi(device) }
    }

    /** Starts recording every device that can record and is not recording yet, and says how that went. */
    fun recordAll() {
        scope.launch { notices.show(MirrorNotice.recordingsStarted(startRecordings(deviceIds = null))) }
    }

    /** Starts recording each of [deviceIds] again after a failed Record all. */
    fun retryRecordings(deviceIds: List<String>) {
        scope.launch { notices.show(MirrorNotice.recordingsStarted(startRecordings(deviceIds))) }
    }

    /** Stops every running recording, keeping each in its device's captures, and says how that went. */
    fun stopAllRecordings() {
        scope.launch { notices.show(MirrorNotice.recordingsSaved(stopRecordings(deviceIds = null))) }
    }

    private suspend fun recordFromUi(device: MirrorDevice) = recordingLockOf(device.id).withLock {
        if (activeRecordings.containsKey(device.id)) return@withLock
        try {
            startRecordingLocked(device)
        } catch (e: DeviceControlException) {
            notices.show(MirrorNotice.failure("Could not start recording ${device.listing.name}: ${e.message}", retry = NoticeAction.RetryRecording(device.id)))
        }
    }

    private suspend fun stopFromUi(deviceId: String) = recordingLockOf(deviceId).withLock {
        if (!activeRecordings.containsKey(deviceId)) return@withLock
        val notice = when (val result = stopResultLocked(deviceId)) {
            is RecordingResult.Saved -> MirrorNotice.saved(result.capture)
            is RecordingResult.Failed -> MirrorNotice.failure(result.reason, retry = null)
            is RecordingResult.Started -> error("a stop never reports a start")
        }
        notices.show(notice)
    }

    override fun wake() = control { controller ->
        controller.wake()
        val power = controller.screenPower()
        screenPower = power
        if (power.locked) notices.show(MirrorNotice.info("The device is still locked. Unlock it to continue."))
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

    override suspend fun startRecording(device: MirrorDevice) = recordingLockOf(device.id).withLock { startRecordingLocked(device) }

    override suspend fun startRecordings(deviceIds: List<String>?): List<RecordingResult> {
        if (deviceIds == null) return startRecordingsOf(recordAllTargets(devices, activeRecordings.keys))
        val resolved = deviceIds.map { id -> id to devices.firstOrNull { it.id == id } }
        val unknown = resolved.filter { it.second == null }.map { (id, _) ->
            RecordingResult.Failed(deviceId = id, deviceName = id, reason = "no device has the id '$id'; call $TOOL_PREFIX.listDevices")
        }
        return startRecordingsOf(resolved.mapNotNull { it.second }) + unknown
    }

    /** Starts recording each of [targets] at once; one that fails to start leaves the others recording. */
    @VisibleForTesting
    internal suspend fun startRecordingsOf(targets: List<MirrorDevice>): List<RecordingResult> = coroutineScope {
        targets.map { device ->
            async {
                try {
                    startRecording(device)
                    RecordingResult.Started(deviceId = device.id, deviceName = device.listing.name)
                } catch (e: DeviceControlException) {
                    RecordingResult.Failed(deviceId = device.id, deviceName = device.listing.name, reason = e.message.orEmpty())
                }
            }
        }.awaitAll()
    }

    private suspend fun startRecordingLocked(device: MirrorDevice) {
        if (isDisposing) throw deviceControlError("the mirror is closing; no recording can start")
        if (activeRecordings.containsKey(device.id)) throw deviceControlError("${device.listing.name} is already recording; stop it first")
        val file = try {
            captures.recordingFile(device.listing)
        } catch (e: IOException) {
            throw deviceControlError("could not reserve a file for the recording of ${device.listing.name}: ${e.message}")
        }
        val handle = try {
            device.controller.startRecording(file)
        } catch (e: DeviceControlException) {
            file.delete()
            throw e
        }
        changeRecordings { it[device.id] = ActiveRecording(device, handle, file, Instant.now()) }
    }

    override suspend fun stopRecording(deviceId: String?): Capture {
        val id = deviceId ?: soleRecordingDeviceId()
        return recordingLockOf(id).withLock { stopRecordingLocked(id) }
    }

    override suspend fun stopRecordings(deviceIds: List<String>?): List<RecordingResult> = coroutineScope {
        (deviceIds ?: synchronized(activeRecordings) { activeRecordings.keys.toList() }).map { id ->
            async { recordingLockOf(id).withLock { stopResultLocked(id) } }
        }.awaitAll()
    }

    private fun soleRecordingDeviceId(): String {
        val running = synchronized(activeRecordings) { activeRecordings.values.toList() }
        return when (running.size) {
            0 -> throw deviceControlError("no recording is running")
            1 -> running.single().device.id
            else -> throw deviceControlError("${running.size} recordings are running (${running.joinToString { it.device.listing.name }}); name the device to stop, or stop them all")
        }
    }

    private suspend fun stopResultLocked(deviceId: String): RecordingResult {
        val name = activeRecordings[deviceId]?.device?.listing?.name ?: devices.firstOrNull { it.id == deviceId }?.listing?.name ?: deviceId
        return try {
            RecordingResult.Saved(stopRecordingLocked(deviceId))
        } catch (e: DeviceControlException) {
            RecordingResult.Failed(deviceId = deviceId, deviceName = name, reason = "Could not stop the recording of $name: ${e.message}")
        } catch (e: IOException) {
            RecordingResult.Failed(deviceId = deviceId, deviceName = name, reason = "The recording of $name stopped, but could not be saved: ${e.message}")
        }
    }

    private suspend fun stopRecordingLocked(deviceId: String): Capture {
        val running = changeRecordings { it.remove(deviceId) } ?: throw deviceControlError("${devices.firstOrNull { it.id == deviceId }?.listing?.name ?: deviceId} is not recording")
        val file = try {
            running.handle.stop()
        } catch (e: DeviceControlException) {
            running.file.delete()
            throw e
        }
        val size = try {
            running.device.controller.screenSize()
        } catch (_: DeviceControlException) {
            null
        }
        return captures.addRecording(running.device.listing, file, running.startedAt, size)
    }

    private fun recordingLockOf(deviceId: String): Mutex = recordingLocks.getOrPut(deviceId, ::Mutex)

    private fun <T> changeRecordings(change: (MutableMap<String, ActiveRecording>) -> T): T = synchronized(activeRecordings) {
        change(activeRecordings).also {
            recordingsStartedAtMillis = activeRecordings.mapValues { entry -> entry.value.startedAt.toEpochMilli() }
        }
    }

    override fun listCaptures(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture> = captures.library.list(deviceId, kind, sinceEpochMillis)

    /**
     * Stops what outlives the UI: the recordings in progress, each kept in the library. Every one is
     * stopped even when another fails; a failure is reported once all have been tried.
     */
    suspend fun dispose() {
        // No start begins after this. One already under way holds its device's lock until its
        // recorder is running, so taking every lock waits for it, and its recording is stopped too.
        isDisposing = true
        val failures = coroutineScope {
            recordingLocks.keys.toList().map { id ->
                async { recordingLockOf(id).withLock { if (activeRecordings.containsKey(id)) stopResultLocked(id) else null } }
            }.awaitAll()
        }.filterIsInstance<RecordingResult.Failed>()
        surface.close()
        failures.firstOrNull()?.let { throw deviceControlError(it.reason) }
    }

    private fun control(action: suspend (DeviceController) -> Unit) {
        val device = selectedDevice ?: return
        scope.launch {
            try {
                action(device.controller)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeviceControlException) {
                notices.show(MirrorNotice.failure(e.message.orEmpty(), retry = null))
            }
        }
    }
}

/** The devices Record all starts: those that can record and are not recording already. */
internal fun recordAllTargets(devices: List<MirrorDevice>, recordingDeviceIds: Set<String>): List<MirrorDevice> = devices.filter { it.controller.capabilities.recording && it.id !in recordingDeviceIds }
