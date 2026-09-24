package com.kitakkun.jetwhale.plugins.screen.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class StopScreenStreamCommand(
    private val controller: ScreenStreamController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.stop"
    override val description = "Stops the screen stream; the app stops capturing."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = buildJsonObject { put("status", controller.stop()) }.toString()
}
