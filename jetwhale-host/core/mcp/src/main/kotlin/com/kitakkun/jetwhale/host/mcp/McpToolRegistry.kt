package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolParameterSummary
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginVersionOrder
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
 * call to the correct plugin instance. Sessions may run different versions of one plugin: the tool
 * is described by the newest version that registers it, and a call runs the version of the target
 * session.
 */
class McpToolRegistry(private val pluginInstanceService: PluginInstanceService) {

    /** Maps a tool name to the sessions that currently have it, each with the version that provides it. */
    private val registrations: ConcurrentHashMap<String, ConcurrentHashMap<String, ToolBinding>> = ConcurrentHashMap()

    private val publishLock = Any()

    /**
     * Which plugins currently offer MCP tools, so the UI can mark them before an agent acts.
     */
    val mcpCapablePluginsFlow: StateFlow<McpCapablePlugins>
        field = MutableStateFlow(McpCapablePlugins.Empty)

    /**
     * Registers all MCP tools declared by a plugin instance of [version].
     * Only called if the plugin implements [JetWhaleMcpCapablePlugin].
     */
    fun register(pluginId: String, sessionId: String, version: String, plugin: JetWhaleMcpCapablePlugin) = synchronized(publishLock) {
        plugin.mcpCommands.forEach { command ->
            registrations.getOrPut(command.name) { ConcurrentHashMap() }[sessionId] =
                ToolBinding(pluginId = pluginId, version = version, descriptor = command.toDescriptor())
        }
        publishCapablePlugins()
    }

    /**
     * Removes the given session from every tool entry.
     * Tool entries with no remaining sessions are cleaned up.
     */
    fun unregister(pluginId: String, sessionId: String) = synchronized(publishLock) {
        registrations.entries.removeIf { (_, bindings) ->
            if (bindings[sessionId]?.pluginId == pluginId) {
                bindings.remove(sessionId)
            }
            bindings.isEmpty()
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
     * payload that names the sessions the tool is available in. A session that runs a version of the
     * plugin without the tool is answered with one that names that version.
     *
     * @return The result string, or null if not found or plugin returned null.
     */
    suspend fun dispatch(toolName: String, arguments: Map<String, JsonElement>): String? {
        val bindings = registrations[toolName] ?: return null
        val sessionId = (arguments["sessionId"] as? JsonPrimitive)?.content
            ?: return errorPayload("'sessionId' is required: the session to run '$toolName' in (available in: ${bindings.availableSessions()}).")
        val binding = bindings[sessionId]
        if (binding == null) {
            val (pluginId, boundVersion) = otherVersionBoundTo(sessionId, bindings.values)
                ?: return errorPayload("'$toolName' is not available in session '$sessionId' (available in: ${bindings.availableSessions()}).")
            return errorPayload(
                "Plugin '$pluginId' $boundVersion, which session '$sessionId' runs, does not provide '$toolName'; " +
                    "it is listed from ${bindings.values.newest().version}.",
            )
        }
        val plugin = pluginInstanceService.getPluginInstanceForSession(
            pluginId = binding.pluginId,
            sessionId = sessionId,
        ) as? JetWhaleMcpCapablePlugin ?: return null
        val command = plugin.mcpCommands.firstOrNull { it.name == toolName } ?: return null
        return try {
            command.execute(JetWhaleMcpArguments(JsonObject(arguments - "sessionId")))
        } catch (e: JetWhaleMcpArgumentException) {
            val listedVersion = bindings.values.newest().version
            val versionNote = if (listedVersion == binding.version) "" else " (session '$sessionId' runs ${binding.pluginId} ${binding.version}; the listed schema is from $listedVersion)"
            errorPayload(e.message.orEmpty() + versionNote)
        }
    }

    /**
     * Resolves which plugin would handle [toolName] for [sessionId], without invoking it.
     * Used to attribute an in-flight tool call to a plugin for the AI activity indicator.
     */
    fun pluginIdFor(toolName: String, sessionId: String): String? {
        val bindings = registrations[toolName] ?: return null
        return bindings[sessionId]?.pluginId ?: otherVersionBoundTo(sessionId, bindings.values)?.first
    }

    /**
     * The plugin id and version [sessionId] runs, when it runs a version of a plugin in [bindings]
     * that does not provide the tool.
     */
    private fun otherVersionBoundTo(sessionId: String, bindings: Collection<ToolBinding>): Pair<String, String>? {
        val boundVersions = pluginInstanceService.boundVersionsFlow.value
        return bindings.map(ToolBinding::pluginId).distinct().firstNotNullOfOrNull { pluginId ->
            boundVersions.versionOf(sessionId, pluginId)?.let { pluginId to it }
        }
    }

    private fun errorPayload(message: String): String = buildJsonObject { put("error", message) }.toString()

    // toSortedSet() copies the live view in one pass; sorted() on a single session reads the size,
    // then the key, and throws if the session leaves in between.
    private fun Map<String, ToolBinding>.availableSessions(): String = keys.toSortedSet().joinToString().ifEmpty { "no session" }

    /** Removes all registered plugin tools. Call on server stop to avoid stale entries on restart. */
    fun clear() = synchronized(publishLock) {
        registrations.clear()
        publishCapablePlugins()
    }

    private fun publishCapablePlugins() {
        val toolsBySessionAndPlugin = mutableMapOf<String, MutableMap<String, MutableList<McpToolSummary>>>()
        registrations.forEach { (toolName, bindings) ->
            if (bindings.isEmpty()) return@forEach
            val descriptor = bindings.values.newest().descriptor
            val summary = McpToolSummary(
                name = toolName,
                description = descriptor.description,
                parameters = descriptor.parameters.map { (paramName, param) ->
                    McpToolParameterSummary(
                        name = paramName,
                        type = (param.schema["type"] as? JsonPrimitive)?.content.orEmpty(),
                        required = param.required,
                        description = param.description,
                    )
                },
            )
            bindings.forEach { (sessionId, binding) ->
                toolsBySessionAndPlugin
                    .getOrPut(sessionId) { mutableMapOf() }
                    .getOrPut(binding.pluginId) { mutableListOf() }
                    .add(summary)
            }
        }
        mcpCapablePluginsFlow.value = McpCapablePlugins(
            toolsBySessionAndPlugin.mapValues { (_, byPlugin) ->
                byPlugin.mapValues { (_, tools) -> tools.sortedBy(McpToolSummary::name) }
            },
        )
    }

    /** Returns all tools that have at least one active session, each with its newest version's descriptor. */
    fun allRegistrations(): List<Pair<String, JetWhaleMcpToolDescriptor>> = registrations.entries
        .filter { it.value.isNotEmpty() }
        .map { (name, bindings) -> name to bindings.values.newest().descriptor }
}

private class ToolBinding(
    val pluginId: String,
    val version: String,
    val descriptor: JetWhaleMcpToolDescriptor,
)

private fun Collection<ToolBinding>.newest(): ToolBinding = maxWith(compareBy(PluginVersionOrder, ToolBinding::version))
