package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginInstanceService : PluginInstanceService {
    private val mutableLoadedPlugins: MutableMap<String, JetWhaleRawHostPlugin> = mutableMapOf()

    override fun getOrPutPluginInstanceForSession(
        pluginId: String,
        sessionId: String,
        pluginFactory: JetWhaleHostPluginFactory,
    ): JetWhaleRawHostPlugin {
        val key = "$pluginId-$sessionId"
        return mutableLoadedPlugins.getOrPut(key) { pluginFactory.createPlugin() }
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
