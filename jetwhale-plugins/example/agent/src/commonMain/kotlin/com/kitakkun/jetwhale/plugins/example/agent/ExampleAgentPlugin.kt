package com.kitakkun.jetwhale.plugins.example.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.example.protocol.ButtonClicked
import com.kitakkun.jetwhale.plugins.example.protocol.Ping
import com.kitakkun.jetwhale.plugins.example.protocol.Pong
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * An example JetWhale agent plugin. It replies to [Ping] requests with [Pong] and sends
 * [ButtonClicked] events to the host, logging both into a StateFlow the demo UI observes.
 */
class ExampleAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = "com.kitakkun.jetwhale.example"
    override val pluginVersion: String get() = "1.0.0"

    private val mutableEventLogsFlow: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    val eventLogsFlow: StateFlow<List<String>> = mutableEventLogsFlow

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: Ping ->
            mutableEventLogsFlow.update { it + "Request: Ping" + "Reply: Pong" }
            reply(Pong)
        }
    }

    /** Sends a button-clicked event to the host (and logs it locally). A click is only meaningful
     *  live, so it is dropped if the host is not connected. */
    fun reportButtonClicked(count: Int) {
        val event = ButtonClicked(count)
        mutableEventLogsFlow.update { it + "Event: $event" }
        messenger.trySend(event)
    }
}
