package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerPointSpace
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerStartException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes
import kotlin.time.Duration

/**
 * A booted iOS simulator. Screenshots and recordings go through `simctl` and the live stream through
 * idb, since simctl can neither stream nor send touches. Input goes through the XCTest runner
 * ([runnerInput]), and through idb when the runner cannot start. idb sends input only when
 * [idbCanSendSimulatorInput]: it streams with every Xcode, but cannot send input with one that moved
 * SimulatorKit.
 */
internal class IosSimulatorDeviceController(
    private val udid: String,
    iosMajorVersion: Int?,
    private val xcrunPath: String,
    private val idbPath: String?,
    idbCanSendSimulatorInput: Boolean,
    private val runnerInput: XcTestRunnerInput?,
) : DeviceController,
    ScreenshotSpaceInput,
    XcTestRunnerDriven {
    private val runnerTarget = XcTestRunnerTarget.Simulator(udid, iosMajorVersion)

    private val idbInputPath = idbPath?.takeIf { idbCanSendSimulatorInput }

    private val runnerRefusal = if (runnerInput == null) "input to a simulator needs Xcode's xcodebuild, or an idb that can send input" else runnerInput.refusalFor(runnerTarget)

    override val capabilities = DeviceCapabilities(
        inputRefusal = runnerRefusal.takeIf { idbInputPath == null },
        buttons = if (runnerRefusal != null && idbInputPath == null) emptyList() else listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power),
        recording = true,
        screenPower = false,
    )

    private var screen: IdbScreen? = null

    @Volatile
    private var fpsCap = MAX_RAW_FPS

    @Volatile
    private var widthCap = MAX_SIMULATOR_STREAM_WIDTH

    override suspend fun captureScreenshot(): ByteArray {
        val file = createTempFile(prefix = "jetwhale-mirror-", suffix = ".png")
        try {
            runCommandChecked(xcrunPath, "simctl", "io", udid, "screenshot", file.toString())
            return file.readBytes()
        } finally {
            file.deleteIfExists()
        }
    }

    override suspend fun tap(x: Int, y: Int) = tapIn(XcTestRunnerPointSpace.Device, x, y)

    // A screenshot follows the interface orientation, while the stream and tap() keep the screen's
    // portrait pixels; the runner turns screenshot points into those.
    override suspend fun tapScreenshotPixel(x: Int, y: Int) = tapIn(XcTestRunnerPointSpace.Screen, x, y)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = swipeIn(XcTestRunnerPointSpace.Device, fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis)

    override suspend fun swipeScreenshotPixels(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = swipeIn(XcTestRunnerPointSpace.Screen, fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis)

    override suspend fun screenshotSize(): IntSize {
        if (runnerInput == null) return screenSize()
        return try {
            runnerInput.screenshotSize(runnerTarget)
        } catch (_: XcTestRunnerStartException) {
            // idb, which takes over, knows only the portrait screen.
            screenSize()
        }
    }

    override suspend fun keepRunnerAlive(duration: Duration): Instant {
        val input = runnerInput ?: throw deviceControlError("this simulator has no XCTest runner to keep alive: Xcode's xcodebuild was not found")
        return try {
            input.keepRunnerAlive(runnerTarget, duration)
        } catch (e: XcTestRunnerStartException) {
            throw DeviceControlException(e.message.orEmpty(), e)
        }
    }

    override fun runnerKeptAliveUntil(): Instant? = runnerInput?.runnerKeptAliveUntil(runnerTarget)

    private suspend fun tapIn(space: XcTestRunnerPointSpace, x: Int, y: Int) = sendInput(
        throughRunner = { it.tap(runnerTarget, x, y, space) },
        throughIdb = { idbPath ->
            val scale = pixelsPerPoint()
            runCommandChecked(idbPath, "ui", "tap", "--udid", udid, "${(x / scale).toInt()}", "${(y / scale).toInt()}")
        },
    )

    private suspend fun swipeIn(space: XcTestRunnerPointSpace, fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = sendInput(
        throughRunner = { it.swipe(runnerTarget, fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis, space = space) },
        throughIdb = { idbPath ->
            val scale = pixelsPerPoint()
            runCommandChecked(
                idbPath, "ui", "swipe", "--udid", udid, "--duration", "${durationMillis / 1000.0}",
                "${(fromX / scale).toInt()}", "${(fromY / scale).toInt()}", "${(toX / scale).toInt()}", "${(toY / scale).toInt()}",
            )
        },
    )

    override suspend fun pressButton(button: DeviceButton) {
        val presses = iosSimulatorPressesOf(button) ?: throw deviceControlError("the iOS simulator has no ${button.label} button")
        sendInput(
            throughRunner = { input ->
                when (button) {
                    DeviceButton.Home -> input.pressButton(runnerTarget, XcTestRunnerButton.Home)
                    DeviceButton.Power -> input.pressButton(runnerTarget, XcTestRunnerButton.Lock)
                    else -> input.openAppSwitcher(runnerTarget)
                }
            },
            throughIdb = { idbPath -> presses.forEach { idbButton -> runCommandChecked(idbPath, "ui", "button", "--udid", udid, idbButton) } },
        )
    }

    override suspend fun inputText(text: String) = sendInput(
        throughRunner = { it.typeText(runnerTarget, text) },
        throughIdb = { idbPath -> runCommandChecked(idbPath, "ui", "text", "--udid", udid, text) },
    )

    /** Sends input through the runner, or through idb when the runner cannot start. */
    private suspend fun sendInput(throughRunner: suspend (XcTestRunnerInput) -> Unit, throughIdb: suspend (idbPath: String) -> Unit) {
        val runnerFailure = runnerInput?.let { input ->
            try {
                throughRunner(input)
                return
            } catch (e: XcTestRunnerStartException) {
                e
            }
        }
        if (idbInputPath == null) {
            throw DeviceControlException(
                runnerFailure?.message ?: when (idbPath) {
                    null -> "input to a simulator goes through idb, which is not installed: $IDB_INSTALL"
                    else -> "input to a simulator goes through idb, which cannot send input with this Xcode: idb_companion does not find SimulatorKit where this Xcode keeps it"
                },
                runnerFailure,
            )
        }
        try {
            throughIdb(idbInputPath)
        } catch (e: DeviceControlException) {
            if (runnerFailure == null) throw e
            throw DeviceControlException("${runnerFailure.message}; idb failed as well: ${e.message}", e)
        }
    }

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    // A simulator's H.264 stream sends a frame only when the framebuffer reports damage, which a
    // current CoreSimulator does so rarely that the picture freezes for seconds. Its raw stream is
    // paced by --fps and scaled by the simulator instead, so there is nothing to decode.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        runnerInput?.startRunnerInBackground(runnerTarget)
        val idbPath = requireIdbPath()
        val layout = rawBgraLayout(screenSize(), wanted, maxFps = fpsCap, maxWidth = widthCap)
        val process = withContext(Dispatchers.IO) {
            SystemProcessLauncher.start(
                // idb names its raw format rbga; the bytes it writes are BGRA.
                listOf(idbPath, "video-stream", "--udid", udid, "--format", "rbga", "--fps", "${layout.fps}", "--scale-factor", "${layout.scale}"),
            )
        }
        return VideoStream.RawBgra(process, layout.frameSize, layout.rowBytes, layout.fps) { arrivedFps ->
            val caps = lighterThan(layout, arrivedFps) ?: return@RawBgra false
            fpsCap = caps.maxFps
            widthCap = caps.maxWidth
            true
        }
    }

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

    private fun requireIdbPath(): String = idbPath ?: throw deviceControlError(IDB_MISSING)

    override suspend fun screenSize(): IntSize {
        if (idbPath != null || runnerInput == null) return readSimulatorScreenFromIdb().size
        return try {
            runnerInput.screenSize(runnerTarget)
        } catch (e: XcTestRunnerStartException) {
            throw DeviceControlException(e.message.orEmpty(), e)
        }
    }

    // idb takes points; the mirror works in pixels, so the screen's density converts between them.
    private suspend fun pixelsPerPoint(): Double = readSimulatorScreenFromIdb().pixelsPerPoint

    private suspend fun readSimulatorScreenFromIdb(): IdbScreen {
        screen?.let { return it }
        val description = runCommandChecked(requireIdbPath(), "describe", "--udid", udid, "--json").stdoutText
        return (parseIdbScreen(description) ?: throw deviceControlError("'idb describe' reported no screen size")).also { screen = it }
    }
}

