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
 * Agent counterpart of the experimental web-based host plugin (same `pluginId`). Behaves exactly
 * like [ExampleAgentPlugin] — replies to [Ping] with [Pong] and can push [ButtonClicked] events —
 * but under `com.kitakkun.jetwhale.example.web` so the web UI has an agent to talk to.
 */
class ExampleWebAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = "com.kitakkun.jetwhale.example.web"
    override val pluginVersion: String get() = "1.0.0"

    private val mutableEventLogsFlow: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    val eventLogsFlow: StateFlow<List<String>> = mutableEventLogsFlow

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: Ping ->
            mutableEventLogsFlow.update { it + "Request: Ping" + "Reply: Pong" }
            reply(Pong)
        }
    }

    /** Sends a button-clicked event to the host (dropped if the host is not connected). */
    fun reportButtonClicked(count: Int) {
        val event = ButtonClicked(count)
        mutableEventLogsFlow.update { it + "Event: $event" }
        messenger.trySend(event)
    }
}
