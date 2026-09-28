package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * Drives one device, through the parts it has: every device has a [screen]; the others are null
 * when the device cannot do what they do, so a caller checks for a part instead of catching a
 * refusal. Coordinates are in the pixels of the device's own screen — the space of its
 * screenshots and stream frames — and each controller converts them to what its tool expects.
 */
internal interface DeviceController {
    val screen: DeviceScreen

    /** Null for a device that is watched and not driven, such as a physical iOS device. */
    val input: DeviceInput?

    /** Null for a device whose screen cannot be switched from here: every iOS device. */
    val power: DevicePower?

    /** Null for a device that cannot be recorded, such as a physical iOS device without ffmpeg. */
    val recorder: DeviceRecorder?

    /** Releases what the controller holds for streaming, such as an idb companion. */
    suspend fun release()
}

internal interface DeviceScreen {
    suspend fun captureScreenshot(): ByteArray

    /** The size of the device's screen in pixels, the space that taps and swipes are given in. */
    suspend fun screenSize(): IntSize

    /**
     * Starts a process that writes the screen to stdout, in the format the returned stream names.
     * [wanted] is the size the frames will be shown at, when smaller than the screen; a device that
     * can scale its stream itself sends frames about that size. The stream may end on its own (adb's
     * screenrecord stops after three minutes); open a new one to continue.
     */
    suspend fun openVideoStream(wanted: IntSize?): VideoStream
}

internal interface DeviceInput {
    /** The hardware buttons [pressButton] can press on this device. */
    val buttons: List<DeviceButton>

    suspend fun tap(x: Int, y: Int)

    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int)

    suspend fun pressButton(button: DeviceButton)

    suspend fun inputText(text: String)
}

internal interface DevicePower {
    suspend fun screenPower(): ScreenPower

    /** Turns the screen on and dismisses a lock screen that asks for no credential. */
    suspend fun wake()

    /** Turns the screen off. */
    suspend fun sleep()
}

internal interface DeviceRecorder {
    /** Starts recording the screen into [outputFile]; the returned handle stops it. */
    suspend fun startRecording(outputFile: File): DeviceRecording
}

internal interface DeviceRecording {
    suspend fun stop(): File
}
