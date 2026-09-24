package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class StopRecordingCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.stopRecording"
    override val description = "Stops the running recording and returns the absolute path of the video file."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val capture = deviceOperation(mirror::stopRecording)
        return buildJsonObject {
            put("path", capture.file.absolutePath)
            capture.info.durationMillis?.let { put("durationMillis", it) }
        }.toString()
    }
}