// The formula installs the command-line client and the companion together, at matching versions.
internal const val IDB_INSTALL = "brew install facebook/fb/idb"

internal const val IDB_MISSING = "iOS live streaming needs idb (https://fbidb.io): $IDB_INSTALL"

/**
 * The idb buttons pressed, in order, for [button] on a simulator, or null for a button it lacks.
 * A Face ID iPhone has no home button to double-press, but the simulator still opens the app
 * switcher on two HOME presses in quick succession; two idb calls in a row land about 0.3 s apart,
 * well inside that window.
 */
@VisibleForTesting
internal fun iosSimulatorPressesOf(button: DeviceButton): List<String>? = when (button) {
    DeviceButton.Home -> listOf("HOME")
    DeviceButton.Recents -> listOf("HOME", "HOME")
    DeviceButton.Power -> listOf("LOCK")
    DeviceButton.Back, DeviceButton.VolumeUp, DeviceButton.VolumeDown -> null
}

/**
 * Whether idb can send input to simulators with the Xcode at [developerDirectory]: idb_companion
 * loads SimulatorKit from `Library/PrivateFrameworks` there, and Xcode 27 keeps it elsewhere.
 */
internal fun isSimulatorKitInPrivateFrameworks(developerDirectory: File): Boolean = File(developerDirectory, "Library/PrivateFrameworks/SimulatorKit.framework").isDirectory
