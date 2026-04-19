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

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginInstanceService(
    private val pluginFactoryRepository: PluginFactoryRepository,
) : PluginInstanceService {
    private val mutableLoadedPlugins: MutableMap<String, JetWhaleRawHostPlugin> = mutableMapOf()

    // Tracks (pluginId, sessionId) for each key so we can emit Disposed events without parsing keys.
    private val keyToIds: MutableMap<String, Pair<String, String>> = mutableMapOf()

    private val mutablePluginInstanceEventFlow: MutableSharedFlow<PluginInstanceEvent> = MutableSharedFlow(extraBufferCapacity = 64)
    override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = mutablePluginInstanceEventFlow.asSharedFlow()

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> {
        return keyToIds.mapNotNull { (key, ids) ->
            val plugin = mutableLoadedPlugins[key] ?: return@mapNotNull null
            LoadedPluginInstance(pluginId = ids.first, sessionId = ids.second, plugin = plugin)
        }
    }

    override fun getPluginInstanceForSession(
        pluginId: String,
        sessionId: String,
    ): JetWhaleRawHostPlugin? {
        val key = "$pluginId-$sessionId"
        return mutableLoadedPlugins[key]
    }

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
        val pluginFactory = pluginFactoryRepository.loadedPluginFactories[pluginId] ?: return emptySet()

        val newlyInitializedSessions = mutableSetOf<String>()
        for (sessionId in sessionIds) {
            val key = "$pluginId-$sessionId"
            mutableLoadedPlugins.computeIfAbsent(key) {
                newlyInitializedSessions += sessionId
                keyToIds[key] = pluginId to sessionId
                pluginFactory.createPlugin()
            }
        }

        newlyInitializedSessions.forEach { sessionId ->
            mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Ready(pluginId, sessionId))
        }
        return newlyInitializedSessions
    }

    override fun unloadPluginInstanceForSession(sessionId: String) {
        val keysToRemove = mutableLoadedPlugins.keys.filter { it.endsWith("-$sessionId") }
        for (key in keysToRemove) {
            mutableLoadedPlugins[key]?.onDispose()
            mutableLoadedPlugins.remove(key)
            keyToIds.remove(key)?.let { (pluginId, sid) ->
                mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Disposed(pluginId, sid))
            }
        }
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        val keysToRemove = mutableLoadedPlugins.keys.filter { it.startsWith("$pluginId-") }
        for (key in keysToRemove) {
            mutableLoadedPlugins[key]?.onDispose()
            mutableLoadedPlugins.remove(key)
            keyToIds.remove(key)?.let { (pid, sessionId) ->
                mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Disposed(pid, sessionId))
            }
        }
    }

    override fun clearAllPluginInstances() {
        for (plugin in mutableLoadedPlugins.values) {
            plugin.onDispose()
        }
        mutableLoadedPlugins.clear()
        keyToIds.clear()
    }
}
