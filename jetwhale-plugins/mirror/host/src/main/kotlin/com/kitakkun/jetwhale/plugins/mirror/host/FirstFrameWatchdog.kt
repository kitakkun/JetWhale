package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tells a stream that is working from one that never will. idb gives no error when it cannot see
 * a device's screen — it keeps the stream open and sends nothing — so silence past [timeoutMillis]
 * is the only sign.
 */
internal class FirstFrameWatchdog(private val timeoutMillis: Long) {
    private val firstFrame = CompletableDeferred<Unit>()

    fun frameArrived() {
        firstFrame.complete(Unit)
    }

    /** True once a frame has arrived; false when [timeoutMillis] pass without one. */
    suspend fun awaitFirstFrame(): Boolean = withTimeoutOrNull(timeoutMillis) { firstFrame.await() } != null
}

/** What to check when a [kind] of device streams nothing, in the order worth trying. */
internal fun noFramesHints(kind: DeviceKind): List<String> = when (kind) {
    DeviceKind.IosDevice -> listOf(
        "Unlock the iPhone and keep its screen on.",
        "Allow Camera access for the app that runs JetWhale in System Settings → Privacy & Security → Camera; macOS delivers a USB device's screen as a camera, and without the permission it delivers nothing.",
        "Check that the device is connected by USB and trusts this Mac.",
    )

    DeviceKind.IosSimulator -> listOf("Check that the simulator is still booted and that `idb video-stream --udid <udid>` works in a terminal.")

    DeviceKind.AndroidEmulator, DeviceKind.AndroidDevice -> listOf("Check that the device is unlocked and that `adb exec-out screenrecord --output-format=h264 -` works in a terminal.")
}
