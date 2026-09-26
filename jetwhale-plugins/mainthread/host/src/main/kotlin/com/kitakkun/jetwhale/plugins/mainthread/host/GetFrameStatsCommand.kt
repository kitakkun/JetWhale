package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.mainthread.protocol.FrameStats

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetFrameStatsCommand(
    private val client: MainThreadClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getFrameStats"
    override val description =
        "Returns frame durations the platform reported (Android 7+): total and janky frame counts (longer than 1.5 refresh intervals), percentiles, the slowest frame and the latest janky frames with their end times, to line up with getLongTasks."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val report = client.report()
        return reportJson(report) { put("frames", McpJson.encodeToJsonElement(FrameStats.serializer(), report.frames)) }
    }
}
