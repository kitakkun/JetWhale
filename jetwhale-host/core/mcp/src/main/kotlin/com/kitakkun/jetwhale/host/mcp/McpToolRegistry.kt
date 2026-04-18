package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that tracks MCP tools contributed by plugin instances that implement
 * [JetWhaleMcpCapablePlugin].
 *
 * Tool names are scoped per (pluginId, sessionId) pair to avoid collisions when
 * multiple sessions have the same plugin installed. The name exposed to AI agents
 * appends a 6-character sessionId prefix: `<originalName>.<sessionId.take(6)>`.
 */
class McpToolRegistry(private val pluginInstanceService: PluginInstanceService) {

    /**
     * Maps a scoped tool name (including session suffix) to the (pluginId, sessionId) pair
     * that owns it, along with the original [JetWhaleMcpToolDescriptor].
     */
    private val registrations: ConcurrentHashMap<String, PluginToolRegistration> = ConcurrentHashMap()

    /**
     * Registers all MCP tools declared by a plugin instance.
     * Only called if the plugin implements [JetWhaleMcpCapablePlugin].
     *
     * @return The list of scoped tool descriptors that were registered.
     */
    fun register(
        pluginId: String,
        sessionId: String,
        plugin: JetWhaleMcpCapablePlugin,
    ): List<ScopedToolDescriptor> {
        val sessionSuffix = sessionId.take(6)
        return plugin.mcpTools().map { descriptor ->
            val scopedName = "${descriptor.name}.$sessionSuffix"
            registrations[scopedName] = PluginToolRegistration(
                pluginId = pluginId,
                sessionId = sessionId,
                originalDescriptor = descriptor,
            )
            ScopedToolDescriptor(scopedName = scopedName, original = descriptor)
        }
    }

    /**
     * Removes all MCP tools registered for the given (pluginId, sessionId) pair.
     *
     * @return The scoped tool names that were removed.
     */
    fun unregister(pluginId: String, sessionId: String): List<String> {
        val removed = mutableListOf<String>()
        registrations.entries.removeIf { (name, reg) ->
            if (reg.pluginId == pluginId && reg.sessionId == sessionId) {
                removed += name
                true
            } else {
                false
            }
        }
        return removed
    }

    /**
     * Dispatches a tool call to the owning plugin instance.
     *
     * @return The result string, or null if not found or plugin returned null.
     */
    suspend fun dispatch(toolName: String, arguments: Map<String, String>): String? {
        val registration = registrations[toolName] ?: return null
        val plugin = pluginInstanceService.getPluginInstanceForSession(
            pluginId = registration.pluginId,
            sessionId = registration.sessionId,
        ) as? JetWhaleMcpCapablePlugin ?: return null
        return plugin.handleMcpTool(toolName, arguments)
    }

    /** Returns all currently registered tool names (scoped). */
    fun registeredToolNames(): Set<String> = registrations.keys.toSet()

    /** Returns all currently registered scoped descriptors with their original metadata. */
    fun allRegistrations(): List<Pair<String, JetWhaleMcpToolDescriptor>> = registrations.entries.map { (name, reg) -> name to reg.originalDescriptor }
}

data class PluginToolRegistration(
    val pluginId: String,
    val sessionId: String,
    val originalDescriptor: JetWhaleMcpToolDescriptor,
)

data class ScopedToolDescriptor(
    val scopedName: String,
    val original: JetWhaleMcpToolDescriptor,
)
