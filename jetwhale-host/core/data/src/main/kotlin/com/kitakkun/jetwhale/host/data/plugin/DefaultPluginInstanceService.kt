package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginInstanceService(
    private val pluginFactoryRepository: PluginFactoryRepository,
) : PluginInstanceService {
    private val mutableLoadedPlugins: MutableMap<String, JetWhaleRawHostPlugin> = mutableMapOf()

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
                pluginFactory.createPlugin()
            }
        }

        return newlyInitializedSessions
    }

    override fun unloadPluginInstanceForSession(sessionId: String) {
        val keysToRemove = mutableLoadedPlugins.keys.filter { it.endsWith("-$sessionId") }
        for (key in keysToRemove) {
            mutableLoadedPlugins[key]?.onDispose()
            mutableLoadedPlugins.remove(key)
        }
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        val keysToRemove = mutableLoadedPlugins.keys.filter { it.startsWith("$pluginId-") }
        for (key in keysToRemove) {
            mutableLoadedPlugins[key]?.onDispose()
            mutableLoadedPlugins.remove(key)
        }
    }
}
