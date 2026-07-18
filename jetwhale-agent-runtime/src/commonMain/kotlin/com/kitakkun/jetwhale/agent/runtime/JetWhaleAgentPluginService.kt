package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.BufferedMessenger
import com.kitakkun.jetwhale.protocol.messaging.DefaultJetWhaleMessagingFormat
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import com.kitakkun.jetwhale.protocol.messaging.configurePeerGuarded
import com.kitakkun.jetwhale.protocol.messaging.launchPeerPreparation
import com.kitakkun.jetwhale.protocol.messaging.replyPeerUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Owns the messaging peers for the agent plugins, and tracks two independent lifecycles:
 *
 * - **activation** (the host enabled/disabled the plugin): `onActivate` / `onDeactivate`. Persists
 *   across connections — a disconnect does not deactivate a plugin.
 * - **connection** (the transport went up/down): a [JetWhalePluginPeer] is the live transport, created
 *   for each active plugin on connect (running its `onPrepare`) and dropped on disconnect (with
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
            // A plugin whose onActivate throws must not take down the connection (and with it every
            // other plugin): isolate the failure and keep the plugin activated so its peer still works.
            try {
                runtime.plugin.dispatchActivate()
            } catch (e: Throwable) {
                JetWhaleLogger.w("JetWhale: onActivate for plugin '${runtime.plugin.pluginId}' failed.", e)
            }
        }
        establishPeer(runtime)
    }

    private suspend fun deactivate(runtime: PluginRuntime?) {
        runtime ?: return
        if (!runtime.active) return
        // Not a transient disconnect, so no onDisconnected; the plugin gets onDeactivate to stop its work.
        dropPeer(runtime, notifyDisconnected = false)
        runtime.active = false
        try {
            runtime.plugin.dispatchDeactivate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            JetWhaleLogger.w("JetWhale: onDeactivate for plugin '${runtime.plugin.pluginId}' failed.", e)
        }
    }

    private fun establishPeer(runtime: PluginRuntime) {
        val scope = connectionScope ?: return
        val sendFrame = sendFrame ?: return
        if (runtime.peer != null) return
        val descriptor = "plugin '${runtime.plugin.pluginId}'"
        val peer = JetWhalePluginPeer(pluginId = runtime.plugin.pluginId, parentScope = scope, sendFrame = sendFrame, awaitReady = true)
        val configured = configurePeerGuarded(
            peer = peer,
            descriptor = descriptor,
            registerHandlers = { runtime.plugin.registerHandlers(this) },
            warn = { message, e -> JetWhaleLogger.w(message, e) },
        )
        if (!configured) {
            scope.launch { peer.close() }
            return
        }
        runtime.peer = peer
        // Bind before prepare so the plugin's own onPrepare requests can flow; the ready gate that
        // holds inbound frames opens only once launchPeerPreparation finishes (or times out).
        runtime.messenger.bind(peer.messenger)
        runtime.connectJob = scope.launchPeerPreparation(
            peer = peer,
            descriptor = descriptor,
            prepareTimeoutMillis = runtime.plugin.prepareTimeoutMillis(),
            dispatchPrepare = { runtime.plugin.dispatchPrepare() },
            warn = { message, e -> JetWhaleLogger.w(message, e) },
            onReady = { runtime.messenger.startFlush() },
        )
    }

    private suspend fun dropPeer(runtime: PluginRuntime, notifyDisconnected: Boolean) {
        val peer = runtime.peer ?: return
        // Join, don't just cancel: the job's finally opens the flush gate (markReady/startFlush), and
        // if it ran after the next connection's bind() it would open that connection's gate before its
        // prepare completed.
        runtime.connectJob?.cancelAndJoin()
        runtime.connectJob = null
        // Detach the transport (closing the flush gate) but keep the messenger alive so it keeps buffering.
        runtime.messenger.unbind()
        runtime.peer = null
        try {
            if (notifyDisconnected) runtime.plugin.dispatchDisconnected()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Isolate per plugin: one throwing onDisconnected must not skip dropPeer for the others,
            // or they would stay bound to the dead peer across the reconnect.
            JetWhaleLogger.w("JetWhale: onDisconnected for plugin '${runtime.plugin.pluginId}' failed.", e)
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
        // No active peer for this frame (e.g. a host request that races ahead of this plugin's
        // activation): fast-fail a request so the requester does not wait out the timeout.
        val sendFrame = sendFrame ?: return
        replyPeerUnavailable(
            scope = serviceScope,
            frame = frame,
            errorMessage = "Plugin '${frame.pluginId}' is not active in the agent.",
            send = sendFrame,
            warn = { message, e -> JetWhaleLogger.w(message, e) },
        )
    }
}
