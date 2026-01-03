package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.net.URLClassLoader
import java.util.ServiceLoader
import kotlin.io.path.Path

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginRepository : PluginRepository {
    private val mutablePluginFactoriesFlow: MutableStateFlow<ImmutableMap<String, JetWhaleHostPluginFactory>> = MutableStateFlow(persistentMapOf())
    override val loadedPluginFactoriesFlow: Flow<Map<String, JetWhaleHostPluginFactory>> = mutablePluginFactoriesFlow

    private val mutableLoadedPlugins: MutableMap<String, JetWhaleRawHostPlugin> = mutableMapOf()

    override suspend fun loadPluginFactory(pluginJarPath: String) {
        try {
            val classLoader = URLClassLoader(arrayOf(Path(pluginJarPath).toUri().toURL()))
            val factoryClasses: List<JetWhaleHostPluginFactory> = ServiceLoader.load(JetWhaleHostPluginFactory::class.java, classLoader).toList()

            mutablePluginFactoriesFlow.update { pluginFactories ->
                pluginFactories.toMutableMap().apply {
                    factoryClasses.forEach {
                        put(it.meta.pluginId, it)
                        println("Loaded plugin: ${it.meta}")
                    }
                }.toPersistentMap()
            }
        } catch (e: Throwable) {
            println("Failed to load plugin from $pluginJarPath: ${e.message}")
        }
    }

    override suspend fun unloadPlugin(pluginId: String) {
        println("Unloaded plugin: $pluginId")
    }

    override suspend fun getOrPutPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleRawHostPlugin {
        val key = "$pluginId-$sessionId"
        return mutableLoadedPlugins.getOrPut(key) {
            val factory = mutablePluginFactoriesFlow.value[pluginId] ?: throw IllegalArgumentException("Plugin with ID $pluginId is not loaded.")
            factory.createPlugin()
        }
    }

    override fun unloadPluginInstanceForSession(sessionId: String) {
        val keysToRemove = mutableLoadedPlugins.keys.filter { it.endsWith("-$sessionId") }
        for (key in keysToRemove) {
            mutableLoadedPlugins[key]?.onDispose()
            mutableLoadedPlugins.remove(key)
        }
    }
}
