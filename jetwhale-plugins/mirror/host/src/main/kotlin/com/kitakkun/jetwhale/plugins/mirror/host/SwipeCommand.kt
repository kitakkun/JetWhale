package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class SwipeCommand(
    private val resolveDevice: (deviceId: String?) -> MirrorDevice,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.swipe"
    override val description = "Swipes on the device screen from one position to another, in the pixel coordinate space of $TOOL_PREFIX.captureScreenshot. Use it for scrolling and drag gestures."

    private val deviceId by stringOrNull("Target device id from $TOOL_PREFIX.listDevices; omitted = the device selected in the mirror UI.")
    private val fromX by int("Start horizontal position in screenshot pixels.")
    private val fromY by int("Start vertical position in screenshot pixels.")
    private val toX by int("End horizontal position in screenshot pixels.")
    private val toY by int("End vertical position in screenshot pixels.")
    private val durationMillis by intOrNull("Gesture duration in milliseconds; omitted = 300.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = resolveDevice(arguments[deviceId])
        try {
            device.controller.swipe(
                fromX = arguments[fromX],
                fromY = arguments[fromY],
                toX = arguments[toX],
                toY = arguments[toY],
                durationMillis = arguments[durationMillis] ?: 300,
            )
        } catch (e: DeviceControlException) {
            throw JetWhaleMcpArgumentException(e.message ?: "swipe failed")
        }
        return okJson()
    }
}
