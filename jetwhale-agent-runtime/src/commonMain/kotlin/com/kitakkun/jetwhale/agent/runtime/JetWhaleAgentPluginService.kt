package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.coroutines.CoroutineScope

/**
 * Owns the symmetric messaging peers for the agent plugins, scoped to the current connection.
 *
 * A peer is created when a plugin is activated (after negotiation, or on a `PluginActivated` event)
 * and dropped when it is deactivated or the connection ends. Inbound [PluginFrame]s are routed to the
 * peer of the matching plugin id.
 */
@OptIn(InternalJetWhaleApi::class)
internal class JetWhaleAgentPluginService(
    private val plugins: List<JetWhaleAgentPlugin>,
) {
    private class ActivePlugin(
        val plugin: JetWhaleAgentPlugin,
        val peer: JetWhalePluginPeer,
    )

    private var connectionScope: CoroutineScope? = null
    private var sendFrame: (suspend (PluginFrame) -> Unit)? = null
    private val activePlugins: MutableMap<String, ActivePlugin> = mutableMapOf()

    /** Binds the service to a freshly opened connection. Call before activating plugins. */
    fun startConnection(scope: CoroutineScope, sendFrame: suspend (PluginFrame) -> Unit) {
        this.connectionScope = scope
        this.sendFrame = sendFrame
    }

    fun activatePlugins(vararg ids: String) {
        val scope = connectionScope ?: return
        val sendFrame = sendFrame ?: return
        ids.forEach { id ->
            if (id in activePlugins) return@forEach
            val plugin = plugins.firstOrNull { it.pluginId == id } ?: return@forEach
            val peer = JetWhalePluginPeer(pluginId = id, parentScope = scope, sendFrame = sendFrame)
            peer.configure { plugin.registerHandlers(this) }
            plugin.create(peer.messenger)
            activePlugins[id] = ActivePlugin(plugin, peer)
        }
    }

    suspend fun deactivatePlugins(vararg ids: String) {
        ids.forEach { id ->
            val active = activePlugins.remove(id) ?: return@forEach
            active.plugin.dispose()
            active.peer.close()
        }
    }

    suspend fun deactivateAllPlugins() {
        val all = activePlugins.values.toList()
        activePlugins.clear()
        all.forEach {
            it.plugin.dispose()
            it.peer.close()
        }
    }

    suspend fun onFrame(frame: PluginFrame) {
        val peer = activePlugins[frame.pluginId]?.peer
        if (peer != null) {
            peer.onFrame(frame)
            return
        }
        // No active plugin for this frame. If it expects a reply, fail it fast so the requester does
        // not wait for the timeout (e.g. a host request that races ahead of this plugin's activation).
        if (frame is PluginFrame.Request) {
            sendFrame?.invoke(
                PluginFrame.Reply.Failure(
                    pluginId = frame.pluginId,
                    inReplyTo = frame.correlationId,
                    errorMessage = "Plugin '${frame.pluginId}' is not active in the agent.",
                ),
            )
        }
    }
}
