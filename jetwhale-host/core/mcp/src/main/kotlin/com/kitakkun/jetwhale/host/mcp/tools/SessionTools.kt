package com.kitakkun.jetwhale.host.mcp.tools

import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.jsonContent
import com.kitakkun.jetwhale.host.mcp.stringProperty
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Returns a JSON string listing all currently known debug sessions.
 */
suspend fun listSessions(debugSessionRepository: DebugSessionRepository): String {
    val sessions = debugSessionRepository.debugSessionsFlow.firstOrNull() ?: return "[]"
    return Json.encodeToString(sessions.map { it.toSessionInfo() })
}

/**
 * Returns a JSON string listing plugins installed in the given session.
 * Each entry includes whether the plugin implements [JetWhaleMcpCapablePlugin].
 */
@OptIn(ExperimentalJetWhaleApi::class)
suspend fun listPlugins(
    sessionId: String,
    debugSessionRepository: DebugSessionRepository,
    pluginFactoryRepository: PluginFactoryRepository,
    pluginInstanceService: PluginInstanceService,
): String {
    val session = debugSessionRepository.debugSessionsFlow
        .firstOrNull()
        ?.find { it.id == sessionId }
        ?: return "[]"

    val factories = pluginFactoryRepository.loadedPluginFactories
    val result = session.installedPlugins.mapNotNull { pluginInfo ->
        val factory = factories[pluginInfo.pluginId] ?: return@mapNotNull null
        val meta = factory.meta
        val instance = pluginInstanceService.getPluginInstanceForSession(meta.pluginId, sessionId)
        PluginInfo(
            pluginId = meta.pluginId,
            pluginName = meta.pluginName,
            version = meta.version,
            mcpCapable = instance is JetWhaleMcpCapablePlugin,
        )
    }
    return Json.encodeToString(result)
}

private fun DebugSession.toSessionInfo() = SessionInfo(
    sessionId = id,
    sessionName = name,
    isActive = isActive,
    installedPlugins = installedPlugins.map { it.pluginId },
)

@Inject
@ContributesIntoSet(AppScope::class)
class ListSessionsMcpTool(
    private val debugSessionRepository: DebugSessionRepository,
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(
            name = "jetwhale.listSessions",
            description = "Lists all active debug sessions currently connected to JetWhale.",
            inputSchema = ToolSchema(),
        ) { _ ->
            val json = listSessions(debugSessionRepository)
            CallToolResult(content = listOf(TextContent(json)))
        }
    }
}

@Inject
@ContributesIntoSet(AppScope::class)
class ListPluginsMcpTool(
    private val debugSessionRepository: DebugSessionRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val pluginInstanceService: PluginInstanceService,
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(
            name = "jetwhale.listPlugins",
            description = "Lists plugins installed in the specified debug session, including whether each plugin supports additional MCP tools.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "sessionId" to stringProperty("The session ID obtained from jetwhale.listSessions."),
                    ),
                ),
                required = listOf("sessionId"),
            ),
        ) { request ->
            val sessionId = request.arguments?.get("sessionId")?.let {
                (it as? JsonPrimitive)?.content
            } ?: return@addTool errorResult("Missing required argument: sessionId")
            val json = listPlugins(sessionId, debugSessionRepository, pluginFactoryRepository, pluginInstanceService)
            CallToolResult(content = listOf(TextContent(json)))
        }
    }
}

@Serializable
data class SessionInfo(
    val sessionId: String,
    val sessionName: String?,
    val isActive: Boolean,
    val installedPlugins: List<String>,
)

@Serializable
data class PluginInfo(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val mcpCapable: Boolean,
)
