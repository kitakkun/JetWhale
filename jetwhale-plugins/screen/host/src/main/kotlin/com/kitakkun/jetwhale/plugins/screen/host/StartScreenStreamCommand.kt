package com.kitakkun.jetwhale.plugins.screen.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class StartScreenStreamCommand(
    private val controller: ScreenStreamController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.start"
    override val description =
        "Starts streaming the app's own windows into the Screen Stream view, captured inside the app (no ADB). The system bars and the soft keyboard are not part of the app and do not appear."

    private val scale by string("Frame size relative to the screen, in (0, 1], e.g. 0.5.")
    private val jpegQuality by int("JPEG quality, 1..100.")
    private val maxFramesPerSecond by int("The most frames a second the app sends.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val parsedScale = arguments[scale].toFloatOrNull()?.takeIf { it > 0f && it <= 1f }
            ?: throw JetWhaleMcpArgumentException("scale must be a number in (0, 1]")
        val status = controller.start(scale = parsedScale, jpegQuality = arguments[jpegQuality].coerceIn(1, 100), maxFramesPerSecond = arguments[maxFramesPerSecond].coerceAtLeast(1))
        return buildJsonObject { put("status", status) }.toString()
    }
}
