package com.kitakkun.jetwhale.host.model

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
     * Loads any jars already present in the dev plugins directory and starts watching it for changes.
     * No-op when no dev plugins directory is configured.
     */
    suspend fun start()

    /** Stops watching the dev plugins directory. */
    fun stop()
}
