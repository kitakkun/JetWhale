package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class InputTextCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.inputText"
    override val description = "Types text into whatever has focus on the device. Not available for a physical iOS device."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val text by string("The text to type.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        deviceOperation { device.controller.inputText(arguments[text]) }
        return okJson()
    }
}
