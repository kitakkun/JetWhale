package com.kitakkun.jetwhale.plugins.example.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetEventLogsCommand(
    private val eventLogs: List<String>,
) : JetWhaleMcpCommand() {
    override val name = "com.kitakkun.jetwhale.example.getEventLogs"
    override val description = "Returns the list of event log entries accumulated by the Example plugin."

    private val limit by intOrNull("Maximum number of log entries to return. Returns all entries if omitted.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val limit = arguments[this.limit]
        val logs = if (limit != null) eventLogs.takeLast(limit) else eventLogs.toList()
        return Json.encodeToJsonElement(logs).toString()
    }
}
