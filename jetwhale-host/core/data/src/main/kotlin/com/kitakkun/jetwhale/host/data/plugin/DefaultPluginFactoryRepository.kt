package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.LoadedPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
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
import kotlinx.serialization.json.Json
import java.net.URLClassLoader
import java.util.ServiceLoader
import kotlin.io.path.Path

private val pluginJson = Json { ignoreUnknownKeys = true }

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginFactoryRepository : PluginFactoryRepository {
    private val mutableLoadedPluginsFlow: MutableStateFlow<ImmutableMap<String, LoadedPlugin>> = MutableStateFlow(persistentMapOf())
    override val loadedPluginsFlow: Flow<Map<String, LoadedPlugin>> = mutableLoadedPluginsFlow
    override val loadedPlugins: Map<String, LoadedPlugin> get() = mutableLoadedPluginsFlow.value

    override suspend fun loadPluginFactory(pluginJarPath: String) {
        try {
            val classLoader = URLClassLoader(arrayOf(Path(pluginJarPath).toUri().toURL()))
            val factoryClasses: List<JetWhaleHostPluginFactory> = ServiceLoader.load(JetWhaleHostPluginFactory::class.java, classLoader).toList()

            mutableLoadedPluginsFlow.update { loadedPlugins ->
                loadedPlugins.toMutableMap().apply {
                    factoryClasses.forEach { factory ->
                        val json = classLoader.getResourceAsStream("META-INF/jetwhale/plugin.json")
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?.let { pluginJson.decodeFromString<JetWhalePluginJson>(it) }
                            ?: return@forEach

                        val loaded = LoadedPlugin(
                            factory = factory,
                            pluginId = json.pluginId,
                            pluginName = json.pluginName,
                            version = json.version,
                            author = json.author,
                            description = json.description,
                            activeIconPath = json.activeIconPath,
                            inactiveIconPath = json.inactiveIconPath,
                        )
                        put(json.pluginId, loaded)
                        println("Loaded plugin: ${json.pluginId} (${json.pluginName} ${json.version})")
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
}
