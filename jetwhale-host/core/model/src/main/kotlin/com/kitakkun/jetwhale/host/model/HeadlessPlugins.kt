package com.kitakkun.jetwhale.host.model

/**
 * Plugins that render no UI: their instance does not implement `JetWhaleHostPluginUi`, so the host
 * has no scene to show for them. They still run — they can talk to their agent and publish MCP
 * tools — so this marks "nothing to display", not "not working".
 *
 * Keyed by session id, because UI-ness is read off a live plugin instance and an instance exists per
 * session. A plugin with no instance yet is absent from the map and is therefore not reported as
 * headless.
 */
data class HeadlessPlugins(val pluginIdsBySession: Map<String, Set<String>>) {
    fun isHeadless(sessionId: String?, pluginId: String): Boolean = sessionId != null && pluginIdsBySession[sessionId]?.contains(pluginId) == true

    companion object {
        val Empty = HeadlessPlugins(emptyMap())
    }
}
