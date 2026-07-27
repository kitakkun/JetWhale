package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSendInterceptor

/**
 * [io.ktor.client.plugins.HttpSend] interceptor that feeds request/response details to a
 * [JetWhaleNetworkAgentPlugin] and serves mock responses configured from the host.
 *
 * Unlike [ktorClientPlugin] this attaches to an already-built client, so a client that comes from a
 * DI container or a library can be inspected without touching where it is constructed:
 * ```
 * val agent = JetWhaleNetworkAgentPlugin()
 * val client = HttpClient()
 * client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client))
 * startJetWhale { plugins { register(agent) } }
 * ```
 *
 * `HttpSend` is installed in every `HttpClient`, so `client.plugin(HttpSend)` always resolves.
 *
 * Pass the same [client] the interceptor is registered on — it is used to synthesize the call that
 * carries a mocked response, and `Sender` doesn't expose the client it is sending through.
 *
 * Register it once per client at setup: `HttpSend` neither rejects duplicates nor offers a way to
 * remove an interceptor, so registering twice records every transaction twice. `HttpSend` runs
 * interceptors outermost-first in registration order, so an interceptor added after the client was
 * built sits closest to the network and records each redirect hop as its own transaction.
 *
 * Known limitation: response bodies are buffered with `save()` before the caller sees them, so a
 * long-lived streaming response that isn't `text/event-stream` (which is skipped) is fully
 * buffered and delays the caller until the stream ends. Headers the engine injects
 * (User-Agent, Accept-Encoding, Host) are not visible here and are omitted.
 *
 * @param maxBodyChars request/response bodies longer than this are truncated for transport.
 */
fun JetWhaleNetworkAgentPlugin.ktorSendInterceptor(client: HttpClient, maxBodyChars: Int = 100_000): HttpSendInterceptor {
    val agent = this
    return { request ->
        agent.monitorSend(client, request, maxBodyChars) { execute(it) }
    }
}
