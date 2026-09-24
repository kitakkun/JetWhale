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
        buttons = if (idb != null) listOf(DeviceButton.Home, DeviceButton.Power) else emptyList(),
        recording = true,
    )

    private var screen: IdbScreen? = null

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
        val idbButton = when (button) {
            DeviceButton.Home -> "HOME"
            DeviceButton.Power -> "LOCK"
            DeviceButton.Back, DeviceButton.VolumeUp, DeviceButton.VolumeDown -> throw deviceControlError("the iOS simulator has no ${button.label} button")
        }
        runCommandChecked(requireIdb(), "ui", "button", "--udid", udid, idbButton)
    }

    override suspend fun inputText(text: String) {
        runCommandChecked(requireIdb(), "ui", "text", "--udid", udid, text)
    }

    override suspend fun openVideoStream(): Process = withContext(Dispatchers.IO) {
        SystemProcessLauncher.start(listOf(requireIdb(), "video-stream", "--udid", udid, "--format", "h264", "--fps", "30"))
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
