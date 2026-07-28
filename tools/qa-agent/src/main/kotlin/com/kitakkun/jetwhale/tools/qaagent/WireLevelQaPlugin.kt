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
    /**
     * Whether the host has enabled this plugin id. False means the host is not listening for this
     * plugin at all — usually because it is disabled there — and no send will ever arrive, however
     * long you wait.
     */
    @Volatile
    var isActivated: Boolean = false
        private set

    /**
     * Whether a message sent right now would reach the host.
     *
     * The control API accepts connections well before the debug session is up, so "the port answers"
     * is not readiness: a send in that window is dropped and only reports `false` afterwards. This
     * tracks the activation/connection lifecycle instead, so a caller can poll for readiness and
     * then send exactly once.
     */
    @Volatile
    var isReady: Boolean = false
        private set

    override fun onActivate() {
        isActivated = true
    }

    override suspend fun onPrepare() {
        isReady = true
    }

    override suspend fun onDisconnected() {
        isReady = false
    }

    override fun onDeactivate() {
        isActivated = false
        isReady = false
    }

    fun send(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean = messenger.sendRaw(messageType, payload, policy)

    suspend fun request(messageType: String, payload: String, timeout: Duration?): String = messenger.requestRaw(messageType, payload, timeout)
}
