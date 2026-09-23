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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that tracks MCP tools contributed by plugin instances that implement
 * [JetWhaleMcpCapablePlugin].
 *
 * A session-scoped plugin's tool entry covers all sessions that have the plugin installed, so an
 * invocation must supply a `sessionId` argument for the registry to route the call to the right
 * instance. A host-scoped plugin has a single instance and no session, so its tools are held
 * separately and dispatched on the tool name alone.
 */
class McpToolRegistry(private val pluginInstanceService: PluginInstanceService) {

    /**
     * Maps a tool name to its descriptor and the set of (sessionId → pluginId) pairs
     * that currently have the tool active.
     */
    private val registrations: ConcurrentHashMap<String, PluginToolEntry> = ConcurrentHashMap()

    /** Maps a host-scoped plugin's tool name to its descriptor and the single plugin that owns it. */
    private val hostScopedRegistrations: ConcurrentHashMap<String, HostScopedToolEntry> = ConcurrentHashMap()

    /**
     * Which plugins currently offer MCP tools, so the UI can mark them before an agent acts.
     */
    val mcpCapablePluginsFlow: StateFlow<McpCapablePlugins>
        field = MutableStateFlow(McpCapablePlugins.Empty)

    /**
     * Registers all MCP tools declared by a plugin instance.
     * Only called if the plugin implements [JetWhaleMcpCapablePlugin].
     *
     * @param sessionId The session the instance belongs to, or null for a host-scoped plugin.
     */
    fun register(pluginId: String, sessionId: String?, plugin: JetWhaleMcpCapablePlugin) {
        plugin.mcpCommands.forEach { command ->
            if (sessionId == null) {
                hostScopedRegistrations[command.name] = HostScopedToolEntry(descriptor = command.toDescriptor(), pluginId = pluginId)
            } else {
                val entry = registrations.getOrPut(command.name) {
                    PluginToolEntry(descriptor = command.toDescriptor(), sessionToPlugin = ConcurrentHashMap())
                }
                entry.sessionToPlugin[sessionId] = pluginId
            }
        }
        publishCapablePlugins()
    }

    /**
     * Removes the given session from every tool entry.
     * Tool entries with no remaining sessions are cleaned up.
     *
     * @param sessionId The session the instance belonged to, or null for a host-scoped plugin, whose
     *   tools are dropped outright.
     */
    fun unregister(pluginId: String, sessionId: String?) {
        if (sessionId == null) {
            hostScopedRegistrations.entries.removeIf { (_, entry) -> entry.pluginId == pluginId }
        } else {
            registrations.entries.removeIf { (_, entry) ->
                if (entry.sessionToPlugin[sessionId] == pluginId) {
                    entry.sessionToPlugin.remove(sessionId)
                }
                entry.sessionToPlugin.isEmpty()
            }
        }
        publishCapablePlugins()
    }

    /**
     * Dispatches a tool call to the owning plugin instance.
     *
     * For a session-scoped tool the [arguments] map must contain a `sessionId` key that identifies
     * the target session; that key is stripped before forwarding to the plugin. A host-scoped tool
     * takes no `sessionId` and goes straight to the plugin's single instance.
     *
     * @return The result, or null if not found.
     */
    suspend fun dispatch(toolName: String, arguments: Map<String, JsonElement>): JetWhaleMcpResult? {
        val hostScoped = hostScopedRegistrations[toolName]
        if (hostScoped != null) {
            val plugin = pluginInstanceService.getHostScopedInstance(hostScoped.pluginId) as? JetWhaleMcpCapablePlugin ?: return null
            return execute(plugin, toolName, JsonObject(arguments))
        }
        val sessionId = (arguments["sessionId"] as? JsonPrimitive)?.content ?: return null
        val entry = registrations[toolName] ?: return null
        val pluginId = entry.sessionToPlugin[sessionId] ?: return null
        val plugin = pluginInstanceService.getPluginInstanceForSession(
            pluginId = pluginId,
            sessionId = sessionId,
        ) as? JetWhaleMcpCapablePlugin ?: return null
        return execute(plugin, toolName, JsonObject(arguments - "sessionId"))
    }

