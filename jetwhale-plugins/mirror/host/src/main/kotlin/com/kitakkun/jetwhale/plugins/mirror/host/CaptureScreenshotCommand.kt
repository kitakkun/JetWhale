package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class CaptureScreenshotCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.captureScreenshot"
    override val description =
        "Saves the device screen as a PNG among the device's captures and returns its absolute path and size in pixels. The tap and swipe tools take coordinates in these pixels. " +
            "Read the file to see the screen, or pass includeImage=true to get the PNG in the result as well."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val includeImage by booleanOrNull(
        "Also return the PNG itself, for a client that shows images to the model. A full-resolution screenshot can be several megabytes, so leave it off when the path is enough. Defaults to false.",
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        val capture = deviceOperation { mirror.saveScreenshot(device) }
        val result = JetWhaleMcpResult.json(
            buildJsonObject {
                put("path", capture.file.absolutePath)
                capture.info.widthPx?.let { put("widthPx", it) }
                capture.info.heightPx?.let { put("heightPx", it) }
            },
        )
        if (arguments[includeImage] != true) return result
        return result.withImage(withContext(Dispatchers.IO) { capture.file.readBytes() }, "image/png")
    }
}
