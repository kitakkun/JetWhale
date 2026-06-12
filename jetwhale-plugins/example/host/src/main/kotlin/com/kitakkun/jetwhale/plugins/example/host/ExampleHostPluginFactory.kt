package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import com.kitakkun.jetwhale.plugins.example.protocol.ButtonClicked
import com.kitakkun.jetwhale.plugins.example.protocol.Ping
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.execute
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class ExampleHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = ExampleHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class ExampleHostPlugin :
    JetWhaleHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {

    private val eventLogs: SnapshotStateList<String> = mutableStateListOf()

    override fun JetWhaleMessagingHandlers.configure() {
        onEvent<ButtonClicked> { event -> eventLogs.add("Event: $event") }
    }

    @Composable
    override fun Content() {
        ExamplePluginContent(
            eventLogs = eventLogs,
            onClickSendPing = {
                messenger.coroutineScope.launch {
                    eventLogs.add("Request: Ping")
                    eventLogs.add(runCatching { messenger.execute(Ping) }.fold({ "Reply: Pong" }, { "Error: ${it.message}" }))
                }
            },
        )
    }

    // -------------------------------------------------------------------------
    // JetWhaleMcpCapablePlugin
    // -------------------------------------------------------------------------

    override fun mcpTools(): List<JetWhaleMcpToolDescriptor> = listOf(
        JetWhaleMcpToolDescriptor(
            name = "com.kitakkun.jetwhale.example.sendPing",
            description = "Sends a Ping request to the debuggee and returns whether a Pong reply was received.",
        ),
        JetWhaleMcpToolDescriptor(
            name = "com.kitakkun.jetwhale.example.getEventLogs",
            description = "Returns the list of event log entries accumulated by the Example plugin.",
            parameters = mapOf(
                "limit" to JetWhaleMcpParameterDescriptor(
                    type = "integer",
                    description = "Maximum number of log entries to return. Returns all entries if omitted.",
                    required = false,
                ),
            ),
        ),
    )

    override suspend fun handleMcpTool(toolName: String, arguments: Map<String, String>): String? = when (toolName) {
        "com.kitakkun.jetwhale.example.sendPing" -> {
            val pongReceived = runCatching { messenger.execute(Ping) }.isSuccess
            buildJsonObject {
                put("pongReceived", pongReceived)
            }.toString()
        }

        "com.kitakkun.jetwhale.example.getEventLogs" -> {
            val limit = arguments["limit"]?.toIntOrNull()
            val logs = if (limit != null) eventLogs.takeLast(limit) else eventLogs.toList()
            Json.encodeToJsonElement(logs).toString()
        }

        else -> null
    }
}
