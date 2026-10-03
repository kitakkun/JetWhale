package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
     *
     * Readers take no lock: every change builds a new map and swaps it in with one write, so a
     * reader sees the tools from before a change or after it, never a plugin halfway through being
     * replaced.
     */
    @Volatile
    private var registrations: Map<String, PluginToolEntry> = emptyMap()

    private val publishLock = Any()

    /**
     * Which plugins currently offer MCP tools, so the UI can mark them before an agent acts.
     */
    val mcpCapablePluginsFlow: StateFlow<McpCapablePlugins>
        field = MutableStateFlow(McpCapablePlugins.Empty)

    /**
     * Registers all MCP tools declared by a plugin instance.
     * Only called if the plugin implements [JetWhaleMcpCapablePlugin].
     */
    fun register(pluginId: String, sessionId: String, plugin: JetWhaleMcpCapablePlugin) = synchronized(publishLock) {
        registrations = registrations.withTools(pluginId, sessionId, plugin.mcpCommands)
        publishCapablePlugins()
    }

    /**
     * Makes [plugin]'s tools the ones registered for [pluginId] in [sessionId], dropping any it had
     * before; null removes them. Tool entries left with no session are cleaned up.
     */
    fun replace(pluginId: String, sessionId: String, plugin: JetWhaleMcpCapablePlugin?) = synchronized(publishLock) {
        registrations = registrations
            .withoutTools(pluginId, sessionId)
            .withTools(pluginId, sessionId, plugin?.mcpCommands.orEmpty())
        publishCapablePlugins()
    }

    /**
     * Dispatches a tool call to the owning plugin instance.
     *
     * The [arguments] map must contain a `sessionId` key that identifies the target session.
     * That key is stripped before forwarding to the plugin.
     *
     * @return The result string, or null if not found or plugin returned null.
     */
    suspend fun dispatch(toolName: String, arguments: Map<String, JsonElement>): String? {
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
            buildJsonObject { put("error", e.message.orEmpty()) }.toString()
        }
    }

    /**
     * Resolves which plugin would handle [toolName] for [sessionId], without invoking it.
     * Used to attribute an in-flight tool call to a plugin for the AI activity indicator.
     */
    fun pluginIdFor(toolName: String, sessionId: String): String? = registrations[toolName]?.sessionToPlugin?.get(sessionId)

    /** Removes all registered plugin tools. Call on server stop to avoid stale entries on restart. */
    fun clear() = synchronized(publishLock) {
        registrations = emptyMap()
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
                byPlugin.mapValues { (_, tools) -> tools.sortedBy(McpToolSummary::name) }
            },
        )
    }

    /** Returns all tools that have at least one active session, with their descriptors. */
    fun allRegistrations(): List<Pair<String, JetWhaleMcpToolDescriptor>> = registrations.map { (name, entry) -> name to entry.descriptor }

    private fun Map<String, PluginToolEntry>.withTools(
        pluginId: String,
        sessionId: String,
        commands: List<JetWhaleMcpCommand>,
    ): Map<String, PluginToolEntry> = toMutableMap().apply {
        commands.forEach { command ->
            val entry = get(command.name) ?: PluginToolEntry(descriptor = command.toDescriptor(), sessionToPlugin = emptyMap())
            put(command.name, entry.copy(sessionToPlugin = entry.sessionToPlugin + (sessionId to pluginId)))
        }
    }

    private fun Map<String, PluginToolEntry>.withoutTools(pluginId: String, sessionId: String): Map<String, PluginToolEntry> = mapValues { (_, entry) ->
        if (entry.sessionToPlugin[sessionId] == pluginId) entry.copy(sessionToPlugin = entry.sessionToPlugin - sessionId) else entry
    }.filterValues { it.sessionToPlugin.isNotEmpty() }
}

data class PluginToolEntry(
    val descriptor: JetWhaleMcpToolDescriptor,
    val sessionToPlugin: Map<String, String>,
)
