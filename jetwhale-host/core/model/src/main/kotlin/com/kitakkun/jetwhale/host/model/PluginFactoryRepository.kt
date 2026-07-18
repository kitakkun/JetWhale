package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing loaded plugins.
 *
 * Each plugin is loaded inside its own [ClassLoader] so that the classloader (and therefore the
 * plugin's classes) can be discarded and reloaded independently. This is what makes development-time
 * hot reload possible (see [PluginHotReloadService]).
 */
interface PluginFactoryRepository {
    val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>>
    val loadedPlugins: Map<String, LoadedHostPlugin>
    val failedJarsFlow: Flow<List<FailedPluginJar>>

    suspend fun loadPlugin(pluginJarPath: String)
    suspend fun unloadPlugin(pluginId: String)

    /**
     * Returns the `pluginId`s currently loaded from [pluginJarPath] (a single jar may provide several
     * plugins), or an empty list if no plugin from that jar is loaded. Used by hot reload to map a
     * changed jar file back to the plugins it provides.
     */
    fun findPluginIdsByJarPath(pluginJarPath: String): List<String>

    /**
     * Reloads every plugin from [pluginJarPath]: the previous classloader for that jar is closed and
     * discarded, then the factories are loaded again from a fresh classloader. Returns the `pluginId`s
     * that were (re)loaded, or an empty list if loading failed.
     */
    suspend fun reloadPlugin(pluginJarPath: String): List<String>

    /**
     * Attempts an in-place hot swap of the plugins served by [pluginJarPath]: the jar's already-loaded
     * classes are redefined from the rebuilt jar **without** dropping the classloader or recreating the
     * plugin instances, so plugin instance state is preserved. Because all plugins in a jar share one
     * classloader, this covers every plugin the jar provides at once.
     *
     * Returns the redefined `pluginId`s on success, or an empty list when an in-place swap is not
     * possible (no JVM Instrumentation available, an unsupported/structural change without an enhanced
     * runtime such as the JetBrains Runtime, etc.). On an empty result the caller should fall back to
     * [reloadPlugin], which does a full reload at the cost of losing the plugins' state.
     */
    fun tryRedefinePlugin(pluginJarPath: String): List<String>
}
