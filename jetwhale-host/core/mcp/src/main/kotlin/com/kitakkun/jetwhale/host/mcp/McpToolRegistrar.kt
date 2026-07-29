package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpToolPermission
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

/**
 * Registers MCP tools on a [Server], wrapping every handler so the call is recorded in
 * [McpActivityRepository] for as long as it runs.
 *
 * Built-in tools and plugin-contributed tools both register through here, which makes this the one
 * place that knows an AI agent is currently acting on the debugger.
 */
class McpToolRegistrar(
    private val server: Server,
    private val activityRepository: McpActivityRepository,
    private val permissionsRepository: McpPermissionsRepository,
) {
    /**
     * Registers a built-in tool, attributed to whatever `pluginId` the caller passed in.
     */
    fun addTool(
        name: String,
        description: String,
        inputSchema: ToolSchema,
        permission: McpToolPermission,
        handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult,
    ) {
        addTrackedTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
            permission = permission,
            // Tools that drive a plugin UI declare this argument; the rest report no target.
            resolvePluginId = { request -> request.arguments?.get("pluginId")?.jsonContent },
            handler = handler,
        )
    }

    /**
     * Registers a tool contributed by a plugin.
     *
     * Plugin tool schemas only carry `sessionId` — the owning plugin is an implementation detail the
     * agent never names — so attribution has to be resolved from the session instead of read off the
     * arguments. Without this the plugin's own tools would be the only ones the UI cannot attribute.
     */
    fun addPluginTool(
        name: String,
        description: String,
        inputSchema: ToolSchema,
        resolvePluginIdForSession: (sessionId: String) -> String?,
        handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult,
    ) {
        addTrackedTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
            permission = McpToolPermission.PluginTool(name),
            resolvePluginId = { request ->
                request.arguments?.get("sessionId")?.jsonContent?.let(resolvePluginIdForSession)
            },
            handler = handler,
        )
    }

    private fun addTrackedTool(
        name: String,
        description: String,
        inputSchema: ToolSchema,
        permission: McpToolPermission,
        resolvePluginId: (CallToolRequest) -> String?,
        handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult,
    ) {
        // A host group is decidable now, so a denied one is not listed at all rather than listed and
        // refused. Per-plugin permissions are not: the same tool is allowed for one plugin and denied
        // for another, so those stay listed and are settled per call.
        if (permission is McpToolPermission.HostGroup &&
            !permissionsRepository.permissionsFlow.value.allows(permission, pluginId = null)
        ) {
            return
        }

        server.addTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
        ) { request ->
            val targetPluginId = resolvePluginId(request)
            // Checked per call, not only when the tool list was built: a tool list is fixed for the
            // life of an SSE connection, so a permission revoked now has to bite the agent that is
            // already connected.
            if (!permissionsRepository.permissionsFlow.value.allows(permission, targetPluginId)) {
                return@addTool deniedResult(name, permission, targetPluginId)
            }
            val invocationId = activityRepository.toolInvocationStarted(
                toolName = name,
                pluginId = targetPluginId,
                sessionId = request.arguments?.get("sessionId")?.jsonContent,
                // Every argument key is reported, including the ones the UI already shows as
                // attribution, so history describes the call exactly as the agent made it.
                arguments = request.arguments.orEmpty().mapValues { (_, value) ->
                    // Primitives render without the surrounding JSON quoting; objects and arrays
                    // fall back to their JSON form.
                    value.jsonContent ?: value.toString()
                },
            )
            // A tool fails in two ways: the handler throws, or it returns a result flagged with
            // `isError`, which is how the protocol wants a tool-level failure reported. Both have to
            // reach the repository before the call leaves here.
            var failed = true
            var response = ""
            try {
                handler(request).also {
                    failed = it.isError == true
                    response = it.renderForHistory()
                }
            } catch (throwable: Throwable) {
                // A failure is only explainable in history if it says what went wrong.
                response = throwable.message.orEmpty()
                throw throwable
            } finally {
                activityRepository.toolInvocationFinished(invocationId, failed, response)
            }
        }
    }
}

/**
 * Explains a refusal in terms of the switch that lifts it, so an agent can tell the user what to
 * turn on instead of reporting an opaque failure and stopping.
 */
private fun deniedResult(
    toolName: String,
    permission: McpToolPermission,
    pluginId: String?,
): CallToolResult {
    val reason = when (permission) {
        McpToolPermission.Unrestricted -> "is not permitted"

        is McpToolPermission.HostGroup ->
            "is in the ${permission.group.displayName} group, which is not allowed for AI agents"

        McpToolPermission.PluginInspect -> when (pluginId) {
            null -> "could not tell which plugin it targets, so it cannot be permission-checked"
            else -> "reads the UI of '$pluginId', which is not exposed to AI agents"
        }

        McpToolPermission.PluginInteract -> when (pluginId) {
            null -> "could not tell which plugin it targets, so it cannot be permission-checked"
            else -> "sends input to '$pluginId', which is not exposed to AI agents"
        }

        is McpToolPermission.PluginTool -> "is a plugin tool that is not exposed to AI agents"
    }
    return errorResult("$toolName $reason. Review it in Settings → AI Agents → Permissions.")
}

private val McpHostToolGroup.displayName: String
    get() = when (this) {
        McpHostToolGroup.OBSERVE -> "Observe"
        McpHostToolGroup.NAVIGATE -> "Navigate"
        McpHostToolGroup.MANAGE_PLUGINS -> "Manage plugins"
        McpHostToolGroup.SETTINGS_AND_SERVERS -> "Settings & servers"
    }

/**
 * Renders a tool result to the text kept in call history.
 *
 * Only text blocks carry something a reader can use; every other block type is a binary payload (a
 * screenshot, audio, an embedded resource) that is named rather than inlined, so history does not
 * fill up with base64.
 *
 * A structured payload follows the blocks on its own line. It is the whole answer for a tool that
 * replies only in `structuredContent`, and it reads as the machine-readable detail behind the prose
 * for a tool that sends both.
 */
private fun CallToolResult.renderForHistory(): String {
    val renderedBlocks = content.map { block ->
        when (block) {
            is TextContent -> block.text
            else -> "<${block.type.value}>"
        }
    }
    return (renderedBlocks + listOfNotNull(structuredContent?.toString())).joinToString(separator = "\n")
}
