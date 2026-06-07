package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
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
import net.bytebuddy.agent.ByteBuddyAgent
import java.io.File
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.io.path.Path

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginFactoryRepository @Inject constructor(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : PluginFactoryRepository {
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

    /**
     * Private per-plugin copy of the jar that each classloader actually opens, keyed by `pluginId`.
     * Loading from a copy lets the source (dev) jar be overwritten without corrupting the running
     * classloader's open zip handle. Replaced copies are NOT deleted eagerly (cached resource URLs
     * such as plugin icons may still point at them — deleting would throw NoSuchFileException); they
     * are cleaned up on JVM exit via deleteOnExit.
     */
    private val runtimeJars: ConcurrentHashMap<String, File> = ConcurrentHashMap()

    override suspend fun loadPlugin(pluginJarPath: String) {
        try {
            // Open the classloader on a private copy of the jar so the source jar can be overwritten
            // (e.g. by dev hot-reload restaging) without corrupting this classloader's open zip
            // handle — which otherwise throws ZipException on later resource/class reads.
            val runtimeJar: File? = createRuntimeCopyIfDevJar(pluginJarPath)
            val classLoader = URLClassLoader(arrayOf((runtimeJar ?: File(pluginJarPath)).toURI().toURL()))

            val manifestJson = classLoader
                .getResourceAsStream(MANIFEST_PATH)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: run {
                    println("Warning: $MANIFEST_PATH not found in $pluginJarPath")
                    classLoader.close()
                    runtimeJar?.delete()
                    mutableFailedJarPathsFlow.update { it + pluginJarPath }
                    return
                }

            val manifest = pluginManifestJson.decodeFromString<JetWhaleHostPluginManifest>(manifestJson)

            val factory = ServiceLoader.load(JetWhaleHostPluginFactory::class.java, classLoader)
                .toList()
                .singleOrNull()
                ?: run {
                    classLoader.close()
                    runtimeJar?.delete()
                    error("Expected exactly one ${JetWhaleHostPluginFactory::class.java.simpleName} in $pluginJarPath")
                }

            // If this jar previously loaded under a *different* plugin id, drop that stale entry so
            // we neither leak its classloader nor show a duplicate plugin.
            jarPathToPluginId[pluginJarPath]?.takeIf { it != manifest.pluginId }?.let { stalePluginId ->
                classLoaders.remove(stalePluginId)?.close()
                runtimeJars.remove(stalePluginId)
                mutablePluginsFlow.update { current ->
                    current.toMutableMap().apply { remove(stalePluginId) }.toPersistentMap()
                }
            }

            // Discard a previously loaded classloader for the same plugin id (e.g. a reload) so that
            // no stale classloader (and its classes) leaks.
            classLoaders.put(manifest.pluginId, classLoader)?.close()
            runtimeJar?.let { runtimeJars[manifest.pluginId] = it }
            // Remove any other jar-path entries that pointed at this plugin id (e.g. the jar was
            // renamed/moved or duplicated) so findPluginIdByJarPath never returns a stale path's id.
            jarPathToPluginId.entries.removeIf { it.value == manifest.pluginId && it.key != pluginJarPath }
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

    /**
     * Returns a private temp copy of [pluginJarPath] when it lives under the dev plugins directory
     * (so the dev jar can be restaged without corrupting the running classloader's open zip handle),
     * or `null` for stable installed jars, which are loaded directly without an extra copy.
     */
    private fun createRuntimeCopyIfDevJar(pluginJarPath: String): File? {
        val devDir = appDataDirectoryProvider.getDevPluginsDir() ?: return null
        val underDevDir = runCatching {
            File(pluginJarPath).canonicalFile.toPath().startsWith(File(devDir).canonicalFile.toPath())
        }.getOrDefault(false)
        if (!underDevDir) return null
        return File.createTempFile("jetwhale-plugin-", ".jar").also {
            it.deleteOnExit()
            File(pluginJarPath).copyTo(it, overwrite = true)
        }
    }

    override suspend fun unloadPlugin(pluginId: String) {
        mutablePluginsFlow.update { current ->
            current.toMutableMap().apply { remove(pluginId) }.toPersistentMap()
        }
        classLoaders.remove(pluginId)?.close()
        runtimeJars.remove(pluginId)
        jarPathToPluginId.entries.removeIf { it.value == pluginId }
        println("Unloaded plugin: $pluginId")
    }

    override fun findPluginIdByJarPath(pluginJarPath: String): String? = jarPathToPluginId[pluginJarPath]

    override suspend fun reloadPlugin(pluginJarPath: String): String? {
        // loadPlugin already closes and replaces the previous classloader for the same plugin id,
        // dropping the stale classes. We simply re-run it and report the resulting plugin id.
        loadPlugin(pluginJarPath)
        // loadPlugin records the path in failedJarPaths on failure; treat that as an unsuccessful
        // reload (don't report stale success from a leftover jarPathToPluginId mapping).
        if (pluginJarPath in mutableFailedJarPathsFlow.value) return null
        return jarPathToPluginId[pluginJarPath]
    }

    override fun tryRedefinePlugin(pluginJarPath: String): String? {
        val instrumentation = instrumentation ?: return null
        val pluginId = jarPathToPluginId[pluginJarPath] ?: return null
        val classLoader = classLoaders[pluginId] ?: return null

        return try {
            // Redefine every class this plugin's classloader has already loaded, using the rebuilt
            // jar's bytecode. redefineClasses works on the loaded Class regardless of classloader, so
            // this reaches the plugin's child-classloader classes (which Compose Hot Reload cannot).
            // Array and hidden classes (e.g. invokedynamic lambdas) have no class file and are not
            // redefinable, so they are skipped.
            val loadedClasses = instrumentation.allLoadedClasses
                .filter { it.classLoader === classLoader && !it.isArray && !it.isHidden }
            if (loadedClasses.isEmpty()) return null

            val definitions = ArrayList<ClassDefinition>(loadedClasses.size)
            JarFile(pluginJarPath).use { jar ->
                for (clazz in loadedClasses) {
                    // A loaded (non-hidden, non-array) class with no class file in the rebuilt jar
                    // would leave stale bytecode behind — a partial redefine. Bail out so the caller
                    // does a full reload instead of reporting a misleading success.
                    val entry = jar.getJarEntry(clazz.name.replace('.', '/') + ".class") ?: return null
                    definitions += ClassDefinition(clazz, jar.getInputStream(entry).use { it.readBytes() })
                }
            }

            instrumentation.redefineClasses(*definitions.toTypedArray())
            pluginId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // UnsupportedOperationException (structural change without an enhanced runtime),
            // LinkageError, etc. The caller falls back to a full reload.
            println("In-place redefine failed for $pluginId: ${e.message}; falling back to full reload")
            null
        }
    }

    /**
     * JVM Instrumentation handle, obtained lazily by self-attaching an agent the first time an
     * in-place redefine is attempted (dev hot-reload only). Null if the agent cannot be installed.
     */
    private val instrumentation: Instrumentation? by lazy {
        runCatching { ByteBuddyAgent.install() }.getOrNull()
    }

    companion object {
        private const val MANIFEST_PATH = "META-INF/jetwhale/plugin-manifest.json"
        private val pluginManifestJson = Json { ignoreUnknownKeys = true }
    }
}
