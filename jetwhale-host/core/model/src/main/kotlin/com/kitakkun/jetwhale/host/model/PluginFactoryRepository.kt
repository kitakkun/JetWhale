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
}
