package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

/** Long enough for a scroll to register as a drag rather than a fling. */
private const val DEFAULT_SWIPE_MILLIS = 300

/** Longer than any gesture a test needs; a larger value is more likely a unit mistake. */
private const val MAX_SWIPE_MILLIS = 10_000

@OptIn(ExperimentalJetWhaleApi::class)
internal class SwipeCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.swipe"
    override val description = "Swipes across the device screen between two points in the pixels of $TOOL_PREFIX.captureScreenshot. Not available for a physical iOS device."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val fromX by int("Start, horizontal, in screenshot pixels.")
    private val fromY by int("Start, vertical, in screenshot pixels.")
    private val toX by int("End, horizontal, in screenshot pixels.")
    private val toY by int("End, vertical, in screenshot pixels.")
    private val durationMillis by intOrNull("How long the swipe takes, from 0 to $MAX_SWIPE_MILLIS ms. Defaults to $DEFAULT_SWIPE_MILLIS ms.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val fromX = arguments[fromX]
        val fromY = arguments[fromY]
        val toX = arguments[toX]
        val toY = arguments[toY]
        val duration = arguments[durationMillis] ?: DEFAULT_SWIPE_MILLIS
        if (minOf(fromX, fromY, toX, toY) < 0) throw JetWhaleMcpArgumentException("coordinates must not be negative (got $fromX,$fromY -> $toX,$toY)")
        if (duration !in 0..MAX_SWIPE_MILLIS) throw JetWhaleMcpArgumentException("durationMillis must be from 0 to $MAX_SWIPE_MILLIS (got $duration)")
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        val screen = deviceOperation { device.controller.screenSize() }
        if (maxOf(fromX, toX) >= screen.width || maxOf(fromY, toY) >= screen.height) {
            throw JetWhaleMcpArgumentException("the swipe leaves the ${screen.width}x${screen.height} screen (got $fromX,$fromY -> $toX,$toY)")
        }
        deviceOperation { device.controller.swipe(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = duration) }
        return okJson()
    }
}
