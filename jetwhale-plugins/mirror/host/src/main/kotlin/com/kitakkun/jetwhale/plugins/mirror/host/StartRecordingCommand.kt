package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class StartRecordingCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.startRecording"
    override val description =
        "Starts recording the device screen to a video file; finish with $TOOL_PREFIX.stopRecording. One recording runs at a time, and Android stops on its own after 180 seconds. Not available for a physical iOS device."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        deviceOperation { mirror.startRecording(device) }
        return okJson()
    }
}
