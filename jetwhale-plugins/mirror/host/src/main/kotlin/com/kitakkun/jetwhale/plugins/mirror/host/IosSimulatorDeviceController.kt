package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerButton
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerStartException
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/**
 * A booted iOS simulator. Screenshots and recordings go through `simctl` and the live stream through
 * idb, since simctl can neither stream nor send touches. Input goes through the XCTest runner
 * ([runnerInput]), and through idb when the runner cannot start; Recent apps always goes through
 * idb, since no runner command opens the app switcher. idb sends input only when
 * [idbCanSendSimulatorInput]: it streams with every Xcode, but cannot send input with one that moved
 * SimulatorKit.
 */
internal class IosSimulatorDeviceController(
    private val udid: String,
    private val xcrunPath: String,
    private val idbPath: String?,
    idbCanSendSimulatorInput: Boolean,
    private val runnerInput: XcTestRunnerInput?,
) : DeviceController {
    private val runnerTarget = XcTestRunnerTarget.Simulator(udid)

    private val idbInputPath = idbPath?.takeIf { idbCanSendSimulatorInput }

    override val capabilities = DeviceCapabilities(
        inputRefusal = if (runnerInput == null && idbInputPath == null) "input to a simulator needs Xcode's xcodebuild, or an idb that can send input" else null,
        buttons = if (runnerInput == null && idbInputPath == null) emptyList() else listOfNotNull(DeviceButton.Home, DeviceButton.Recents.takeIf { idbInputPath != null }, DeviceButton.Power),
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

    override suspend fun tap(x: Int, y: Int) = sendInput(
        throughRunner = { it.tap(runnerTarget, x, y) },
        throughIdb = { idbPath ->
            val scale = pixelsPerPoint()
            runCommandChecked(idbPath, "ui", "tap", "--udid", udid, "${(x / scale).toInt()}", "${(y / scale).toInt()}")
        },
    )

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = sendInput(
        throughRunner = { it.swipe(runnerTarget, fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis) },
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
        val pressThroughIdb: suspend (String) -> Unit = { idbPath -> presses.forEach { idbButton -> runCommandChecked(idbPath, "ui", "button", "--udid", udid, idbButton) } }
        val runnerButton = when (button) {
            DeviceButton.Home -> XcTestRunnerButton.Home
            DeviceButton.Power -> XcTestRunnerButton.Lock
            else -> null
        }
        if (runnerButton == null) {
            pressThroughIdb(idbInputPath ?: throw deviceControlError(idbInputRefusal("${button.label} on a simulator")))
        } else {
            sendInput(throughRunner = { it.pressButton(runnerTarget, runnerButton) }, throughIdb = pressThroughIdb)
        }
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
        val idbPath = idbInputPath ?: throw DeviceControlException(runnerFailure?.message ?: idbInputRefusal("input to a simulator"), runnerFailure)
        try {
            throughIdb(idbPath)
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

    private fun idbInputRefusal(refusedInput: String): String = if (idbPath == null) "$refusedInput goes through idb, which is not installed: $IDB_INSTALL" else "$refusedInput goes through idb, which cannot send input with this Xcode: idb_companion does not find SimulatorKit where this Xcode keeps it"

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
