package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that tracks MCP tools contributed by plugin instances that implement
 * [JetWhaleMcpCapablePlugin].
 *
 * A single tool entry covers all sessions that have the plugin installed. When a tool is
 * invoked, the caller must supply a `sessionId` argument so the registry can route the
 * call to the correct plugin instance.
 */
class McpToolRegistry(private val pluginInstanceService: PluginInstanceService) {

    /**
     * Maps a tool name to its descriptor and the set of (sessionId → pluginId) pairs
     * that currently have the tool active.
     */
    private val registrations: ConcurrentHashMap<String, PluginToolEntry> = ConcurrentHashMap()

    /**
     * Which plugins currently offer MCP tools, so the UI can mark them before an agent acts.
     */
    val mcpCapablePluginsFlow: StateFlow<McpCapablePlugins>
        field = MutableStateFlow(McpCapablePlugins.Empty)

    /**
     * Registers all MCP tools declared by a plugin instance.
     * Only called if the plugin implements [JetWhaleMcpCapablePlugin].
     */
    fun register(pluginId: String, sessionId: String, plugin: JetWhaleMcpCapablePlugin) {
        plugin.mcpCommands.forEach { command ->
            val entry = registrations.getOrPut(command.name) {
                PluginToolEntry(descriptor = command.toDescriptor(), sessionToPlugin = ConcurrentHashMap())
            }
            entry.sessionToPlugin[sessionId] = pluginId
        }
        publishCapablePlugins()
    }

    /**
     * Removes the given session from every tool entry.
     * Tool entries with no remaining sessions are cleaned up.
     */
    fun unregister(pluginId: String, sessionId: String) {
        registrations.entries.removeIf { (_, entry) ->
            if (entry.sessionToPlugin[sessionId] == pluginId) {
                entry.sessionToPlugin.remove(sessionId)
            }
            entry.sessionToPlugin.isEmpty()
        }
        publishCapablePlugins()
    }

    /**
     * Dispatches a tool call to the owning plugin instance.
     *
     * The [arguments] map must contain a `sessionId` key that identifies the target session.
     * That key is stripped before forwarding to the plugin.
     *
     * @return The command's result, or null if the call could not be routed to a plugin instance.
     */
    suspend fun dispatch(toolName: String, arguments: Map<String, JsonElement>): JetWhaleMcpResult? {
        val sessionId = (arguments["sessionId"] as? JsonPrimitive)?.content ?: return null
        val entry = registrations[toolName] ?: return null
        val pluginId = entry.sessionToPlugin[sessionId] ?: return null
        val plugin = pluginInstanceService.getPluginInstanceForSession(
            pluginId = pluginId,
            sessionId = sessionId,
        ) as? JetWhaleMcpCapablePlugin ?: return null
        val command = plugin.mcpCommands.firstOrNull { it.name == toolName } ?: return null
        return try {
            command.execute(JetWhaleMcpArguments(JsonObject(arguments - "sessionId")))
        } catch (e: JetWhaleMcpArgumentException) {
            // A caller mistake becomes a failed result the AI agent can read and correct, instead
            // of an MCP-level failure.
            JetWhaleMcpResult.error(e.message.orEmpty())
        }
    }

    /**
     * Resolves which plugin would handle [toolName] for [sessionId], without invoking it.
     * Used to attribute an in-flight tool call to a plugin for the AI activity indicator.
     */
    fun pluginIdFor(toolName: String, sessionId: String): String? = registrations[toolName]?.sessionToPlugin?.get(sessionId)

    /** Removes all registered plugin tools. Call on server stop to avoid stale entries on restart. */
    fun clear() {
        registrations.clear()
        publishCapablePlugins()
    }

    private fun publishCapablePlugins() {
        val toolsBySessionAndPlugin = mutableMapOf<String, MutableMap<String, MutableList<McpToolSummary>>>()
        registrations.forEach { (toolName, entry) ->
            val summary = McpToolSummary(
                name = toolName,
                description = entry.descriptor.description,
                parameters = entry.descriptor.parameters.map { (paramName, param) ->
                    McpToolParameterSummary(
                        name = paramName,
                        type = (param.schema["type"] as? JsonPrimitive)?.content.orEmpty(),
                        required = param.required,
                        description = param.description,
                    )
                },
            )
            entry.sessionToPlugin.forEach { (sessionId, pluginId) ->
                toolsBySessionAndPlugin
                    .getOrPut(sessionId) { mutableMapOf() }
                    .getOrPut(pluginId) { mutableListOf() }
                    .add(summary)
            }
        }
        mcpCapablePluginsFlow.value = McpCapablePlugins(
            toolsBySessionAndPlugin.mapValues { (_, byPlugin) ->
                byPlugin.mapValues { (_, tools) -> tools.sortedBy { it.name } }
            },
        )
    }

    /** Returns all tools that have at least one active session, with their descriptors. */
    fun allRegistrations(): List<Pair<String, JetWhaleMcpToolDescriptor>> = registrations.entries
        .filter { it.value.sessionToPlugin.isNotEmpty() }
        .map { (name, entry) -> name to entry.descriptor }
}

data class PluginToolEntry(
    val descriptor: JetWhaleMcpToolDescriptor,
    val sessionToPlugin: ConcurrentHashMap<String, String>,
)
