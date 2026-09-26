package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginVersionOrder
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import net.bytebuddy.agent.ByteBuddyAgent
import java.io.File
import java.io.InputStream
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.io.path.Path

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginFactoryRepository(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : PluginFactoryRepository {
    override val loadedPluginVersionsFlow: StateFlow<ImmutableMap<String, List<LoadedHostPlugin>>>
        field = MutableStateFlow<ImmutableMap<String, List<LoadedHostPlugin>>>(persistentMapOf())
    override val loadedPluginVersions: Map<String, List<LoadedHostPlugin>> get() = loadedPluginVersionsFlow.value

    override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = loadedPluginVersionsFlow.map(::newestOfEach).distinctUntilChanged()
    override val loadedPlugins: Map<String, LoadedHostPlugin> get() = newestOfEach(loadedPluginVersionsFlow.value)

    private val mutableFailedJarsFlow: MutableStateFlow<List<FailedPluginJar>> = MutableStateFlow(emptyList())
    override val failedJarsFlow: Flow<List<FailedPluginJar>> = mutableFailedJarsFlow.asStateFlow()

    /**
     * The classloader that owns each loaded jar, keyed by absolute jar path. A single jar may declare
     * several plugins; they all share this one classloader, so that on reload the previous loader can
     * be closed and discarded together with all of the jar's stale classes.
     */
    private val classLoaders: ConcurrentHashMap<String, URLClassLoader> = ConcurrentHashMap()

    /** Maps the absolute jar path plugins were loaded from to the plugin versions it provides. */
    private val jarPathToPlugins: ConcurrentHashMap<String, List<PluginVersionKey>> = ConcurrentHashMap()

    /**
     * Private per-jar copy of the jar that each classloader actually opens, keyed by absolute jar path.
     * Loading from a copy lets the source jar (a dev jar, or an installed one) be overwritten without
     * corrupting the running classloader's open zip handle. Replaced copies are NOT deleted eagerly (cached resource URLs
     * such as plugin icons may still point at them — deleting would throw NoSuchFileException); they
     * are cleaned up on JVM exit via deleteOnExit.
     */
    private val runtimeJars: ConcurrentHashMap<String, File> = ConcurrentHashMap()

    /**
     * Serializes load/unload/reload: each performs compound read-modify-write across several maps
     * ([classLoaders], [jarPathToPlugins], [runtimeJars], [loadedPluginVersionsFlow]), which per-map
     * atomicity alone does not make safe. `loadPlugin` is invoked from the initial load, the install
     * flow, and the hot-reload watcher, so these can overlap.
     */
    private val loadMutex = Mutex()

    override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?): Unit = loadMutex.withLock {
        loadPluginUnderLock(pluginJarPath, expectedSha256)
    }

    private fun loadPluginUnderLock(pluginJarPath: String, expectedSha256: String?) {
        // Open the classloader on a private copy of the jar so the source jar can be overwritten
        // (dev hot-reload restaging, or a new version dropped over an installed jar that keeps running
        // until the user approves it) without corrupting this classloader's open zip handle —
        // which otherwise throws ZipException on later resource/class reads. Every plugin declared by
        // the jar shares this single classloader.
        val runtimeJar = createRuntimeCopyIfReplaceable(pluginJarPath)
        val openedJar = runtimeJar ?: File(pluginJarPath)
        if (expectedSha256 != null && openedJar.sha256Hex() != expectedSha256) {
            recordFailedJar(pluginJarPath, "the jar changed after it was approved; approve it again")
            runtimeJar?.delete()
            return
        }

        // Maven-installed plugins declare their external dependencies in a manifest instead of
        // bundling them; those jars were downloaded into the plugin libs directory at install time
        // and join the plugin's classpath here. A missing jar (or an unreadable manifest) fails the
        // load and surfaces the jar in the failed list. Fat-jars have no manifest → empty list.
        val dependencyJarUrls = try {
            resolveDeclaredDependencyJars(openedJar).map { it.toURI().toURL() }
        } catch (e: Exception) {
            println("Failed to load plugin from $pluginJarPath: ${e.message}")
            recordFailedJar(pluginJarPath, e.message ?: e.javaClass.simpleName)
            runtimeJar?.delete()
            return
        }
        // Parented to the host's own loader, not the system one: the SDK and Compose the plugin was
        // compiled against are only guaranteed to be visible there. As a standalone app the two loaders
        // are the same; inside an IDE plugin the system loader has none of them.
        val classLoader = URLClassLoader(
            (listOf(openedJar.toURI().toURL()) + dependencyJarUrls).toTypedArray(),
            DefaultPluginFactoryRepository::class.java.classLoader,
        )

        // Once the classloader is handed to `classLoaders`, the map owns it and the `finally` below
        // must not close it; until then it (and its temp copy) is ours to discard on any failure.
        var committed = false
        try {
            val loaded = loadDeclaredPlugins(pluginJarPath, classLoader)
            val newPlugins = loaded.map(LoadedHostPlugin::versionKey)

            // Detach any of these plugin versions that another jar currently provides, so we neither
            // leak that jar's classloader nor show a duplicate (e.g. a plugin moved between jars).
            // Another version of the same plugin stays: versions coexist.
            detachPluginVersionsFromOtherJars(newPlugins.toSet(), keepJarPath = pluginJarPath)

            // Hand the new classloader to the map (discarding the previous one for this jar, e.g. a
            // reload, so no stale classloader and its classes leak) and record this load's runtime copy.
            val previousClassLoader = classLoaders.put(pluginJarPath, classLoader)
            committed = true
            previousClassLoader?.close()
            if (runtimeJar != null) runtimeJars[pluginJarPath] = runtimeJar else runtimeJars.remove(pluginJarPath)

            // Replaces everything this jar provided before, which also drops plugins a rebuild removed
            // or renamed.
            jarPathToPlugins[pluginJarPath] = newPlugins
            loadedPluginVersionsFlow.update { current ->
                (current.withoutJar(pluginJarPath).values.flatten() + loaded).toVersionsById()
            }
            loaded.forEach { println("Loaded plugin: ${it.manifest.pluginId} v${it.manifest.version}") }
            // A previously failed jar may now succeed (e.g. after a rebuild); clear it from failures.
            mutableFailedJarsFlow.update { failed -> failed.filterNot { it.jarPath == pluginJarPath } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("Failed to load plugin from $pluginJarPath: ${e.message}")
            // recordFailedJar replaces a previous entry, so repeated failed loads (e.g. a hot-reload
            // rebuild loop) don't accumulate duplicates.
            recordFailedJar(pluginJarPath, e.message ?: e.javaClass.simpleName)
        } finally {
            if (!committed) {
                classLoader.close()
                runtimeJar?.delete()
            }
        }
    }

    /** Records [pluginJarPath] as failed with [reason], replacing any previous failure for the jar. */
    private fun recordFailedJar(pluginJarPath: String, reason: String) {
        mutableFailedJarsFlow.update { failed ->
            failed.filterNot { it.jarPath == pluginJarPath } + FailedPluginJar(pluginJarPath, reason)
        }
    }

    /**
     * Maps the plugin jar's dependency manifest to the downloaded jar files in the plugin libs
     * directory, failing with a clear message when one is missing (e.g. the jar was copied from
     * another machine without its libs, or the libs directory was cleaned).
     */
    private fun resolveDeclaredDependencyJars(pluginJar: File): List<File> {
        val libsDir = appDataDirectoryProvider.getPluginLibsDirectory()
        return PluginDependencyManifest.readFrom(pluginJar).map { dependency ->
            val libFile = File(libsDir, dependency.jarFileName())
            check(libFile.exists()) {
                "Declared dependency $dependency is missing (expected at ${libFile.path}). " +
                    "Reinstall the plugin to download it."
            }
            libFile
        }
    }

    /**
     * Reads the manifest from [classLoader] and instantiates every plugin the jar declares, pairing
     * each manifest entry with the factory class it names (no ServiceLoader) — which is what lets a
     * single jar provide several plugins. Throws on any problem (manifest missing, empty, or with
     * duplicate ids; a factory class that is missing, lacks a public no-arg constructor, or is not a
     * [JetWhaleHostPluginFactory]). The caller owns [classLoader]'s lifecycle.
     */
    private fun loadDeclaredPlugins(pluginJarPath: String, classLoader: ClassLoader): List<LoadedHostPlugin> {
        val manifestJson = classLoader.getResourceAsStream(PLUGIN_MANIFEST_PATH)?.use(InputStream::readPluginManifestJson)
            ?: error("$PLUGIN_MANIFEST_PATH not found in $pluginJarPath")

        val manifests = try {
            decodeJetWhaleHostPluginManifestFile(manifestJson).plugins
        } catch (e: SerializationException) {
            throw PluginManifestParseException(
                "$PLUGIN_MANIFEST_PATH in $pluginJarPath is not a valid plugin manifest — it may follow an " +
                    "older manifest format or target a different JetWhale version (${e.message})",
                e,
            )
        }
        require(manifests.isNotEmpty()) { "$PLUGIN_MANIFEST_PATH in $pluginJarPath declares no plugins" }
        val duplicateIds = manifests.groupingBy(JetWhaleHostPluginManifest::pluginId).eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) {
            "$PLUGIN_MANIFEST_PATH in $pluginJarPath declares duplicate pluginId(s): ${duplicateIds.joinToString()}"
        }

        return manifests.map { manifest ->
            val factory = try {
                // getConstructor (not getDeclaredConstructor): the contract is a *public* no-arg
                // constructor, so a non-public/missing one fails clearly with NoSuchMethodException.
                classLoader.loadClass(manifest.factoryClass).getConstructor().newInstance()
            } catch (e: ReflectiveOperationException) {
                throw IllegalStateException(
                    "Could not load factory '${manifest.factoryClass}' for plugin '${manifest.pluginId}' " +
                        "in $pluginJarPath: ${e.message}",
                    e,
                )
            }
            require(factory is JetWhaleHostPluginFactory) {
                "Factory '${manifest.factoryClass}' for plugin '${manifest.pluginId}' in $pluginJarPath " +
                    "is not a ${JetWhaleHostPluginFactory::class.java.simpleName}"
            }
            LoadedHostPlugin(manifest = manifest, factory = factory, jarPath = pluginJarPath)
        }
    }

    /**
     * Removes [plugins] from every jar other than [keepJarPath] that currently provides them, together
     * with their loaded entries; a jar left with no plugins has its classloader closed and dropped.
     */
    private fun detachPluginVersionsFromOtherJars(plugins: Set<PluginVersionKey>, keepJarPath: String) {
        for ((jarPath, provided) in jarPathToPlugins) {
            if (jarPath == keepJarPath) continue
            val remaining = provided.filterNot(plugins::contains)
            if (remaining.size == provided.size) continue
            loadedPluginVersionsFlow.update { current ->
                current.values.flatten().filterNot { it.jarPath == jarPath && it.versionKey() in plugins }.toVersionsById()
            }
            if (remaining.isEmpty()) {
                jarPathToPlugins.remove(jarPath)
                classLoaders.remove(jarPath)?.close()
                runtimeJars.remove(jarPath)
            } else {
                jarPathToPlugins[jarPath] = remaining
            }
        }
    }

    /**
     * Returns a private temp copy of [pluginJarPath] when it lives where the host watches for new
     * content — the dev plugins directory or the managed plugins directory — or `null` for a jar from
     * a `--plugin-dir` directory, which is loaded directly without an extra copy.
     */
    private fun createRuntimeCopyIfReplaceable(pluginJarPath: String): File? {
        val devDir = appDataDirectoryProvider.getDevPluginsDir()
        val underDevDir = devDir != null && runCatching {
            File(pluginJarPath).canonicalFile.toPath().startsWith(File(devDir).canonicalFile.toPath())
        }.getOrDefault(false)
        if (!underDevDir && !appDataDirectoryProvider.isManagedPluginJarPath(pluginJarPath)) return null
        return File.createTempFile("jetwhale-plugin-", ".jar").also {
            it.deleteOnExit()
            File(pluginJarPath).copyTo(it, overwrite = true)
        }
    }

    override suspend fun unloadPluginJar(pluginJarPath: String): Unit = loadMutex.withLock {
        val plugins = jarPathToPlugins.remove(pluginJarPath).orEmpty()
        loadedPluginVersionsFlow.update { current -> current.withoutJar(pluginJarPath) }
        classLoaders.remove(pluginJarPath)?.close()
        runtimeJars.remove(pluginJarPath)
        mutableFailedJarsFlow.update { failed -> failed.filterNot { it.jarPath == pluginJarPath } }
        plugins.forEach { println("Unloaded plugin: ${it.pluginId} v${it.version}") }
    }

    override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = jarPathToPlugins[pluginJarPath].orEmpty().map(PluginVersionKey::pluginId)

    override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = loadMutex.withLock {
        // loadPlugin already closes and replaces the previous classloader for this jar, dropping the
        // stale classes. We simply re-run it (under the same lock, so the result read below is
        // consistent with it) and report the resulting plugin ids.
        loadPluginUnderLock(pluginJarPath, expectedSha256)
        // loadPlugin records the path in failedJarPaths on failure; treat that as an unsuccessful
        // reload (don't report stale success from a leftover jarPathToPlugins mapping).
        if (mutableFailedJarsFlow.value.any { it.jarPath == pluginJarPath }) {
            emptyList()
        } else {
            findPluginIdsByJarPath(pluginJarPath)
        }
    }

    override fun tryRedefinePlugin(pluginJarPath: String): List<String> {
        val instrumentation = instrumentation ?: return emptyList()
        val pluginIds = findPluginIdsByJarPath(pluginJarPath).takeIf { it.isNotEmpty() } ?: return emptyList()
        val classLoader = classLoaders[pluginJarPath] ?: return emptyList()

        return try {
            // Redefine every class this jar's classloader has already loaded, using the rebuilt jar's
            // bytecode. redefineClasses works on the loaded Class regardless of classloader, so this
            // reaches the plugin's child-classloader classes (which Compose Hot Reload cannot). Because
            // all plugins in the jar share this classloader, one redefine covers every plugin. Array
            // and hidden classes (e.g. invokedynamic lambdas) have no class file and are skipped.
            val loadedClasses = instrumentation.allLoadedClasses
                .filter { it.classLoader === classLoader && !it.isArray && !it.isHidden }
            if (loadedClasses.isEmpty()) return emptyList()

            val definitions = ArrayList<ClassDefinition>(loadedClasses.size)
            JarFile(pluginJarPath).use { jar ->
                for (clazz in loadedClasses) {
                    // A loaded (non-hidden, non-array) class with no class file in the rebuilt jar
                    // would leave stale bytecode behind — a partial redefine. Bail out so the caller
                    // does a full reload instead of reporting a misleading success.
                    val entry = jar.getJarEntry(clazz.name.replace('.', '/') + ".class") ?: return emptyList()
                    definitions += ClassDefinition(clazz, jar.getInputStream(entry).use { it.readBytes() })
                }
            }

            instrumentation.redefineClasses(*definitions.toTypedArray())
            pluginIds
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // UnsupportedOperationException (structural change without an enhanced runtime),
            // LinkageError, etc. The caller falls back to a full reload.
            println("In-place redefine failed for ${pluginIds.joinToString()}: ${e.message}; falling back to full reload")
            emptyList()
        }
    }

    /**
     * JVM Instrumentation handle, obtained lazily by self-attaching an agent the first time an
     * in-place redefine is attempted (dev hot-reload only). Null if the agent cannot be installed.
     */
    private val instrumentation: Instrumentation? by lazy {
        runCatching { ByteBuddyAgent.install() }.getOrNull()
    }
}

/** One version of one plugin, as a jar provides it. */
private data class PluginVersionKey(val pluginId: String, val version: String)

private fun LoadedHostPlugin.versionKey(): PluginVersionKey = PluginVersionKey(manifest.pluginId, manifest.version)

private fun Map<String, List<LoadedHostPlugin>>.withoutJar(jarPath: String): ImmutableMap<String, List<LoadedHostPlugin>> = values.flatten().filterNot { it.jarPath == jarPath }.toVersionsById()

/** Groups loaded versions by plugin id, newest first. */
private fun List<LoadedHostPlugin>.toVersionsById(): ImmutableMap<String, List<LoadedHostPlugin>> = groupBy { it.manifest.pluginId }
    .mapValues { (_, versions) -> versions.sortedWith(compareByDescending(PluginVersionOrder) { it.manifest.version }) }
    .toPersistentMap()

private fun newestOfEach(versions: Map<String, List<LoadedHostPlugin>>): Map<String, LoadedHostPlugin> = versions.mapValues { (_, newestFirst) -> newestFirst.first() }

/** The jar's plugin manifest exists but could not be parsed (malformed or incompatible schema). */
class PluginManifestParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
