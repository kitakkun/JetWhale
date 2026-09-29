package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class StopRecordingCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.stopRecording"
    override val description =
        "Stops a recording and returns the absolute path of the video file. With no argument it stops the one recording running; " +
            "name a device with deviceId, or pass all=true or deviceIds to stop several at once, and each device's result comes back."

    private val deviceId by stringOrNull("Device id whose recording to stop. Omit it when only one recording runs.")
    private val all by booleanOrNull("Stop every running recording. Not combined with deviceId or deviceIds.")
    private val deviceIds by stringListOrNull("Device ids whose recordings to stop at once. Not combined with deviceId or all.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val single = arguments[deviceId]
        val several = arguments[deviceIds]
        val everyDevice = arguments[all] == true
        if (listOf(single != null, several != null, everyDevice).count { it } > 1) {
            throw JetWhaleMcpArgumentException("pass one of deviceId, deviceIds or all=true")
        }
        if (several == null && !everyDevice) {
            val capture = deviceOperation { mirror.stopRecording(single) }
            return buildJsonObject {
                put("path", capture.file.absolutePath)
                capture.info.durationMillis?.let { put("durationMillis", it) }
            }.toString()
        }
        val results = mirror.stopRecordings(several)
        return buildJsonObject { put("results", buildJsonArray { results.forEach { add(it.toJson()) } }) }.toString()
    }
}
