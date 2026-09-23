package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import kotlinx.serialization.json.JsonObject

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
 * @property name        Unique tool name (no spaces; use dots as separators).
 * @property description Human-readable description shown to the AI agent.
 * @property parameters  Parameter descriptors keyed by parameter name.
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpToolDescriptor(
    public val name: String,
    public val description: String,
    public val parameters: Map<String, JetWhaleMcpParameterDescriptor> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean = other is JetWhaleMcpToolDescriptor &&
        name == other.name &&
        description == other.description &&
        parameters == other.parameters

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + parameters.hashCode()
        return result
    }

    override fun toString(): String = "JetWhaleMcpToolDescriptor(name=$name, description=$description, parameters=$parameters)"
}

/**
 * Describes a single parameter of an MCP tool.
 *
 * @property schema      JSON Schema fragment for the values the parameter accepts, e.g.
 *                    `{"type":"string"}` or a full nested object schema. It carries no
 *                    "description" of its own; the MCP server merges [description] in when it
 *                    assembles the tool's input schema.
 * @property description Human-readable description of the parameter.
 * @property required    Whether the parameter is required. Defaults to true.
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpParameterDescriptor(
    public val schema: JsonObject,
    public val description: String,
    public val required: Boolean = true,
) {
    override fun equals(other: Any?): Boolean = other is JetWhaleMcpParameterDescriptor &&
        schema == other.schema &&
        description == other.description &&
        required == other.required

    override fun hashCode(): Int {
        var result = schema.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + required.hashCode()
        return result
    }

    override fun toString(): String = "JetWhaleMcpParameterDescriptor(schema=$schema, " +
        "description=$description, required=$required)"
}
