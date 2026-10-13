package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class PressButtonCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.pressButton"
    override val description = "Presses a hardware button. Which buttons a device has differs by device; pass one from the buttons field $TOOL_PREFIX.listDevices reports for it. A button the device lacks is refused."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val button by enum("The button to press.", DeviceButton.entries)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        deviceOperation { device.controller.pressButton(arguments[button]) }
        return okJson()
    }
}
