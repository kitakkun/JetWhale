package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.mainthread.protocol.LongTask
import kotlinx.serialization.builtins.ListSerializer

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetLongTasksCommand(
    private val client: MainThreadClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getLongTasks"
    override val description =
        "Returns the main-thread tasks that ran at least the long-task threshold, latest last: when each started, how long it ran, what was dispatched (the Handler and callback on Android, the event on the JVM), how many stack samples it produced, and whether it ran long enough to count as the app being unresponsive (like an ANR)."

    private val minDurationMillis by longOrNull("Only tasks at least this long.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val report = client.report()
        val minimum = arguments[minDurationMillis] ?: 0
        val tasks = report.longTasks.filter { it.durationMillis >= minimum }
        return reportJson(report) { put("longTasks", McpJson.encodeToJsonElement(ListSerializer(LongTask.serializer()), tasks)) }
    }
}
