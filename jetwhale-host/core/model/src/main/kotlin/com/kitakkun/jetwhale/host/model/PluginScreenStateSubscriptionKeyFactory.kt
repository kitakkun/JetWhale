package com.kitakkun.jetwhale.host.model

/**
 * Factory for [PluginScreenStateSubscriptionKey], whose subscription is keyed by the plugin/session
 * ids that arrive as NavKey arguments. The implementation lives in the data layer; features inject
 * this interface and create a key per plugin screen.
 */
fun interface PluginScreenStateSubscriptionKeyFactory {
    fun create(pluginId: String, sessionId: String): PluginScreenStateSubscriptionKey
}
