package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

private data class PluginInstanceKey(val pluginId: String, val sessionId: String)

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginInstanceService(
    private val pluginFactoryRepository: PluginFactoryRepository,
) : PluginInstanceService {
    private val logger = Logger.getLogger(DefaultPluginInstanceService::class.java.name)
    private val loadedPlugins: ConcurrentHashMap<PluginInstanceKey, JetWhaleRawHostPlugin> = ConcurrentHashMap()

    private val mutablePluginInstanceEventFlow: MutableSharedFlow<PluginInstanceEvent> = MutableSharedFlow(extraBufferCapacity = 64)
    override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = mutablePluginInstanceEventFlow.asSharedFlow()

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, plugin) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = plugin)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleRawHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
        val loaded = pluginFactoryRepository.loadedPlugins[pluginId] ?: return emptySet()

        val newlyInitializedSessions = mutableSetOf<String>()
        for (sessionId in sessionIds) {
            val key = PluginInstanceKey(pluginId, sessionId)
            loadedPlugins.computeIfAbsent(key) {
                newlyInitializedSessions += sessionId
                loaded.factory.createPlugin()
            }
        }

        newlyInitializedSessions.forEach { sessionId ->
            emitEvent(PluginInstanceEvent.Ready(pluginId, sessionId))
        }
        return newlyInitializedSessions
    }

    override fun unloadPluginInstanceForSession(sessionId: String) {
        val keysToRemove = loadedPlugins.keys.filter { it.sessionId == sessionId }
        for (key in keysToRemove) {
            val removed = loadedPlugins.remove(key) ?: continue
            removed.onDispose()
            emitEvent(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
        }
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        val keysToRemove = loadedPlugins.keys.filter { it.pluginId == pluginId }
        for (key in keysToRemove) {
            val removed = loadedPlugins.remove(key) ?: continue
            removed.onDispose()
            emitEvent(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
        }
    }

    override fun clearAllPluginInstances() {
        loadedPlugins.values.forEach { it.onDispose() }
        loadedPlugins.clear()
    }

    private fun emitEvent(event: PluginInstanceEvent) {
        if (!mutablePluginInstanceEventFlow.tryEmit(event)) {
            logger.warning("Plugin instance event dropped (buffer full): $event")
        }
    }
}
