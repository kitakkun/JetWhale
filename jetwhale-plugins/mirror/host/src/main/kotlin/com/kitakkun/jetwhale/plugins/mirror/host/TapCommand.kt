package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class TapCommand(
    private val resolveDevice: (deviceId: String?) -> MirrorDevice,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.tap"
    override val description = "Taps the device screen at the given position, in the pixel coordinate space of $TOOL_PREFIX.captureScreenshot."

    private val deviceId by stringOrNull("Target device id from $TOOL_PREFIX.listDevices; omitted = the device selected in the mirror UI.")
    private val x by int("Horizontal position in screenshot pixels.")
    private val y by int("Vertical position in screenshot pixels.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = resolveDevice(arguments[deviceId])
        try {
            device.controller.tap(arguments[x], arguments[y])
        } catch (e: DeviceControlException) {
            throw JetWhaleMcpArgumentException(e.message ?: "tap failed")
        }
        return okJson()
    }
}
