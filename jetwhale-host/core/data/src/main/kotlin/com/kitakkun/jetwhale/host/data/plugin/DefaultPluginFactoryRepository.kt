package com.kitakkun.jetwhale.host.data.plugin

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
import java.net.URLClassLoader
import java.util.ServiceLoader
import kotlin.io.path.Path

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginFactoryRepository : PluginFactoryRepository {
    private val mutablePluginFactoriesFlow: MutableStateFlow<ImmutableMap<String, JetWhaleHostPluginFactory>> = MutableStateFlow(persistentMapOf())
    override val loadedPluginFactoriesFlow: Flow<Map<String, JetWhaleHostPluginFactory>> = mutablePluginFactoriesFlow
    override val loadedPluginFactories: Map<String, JetWhaleHostPluginFactory> get() = mutablePluginFactoriesFlow.value

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
}
