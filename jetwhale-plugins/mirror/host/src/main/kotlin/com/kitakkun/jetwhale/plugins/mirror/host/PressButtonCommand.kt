package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class PressButtonCommand(
    private val resolveDevice: (deviceId: String?) -> MirrorDevice,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.pressButton"
    override val description = "Presses a hardware button or keyboard key on the device. BACKSPACE deletes one character in the focused field and ENTER confirms it. Android supports all buttons; the iOS simulator supports HOME, POWER, BACKSPACE, and ENTER (BACK and volume buttons fail there)."

    private val deviceId by stringOrNull("Target device id from $TOOL_PREFIX.listDevices; omitted = the device selected in the mirror UI.")
    private val button by enum("The hardware button to press.", DeviceButton.entries)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = resolveDevice(arguments[deviceId])
        try {
            device.controller.pressButton(arguments[button])
        } catch (e: DeviceControlException) {
            throw JetWhaleMcpArgumentException(e.message ?: "button press failed")
        }
        return okJson()
    }
}
