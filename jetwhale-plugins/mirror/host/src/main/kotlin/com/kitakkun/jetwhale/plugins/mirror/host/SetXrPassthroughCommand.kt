package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetXrPassthroughCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setXrPassthrough"
    override val description =
        "Shows the room around the user in place of an Android XR emulator's virtual environment (on: true), as a headset's passthrough does, or hides it again; the emulator's room is a simulated living room, which can take a few seconds to appear. " +
            "Answers whether passthrough is on afterwards. Only an Android XR emulator has passthrough: kind \"XR emulator\" in $TOOL_PREFIX.listDevices."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val on by boolean("true to show the room, false to show the virtual environment.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val xrView = xrViewControllerOf(deviceOperation { mirror.resolve(arguments[deviceId]) })
        val passthrough = deviceOperation {
            xrView.setPassthrough(arguments[on])
            xrView.isPassthroughOn()
        }
        return buildJsonObject { put("passthrough", passthrough) }.toString()
    }
}
