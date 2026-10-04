package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
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
    fun unregister(pluginId: String, sessionId: String) = synchronized(publishLock) {
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
     * A missing `sessionId`, or a session the tool is not offered in, is answered with an error
     * payload that names the sessions the tool is available in.
     *
     * @return The result string, or null if not found or plugin returned null.
     */
    suspend fun dispatch(toolName: String, arguments: Map<String, JsonElement>): String? {
        val entry = registrations[toolName] ?: return null
        val sessionId = (arguments["sessionId"] as? JsonPrimitive)?.content
            ?: return errorPayload("'sessionId' is required: the session to run '$toolName' in (available in: ${entry.availableSessions()}).")
        val pluginId = entry.sessionToPlugin[sessionId]
            ?: return errorPayload("'$toolName' is not available in session '$sessionId' (available in: ${entry.availableSessions()}).")
        val plugin = pluginInstanceService.getPluginInstanceForSession(
            pluginId = pluginId,
            sessionId = sessionId,
        ) as? JetWhaleMcpCapablePlugin ?: return null
        val command = plugin.mcpCommands.firstOrNull { it.name == toolName } ?: return null
        return try {
            command.execute(JetWhaleMcpArguments(JsonObject(arguments - "sessionId")))
        } catch (e: JetWhaleMcpArgumentException) {
            errorPayload(e.message.orEmpty())
        }
    }

    /**
     * Resolves which plugin would handle [toolName] for [sessionId], without invoking it.
     * Used to attribute an in-flight tool call to a plugin for the AI activity indicator.
     */
    fun pluginIdFor(toolName: String, sessionId: String): String? = registrations[toolName]?.sessionToPlugin?.get(sessionId)

    private fun errorPayload(message: String): String = buildJsonObject { put("error", message) }.toString()

    // Not sorted(): for a single session it reads the size and then the key, and throws when the
    // session unregisters in between. toSortedSet() copies in a single pass.
    private fun PluginToolEntry.availableSessions(): String = sessionToPlugin.keys.toSortedSet().joinToString().ifEmpty { "no session" }

    /** Removes all registered plugin tools. Call on server stop to avoid stale entries on restart. */
    fun clear() = synchronized(publishLock) {
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
                byPlugin.mapValues { (_, tools) -> tools.sortedBy(McpToolSummary::name) }
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
