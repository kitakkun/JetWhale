package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tells a stream that is working from one that never will. A stream can stay open and send nothing
 * without an error, as a screenrecord does that cannot record, so silence past [timeoutMillis] is
 * the only sign.
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
        "Check that the device is connected by USB and trusts this Mac.",
    )

    DeviceKind.IosSimulator -> listOf("Check that the simulator is still booted.")

    DeviceKind.AndroidEmulator, DeviceKind.AndroidDevice -> listOf("Check that the device is unlocked and that `adb exec-out screenrecord --output-format=h264 -` works in a terminal.")
}
