package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerPointSpace
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.time.Duration

/**
 * A physical iOS device over USB. idb streams its screen through the device's [companions], null when
 * idb or its companion is missing; input goes through the XCTest runner, signed with the user's
 * development team and reached through `iproxy`. Driving a device that way is experimental: it
 * follows Apple's and Appium's documentation and has not been tried on one.
 *
 * Its screenshots and recordings come from that H.264 stream through ffmpeg: `idb screenshot` does
 * not reach a device running iOS 17 or later. The companion outlives each use by
 * [IdbCompanions]' idle timeout, so repeated screenshots do not restart it.
 */
internal class IosPhysicalDeviceController(
    private val udid: String,
    iosMajorVersion: Int?,
    private val companions: IdbCompanions?,
    private val ffmpegPath: String?,
    private val runnerInput: XcTestRunnerInput?,
) : DeviceController,
    XcTestRunnerDriven {
    private val runnerTarget = XcTestRunnerTarget.Device(udid, iosMajorVersion)

    override val capabilities: DeviceCapabilities
        get() {
            val refusal = inputRefusal()
            return DeviceCapabilities(
                inputRefusal = refusal,
                buttons = if (refusal == null) listOf(DeviceButton.Home, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown) else emptyList(),
                recording = ffmpegPath != null && companions != null,
                screenPower = false,
            )
        }

    private var holdsStreamCompanion = false

    @Volatile
    private var screen: IntSize? = null

    override suspend fun captureScreenshot(): ByteArray {
        val companions = requireCompanions("a screenshot of a physical iOS device")
        val ffmpegPath = requireFfmpegPath("a screenshot of a physical iOS device")
        companions.acquire(udid)
        try {
            val stream = startVideoStream(companions.idbPath)
            try {
                return withContext(Dispatchers.IO) { firstH264FrameAsPng(stream.inputStream, ffmpegPath, STILL_FRAME_TIMEOUT_MILLIS) }
            } finally {
                stream.destroyForcibly()
            }
        } finally {
            companions.release(udid)
        }
    }

    override suspend fun screenSize(): IntSize {
        screen?.let { return it }
        val idbScreenSize = try {
            companions?.let { readIdbScreenSize(it) }
        } catch (e: DeviceControlException) {
            if (inputRefusal() != null) throw e
            null
        }
        val size = idbScreenSize ?: readRunnerScreenSize()
        return size.also { screen = it }
    }

    /** The size `idb describe` reports for the device, or null when it reports none. */
    private suspend fun readIdbScreenSize(companions: IdbCompanions): IntSize? {
        companions.acquire(udid)
        val description = try {
            runCommandChecked(companions.idbPath, "describe", "--udid", udid, "--json").stdoutText
        } finally {
            companions.release(udid)
        }
        return parseIdbScreen(description)
    }

    private suspend fun readRunnerScreenSize(): IntSize {
        inputRefusal()?.let { refusal ->
            val idbNoSizeReason = if (companions == null) "idb is not installed" else "'idb describe' reported no size"
            throw deviceControlError("the size of a physical iOS device's screen comes from idb, or from its XCTest runner once it takes input: $idbNoSizeReason, and $refusal")
        }
        return checkNotNull(runnerInput).screenSize(runnerTarget)
    }

    override suspend fun tap(x: Int, y: Int) = sendInput { input, target -> input.tap(target, x, y, XcTestRunnerPointSpace.Device) }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = sendInput { input, target ->
        input.swipe(target, fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis, space = XcTestRunnerPointSpace.Device)
    }

    override suspend fun pressButton(button: DeviceButton) {
        val runnerButton = when (button) {
            DeviceButton.Home -> XcTestRunnerButton.Home
            DeviceButton.Power -> XcTestRunnerButton.Lock
            DeviceButton.VolumeUp -> XcTestRunnerButton.VolumeUp
            DeviceButton.VolumeDown -> XcTestRunnerButton.VolumeDown
            DeviceButton.Back, DeviceButton.Recents -> throw deviceControlError("an iPhone has no ${button.label} button to press from here")
        }
        sendInput { input, target -> input.pressButton(target, runnerButton) }
    }

    override suspend fun inputText(text: String) = sendInput { input, target -> input.typeText(target, text) }

    override suspend fun keepRunnerAlive(duration: Duration): Instant {
        inputRefusal()?.let { throw deviceControlError(it) }
        return checkNotNull(runnerInput).keepRunnerAlive(runnerTarget, duration)
    }

    override fun runnerKeptAliveUntil(): Instant? = runnerInput?.runnerKeptAliveUntil(runnerTarget)

    private suspend fun sendInput(command: suspend (XcTestRunnerInput, XcTestRunnerTarget) -> Unit) {
        inputRefusal()?.let { throw deviceControlError(it) }
        command(checkNotNull(runnerInput), runnerTarget)
    }

    /** Starts the runner ahead of the first input, once the iPhone can take input; until then, does nothing. */
    fun startRunnerInBackground() {
        if (inputRefusal() == null) checkNotNull(runnerInput).startRunnerInBackground(runnerTarget)
    }

    private fun inputRefusal(): String? = if (runnerInput == null) "driving an iPhone needs Xcode's xcodebuild, which was not found" else runnerInput.refusalFor(runnerTarget)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        val companions = requireCompanions("recording a physical iOS device")
        val ffmpegPath = requireFfmpegPath("recording a physical iOS device")
        companions.acquire(udid)
        val recorder = try {
            val stream = startVideoStream(companions.idbPath)
            try {
                withContext(Dispatchers.IO) { H264FileRecorder(stream, ffmpegPath, outputFile) }
            } catch (e: DeviceControlException) {
                stream.destroyForcibly()
                throw e
            }
        } catch (e: DeviceControlException) {
            companions.release(udid)
            throw e
        }
        return object : DeviceRecording {
            override suspend fun stop(): File = try {
                withContext(Dispatchers.IO) { recorder.stop() }
            } finally {
                companions.release(udid)
            }
        }
    }

    // --fps is ignored for a device, which streams at about 60; the mirror drops what it cannot show.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        startRunnerInBackground()
        val companions = requireCompanions("mirroring a physical iOS device")
        val ffmpegPath = requireFfmpegPath("mirroring a physical iOS device")
        if (!holdsStreamCompanion) {
            companions.acquire(udid)
            holdsStreamCompanion = true
        }
        return withContext(Dispatchers.IO) { VideoStream.H264(SystemProcessLauncher.start(videoStreamCommand(companions.idbPath)), ffmpegPath) }
    }

    private fun videoStreamCommand(idbPath: String): List<String> = listOf(
        idbPath, "video-stream", "--udid", udid, "--format", "h264", "--fps", "30",
        "--compression-quality", "$DEVICE_STREAM_COMPRESSION_QUALITY",
    )

    /** An idb stream of the device's screen as H.264 on stdout, its log drained so it never stalls on a full pipe. */
    private suspend fun startVideoStream(idbPath: String): Process = withContext(Dispatchers.IO) {
        SystemProcessLauncher.start(videoStreamCommand(idbPath)).also { stream ->
            thread(isDaemon = true, name = "mirror-idb-stream-log") { stream.errorStream.use(InputStream::readAllBytes) }
        }
    }

    private fun requireCompanions(use: String): IdbCompanions = companions ?: throw deviceControlError("$use needs idb, which streams the device's screen. $IDB_INSTALL_INSTRUCTIONS")

    private fun requireFfmpegPath(use: String): String = ffmpegPath ?: throw deviceControlError("$use needs ffmpeg to decode its video. $FFMPEG_INSTALL")

    override suspend fun release() {
        if (!holdsStreamCompanion) return
        holdsStreamCompanion = false
        checkNotNull(companions).release(udid)
    }
}

// The formula installs the idb client and idb_companion together, at matching versions.
internal const val IDB_INSTALL_COMMAND = "brew install facebook/fb/idb"

internal const val IDB_INSTALL_INSTRUCTIONS = "Install idb (https://fbidb.io) with its companion: $IDB_INSTALL_COMMAND"

/**
 * The VideoToolbox quality idb asks the device's H.264 encoder for. idb's own default of 0.2 leaves
 * a moving screen at about 1 Mbps, where edges break into blocks until the screen settles; at 0.8
 * a scrolling screen stays close to the source for about 10 Mbps, well within USB.
 */
private const val DEVICE_STREAM_COMPRESSION_QUALITY = 0.8

/** How long a screenshot waits for the device's stream to send its first frame. */
private const val STILL_FRAME_TIMEOUT_MILLIS = 10_000L
