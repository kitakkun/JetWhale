package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/**
 * A booted iOS simulator. Screenshots and recordings go through `simctl`; the live stream and all
 * input need idb, since simctl can neither stream nor send touches.
 *
 * idb reaches a simulator through a companion. Left to itself, idb starts one that it never stops,
 * so the simulator's companion comes from [companions] instead, which stops it once it is unused and
 * when the host exits. Without `idb_companion` found ([companions] null), idb is left to start its own.
 */
internal class IosSimulatorDeviceController(
    private val udid: String,
    private val xcrunPath: String,
    private val idbPath: String?,
    private val companions: IdbCompanions?,
) : DeviceController {
    override val capabilities = DeviceCapabilities(
        input = idbPath != null,
        buttons = if (idbPath != null) listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power) else emptyList(),
        recording = true,
        screenPower = false,
    )

    private var screen: IdbScreen? = null

    private var holdsStreamCompanion = false

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

    override suspend fun tap(x: Int, y: Int) {
        val scale = pixelsPerPoint()
        runIdb("ui", "tap", "--udid", udid, "${(x / scale).toInt()}", "${(y / scale).toInt()}")
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) {
        val scale = pixelsPerPoint()
        runIdb(
            "ui", "swipe", "--udid", udid, "--duration", "${durationMillis / 1000.0}",
            "${(fromX / scale).toInt()}", "${(fromY / scale).toInt()}", "${(toX / scale).toInt()}", "${(toY / scale).toInt()}",
        )
    }

    override suspend fun pressButton(button: DeviceButton) {
        val presses = iosSimulatorPressesOf(button) ?: throw deviceControlError("the iOS simulator has no ${button.label} button")
        presses.forEach { idbButton -> runIdb("ui", "button", "--udid", udid, idbButton) }
    }

    override suspend fun inputText(text: String) {
        runIdb("ui", "text", "--udid", udid, text)
    }

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    // A simulator's H.264 stream sends a frame only when the framebuffer reports damage, which a
    // current CoreSimulator does so rarely that the picture freezes for seconds. Its raw stream is
    // paced by --fps and scaled by the simulator instead, so there is nothing to decode.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val layout = rawBgraLayout(screenSize(), wanted, maxFps = fpsCap, maxWidth = widthCap)
        if (companions != null && !holdsStreamCompanion) {
            companions.acquire(udid)
            holdsStreamCompanion = true
        }
        val process = withContext(Dispatchers.IO) {
            SystemProcessLauncher.start(
                // idb names its raw format rbga; the bytes it writes are BGRA.
                listOf(requireIdbPath(), "video-stream", "--udid", udid, "--format", "rbga", "--fps", "${layout.fps}", "--scale-factor", "${layout.scale}"),
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

    override suspend fun release() {
        if (!holdsStreamCompanion) return
        holdsStreamCompanion = false
        companions?.release(udid)
    }

    private fun requireIdbPath(): String = idbPath ?: throw deviceControlError(IDB_MISSING)

    /** Runs idb with [arguments], holding the simulator's companion meanwhile. */
    private suspend fun runIdb(vararg arguments: String): CommandResult {
        val idb = requireIdbPath()
        companions?.acquire(udid)
        try {
            return runCommandChecked(idb, *arguments)
        } finally {
            // A cancelled caller still gives its use back, or the companion would never stop when idle.
            withContext(NonCancellable) { companions?.release(udid) }
        }
    }

    override suspend fun screenSize(): IntSize = readSimulatorScreenFromIdb().size

    // idb takes points; the mirror works in pixels, so the screen's density converts between them.
    private suspend fun pixelsPerPoint(): Double = readSimulatorScreenFromIdb().pixelsPerPoint

    private suspend fun readSimulatorScreenFromIdb(): IdbScreen {
        screen?.let { return it }
        val description = runIdb("describe", "--udid", udid, "--json").stdoutText
        return (parseIdbScreen(description) ?: throw deviceControlError("'idb describe' reported no screen size")).also { screen = it }
    }
}

// The formula installs the command-line client and the companion together, at matching versions.
internal const val IDB_INSTALL = "brew install facebook/fb/idb"

internal const val IDB_MISSING = "iOS input and live streaming need idb (https://fbidb.io): $IDB_INSTALL"

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
