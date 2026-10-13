package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListCapturesCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listCaptures"
    override val description =
        "Lists saved screenshots and recordings, newest first, with each file's absolute path, the device it shows, when it was taken (epoch milliseconds), its size in pixels and, for a recording, its duration. Captures from earlier sessions are included."

    private val deviceId by stringOrNull("Only this device's captures (a deviceId from $TOOL_PREFIX.listDevices). Omit for every device.")
    private val kind by enumOrNull("Only screenshots or only recordings.", CaptureKind.entries)
    private val since by longOrNull("Only captures taken at or after this time, in epoch milliseconds.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = buildJsonObject {
        putJsonArray("captures") {
            mirror.listCaptures(arguments[deviceId], arguments[kind], arguments[since]).forEach { capture ->
                addJsonObject {
                    put("path", capture.file.absolutePath)
                    put("kind", capture.info.kind.name)
                    put("deviceId", capture.info.deviceId)
                    put("deviceName", capture.info.deviceName)
                    put("capturedAtEpochMillis", capture.info.capturedAtEpochMillis)
                    capture.info.widthPx?.let { put("widthPx", it) }
                    capture.info.heightPx?.let { put("heightPx", it) }
                    capture.info.durationMillis?.let { put("durationMillis", it) }
                }
            }
        }
    }.toString()
}
