package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class CaptureScreenshotCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.captureScreenshot"
    override val description =
        "Saves the device screen as a PNG among the device's captures and returns its absolute path and size in pixels. The tap and swipe tools take coordinates in these pixels. Read the file to see the screen."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        val capture = deviceOperation { mirror.saveScreenshot(device) }
        return buildJsonObject {
            put("path", capture.file.absolutePath)
            capture.info.widthPx?.let { put("widthPx", it) }
            capture.info.heightPx?.let { put("heightPx", it) }
        }.toString()
    }
}
