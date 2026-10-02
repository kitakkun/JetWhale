package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class StartRecordingCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.startRecording"
    override val description =
        "Starts recording a device screen to a video file; finish with $TOOL_PREFIX.stopRecording. Each device records on its own, so several can record at once: " +
            "pass all=true for every device that can record, or deviceIds for several, and each device's result comes back. Android stops on its own after 180 seconds. " +
            "A physical iOS device needs ffmpeg."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val all by booleanOrNull("Start recording every device that can record and is not recording yet. Not combined with deviceId or deviceIds.")
    private val deviceIds by stringListOrNull("Device ids from $TOOL_PREFIX.listDevices to start recording at once. Not combined with deviceId or all.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val single = arguments[deviceId]
        val several = arguments[deviceIds]
        val everyDevice = arguments[all] == true
        if (listOf(single != null, several != null, everyDevice).count { it } > 1) {
            throw JetWhaleMcpArgumentException("pass one of deviceId, deviceIds or all=true")
        }
        if (several == null && !everyDevice) {
            val device = deviceOperation { mirror.resolve(single) }
            deviceOperation { mirror.startRecording(device) }
            return okResult()
        }
        if (everyDevice) mirror.refresh()
        val results = mirror.startRecordings(several)
        return results.toMcpResult(nothingToDo = "there is no device to start recording: each one that can record already is, or none is connected; call $TOOL_PREFIX.listDevices")
    }
}
