package com.kitakkun.jetwhale.host.sdk

/**
 * Optional interface that a [JetWhaleRawHostPlugin] can implement to
 * advertise plugin-specific MCP tools to the MCP server.
 *
 * The MCP server queries all active plugin instances for this interface
 * after each session negotiation and registers the returned tool descriptors.
 * When a tool is invoked, [handleMcpTool] is called on the correct
 * plugin instance (keyed by pluginId + sessionId).
 *
 * Usage:
 * ```kotlin
 * class MyHostPlugin : JetWhaleHostPlugin<...>(), JetWhaleMcpCapablePlugin {
 *     override fun mcpTools() = listOf(
 *         JetWhaleMcpToolDescriptor(
 *             name = "com.example.myplugin.inspectWidget",
 *             description = "Inspect the selected widget",
 *             parameters = mapOf(
 *                 "widgetId" to JetWhaleMcpParameterDescriptor("string", "The widget ID")
 *             )
 *         )
 *     )
 *
 *     override suspend fun handleMcpTool(toolName: String, arguments: Map<String, String>): String? {
 *         return when (toolName) {
 *             "com.example.myplugin.inspectWidget" -> {
 *                 val widgetId = arguments["widgetId"] ?: return null
 *                 // dispatch to debuggee via context.dispatch(...) or return local data
 *                 """{"widget": "$widgetId", "type": "Button"}"""
 *             }
 *             else -> null
 *         }
 *     }
 * }
 * ```
 */
public interface JetWhaleMcpCapablePlugin {
    /**
     * Returns the list of MCP tool descriptors this plugin exposes.
     * Called once per plugin instance activation; the list is treated
     * as static for the lifetime of the plugin instance.
     *
     * Tool names must be globally unique; by convention prefix with the
     * pluginId, e.g. "com.example.myplugin.inspectWidget".
     */
    public fun mcpTools(): List<JetWhaleMcpToolDescriptor>

    /**
     * Called by the MCP server when a tool registered by this plugin is invoked.
     *
     * @param toolName  The exact name returned in [mcpTools].
     * @param arguments Map of argument name to JSON value string.
     * @return          A result string (plain text or JSON); null means no result.
     */
    public suspend fun handleMcpTool(
        toolName: String,
        arguments: Map<String, String>,
    ): String?
}

/**
 * Describes a single MCP tool contributed by a plugin.
 *
 * @param name        Unique tool name (no spaces; use dots as separators).
 * @param description Human-readable description shown to the AI agent.
 * @param parameters  Parameter descriptors keyed by parameter name.
 */
public data class JetWhaleMcpToolDescriptor(
    val name: String,
    val description: String,
    val parameters: Map<String, JetWhaleMcpParameterDescriptor> = emptyMap(),
)

/**
 * Describes a single parameter of an MCP tool.
 *
 * @param type        JSON Schema primitive type: "string", "number", "boolean", "integer".
 * @param description Human-readable description of the parameter.
 * @param required    Whether the parameter is required. Defaults to true.
 */
public data class JetWhaleMcpParameterDescriptor(
    val type: String,
    val description: String,
    val required: Boolean = true,
)
