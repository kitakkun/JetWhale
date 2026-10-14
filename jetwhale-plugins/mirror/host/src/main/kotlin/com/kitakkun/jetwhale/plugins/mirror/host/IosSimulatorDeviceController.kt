package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerPointSpace
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A booted iOS simulator, which needs only Xcode. Live video and input go through its XCTest runner
 * ([xcTestRunnerClient]); screenshots and recordings go through `simctl`, which needs no runner, and its
 * screenshots stand in for the video while the runner starts.
 *
 * A simulator whose runner cannot run is shown view only: [openVideoStream] and every input fail with
 * the reason, which the mirror shows beside the screenshots it takes instead. A runner that fails to
 * build or start while the stream starts it is not tried again until [forgetRunnerStartFailure].
 *
 * Frames, screenshots, taps and swipes are all in the screen's pixels as the interface turns it: a
 * simulator turned to landscape is mirrored, and tapped, in landscape.
 */
internal class IosSimulatorDeviceController(
    private val udid: String,
    iosMajorVersion: Int?,
    private val xcrunPath: String,
    private val xcTestRunnerClient: XcTestRunnerClient?,
) : XcTestRunnerDeviceController {
    private val runnerTarget = XcTestRunnerTarget.Simulator(udid, iosMajorVersion)

    /** Why the simulator has no live video and takes no input, when that is known without starting a runner. */
    private val runnerRefusal = if (xcTestRunnerClient == null) "a simulator's live video and input need Xcode, which was not found: install it from the App Store" else xcTestRunnerClient.refusalFor(runnerTarget)

    /** Why the runner could not be built or started when the stream last tried it. */
    @Volatile
    private var runnerStartFailure: String? = null

    override val capabilities: DeviceCapabilities
        get() {
            val refusal = runnerRefusal ?: runnerStartFailure
            return DeviceCapabilities(
                inputRefusal = refusal,
                buttons = if (refusal == null) listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power) else emptyList(),
                recording = true,
                screenPower = false,
            )
        }

    override suspend fun captureScreenshot(): ByteArray {
        val file = createTempFile(prefix = "jetwhale-mirror-", suffix = ".png")
        try {
            runCommandChecked(SIMCTL_SCREENSHOT_TIMEOUT, xcrunPath, "simctl", "io", udid, "screenshot", file.toString())
            return file.readBytes()
        } finally {
            file.deleteIfExists()
        }
    }

    override suspend fun screenSize(): IntSize = parseSimulatorScreen(runCommandChecked(SIMCTL_IO_ENUMERATE_TIMEOUT, xcrunPath, "simctl", "io", udid, "enumerate").stdoutText)
        ?: throw deviceControlError("'simctl io enumerate' reported no screen for the simulator")

    override suspend fun tap(x: Int, y: Int) = requireXcTestRunnerClient().tap(runnerTarget, x, y, XcTestRunnerPointSpace.Screen)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = requireXcTestRunnerClient().swipe(runnerTarget, fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis, space = XcTestRunnerPointSpace.Screen)

    override suspend fun pressButton(button: DeviceButton) = when (button) {
        DeviceButton.Home -> requireXcTestRunnerClient().pressButton(runnerTarget, XcTestRunnerButton.Home)
        DeviceButton.Power -> requireXcTestRunnerClient().pressButton(runnerTarget, XcTestRunnerButton.Lock)
        DeviceButton.Recents -> requireXcTestRunnerClient().openAppSwitcher(runnerTarget)
        DeviceButton.Back, DeviceButton.VolumeUp, DeviceButton.VolumeDown -> throw deviceControlError("the iOS simulator has no ${button.label} button")
    }

    override suspend fun inputText(text: String) = requireXcTestRunnerClient().typeText(runnerTarget, text)

    override suspend fun keepRunnerAlive(duration: Duration): Instant = requireXcTestRunnerClient().keepRunnerAlive(runnerTarget, duration)

    override fun runnerKeptAliveUntil(): Instant? = xcTestRunnerClient?.runnerKeptAliveUntil(runnerTarget)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = VideoStream.EncodedImages(streamRunnerFramesWithStandInScreenshots(requireXcTestRunnerClient()))

    /** Lets the next [openVideoStream] try the runner again after it failed to build or start. */
    fun forgetRunnerStartFailure() {
        runnerStartFailure = null
    }

    /**
     * The runner's frames, with screenshots standing in until the first one comes: a runner takes
     * about three seconds to start, and the first build for an Xcode about ten more. A newer frame
     * replaces one the reader has not taken yet. A runner that cannot be built or started ends the
     * stream with [RunnerStartException], and the simulator is view only from then on.
     */
    private fun streamRunnerFramesWithStandInScreenshots(input: XcTestRunnerClient): Flow<ByteArray> = channelFlow {
        val screenshots = launch {
            while (true) {
                // A failed stand-in is skipped: the runner's frames soon replace the screenshots,
                // or its failure ends the stream.
                try {
                    send(captureScreenshot())
                } catch (_: DeviceControlException) {
                } catch (_: IOException) {
                }
                delay(STAND_IN_SCREENSHOT_INTERVAL_MILLIS)
            }
        }
        try {
            input.streamScreenAsJpeg(runnerTarget, RUNNER_STREAM_FPS).collect { frame ->
                if (screenshots.isActive) screenshots.cancelAndJoin()
                send(frame)
            }
        } catch (e: RunnerStartException) {
            runnerStartFailure = e.message.orEmpty()
            throw e
        }
    }.conflate()

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        val process = withContext(Dispatchers.IO) {
            SystemProcessLauncher.start(listOf(xcrunPath, "simctl", "io", udid, "recordVideo", "--codec=h264", "--force", outputFile.absolutePath))
        }
        return object : DeviceRecording {
            override suspend fun stop(): File = withContext(Dispatchers.IO) {
                // recordVideo finishes the file only on SIGINT; Process.destroy() sends SIGTERM,
                // which leaves it unplayable.
                runCommand(5.seconds, "kill", "-INT", "${process.pid()}")
                if (!process.waitFor(15, TimeUnit.SECONDS)) process.destroyForcibly()
                if (!outputFile.exists() || outputFile.length() == 0L) throw deviceControlError("recording failed: ${outputFile.absolutePath} was not written")
                outputFile
            }
        }
    }

    override suspend fun release() = Unit

    private fun requireXcTestRunnerClient(): XcTestRunnerClient {
        (runnerRefusal ?: runnerStartFailure)?.let { throw deviceControlError(it) }
        return checkNotNull(xcTestRunnerClient)
    }
}

/**
 * How long simctl may take to write a screenshot. It takes well under a second, but can wait forever
 * on a simulator that is shut down or deleted while it runs.
 */
private val SIMCTL_SCREENSHOT_TIMEOUT = 10.seconds

/**
 * How long simctl may take to list a simulator's screens. It answers well under a second, so one
 * still running after this long is not answering.
 */
private val SIMCTL_IO_ENUMERATE_TIMEOUT = 10.seconds

/**
 * The rate a simulator's runner is asked to stream at: the simulator draws at 60 Hz, and 60 is the
 * most the runner sends. testmanagerd takes a frame in about 13 ms, so the runner keeps up, and Skia
 * decodes one in a few milliseconds. The runner sends an unchanged frame only once a second, so a
 * still screen costs the host next to nothing.
 */
private const val RUNNER_STREAM_FPS = 60

/** simctl takes a screenshot in about 150 ms, and a stand-in needs no more than a few a second. */
private const val STAND_IN_SCREENSHOT_INTERVAL_MILLIS = 250L
