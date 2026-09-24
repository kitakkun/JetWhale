package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

/** Long enough for a scroll to register as a drag rather than a fling. */
private const val DEFAULT_SWIPE_MILLIS = 300

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
    private val durationMillis by intOrNull("How long the swipe takes. Defaults to $DEFAULT_SWIPE_MILLIS ms.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        deviceOperation {
            device.controller.swipe(
                fromX = arguments[fromX],
                fromY = arguments[fromY],
                toX = arguments[toX],
                toY = arguments[toY],
                durationMillis = arguments[durationMillis] ?: DEFAULT_SWIPE_MILLIS,
            )
        }
        return okJson()
    }
}
