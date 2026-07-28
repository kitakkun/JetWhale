package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import kotlin.time.Duration

/**
 * An agent plugin that carries no vocabulary of its own.
 *
 * A normal agent plugin speaks in typed messages, so driving one means compiling its protocol in.
 * This plugin instead exposes the messenger's raw (string) layer, where a message is just a
 * `messageType` — the `serialName` of the payload's `@Serializable` class — and a JSON string. Any
 * host plugin can therefore be driven with no compile-time dependency on it at all: name the
 * `pluginId` it is paired with, and the messages it already understands go straight through.
 *
 * Sending only. Inbound handlers (`onEvent<E>` / `onRequest<REQ, R>`) resolve their serializer from
 * a reified type parameter, so there is no raw shape to register a catch-all against — a host plugin
 * that *requests* the agent (rather than being driven by it) cannot be answered from here.
 */
internal class WireLevelQaPlugin(
    override val pluginId: String,
    override val pluginVersion: String,
) : JetWhaleAgentPlugin() {
    fun send(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean = messenger.sendRaw(messageType, payload, policy)

    suspend fun request(messageType: String, payload: String, timeout: Duration?): String = messenger.requestRaw(messageType, payload, timeout)
}
