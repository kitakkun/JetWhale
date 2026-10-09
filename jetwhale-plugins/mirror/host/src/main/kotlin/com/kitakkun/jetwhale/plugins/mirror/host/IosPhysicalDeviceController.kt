package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerPointSpace
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * A physical iOS device over USB, called [name]. Its screen comes from the iPhone capture helper
 * through [captures], shared with everything else that reads it: the live view, screenshots and
 * recordings, which ffmpeg decodes and wraps. Input goes through the XCTest runner, signed with
 * the user's development team and reached through `iproxy`; driving a device that way is
 * experimental: it follows Apple's and Appium's documentation and has not been tried on one.
 *
 * The capture comes before the runner. Starting it switches the iPhone's USB connection over,
 * which drops iproxy's connection to the runner, so the runner is started only once the capture
 * sends frames, and input starts the capture before it reaches the runner. The capture outlives
 * its last use by the captures' idle timeout, and its stop switches the connection back, after
 * which the next input finds the runner gone and starts it again. A capture that cannot start
 * leaves input, and the screen's size, to the runner alone.
 */
internal class IosPhysicalDeviceController(
    private val udid: String,
    private val name: String,
    iosMajorVersion: Int?,
    private val captures: IphoneScreenCaptures,
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
                recording = ffmpegPath != null,
                screenPower = false,
            )
        }

    /** A key frame of the current screen, asked of the capture for a reader of its own, as a PNG. */
    override suspend fun captureScreenshot(): ByteArray {
        val ffmpegPath = requireFfmpegPath("a screenshot of an iPhone")
        return withStartedCapture { capture ->
            capture.subscribe().use { subscription ->
                withContext(Dispatchers.IO) { firstH264FrameAsPng(subscription.stream, ffmpegPath, STILL_FRAME_TIMEOUT_MILLIS) }
            }
        }
    }

    /**
     * The size of the frames the capture sends, starting a capture when none runs, or the size the
     * runner reports when the capture fails and the iPhone takes input.
     */
    override suspend fun screenSize(): IntSize = try {
        withStartedCapture(::awaitFrameSize)
    } catch (e: DeviceControlException) {
        if (inputRefusal() != null) throw e
        checkNotNull(runnerInput).screenSize(runnerTarget)
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

    override suspend fun keepRunnerAlive(duration: Duration): Instant = sendInput { input, target -> input.keepRunnerAlive(target, duration) }

    override fun runnerKeptAliveUntil(): Instant? = runnerInput?.runnerKeptAliveUntil(runnerTarget)

    private suspend fun <T> sendInput(command: suspend (XcTestRunnerInput, XcTestRunnerTarget) -> T): T {
        inputRefusal()?.let { throw deviceControlError(it) }
        try {
            withStartedCapture(::awaitFrameSize)
        } catch (_: DeviceControlException) {
        }
        return command(checkNotNull(runnerInput), runnerTarget)
    }

    /**
     * Starts the runner ahead of the first input, once the iPhone can take input and its screen is
     * being captured; until then, does nothing.
     */
    fun startRunnerInBackground() {
        if (inputRefusal() == null && captures.isCapturing(udid)) checkNotNull(runnerInput).startRunnerInBackground(runnerTarget)
    }

    private fun inputRefusal(): String? = if (runnerInput == null) "driving an iPhone needs Xcode's xcodebuild, which was not found" else runnerInput.refusalFor(runnerTarget)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        val ffmpegPath = requireFfmpegPath("recording an iPhone")
        val capture = captures.acquire(udid, name)
        var recorder: H264FileRecorder? = null
        try {
            capture.awaitStarted()
            val subscription = capture.subscribe()
            try {
                recorder = withContext(Dispatchers.IO) { H264FileRecorder(subscription.stream, ffmpegPath, outputFile, subscription::end) }
            } finally {
                if (recorder == null) subscription.close()
            }
        } finally {
            if (recorder == null) captures.release(capture)
        }
        val started = checkNotNull(recorder)
        return object : DeviceRecording {
            override suspend fun stop(): File = try {
                withContext(Dispatchers.IO) { started.stop() }
            } finally {
                captures.release(capture)
            }
        }
    }

    /**
     * A reader of the capture at its own size, which ffmpeg shrinks to the size it is shown at; the
     * runner is started once the capture's first frame has arrived.
     */
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val ffmpegPath = requireFfmpegPath("mirroring an iPhone")
        val capture = captures.acquire(udid, name)
        var subscription: H264Subscription? = null
        try {
            capture.awaitStarted()
            capture.firstFrameSize.invokeOnCompletion { failure -> if (failure == null) startRunnerInBackground() }
            subscription = capture.subscribe()
        } finally {
            if (subscription == null) captures.release(capture)
        }
        val reader = checkNotNull(subscription)
        val released = AtomicBoolean(false)
        return VideoStream.H264(reader.stream, ffmpegPath) {
            reader.close()
            if (released.compareAndSet(false, true)) captures.release(capture)
        }
    }

    private suspend fun <T> withStartedCapture(use: suspend (IphoneScreenCapture) -> T): T {
        val capture = captures.acquire(udid, name)
        try {
            capture.awaitStarted()
            return use(capture)
        } finally {
            captures.release(capture)
        }
    }

    private suspend fun awaitFrameSize(capture: IphoneScreenCapture): IntSize = capture.frameSize
        ?: withTimeoutOrNull(FIRST_FRAME_WAIT_MILLIS) { capture.firstFrameSize.await() }
        ?: throw deviceControlError("the iPhone sent no picture of its screen within ${FIRST_FRAME_WAIT_MILLIS / 1000} s. Unlock it and keep its screen on.")

    private fun requireFfmpegPath(use: String): String = ffmpegPath ?: throw deviceControlError("$use needs ffmpeg to decode its video. $FFMPEG_INSTALL")
}

/** How long a screenshot waits for the capture's key frame. */
private const val STILL_FRAME_TIMEOUT_MILLIS = 10_000L

/** How long a running capture gets to send its first frame. */
private const val FIRST_FRAME_WAIT_MILLIS = 10_000L
