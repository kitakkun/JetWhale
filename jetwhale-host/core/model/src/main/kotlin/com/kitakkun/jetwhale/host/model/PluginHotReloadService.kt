package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.SharedFlow

/**
 * Development-time service that watches a "dev plugins directory" and hot-reloads plugin jars as a
 * plugin developer rebuilds them.
 *
 * The dev plugins directory is opt-in and provided via the `jetwhale.devPluginsDir` JVM system
 * property (set by the `runJetWhale` Gradle task). When the property is absent the service is inert,
 * so production behaviour is unchanged.
 */
interface PluginHotReloadService {
    /**
     * Emits the `pluginId` of a plugin that has just been reloaded. The plugin screen observes this
     * to re-create its compose scene from the freshly loaded plugin code.
     */
    val pluginReloadedFlow: SharedFlow<String>

    /**
     * Loads any jars already present in the dev plugins directory and starts watching it for changes.
     * No-op when no dev plugins directory is configured.
     */
    suspend fun start()

    /** Stops watching the dev plugins directory. */
    fun stop()
}
