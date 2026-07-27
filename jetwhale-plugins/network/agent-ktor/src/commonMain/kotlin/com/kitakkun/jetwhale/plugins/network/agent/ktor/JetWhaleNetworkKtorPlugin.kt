package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin

/**
 * Ktor [io.ktor.client.HttpClient] plugin that feeds request/response details to a
 * [JetWhaleNetworkAgentPlugin] and serves mock responses configured from the host.
 *
 * Install it in your client and register the same agent plugin with JetWhale:
 * ```
 * val agent = JetWhaleNetworkAgentPlugin()
 * val client = HttpClient { install(agent.ktorClientPlugin()) }
 * startJetWhale { plugins { register(agent) } }
 * ```
 *
 * Use [ktorSendInterceptor] instead when the client is built somewhere you can't touch.
 *
 * Known limitation: response bodies are buffered with `save()` before the caller sees them, so a
 * long-lived streaming response that isn't `text/event-stream` (which is skipped) is fully
 * buffered and delays the caller until the stream ends. Headers the engine injects
 * (User-Agent, Accept-Encoding, Host) are not visible to a client plugin and are omitted.
 *
 * @param maxBodyChars request/response bodies longer than this are truncated for transport.
 */
fun JetWhaleNetworkAgentPlugin.ktorClientPlugin(maxBodyChars: Int = 100_000): ClientPlugin<Unit> {
    val agent = this
    return createClientPlugin("JetWhaleNetworkMonitor") {
        on(Send) { request ->
            agent.monitorSend(client, request, maxBodyChars) { proceed(it) }
        }
    }
}
