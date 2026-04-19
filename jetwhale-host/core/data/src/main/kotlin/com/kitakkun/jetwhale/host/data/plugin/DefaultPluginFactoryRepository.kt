package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.net.URLClassLoader
import java.util.ServiceLoader
import kotlin.io.path.Path

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginFactoryRepository : PluginFactoryRepository {
    private val mutablePluginsFlow: MutableStateFlow<ImmutableMap<String, LoadedHostPlugin>> =
        MutableStateFlow(persistentMapOf())
    override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = mutablePluginsFlow
    override val loadedPlugins: Map<String, LoadedHostPlugin> get() = mutablePluginsFlow.value

    private val mutableFailedJarPathsFlow: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    override val failedJarPathsFlow: Flow<List<String>> = mutableFailedJarPathsFlow.asStateFlow()

    override suspend fun loadPlugin(pluginJarPath: String) {
        try {
            val classLoader = URLClassLoader(arrayOf(Path(pluginJarPath).toUri().toURL()))

            val manifestJson = classLoader
                .getResourceAsStream(MANIFEST_PATH)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: run {
                    println("Warning: $MANIFEST_PATH not found in $pluginJarPath")
                    mutableFailedJarPathsFlow.update { it + pluginJarPath }
                    return
                }

            val manifest = pluginManifestJson.decodeFromString<JetWhaleHostPluginManifest>(manifestJson)

            val factory = ServiceLoader.load(JetWhaleHostPluginFactory::class.java, classLoader)
                .toList()
                .singleOrNull()
                ?: error("Expected exactly one ${JetWhaleHostPluginFactory::class.java.simpleName} in $pluginJarPath")

            mutablePluginsFlow.update { current ->
                current.toMutableMap().apply {
                    put(manifest.pluginId, LoadedHostPlugin(manifest = manifest, factory = factory))
                    println("Loaded plugin: ${manifest.pluginId} v${manifest.version}")
                }.toPersistentMap()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            println("Failed to load plugin from $pluginJarPath: ${e.message}")
            mutableFailedJarPathsFlow.update { it + pluginJarPath }
        }
    }

    override suspend fun unloadPlugin(pluginId: String) {
        mutablePluginsFlow.update { current ->
            current.toMutableMap().apply { remove(pluginId) }.toPersistentMap()
        }
        println("Unloaded plugin: $pluginId")
    }

    companion object {
        private const val MANIFEST_PATH = "META-INF/jetwhale/plugin-manifest.json"
        private val pluginManifestJson = Json { ignoreUnknownKeys = true }
    }
}
