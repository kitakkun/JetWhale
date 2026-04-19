package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.google.auto.service.AutoService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleDebugOperationContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleEvent
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethod
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethodResult
import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol
import com.kitakkun.jetwhale.protocol.host.kotlinxSerializationJetWhaleHostPluginProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Suppress("UNUSED")
@AutoService(JetWhaleHostPluginFactory::class)
class ExampleHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleRawHostPlugin = ExampleHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class ExampleHostPlugin :
    JetWhaleHostPlugin<ExampleEvent, ExampleMethod, ExampleMethodResult>(),
    JetWhaleMcpCapablePlugin {

    override val protocol: JetWhaleHostPluginProtocol<ExampleEvent, ExampleMethod, ExampleMethodResult> = kotlinxSerializationJetWhaleHostPluginProtocol()

    private val eventLogs: SnapshotStateList<String> = mutableStateListOf()

    // Stored so that MCP tool handlers can dispatch methods to the debuggee.
    private var debugContext: JetWhaleDebugOperationContext<ExampleMethod, ExampleMethodResult>? = null

    override fun onEvent(event: ExampleEvent) {
        when (event) {
            is ExampleEvent.ButtonClicked -> eventLogs.add("Event: $event")
        }
    }

    override fun onDispose() {
        debugContext = null
    }

    @Composable
    override fun Content(context: JetWhaleDebugOperationContext<ExampleMethod, ExampleMethodResult>) {
        // FIXME: `context` should ideally be provided to MCP tool handlers via a safer API rather than relying on Composable function execution.
        //   This is just for demonstration purposes to show that method dispatch from MCP tools is possible.
        //   We should consider alternative ways to provide context to tools.
        debugContext = context
        ExamplePluginContent(
            eventLogs = eventLogs,
            context = context,
        )
    }

    // -------------------------------------------------------------------------
    // JetWhaleMcpCapablePlugin
    // -------------------------------------------------------------------------

    override fun mcpTools(): List<JetWhaleMcpToolDescriptor> = listOf(
        JetWhaleMcpToolDescriptor(
            name = "com.kitakkun.jetwhale.example.sendPing",
            description = "Sends a Ping method to the debuggee and returns whether a Pong response was received.",
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

    override suspend fun handleMcpTool(toolName: String, arguments: Map<String, String>): String? {
        return when (toolName) {
            "com.kitakkun.jetwhale.example.sendPing" -> {
                val context = debugContext
                    ?: return buildJsonObject { put("error", "Plugin UI is not active") }.toString()
                val result: ExampleMethodResult? = context.dispatch(ExampleMethod.Ping)
                buildJsonObject {
                    put("pongReceived", result is ExampleMethodResult.Pong)
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
}