    private suspend fun execute(plugin: JetWhaleMcpCapablePlugin, toolName: String, arguments: JsonObject): JetWhaleMcpResult? {
        val command = plugin.mcpCommands.firstOrNull { it.name == toolName } ?: return null
        return try {
            command.execute(JetWhaleMcpArguments(arguments))
        } catch (e: JetWhaleMcpArgumentException) {
            // A caller mistake becomes a payload the AI agent can read and correct, instead of
            // an MCP-level failure.
            JetWhaleMcpResult.text(buildJsonObject { put("error", e.message.orEmpty()) }.toString())
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
        hostScopedRegistrations.clear()
        publishCapablePlugins()
    }

    private fun publishCapablePlugins() {
        val toolsBySessionAndPlugin = mutableMapOf<String, MutableMap<String, MutableList<McpToolSummary>>>()
        registrations.forEach { (toolName, entry) ->
            val summary = entry.descriptor.toSummary(toolName)
            entry.sessionToPlugin.forEach { (sessionId, pluginId) ->
                toolsBySessionAndPlugin
                    .getOrPut(sessionId) { mutableMapOf() }
                    .getOrPut(pluginId) { mutableListOf() }
                    .add(summary)
            }
        }
        val hostScopedToolsByPlugin = mutableMapOf<String, MutableList<McpToolSummary>>()
        hostScopedRegistrations.forEach { (toolName, entry) ->
            hostScopedToolsByPlugin.getOrPut(entry.pluginId) { mutableListOf() }.add(entry.descriptor.toSummary(toolName))
        }
        mcpCapablePluginsFlow.value = McpCapablePlugins(
            toolsBySessionAndPlugin = toolsBySessionAndPlugin.mapValues { (_, byPlugin) ->
                byPlugin.mapValues { (_, tools) -> tools.sortedBy { it.name } }
            },
            hostScopedToolsByPlugin = hostScopedToolsByPlugin.mapValues { (_, tools) -> tools.sortedBy { it.name } },
        )
    }

    /** Returns all session-scoped tools that have at least one active session, with their descriptors. */
    fun allRegistrations(): List<Pair<String, JetWhaleMcpToolDescriptor>> = registrations.entries
        .filter { it.value.sessionToPlugin.isNotEmpty() }
        .map { (name, entry) -> name to entry.descriptor }

    /** Returns every tool published by a host-scoped plugin instance, with the plugin that owns it. */
    fun allHostScopedRegistrations(): List<HostScopedRegistration> = hostScopedRegistrations.entries
        .map { (name, entry) -> HostScopedRegistration(name = name, pluginId = entry.pluginId, descriptor = entry.descriptor) }
}

private fun JetWhaleMcpToolDescriptor.toSummary(toolName: String): McpToolSummary = McpToolSummary(
    name = toolName,
    description = description,
    parameters = parameters.map { (paramName, param) ->
        McpToolParameterSummary(
            name = paramName,
            type = (param.schema["type"] as? JsonPrimitive)?.content.orEmpty(),
            required = param.required,
            description = param.description,
        )
    },
)

data class PluginToolEntry(
    val descriptor: JetWhaleMcpToolDescriptor,
    val sessionToPlugin: ConcurrentHashMap<String, String>,
)

data class HostScopedToolEntry(
    val descriptor: JetWhaleMcpToolDescriptor,
    val pluginId: String,
)

/** One tool of a host-scoped plugin, as the MCP server registers it: no session, one owning plugin. */
data class HostScopedRegistration(
    val name: String,
    val pluginId: String,
    val descriptor: JetWhaleMcpToolDescriptor,
)
