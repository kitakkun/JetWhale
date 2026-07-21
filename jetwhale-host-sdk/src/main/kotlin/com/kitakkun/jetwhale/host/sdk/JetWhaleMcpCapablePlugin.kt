package com.kitakkun.jetwhale.host.sdk

/**
 * Optional interface that a [JetWhaleHostPlugin] can implement to advertise plugin-specific MCP
 * tools to the MCP server, as a list of [JetWhaleMcpCommand]s.
 *
 * The MCP server queries all active plugin instances for this interface as sessions come up,
 * registers each command's descriptor, and dispatches invocations to the matching command on the
 * correct plugin instance (keyed by pluginId + sessionId). A [JetWhaleMcpArgumentException]
 * thrown by a command is rendered as an `{"error": ...}` payload instead of failing the server.
 *
 * Usage:
 * ```kotlin
 * class MyHostPlugin : JetWhaleHostPlugin(), JetWhaleMcpCapablePlugin {
 *     override val mcpCommands = listOf(InspectWidgetCommand(widgetStore))
 * }
 * ```
 * See [JetWhaleMcpCommand] for how to implement a command.
 */
@ExperimentalJetWhaleApi
public interface JetWhaleMcpCapablePlugin {
    /**
     * The commands this plugin exposes. Read once per plugin instance activation; the list is
     * treated as static for the lifetime of the plugin instance.
     *
     * Command names must be globally unique; by convention prefix with the pluginId,
     * e.g. "com.example.myplugin.inspectWidget".
     */
    public val mcpCommands: List<JetWhaleMcpCommand>
}

/**
 * Describes a single MCP tool contributed by a plugin.
 *
 * @param name        Unique tool name (no spaces; use dots as separators).
 * @param description Human-readable description shown to the AI agent.
 * @param parameters  Parameter descriptors keyed by parameter name.
 */
@ExperimentalJetWhaleApi
public data class JetWhaleMcpToolDescriptor(
    val name: String,
    val description: String,
    val parameters: Map<String, JetWhaleMcpParameterDescriptor> = emptyMap(),
)

/**
 * Describes a single parameter of an MCP tool.
 *
 * @param type        JSON Schema type: "string", "number", "boolean", "integer", "object", "array".
 * @param description Human-readable description of the parameter.
 * @param required    Whether the parameter is required. Defaults to true.
 * @param itemsType   For [type] "array": the JSON Schema type of each element, or null when the
 *                    element type is unconstrained.
 * @param valueType   For [type] "object": the JSON Schema type of each value, or null when the
 *                    value type is unconstrained.
 */
@ExperimentalJetWhaleApi
public data class JetWhaleMcpParameterDescriptor(
    val type: String,
    val description: String,
    val required: Boolean = true,
    val itemsType: String? = null,
    val valueType: String? = null,
)
