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
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes
import kotlin.time.Duration

/**
 * A booted iOS simulator, which needs only Xcode. Live video and input go through its XCTest runner
 * ([runnerInput]); screenshots and recordings go through `simctl`, which needs no runner, and its
 * screenshots stand in for the video while the runner starts.
 *
 * Frames, screenshots, taps and swipes are all in the screen's pixels as the interface turns it: a
 * simulator turned to landscape is mirrored, and tapped, in landscape.
 */
internal class IosSimulatorDeviceController(
    private val udid: String,
    iosMajorVersion: Int?,
    private val xcrunPath: String,
    private val runnerInput: XcTestRunnerInput?,
) : DeviceController,
    XcTestRunnerDriven {
    private val runnerTarget = XcTestRunnerTarget.Simulator(udid, iosMajorVersion)

    /** Why the simulator has no live video and takes no input, when that is known without starting a runner. */
    private val runnerRefusal = if (runnerInput == null) "a simulator's live video and input need Xcode's xcodebuild, which was not found" else runnerInput.refusalFor(runnerTarget)

    override val capabilities = DeviceCapabilities(
        inputRefusal = runnerRefusal,
        buttons = if (runnerRefusal == null) listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power) else emptyList(),
        recording = true,
        screenPower = false,
    )

    override suspend fun captureScreenshot(): ByteArray {
        val file = createTempFile(prefix = "jetwhale-mirror-", suffix = ".png")
        try {
            runCommandChecked(xcrunPath, "simctl", "io", udid, "screenshot", file.toString())
            return file.readBytes()
        } finally {
            file.deleteIfExists()
        }
    }

    override suspend fun screenSize(): IntSize = parseSimulatorScreen(runCommandChecked(xcrunPath, "simctl", "io", udid, "enumerate").stdoutText)
        ?: throw deviceControlError("'simctl io enumerate' reported no screen for the simulator")

    override suspend fun tap(x: Int, y: Int) = requireRunnerInput().tap(runnerTarget, x, y, XcTestRunnerPointSpace.Screen)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = requireRunnerInput().swipe(runnerTarget, fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis, space = XcTestRunnerPointSpace.Screen)

    override suspend fun pressButton(button: DeviceButton) = when (button) {
        DeviceButton.Home -> requireRunnerInput().pressButton(runnerTarget, XcTestRunnerButton.Home)
        DeviceButton.Power -> requireRunnerInput().pressButton(runnerTarget, XcTestRunnerButton.Lock)
        DeviceButton.Recents -> requireRunnerInput().openAppSwitcher(runnerTarget)
        DeviceButton.Back, DeviceButton.VolumeUp, DeviceButton.VolumeDown -> throw deviceControlError("the iOS simulator has no ${button.label} button")
    }

    override suspend fun inputText(text: String) = requireRunnerInput().typeText(runnerTarget, text)

    override suspend fun keepRunnerAlive(duration: Duration): Instant = requireRunnerInput().keepRunnerAlive(runnerTarget, duration)

    override fun runnerKeptAliveUntil(): Instant? = runnerInput?.runnerKeptAliveUntil(runnerTarget)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = VideoStream.EncodedImages(streamRunnerFramesWithStandInScreenshots(requireRunnerInput()))

    /**
     * The runner's frames, with screenshots standing in until the first one comes: a runner takes
     * about three seconds to start, and the first build for an Xcode about ten more. A newer frame
     * replaces one the reader has not taken yet.
     */
    private fun streamRunnerFramesWithStandInScreenshots(input: XcTestRunnerInput): Flow<ByteArray> = channelFlow {
        val screenshots = launch {
            while (true) {
                try {
                    send(captureScreenshot())
                } catch (_: DeviceControlException) {
                    // A failed stand-in is skipped: the runner's frames replace screenshots soon,
                    // or its failure ends the stream.
                }
                delay(STAND_IN_SCREENSHOT_INTERVAL_MILLIS)
            }
        }
        input.streamScreenAsJpeg(runnerTarget, RUNNER_STREAM_FPS).collect { frame ->
            if (screenshots.isActive) screenshots.cancelAndJoin()
            send(frame)
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
                runCommand("kill", "-INT", "${process.pid()}")
                if (!process.waitFor(15, TimeUnit.SECONDS)) process.destroyForcibly()
                if (!outputFile.exists() || outputFile.length() == 0L) throw deviceControlError("recording failed: ${outputFile.absolutePath} was not written")
                outputFile
            }
        }
    }

    override suspend fun release() = Unit

    private fun requireRunnerInput(): XcTestRunnerInput {
        runnerRefusal?.let { throw deviceControlError(it) }
        return checkNotNull(runnerInput)
    }
}

/**
 * The rate a simulator's runner is asked to stream at. testmanagerd takes a frame in about 22 ms, so
 * the runner keeps up with this, and Skia decodes a frame in a few milliseconds.
 */
private const val RUNNER_STREAM_FPS = 30

/** simctl takes a screenshot in about 150 ms, and a stand-in needs no more than a few a second. */
private const val STAND_IN_SCREENSHOT_INTERVAL_MILLIS = 250L
