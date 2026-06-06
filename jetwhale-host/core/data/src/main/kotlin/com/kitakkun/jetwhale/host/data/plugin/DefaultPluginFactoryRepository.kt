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
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * The classloader that owns each loaded plugin, keyed by `pluginId`. Each plugin gets its own
     * [URLClassLoader] so that, on reload, the previous loader can be closed and discarded together
     * with all of the plugin's stale classes.
     */
    private val classLoaders: ConcurrentHashMap<String, URLClassLoader> = ConcurrentHashMap()

    /** Maps the absolute jar path a plugin was loaded from to its `pluginId`. */
    private val jarPathToPluginId: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    override suspend fun loadPlugin(pluginJarPath: String) {
        try {
            val classLoader = URLClassLoader(arrayOf(Path(pluginJarPath).toUri().toURL()))

            val manifestJson = classLoader
                .getResourceAsStream(MANIFEST_PATH)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: run {
                    println("Warning: $MANIFEST_PATH not found in $pluginJarPath")
                    classLoader.close()
                    mutableFailedJarPathsFlow.update { it + pluginJarPath }
                    return
                }

            val manifest = pluginManifestJson.decodeFromString<JetWhaleHostPluginManifest>(manifestJson)

            val factory = ServiceLoader.load(JetWhaleHostPluginFactory::class.java, classLoader)
                .toList()
                .singleOrNull()
                ?: run {
                    classLoader.close()
                    error("Expected exactly one ${JetWhaleHostPluginFactory::class.java.simpleName} in $pluginJarPath")
                }

            // Discard a previously loaded classloader for the same plugin id (e.g. a reload) so that
            // no stale classloader (and its classes) leaks.
            classLoaders.put(manifest.pluginId, classLoader)?.close()
            jarPathToPluginId[pluginJarPath] = manifest.pluginId

            mutablePluginsFlow.update { current ->
                current.toMutableMap().apply {
                    put(manifest.pluginId, LoadedHostPlugin(manifest = manifest, factory = factory))
                    println("Loaded plugin: ${manifest.pluginId} v${manifest.version}")
                }.toPersistentMap()
            }
            // A previously failed jar may now succeed (e.g. after a rebuild); clear it from failures.
            mutableFailedJarPathsFlow.update { it.filterNot { path -> path == pluginJarPath } }
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
        classLoaders.remove(pluginId)?.close()
        jarPathToPluginId.entries.removeIf { it.value == pluginId }
        println("Unloaded plugin: $pluginId")
    }

    override fun findPluginIdByJarPath(pluginJarPath: String): String? = jarPathToPluginId[pluginJarPath]

    override suspend fun reloadPlugin(pluginJarPath: String): String? {
        // loadPlugin already closes and replaces the previous classloader for the same plugin id,
        // dropping the stale classes. We simply re-run it and report the resulting plugin id.
        loadPlugin(pluginJarPath)
        return jarPathToPluginId[pluginJarPath]
    }

    companion object {
        private const val MANIFEST_PATH = "META-INF/jetwhale/plugin-manifest.json"
        private val pluginManifestJson = Json { ignoreUnknownKeys = true }
    }
}
