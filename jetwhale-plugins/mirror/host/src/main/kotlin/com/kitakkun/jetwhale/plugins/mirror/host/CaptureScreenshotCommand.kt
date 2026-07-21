package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalJetWhaleApi::class)
internal class CaptureScreenshotCommand(
    private val resolveDevice: (deviceId: String?) -> MirrorDevice,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.captureScreenshot"
    override val description = "Captures the device screen as a PNG file and returns its absolute path plus the pixel dimensions. Coordinates passed to the tap/swipe tools are in this pixel coordinate space. Read the file to see the screen."

    private val deviceId by stringOrNull("Target device id from $TOOL_PREFIX.listDevices; omitted = the device selected in the mirror UI.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = resolveDevice(arguments[deviceId])
        val png = try {
            device.controller.captureScreenshot()
        } catch (e: DeviceControlException) {
            throw JetWhaleMcpArgumentException(e.message ?: "screenshot failed")
        }
        val file = screenshotFile(device)
        file.writeBytes(png)
        val image = SkiaImage.makeFromEncoded(png)
        return buildJsonObject {
            put("path", file.absolutePath)
            put("widthPx", image.width)
            put("heightPx", image.height)
        }.toString()
    }
}
