package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class ResetStatsCommand(
    private val client: MainThreadClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.resetStats"
    override val description =
        "Clears every recorded long task, hotspot, violation and frame, so that a following interaction is measured on its own: reset, drive the app, then read the other tools."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val report = client.reset()
        return reportJson(report) { }
    }
}
