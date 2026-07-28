package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpActivityRepository
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
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
) {
    /**
     * Registers a built-in tool, attributed to whatever `pluginId` the caller passed in.
     */
    fun addTool(
        name: String,
        description: String,
        inputSchema: ToolSchema,
        handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult,
    ) {
        addTrackedTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
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
        resolvePluginId: (CallToolRequest) -> String?,
        handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult,
    ) {
        server.addTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
        ) { request ->
            val invocationId = activityRepository.toolInvocationStarted(
                toolName = name,
                pluginId = resolvePluginId(request),
                sessionId = request.arguments?.get("sessionId")?.jsonContent,
            )
            try {
                handler(request)
            } finally {
                activityRepository.toolInvocationFinished(invocationId)
            }
        }
    }
}
