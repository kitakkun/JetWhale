package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class TapCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.tap"
    override val description = "Taps the device screen at a point in the pixels of $TOOL_PREFIX.captureScreenshot. Not available for a physical iOS device."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val x by int("Horizontal position in screenshot pixels.")
    private val y by int("Vertical position in screenshot pixels.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val x = arguments[x]
        val y = arguments[y]
        if (x < 0 || y < 0) throw JetWhaleMcpArgumentException("coordinates must not be negative (got x=$x, y=$y)")
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        // Refused before screenSize(), which holds an iPhone's idb companion that nothing here releases.
        if (!device.controller.capabilities.input) throw JetWhaleMcpArgumentException(VIEW_ONLY)
        val screen = deviceOperation { device.controller.screenSize() }
        if (x >= screen.width || y >= screen.height) {
            throw JetWhaleMcpArgumentException("the tap is off the ${screen.width}x${screen.height} screen (got x=$x, y=$y)")
        }
        deviceOperation { device.controller.tap(x, y) }
        return okJson()
    }
}
