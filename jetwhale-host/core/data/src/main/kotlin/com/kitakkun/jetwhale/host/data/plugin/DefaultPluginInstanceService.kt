package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

private data class PluginInstanceKey(val pluginId: String, val sessionId: String)

/**
 * A plugin instance paired with the messaging peer that delivers its frames. The peer's outbound
 * frames are sent to this instance's session; inbound frames are routed to it by the server.
 */
private class LoadedInstance(
    val plugin: JetWhaleHostPlugin,
    val peer: JetWhalePluginPeer,
)

@OptIn(InternalJetWhaleHostApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginInstanceService(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val frameSender: HostPluginFrameSender,
) : PluginInstanceService {
    private val logger = Logger.getLogger(DefaultPluginInstanceService::class.java.name)

    /** Parent scope for every plugin peer; each peer also gets its own child supervisor. */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val loadedPlugins: ConcurrentHashMap<PluginInstanceKey, LoadedInstance> = ConcurrentHashMap()

    private val mutablePluginInstanceEventFlow: MutableSharedFlow<PluginInstanceEvent> = MutableSharedFlow(extraBufferCapacity = 64)
    override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = mutablePluginInstanceEventFlow.asSharedFlow()

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, instance) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = instance.plugin)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.plugin

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
        val loaded = pluginFactoryRepository.loadedPlugins[pluginId] ?: return emptySet()

        val newlyInitializedSessions = mutableSetOf<String>()
        for (sessionId in sessionIds) {
            val key = PluginInstanceKey(pluginId, sessionId)
            loadedPlugins.computeIfAbsent(key) {
                newlyInitializedSessions += sessionId
                createInstance(pluginId, sessionId, loaded.factory.createPlugin())
            }
        }

        newlyInitializedSessions.forEach { sessionId ->
            emitEvent(PluginInstanceEvent.Ready(pluginId, sessionId))
        }
        return newlyInitializedSessions
    }

    private fun createInstance(pluginId: String, sessionId: String, plugin: JetWhaleHostPlugin): LoadedInstance {
        val peer = JetWhalePluginPeer(
            pluginId = pluginId,
            parentScope = scope,
            sendFrame = { frame -> frameSender.sendFrame(sessionId, frame) },
        )
        peer.configure { plugin.registerHandlers(this) }
        plugin.create(peer.messenger)
        return LoadedInstance(plugin, peer)
    }

    override suspend fun routeFrame(sessionId: String, frame: PluginFrame) {
        loadedPlugins[PluginInstanceKey(frame.pluginId, sessionId)]?.peer?.onFrame(frame)
    }

    override fun unloadPluginInstanceForSession(sessionId: String) {
        loadedPlugins.keys.filter { it.sessionId == sessionId }.forEach { disposeInstance(it) }
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        loadedPlugins.keys.filter { it.pluginId == pluginId }.forEach { disposeInstance(it) }
    }

    override fun clearAllPluginInstances() {
        loadedPlugins.keys.toList().forEach { disposeInstance(it, emitEvent = false) }
    }

    private fun disposeInstance(key: PluginInstanceKey, emitEvent: Boolean = true) {
        val removed = loadedPlugins.remove(key) ?: return
        removed.plugin.dispose()
        // close() suspends (it fails pending requests under a mutex), so run it off the caller.
        scope.launch { removed.peer.close() }
        if (emitEvent) emitEvent(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
    }

    private fun emitEvent(event: PluginInstanceEvent) {
        if (!mutablePluginInstanceEventFlow.tryEmit(event)) {
            logger.warning("Plugin instance event dropped (buffer full): $event")
        }
    }
}
