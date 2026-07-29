package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class StartRecordingCommand(
    private val resolveDevice: (deviceId: String?) -> MirrorDevice,
    private val startRecording: suspend (MirrorDevice) -> Unit,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.startRecording"
    override val description = "Starts recording the device screen to an mp4 file. Only one recording can run at a time; finish with $TOOL_PREFIX.stopRecording (Android stops automatically after 180 seconds)."

    private val deviceId by stringOrNull("Target device id from $TOOL_PREFIX.listDevices; omitted = the device selected in the mirror UI.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = resolveDevice(arguments[deviceId])
        try {
            startRecording(device)
        } catch (e: DeviceControlException) {
            throw JetWhaleMcpArgumentException(e.message ?: "recording start failed")
        }
        return okJson()
    }
}
