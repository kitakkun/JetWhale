package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that tracks MCP tools contributed by plugin instances that implement
 * [JetWhaleMcpCapablePlugin].
 *
 * A single tool entry covers all sessions that have the plugin installed. When a tool is
 * invoked, the caller must supply a `sessionId` argument so the registry can route the
 * call to the correct plugin instance.
 */
@OptIn(ExperimentalJetWhaleApi::class)
class McpToolRegistry(private val pluginInstanceService: PluginInstanceService) {

    /**
     * Maps a tool name to its descriptor and the set of (sessionId → pluginId) pairs
     * that currently have the tool active.
     */
    private val registrations: ConcurrentHashMap<String, PluginToolEntry> = ConcurrentHashMap()

    /**
     * Registers all MCP tools declared by a plugin instance.
     * Only called if the plugin implements [JetWhaleMcpCapablePlugin].
     */
    fun register(pluginId: String, sessionId: String, plugin: JetWhaleMcpCapablePlugin) {
        plugin.mcpTools().forEach { descriptor ->
            val entry = registrations.getOrPut(descriptor.name) {
                PluginToolEntry(descriptor = descriptor, sessionToPlugin = ConcurrentHashMap())
            }
            entry.sessionToPlugin[sessionId] = pluginId
        }
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
    }

    /**
     * Dispatches a tool call to the owning plugin instance.
     *
     * The [arguments] map must contain a `sessionId` key that identifies the target session.
     * That key is stripped before forwarding to the plugin.
     *
     * @return The result string, or null if not found or plugin returned null.
     */
    suspend fun dispatch(toolName: String, arguments: Map<String, String>): String? {
        val sessionId = arguments["sessionId"] ?: return null
        val entry = registrations[toolName] ?: return null
        val pluginId = entry.sessionToPlugin[sessionId] ?: return null
        val plugin = pluginInstanceService.getPluginInstanceForSession(
            pluginId = pluginId,
            sessionId = sessionId,
        ) as? JetWhaleMcpCapablePlugin ?: return null
        return plugin.handleMcpTool(toolName, arguments - "sessionId")
    }

    /** Returns all tools that have at least one active session, with their descriptors. */
    fun allRegistrations(): List<Pair<String, JetWhaleMcpToolDescriptor>> = registrations.entries
        .filter { it.value.sessionToPlugin.isNotEmpty() }
        .map { (name, entry) -> name to entry.descriptor }
}

@OptIn(ExperimentalJetWhaleApi::class)
data class PluginToolEntry(
    val descriptor: JetWhaleMcpToolDescriptor,
    val sessionToPlugin: ConcurrentHashMap<String, String>,
)
