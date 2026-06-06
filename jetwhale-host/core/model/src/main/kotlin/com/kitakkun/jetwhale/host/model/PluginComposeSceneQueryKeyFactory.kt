package com.kitakkun.jetwhale.host.model

/**
 * Factory for [PluginComposeSceneQueryKey], whose query is keyed by the plugin/session ids that
 * arrive as NavKey arguments. The implementation lives in the data layer; features inject this
 * interface and create a key per plugin instance.
 */
fun interface PluginComposeSceneQueryKeyFactory {
    fun create(pluginId: String, sessionId: String): PluginComposeSceneQueryKey
}
