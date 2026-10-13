package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tells a stream that is working from one that never will. A stream can stay open and send nothing
 * without an error, as a screenrecord does that cannot record, so silence past [timeoutMillis] is
 * the only sign.
 */
internal class FirstFrameWatchdog(private val timeoutMillis: Long) {
    private val firstFrameOrFailure = CompletableDeferred<Unit>()

    fun frameArrived() {
        firstFrameOrFailure.complete(Unit)
    }

    /** Ends the wait for a first frame, for a stream that failed before one and has its own reason to give. */
    fun endFirstFrameWait() {
        firstFrameOrFailure.complete(Unit)
    }

    /** True once a frame has arrived or [endFirstFrameWait] has ended the wait; false when [timeoutMillis] pass without either. */
    suspend fun awaitFirstFrame(): Boolean = withTimeoutOrNull(timeoutMillis) { firstFrameOrFailure.await() } != null
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
