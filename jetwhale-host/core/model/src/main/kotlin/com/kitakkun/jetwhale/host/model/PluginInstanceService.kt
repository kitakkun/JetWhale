package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.coroutines.flow.SharedFlow

data class LoadedPluginInstance(
    val pluginId: String,
    val sessionId: String,
    val plugin: JetWhaleHostPlugin,
)

interface PluginInstanceService {
    /** Emits lifecycle events as plugin instances are created or disposed. */
    val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent>

    /** Returns all currently loaded plugin instances. */
    fun getLoadedPluginInstances(): List<LoadedPluginInstance>

    fun unloadPluginInstanceForSession(sessionId: String)
    fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin?
    fun unloadPluginInstancesForPlugin(pluginId: String)
    fun clearAllPluginInstances()

    /**
     * Initializes plugin instances for the specified plugin and sessions if they don't already exist.
     * Each new instance is wired to its own messaging peer.
     * @return The set of session IDs for which new plugin instances were initialized.
     */
    fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String>

    /** Routes an inbound plugin [frame] to the peer of the matching plugin instance in [sessionId]. */
    suspend fun routeFrame(sessionId: String, frame: PluginFrame)
}
