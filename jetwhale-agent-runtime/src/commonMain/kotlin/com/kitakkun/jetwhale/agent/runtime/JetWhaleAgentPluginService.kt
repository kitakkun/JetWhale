package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.BufferedMessenger
import com.kitakkun.jetwhale.protocol.messaging.DefaultJetWhaleMessagingFormat
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Owns the messaging peers for the agent plugins, and tracks two independent lifecycles:
 *
 * - **activation** (the host enabled/disabled the plugin): `onActivate` / `onDeactivate`. Persists
 *   across connections — a disconnect does not deactivate a plugin.
 * - **connection** (the transport went up/down): a [JetWhalePluginPeer] is the live transport, created
 *   for each active plugin on connect (running its `negotiation`) and dropped on disconnect (with
 *   `onDisconnected`). The plugin keeps its connection-independent [BufferedMessenger] (and keeps
 *   buffering, per [JetWhaleAgentPlugin.offlineEventBufferCapacity]) across the gap.
 *
 * Inbound [PluginFrame]s are routed to the peer of the matching plugin id.
 */
@OptIn(InternalJetWhaleApi::class)
internal class JetWhaleAgentPluginService(
    plugins: List<JetWhaleAgentPlugin>,
) {
    private class PluginRuntime(
        val plugin: JetWhaleAgentPlugin,
        val messenger: BufferedMessenger,
        var peer: JetWhalePluginPeer? = null,
        var connectJob: Job? = null,
        var active: Boolean = false,
    )

    /** Lives for the whole agent, independent of any connection, so offline buffers survive reconnects. */
    private val serviceScope: CoroutineScope = CoroutineScope(messagingServiceCoroutineDispatcher() + SupervisorJob())

    private var connectionScope: CoroutineScope? = null
    private var sendFrame: (suspend (PluginFrame) -> Unit)? = null

    private val runtimes: Map<String, PluginRuntime> = plugins.associate { plugin ->
        val messenger = BufferedMessenger(
            parentScope = serviceScope,
            payloadFormat = DefaultJetWhaleMessagingFormat,
            bufferCapacity = plugin.offlineEventBufferCapacity(),
        )
        // Bind the connection-independent messenger once, up front.
        plugin.bindMessenger(messenger)
        plugin.pluginId to PluginRuntime(plugin, messenger)
    }

    /** Binds the service to a freshly opened connection. Call before [syncActivePlugins]. */
    fun startConnection(scope: CoroutineScope, sendFrame: suspend (PluginFrame) -> Unit) {
        this.connectionScope = scope
        this.sendFrame = sendFrame
    }

    /**
     * On connect: reconcile the host-activated set against [availableIds] — activating newly-enabled
     * plugins and deactivating any the host disabled while we were away — then (re)establish peers.
     */
    suspend fun syncActivePlugins(availableIds: Set<String>) {
        runtimes.values
            .filter { it.active && it.plugin.pluginId !in availableIds }
            .forEach { deactivate(it) }
        availableIds.forEach { activate(it) }
    }

    /** On a `PluginActivated` event: the host enabled one plugin. */
    fun activatePlugin(id: String) {
        activate(id)
    }

    /** On a `PluginDeactivated` event: the host disabled one plugin. */
    suspend fun deactivatePlugin(id: String) {
        deactivate(runtimes[id])
    }

    /** On disconnect: drop every peer, keeping plugins activated for the next connection. */
    suspend fun disconnectAll() {
        runtimes.values.forEach { dropPeer(it, notifyDisconnected = true) }
    }

    private fun activate(id: String) {
        val runtime = runtimes[id] ?: return
        if (!runtime.active) {
            runtime.active = true
            runtime.plugin.dispatchActivate()
        }
        establishPeer(runtime)
    }

    private suspend fun deactivate(runtime: PluginRuntime?) {
        runtime ?: return
        if (!runtime.active) return
        // Not a transient disconnect, so no onDisconnected; the plugin gets onDeactivate to stop its work.
        dropPeer(runtime, notifyDisconnected = false)
        runtime.active = false
        runtime.plugin.dispatchDeactivate()
    }

    private fun establishPeer(runtime: PluginRuntime) {
        val scope = connectionScope ?: return
        val sendFrame = sendFrame ?: return
        if (runtime.peer != null) return
        val peer = JetWhalePluginPeer(pluginId = runtime.plugin.pluginId, parentScope = scope, sendFrame = sendFrame)
        peer.configure { runtime.plugin.registerHandlers(this) }
        runtime.peer = peer
        // Attach the live transport (requests work now); run the plugin's negotiation, then open the
        // gate so buffered events flush only after it finishes. A negotiation that hangs (e.g. a
        // mismatched script) is bounded by a timeout: warn loudly and proceed, rather than freeze.
        runtime.messenger.bind(peer.messenger)
        runtime.connectJob = scope.launch {
            try {
                withTimeout(runtime.plugin.negotiationTimeoutMillis()) {
                    runtime.plugin.runNegotiation(peer.negotiationScope)
                }
            } catch (e: TimeoutCancellationException) {
                JetWhaleLogger.w("JetWhale: negotiation for plugin '${runtime.plugin.pluginId}' did not complete in time; proceeding.", e)
            } finally {
                runtime.messenger.startFlush()
            }
        }
    }

    private suspend fun dropPeer(runtime: PluginRuntime, notifyDisconnected: Boolean) {
        val peer = runtime.peer ?: return
        runtime.connectJob?.cancel()
        runtime.connectJob = null
        // Detach the transport (closing the flush gate) but keep the messenger alive so it keeps buffering.
        runtime.messenger.unbind()
        runtime.peer = null
        try {
            if (notifyDisconnected) runtime.plugin.dispatchDisconnected()
        } finally {
            peer.close()
        }
    }

    suspend fun onFrame(frame: PluginFrame) {
        val peer = runtimes[frame.pluginId]?.peer
        if (peer != null) {
            peer.onFrame(frame)
            return
        }
        // No active peer for this frame. If it expects a reply, fail it fast so the requester does
        // not wait for the timeout (e.g. a host request that races ahead of this plugin's activation).
        // Launch, don't await: sendFrame is a suspending socket write and onFrame runs on the
        // connection's single frame collector, so awaiting here would stall delivery of every other
        // plugin's frames while this one failure reply is in flight.
        if (frame is PluginFrame.Request) {
            val sendFrame = sendFrame ?: return
            serviceScope.launch {
                sendFrame(
                    PluginFrame.Reply.Failure(
                        pluginId = frame.pluginId,
                        inReplyTo = frame.correlationId,
                        errorMessage = "Plugin '${frame.pluginId}' is not active in the agent.",
                    ),
                )
            }
        }
    }
}
