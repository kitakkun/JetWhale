package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class LoadedPluginInstance(
    val pluginId: String,
    val sessionId: String,
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

    fun unloadPluginInstanceForSession(sessionId: String)
    fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin?

    /**
     * What the screen of [pluginId] in [sessionId] can show, following its instance as it starts,
     * fails, is replaced or goes away. A UI instance's scene is created when it is first needed and
     * closed when the instance goes, so no state ever carries the scene of an instance that is gone.
     */
    fun pluginScreenStateFlow(pluginId: String, sessionId: String): Flow<PluginScreenState>

    /** The scene of [pluginId]'s instance in [sessionId], created on first use; null while no instance with a UI runs there. */
    @OptIn(InternalComposeUiApi::class)
    suspend fun getOrCreatePluginScene(pluginId: String, sessionId: String): PluginComposeScene?

    /**
     * Gives [pluginId]'s instances new scenes and closes the old ones, keeping the instances: an
     * in-place reload has redefined the code their content runs.
     */
    fun recreatePluginScenes(pluginId: String)

    fun unloadPluginInstancesForPlugin(pluginId: String)

    /**
     * Disposes every instance that belongs to an app, for when the server stops and takes every app
     * with it. The instances of [HostSession] stay: they need no app and keep running.
     */
    fun clearAppSessionPluginInstances()

    /**
     * Initializes plugin instances for the specified plugin and sessions if they don't already exist.
     * Each new instance is wired to its own messaging peer.
     *
     * A new instance of a plugin that requires an agent does not run its `onPrepare` until
     * [startPluginInstancePreparation] is called for it.
     * @return The set of session IDs for which new plugin instances were initialized.
     */
    fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String>

    /**
     * Runs the `onPrepare` of [pluginId]'s instance in [sessionId]. Call it once that session's agent
     * has the plugin active: an agent fails every request for a plugin it has not activated, so a
     * preparation that runs earlier loses the plugin's initial exchange. Does nothing when the
     * instance has already started preparing or does not exist.
     */
    fun startPluginInstancePreparation(pluginId: String, sessionId: String)

    /** Routes an inbound plugin [frame] to the peer of the matching plugin instance in [sessionId]. */
    suspend fun routeFrame(sessionId: String, frame: PluginFrame)
}
