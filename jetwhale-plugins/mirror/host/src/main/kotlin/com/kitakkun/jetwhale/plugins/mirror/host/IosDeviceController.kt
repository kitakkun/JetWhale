package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/**
 * A physical iOS device over USB. idb streams its screen through the device's companion, but idb
 * sends touches, buttons and text to simulators only, so a device is watched and not driven.
 */
internal class IosDeviceController(
    private val udid: String,
    private val idb: String,
    private val companions: IdbCompanions,
) : DeviceController {
    override val capabilities = DeviceCapabilities(input = false, buttons = emptyList(), recording = false, screenPower = false)

    private var streaming = false

    override suspend fun captureScreenshot(): ByteArray {
        companions.acquire(udid)
        val file = createTempFile(prefix = "jetwhale-mirror-", suffix = ".png")
        try {
            runCommandChecked(idb, "screenshot", "--udid", udid, file.toString())
            return file.readBytes()
        } finally {
            file.deleteIfExists()
            companions.release(udid)
        }
    }

    // The companion that answers this stays up with the stream that follows.
    override suspend fun screenSize(): IntSize {
        holdCompanion()
        val description = runCommandChecked(idb, "describe", "--udid", udid, "--json").stdoutText
        return parseIdbScreen(description)?.size ?: throw deviceControlError("'idb describe' reported no screen size")
    }

    override suspend fun tap(x: Int, y: Int) = throw deviceControlError(VIEW_ONLY)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = throw deviceControlError(VIEW_ONLY)

    override suspend fun pressButton(button: DeviceButton) = throw deviceControlError(VIEW_ONLY)

    override suspend fun inputText(text: String) = throw deviceControlError(VIEW_ONLY)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun sleep() = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("recording is not available for a physical iOS device")

    // --fps is ignored for a device, which streams at about 60; the mirror drops what it cannot show.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        holdCompanion()
        return withContext(Dispatchers.IO) {
            VideoStream.H264(SystemProcessLauncher.start(listOf(idb, "video-stream", "--udid", udid, "--format", "h264", "--fps", "30")))
        }
    }

    private suspend fun holdCompanion() {
        if (streaming) return
        companions.acquire(udid)
        streaming = true
    }

    override suspend fun release() {
        if (!streaming) return
        streaming = false
        companions.release(udid)
    }
}

internal const val VIEW_ONLY = "a physical iOS device is view-only: idb sends touches, buttons and text to simulators only"
