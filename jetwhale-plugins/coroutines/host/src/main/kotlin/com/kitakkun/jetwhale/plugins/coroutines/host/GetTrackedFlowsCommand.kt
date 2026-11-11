package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetTrackedFlowsCommand(
    private val client: CoroutineInspectorClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getTrackedFlows"
    override val description =
        "Returns every flow the app tracks: collectors running now, how many collections completed, were cancelled or failed, emissions in total and per second over the last ten seconds, and the most recent values as text, newest first."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = McpJson.encodeToString(TrackedFlowReport.serializer(), client.trackedFlows())
}
