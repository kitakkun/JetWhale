package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.example.protocol.ButtonClicked
import com.kitakkun.jetwhale.plugins.example.protocol.Ping
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
        ExamplePluginViewRoot(
            eventLogs = eventLogs,
            onClickSendPing = {
                pluginScope.launch {
                    eventLogs.add("Request: Ping")
                    val reply = try {
                        messenger.request(Ping)
                        "Reply: Pong"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: JetWhaleMessagingException) {
                        "Error: ${e.message}"
                    } catch (e: Exception) {
                        "Unexpected error: $e"
                    }
                    eventLogs.add(reply)
                }
            },
        )
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        SendPingCommand(requestPing = { messenger.request(Ping) }),
        GetEventLogsCommand(eventLogs),
    )
}
