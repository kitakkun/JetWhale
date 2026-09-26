package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * Drives one device. Coordinates are in the pixels of the device's own screen — the space of its
 * screenshots and stream frames — and each controller converts them to what its tool expects.
 * An operation the device does not support throws [DeviceControlException] saying so.
 */
internal interface DeviceController {
    val capabilities: DeviceCapabilities

    suspend fun captureScreenshot(): ByteArray

    /** The size of the device's screen in pixels, the space that taps and swipes are given in. */
    suspend fun screenSize(): IntSize

    suspend fun tap(x: Int, y: Int)

    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int)

    suspend fun pressButton(button: DeviceButton)

    suspend fun inputText(text: String)

    suspend fun screenPower(): ScreenPower

    /** Turns the screen on and dismisses a lock screen that asks for no credential. */
    suspend fun wake()

    /** Turns the screen off. */
    suspend fun sleep()

    /** Starts recording the screen into [outputFile]; the returned handle stops it. */
    suspend fun startRecording(outputFile: File): DeviceRecording

    /**
     * Starts a process that writes the screen to stdout, in the format the returned stream names.
     * [wanted] is the size the frames will be shown at, when smaller than the screen; a device that
     * can scale its stream itself sends frames about that size. The stream may end on its own (adb's
     * screenrecord stops after three minutes); open a new one to continue.
     */
    suspend fun openVideoStream(wanted: IntSize?): VideoStream

    /** Releases what the controller holds for streaming, such as an idb companion. */
    suspend fun release()
}

internal interface DeviceRecording {
    suspend fun stop(): File
}
