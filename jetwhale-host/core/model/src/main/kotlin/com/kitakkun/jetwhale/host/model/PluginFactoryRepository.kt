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
    val failedJarPathsFlow: Flow<List<String>>

    suspend fun loadPlugin(pluginJarPath: String)
    suspend fun unloadPlugin(pluginId: String)

    /**
     * Returns the `pluginId` currently loaded from [pluginJarPath], or `null` if no plugin from that
     * jar is loaded. Used by hot reload to map a changed jar file back to the plugin it provides.
     */
    fun findPluginIdByJarPath(pluginJarPath: String): String?

    /**
     * Reloads the plugin from [pluginJarPath]: the previous classloader for that jar is closed and
     * discarded, then the factory is loaded again from a fresh classloader. Returns the `pluginId`
     * that was (re)loaded, or `null` if loading failed.
     */
    suspend fun reloadPlugin(pluginJarPath: String): String?

    /**
     * Attempts an in-place hot swap of the plugin served by [pluginJarPath]: the plugin's
     * already-loaded classes are redefined from the rebuilt jar **without** dropping the classloader
     * or recreating the plugin instance, so the plugin instance's state is preserved.
     *
     * Returns the `pluginId` on success, or `null` when an in-place swap is not possible (no JVM
     * Instrumentation available, an unsupported/structural change without an enhanced runtime such as
     * the JetBrains Runtime, etc.). On `null` the caller should fall back to [reloadPlugin], which
     * does a full reload at the cost of losing state.
     */
    fun tryRedefinePlugin(pluginJarPath: String): String?
}
