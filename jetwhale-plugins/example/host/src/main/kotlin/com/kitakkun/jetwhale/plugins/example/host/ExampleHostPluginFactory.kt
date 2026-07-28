package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.example.protocol.ButtonClicked
import com.kitakkun.jetwhale.plugins.example.protocol.Ping
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import com.kitakkun.jetwhale.protocol.messaging.request
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
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {

    private val eventLogs: SnapshotStateList<String> = mutableStateListOf()

    override fun JetWhaleMessageHandlers.configure() {
        onEvent { event: ButtonClicked -> eventLogs.add("Event: $event") }
    }

    @Composable
    override fun Content() {
        ExamplePluginContent(
            eventLogs = eventLogs,
            onClickSendPing = {
                pluginScope.launch {
                    eventLogs.add("Request: Ping")
                    // Catch the messaging failure specifically — runCatching would also swallow the
                    // CancellationException that cancels this coroutine.
                    val reply = try {
                        messenger.request(Ping)
                        "Reply: Pong"
                    } catch (e: JetWhaleMessagingException) {
                        "Error: ${e.message}"
                    }
                    eventLogs.add(reply)
                }
            },
        )
    }

    // -------------------------------------------------------------------------
    // JetWhaleMcpCapablePlugin
    // -------------------------------------------------------------------------

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = "com.kitakkun.jetwhale.example.sendPing"
            override val description = "Sends a Ping request to the debuggee and reports whether a Pong reply came back."

            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = try {
                messenger.request(Ping)
                JetWhaleMcpResult.json(buildJsonObject { put("pongReceived", true) })
            } catch (e: JetWhaleMessagingException) {
                // An unanswered Ping is the debuggee failing to respond, not an answer of "no".
                JetWhaleMcpResult.error("the debuggee did not answer Ping: ${e.message}")
            }
        },
        object : JetWhaleMcpTextCommand() {
            override val name = "com.kitakkun.jetwhale.example.getEventLogs"
            override val description = "Returns the list of event log entries accumulated by the Example plugin."

            private val limit by intOrNull("Maximum number of log entries to return. Returns all entries if omitted.")

            override suspend fun executeText(arguments: JetWhaleMcpArguments): String {
                val limit = arguments[this.limit]
                val logs = if (limit != null) eventLogs.takeLast(limit) else eventLogs.toList()
                return Json.encodeToJsonElement(logs).toString()
            }
        },
    )
}
