package com.kitakkun.jetwhale.host.model

/**
 * Plugins that currently expose their own MCP tools, keyed by the session they are active in.
 *
 * This covers plugin-contributed semantic tools only. The built-in UI tools (click, type, scroll,
 * ...) can drive any plugin's UI regardless, so absence here does not mean an agent cannot reach
 * the plugin at all — only that the plugin publishes nothing of its own.
 */
data class McpCapablePlugins(val pluginIdsBySessionId: Map<String, Set<String>>) {
    fun pluginIdsFor(sessionId: String?): Set<String> = sessionId?.let { pluginIdsBySessionId[it] }.orEmpty()

    companion object {
        val Empty = McpCapablePlugins(emptyMap())
    }
}
