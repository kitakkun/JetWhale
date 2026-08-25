package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A live plugin instance. [sessionId] is null for the single instance of a host-scoped plugin. */
data class LoadedPluginInstance(
    val pluginId: String,
    val sessionId: String?,
    val plugin: JetWhaleHostPlugin,
)

interface PluginInstanceService {
    /** Emits lifecycle events as plugin instances are created or disposed. */
    val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent>

    /**
     * Which of the currently loaded instances render no UI, so the UI can say so instead of showing
     * an empty scene. Only this service can tell: UI-ness is a property of the instantiated plugin,
     * not of anything the manifest declares.
     */
    val headlessPluginsFlow: StateFlow<HeadlessPlugins>

    /** Returns all currently loaded plugin instances. */
    fun getLoadedPluginInstances(): List<LoadedPluginInstance>

    /** Disposes every session-scoped instance of [sessionId]; host-scoped instances are untouched. */
    fun unloadPluginInstanceForSession(sessionId: String)
    fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin?

    /** The single instance of a host-scoped plugin, or null while it has none. */
    fun getHostScopedInstance(pluginId: String): JetWhaleHostPlugin?

    /** Disposes every instance of [pluginId], the host-scoped one included. */
    fun unloadPluginInstancesForPlugin(pluginId: String)

    /** Disposes every instance the host holds, host-scoped ones included. */
    fun clearAllPluginInstances()

    /**
     * Initializes plugin instances for the specified plugin and sessions if they don't already exist.
     * Each new instance is wired to its own messaging peer.
     * @return The set of session IDs for which new plugin instances were initialized.
     */
    fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String>

    /**
     * Initializes the single instance of a host-scoped plugin if it does not exist yet. It is tied to
     * no session, so it is created as soon as the plugin is enabled and loaded.
     *
     * @return true when this call created the instance.
     */
    fun initializeHostScopedInstanceIfNeeded(pluginId: String): Boolean

    /** Routes an inbound plugin [frame] to the peer of the matching plugin instance in [sessionId]. */
    suspend fun routeFrame(sessionId: String, frame: PluginFrame)
}
