package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

@OptIn(ExperimentalJetWhaleApi::class)
internal class StopRecordingCommand(
    private val stopRecording: suspend () -> File,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.stopRecording"
    override val description = "Stops the in-progress screen recording started by $TOOL_PREFIX.startRecording and returns the absolute path of the finished mp4 file."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val file = try {
            stopRecording()
        } catch (e: DeviceControlException) {
            throw JetWhaleMcpArgumentException(e.message ?: "recording stop failed")
        }
        return buildJsonObject {
            put("path", file.absolutePath)
        }.toString()
    }
}
