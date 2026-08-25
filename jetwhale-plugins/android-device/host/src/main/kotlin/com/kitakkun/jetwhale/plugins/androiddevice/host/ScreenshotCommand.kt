package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.put
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalJetWhaleApi::class)
internal class ScreenshotCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.screenshot"
    override val description =
        "Captures the device screen as a PNG and returns it as an image block, plus a text block " +
            "{serial, width, height, savedTo?, adb}. The reported width and height are of the " +
            "returned image; tap coordinates are in the device's own screen pixels, so read a scaled " +
            "screenshot's coordinates back through the scale, or capture at full size."

    private val scale by serializableOrNull<Double>(
        "Downscale factor in (0, 1]; defaults to 1, because a full-size capture is what a caller " +
            "expects unless it asks for less. Only shrinks — the device is never captured larger than it is.",
    )
    private val saveTo by stringOrNull("Absolute path on the machine running the debug tool to also write the PNG to. The parent directory must exist.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val scale = arguments[scale] ?: 1.0
        if (scale <= 0.0 || scale > 1.0) throw JetWhaleMcpArgumentException("invalid scale: $scale (expected a value greater than 0 and at most 1)")
        val saveTo = arguments[saveTo]?.let { path ->
            val file = File(path)
            if (!file.isAbsolute) throw JetWhaleMcpArgumentException("invalid saveTo: $path (expected an absolute path)")
            if (file.parentFile?.isDirectory != true) throw JetWhaleMcpArgumentException("invalid saveTo: $path (its parent directory does not exist)")
            file
        }

        val captured = target.stream("exec-out", "screencap", "-p", timeout = AdbTimeouts.TRANSFER) { it.readBytes() }
        val decoded = ImageIO.read(ByteArrayInputStream(captured))
            ?: return JetWhaleMcpResult.text(
                target.resultJson {
                    put("ok", false)
                    put("error", "screencap returned ${captured.size} bytes that are not a readable PNG")
                },
            )

        val png = if (scale == 1.0) captured else encodePng(decoded.scaledBy(scale))
        val image = if (scale == 1.0) decoded else ImageIO.read(ByteArrayInputStream(png))
        saveTo?.writeBytes(png)

        return JetWhaleMcpResult(
            listOf(
                JetWhaleMcpContent.Image(png, "image/png"),
                JetWhaleMcpContent.Text(
                    target.resultJson {
                        put("ok", true)
                        put("width", image.width)
                        put("height", image.height)
                        put("scale", scale)
                        put("savedTo", saveTo?.absolutePath)
                    },
                ),
            ),
        )
    }
}

/** At least one pixel each way, so a very small scale still produces an image rather than a failure. */
private fun BufferedImage.scaledBy(scale: Double): BufferedImage {
    val targetWidth = max(1, (width * scale).roundToInt())
    val targetHeight = max(1, (height * scale).roundToInt())
    val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaled.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return scaled
}

private fun encodePng(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { bytes ->
    ImageIO.write(image, "png", bytes)
    bytes.toByteArray()
}
