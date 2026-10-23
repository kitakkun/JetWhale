package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** @property version The version of the plugin the instance was created from. */
data class LoadedPluginInstance(
    val pluginId: String,
    val sessionId: String,
    val plugin: JetWhaleHostPlugin,
    val version: String,
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

    /**
     * The version each instance was created from. A session keeps the version it was bound to while
     * that version stays loaded.
     */
    val boundPluginVersionsFlow: StateFlow<BoundPluginVersions>

    /** Returns all currently loaded plugin instances. */
    fun getLoadedPluginInstances(): List<LoadedPluginInstance>

    fun unloadPluginInstanceForSession(sessionId: String)
    fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin?

    fun unloadPluginInstancesForPlugin(pluginId: String)

    /**
     * Disposes the instances created from the plugin versions [jarPath] provides, for the jar to be
     * reloaded or removed. An app among them gets the same version again when its instance is next
     * created, while that version is still loaded.
     */
    fun unloadPluginInstancesForJar(jarPath: String)

    /**
     * Disposes every instance that belongs to an app, for when the server stops and takes every app
     * with it. The instances of [HostSession] stay: they need no app and keep running.
     */
    fun clearAppSessionPluginInstances()

    /**
     * Initializes plugin instances for the specified plugin and sessions if they don't already exist.
     * [agentVersionsBySession] maps each target session id to the version of the plugin its agent
     * advertised, or to null for [HostSession]; each session gets the loaded version
     * [AgentVersionCompatibility] picks for it. A session keeps a version it is already bound to while
     * that version stays loaded, across a reload of that version's jar too, and gets no instance when no
     * loaded version fits it. Each new instance is wired to its own messaging peer.
     *
     * A new instance of a plugin that requires an agent does not run its `onPrepare` until
     * [startPluginInstancePreparation] is called for it.
     * @return The set of session IDs for which new plugin instances were initialized.
     */
    fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, agentVersionsBySession: Map<String, String?>): Set<String>

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
