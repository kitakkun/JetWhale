package com.kitakkun.jetwhale.host.model

/** One input parameter of an MCP tool. [type] is best-effort from the parameter's JSON schema. */
data class McpToolParameterSummary(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String,
)

/** One MCP tool a plugin exposes, for listing in the UI. */
data class McpToolSummary(
    val name: String,
    val description: String,
    val parameters: List<McpToolParameterSummary>,
)

/**
 * Plugins that currently expose their own MCP tools, keyed by the session they are active in and
 * then by plugin id, with the tools each one publishes.
 *
 * This covers plugin-contributed semantic tools only. The built-in UI tools (click, type, scroll,
 * ...) can drive any plugin's UI regardless, so absence here does not mean an agent cannot reach
 * the plugin at all — only that the plugin publishes nothing of its own.
 */
data class McpCapablePlugins(val toolsBySessionAndPlugin: Map<String, Map<String, List<McpToolSummary>>>) {
    fun pluginIdsFor(sessionId: String?): Set<String> = sessionId?.let { toolsBySessionAndPlugin[it]?.keys }.orEmpty()

    fun toolsFor(sessionId: String?, pluginId: String): List<McpToolSummary> = sessionId?.let { toolsBySessionAndPlugin[it]?.get(pluginId) }.orEmpty()

    companion object {
        val Empty = McpCapablePlugins(emptyMap())
    }
}
