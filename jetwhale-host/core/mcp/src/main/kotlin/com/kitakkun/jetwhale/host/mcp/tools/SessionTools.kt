package com.kitakkun.jetwhale.host.mcp.tools

import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.McpToolRegistrar
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.stringProperty
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.McpToolPermission
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What MCP callers see the host session called; the sidebar lists its plugins without a heading. */
private const val HOST_SESSION_NAME = "Host"

/**
 * Returns a JSON string listing [HostSession], which holds the plugins that need no app and is
 * always there, followed by every known app session.
 */
suspend fun listSessions(
    debugSessionRepository: DebugSessionRepository,
    pluginFactoryRepository: PluginFactoryRepository,
): String {
    val hostSession = SessionInfo(
        sessionId = HostSession.ID,
        sessionName = HOST_SESSION_NAME,
        isActive = true,
        installedPlugins = pluginFactoryRepository.hostOnlyPluginIds(),
    )
    val appSessions = debugSessionRepository.debugSessionsFlow.firstOrNull().orEmpty().map(DebugSession::toSessionInfo)
    return Json.encodeToString(listOf(hostSession) + appSessions)
}

/**
 * Returns a JSON string listing plugins installed in the given session: for [HostSession], the
 * loaded plugins that need no app; for an app, the plugins its agent advertised.
 * Each entry includes whether the plugin implements [JetWhaleMcpCapablePlugin].
 */
suspend fun listPlugins(
    sessionId: String,
    debugSessionRepository: DebugSessionRepository,
    pluginFactoryRepository: PluginFactoryRepository,
    pluginInstanceService: PluginInstanceService,
): String {
    val pluginIds = if (HostSession.isHost(sessionId)) {
        pluginFactoryRepository.hostOnlyPluginIds()
    } else {
        debugSessionRepository.debugSessionsFlow
            .firstOrNull()
            ?.find { it.id == sessionId }
            ?.installedPlugins
            ?.map(JetWhalePluginInfo::pluginId)
            ?: return "[]"
    }

    val loadedPlugins = pluginFactoryRepository.loadedPlugins
    val result = pluginIds.mapNotNull { pluginId ->
        val manifest = loadedPlugins[pluginId]?.manifest ?: return@mapNotNull null
        val instance = pluginInstanceService.getPluginInstanceForSession(manifest.pluginId, sessionId)
        PluginInfo(
            pluginId = manifest.pluginId,
            pluginName = manifest.pluginName,
            version = manifest.version,
            mcpCapable = instance is JetWhaleMcpCapablePlugin,
        )
    }
    return Json.encodeToString(result)
}

private fun PluginFactoryRepository.hostOnlyPluginIds(): List<String> = loadedPlugins.values
    .filterNot { it.manifest.requiresAgent }
    .map { it.manifest.pluginId }

private fun DebugSession.toSessionInfo() = SessionInfo(
    sessionId = id,
    sessionName = name,
    isActive = isActive,
    installedPlugins = installedPlugins.map(JetWhalePluginInfo::pluginId),
    appName = appName,
    deviceId = deviceId,
    deviceName = deviceName,
)

@Inject
@ContributesIntoSet(AppScope::class)
class ListSessionsMcpTool(
    private val debugSessionRepository: DebugSessionRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = "jetwhale.listSessions",
            description = "Lists the debug sessions: first \"${HostSession.ID}\", which is always present and holds the tools that need no app " +
                "(pass it as sessionId to reach them), then every app connected to JetWhale.",
            inputSchema = ToolSchema(),
            permission = McpToolPermission.Unrestricted,
        ) { _ ->
            val json = listSessions(debugSessionRepository, pluginFactoryRepository)
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
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
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
            permission = McpToolPermission.Unrestricted,
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
    val appName: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
)

@Serializable
data class PluginInfo(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val mcpCapable: Boolean,
)
