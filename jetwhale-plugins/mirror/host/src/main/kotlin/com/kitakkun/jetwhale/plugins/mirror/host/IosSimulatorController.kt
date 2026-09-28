package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/**
 * A booted iOS simulator. Screenshots and recordings go through `simctl`; the live stream and all
 * input need idb, since simctl can neither stream nor send touches.
 */
internal class IosSimulatorController(
    private val udid: String,
    private val xcrun: String,
    private val idb: String?,
) : DeviceController {
    override val capabilities = DeviceCapabilities(
        input = idb != null,
        buttons = if (idb != null) listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power) else emptyList(),
        recording = true,
        screenPower = false,
    )

    private var screen: IdbScreen? = null

    // Lowered each time a stream falls behind, for instance while another tool streams this
    // simulator too; they stay lowered for as long as the simulator is listed.
    @Volatile
    private var fpsCap = MAX_RAW_FPS

    @Volatile
    private var widthCap = Int.MAX_VALUE

    override suspend fun captureScreenshot(): ByteArray {
        val file = createTempFile(prefix = "jetwhale-mirror-", suffix = ".png")
        try {
            runCommandChecked(xcrun, "simctl", "io", udid, "screenshot", file.toString())
            return file.readBytes()
        } finally {
            file.deleteIfExists()
        }
    }

    override suspend fun tap(x: Int, y: Int) {
        val scale = pixelsPerPoint()
        runCommandChecked(requireIdb(), "ui", "tap", "--udid", udid, "${(x / scale).toInt()}", "${(y / scale).toInt()}")
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) {
        val scale = pixelsPerPoint()
        runCommandChecked(
            requireIdb(), "ui", "swipe", "--udid", udid, "--duration", "${durationMillis / 1000.0}",
            "${(fromX / scale).toInt()}", "${(fromY / scale).toInt()}", "${(toX / scale).toInt()}", "${(toY / scale).toInt()}",
        )
    }

    override suspend fun pressButton(button: DeviceButton) {
        val presses = iosSimulatorPressesOf(button) ?: throw deviceControlError("the iOS simulator has no ${button.label} button")
        presses.forEach { idbButton -> runCommandChecked(requireIdb(), "ui", "button", "--udid", udid, idbButton) }
    }

    override suspend fun inputText(text: String) {
        runCommandChecked(requireIdb(), "ui", "text", "--udid", udid, text)
    }

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    // A simulator's H.264 stream sends a frame only when the framebuffer reports damage, which a
    // current CoreSimulator does so rarely that the picture freezes for seconds. Its raw stream is
    // paced by --fps instead, and scaled to the view by the simulator, so there is nothing to decode;
    // see rawBgraLayout for the size and rate.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val layout = rawBgraLayout(screenSize(), wanted, maxFps = fpsCap, maxWidth = widthCap)
        val process = withContext(Dispatchers.IO) {
            SystemProcessLauncher.start(
                listOf(requireIdb(), "video-stream", "--udid", udid, "--format", "rbga", "--fps", "${layout.fps}", "--scale-factor", "${layout.scale}"),
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
            SystemProcessLauncher.start(listOf(xcrun, "simctl", "io", udid, "recordVideo", "--codec=h264", "--force", outputFile.absolutePath))
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

    private fun requireIdb(): String = idb ?: throw deviceControlError(IDB_MISSING)

    override suspend fun screenSize(): IntSize = describe().size

    // idb takes points; the mirror works in pixels, so the screen's density converts between them.
    private suspend fun pixelsPerPoint(): Double = describe().pixelsPerPoint

    private suspend fun describe(): IdbScreen {
        screen?.let { return it }
        val description = runCommandChecked(requireIdb(), "describe", "--udid", udid, "--json").stdoutText
        return (parseIdbScreen(description) ?: throw deviceControlError("'idb describe' reported no screen size")).also { screen = it }
    }
}

internal const val IDB_MISSING = "iOS input and live streaming need idb (https://fbidb.io): brew install idb-companion && pipx install fb-idb"

/**
 * The idb buttons pressed, in order, for [button] on a simulator, or null for a button it lacks.
 * A Face ID iPhone has no home button to double-press, but the simulator still opens the app
 * switcher on two HOME presses in quick succession; two idb calls in a row land about 0.3 s apart,
 * well inside that window.
 */
internal fun iosSimulatorPressesOf(button: DeviceButton): List<String>? = when (button) {
    DeviceButton.Home -> listOf("HOME")
    DeviceButton.Recents -> listOf("HOME", "HOME")
    DeviceButton.Power -> listOf("LOCK")
    DeviceButton.Back, DeviceButton.VolumeUp, DeviceButton.VolumeDown -> null
}
