package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetDispatcherStatsCommand(
    private val client: CoroutineInspectorClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getDispatcherStats"
    override val description =
        "Returns every dispatcher the app tracks: tasks waiting and running, completed tasks, average and maximum wait before a task starts and time a task runs, and the recent long runs — tasks that held the dispatcher's thread past its threshold — with the name of the coroutine that ran. A long run on a main dispatcher is a frozen UI."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = McpJson.encodeToString(DispatcherStatsReport.serializer(), client.dispatcherStats())
}
