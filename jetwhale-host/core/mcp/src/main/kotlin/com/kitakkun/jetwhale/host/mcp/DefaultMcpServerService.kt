package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalJetWhaleApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultMcpServerService(
    private val pluginInstanceService: PluginInstanceService,
    private val builtInTools: Set<JetWhaleMcpTool>,
) : McpServerService {

    private val toolRegistry = McpToolRegistry(pluginInstanceService)
    private var ktorServer: EmbeddedServer<*, *>? = null
    private val running = AtomicBoolean(false)

    private val _statusFlow = MutableStateFlow<McpServerStatus>(McpServerStatus.Stopped)
    override val statusFlow: StateFlow<McpServerStatus> = _statusFlow.asStateFlow()

    override suspend fun start(host: String, port: Int) {
        if (!running.compareAndSet(false, true)) return

        _statusFlow.value = McpServerStatus.Starting
        val transports = java.util.concurrent.ConcurrentHashMap<String, SseServerTransport>()
        val server = embeddedServer(Netty, host = host, port = port) {
            install(SSE)
            routing {
                sse("/sse") {
                    val transport = SseServerTransport("/message", this)
                    transports[transport.sessionId] = transport
                    val mcpServer = createMcpServer()
                    mcpServer.onClose { transports.remove(transport.sessionId) }
                    mcpServer.createSession(transport)
                    awaitCancellation()
                }
                post("/message") {
                    val sessionId = call.request.queryParameters["sessionId"]
                        ?: run {
                            call.respondText("Missing sessionId", status = HttpStatusCode.BadRequest)
                            return@post
                        }
                    val transport = transports[sessionId]
                        ?: run {
                            call.respondText("Session not found", status = HttpStatusCode.NotFound)
                            return@post
                        }
                    transport.handlePostMessage(call)
                }
            }
        }
        ktorServer = server
        try {
            server.start(wait = false)
            _statusFlow.value = McpServerStatus.Running(host = host, port = port)
        } catch (e: Exception) {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 0)
            ktorServer = null
            running.set(false)
            _statusFlow.value = McpServerStatus.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun stop() {
        if (!running.compareAndSet(true, false)) return
        _statusFlow.value = McpServerStatus.Stopping
        ktorServer?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        ktorServer = null
        _statusFlow.value = McpServerStatus.Stopped
    }

    override fun onPluginInstanceReady(pluginId: String, sessionId: String) {
        val plugin = pluginInstanceService.getPluginInstanceForSession(pluginId, sessionId)
        if (plugin is JetWhaleMcpCapablePlugin) {
            toolRegistry.register(pluginId, sessionId, plugin)
        }
    }

    override fun onPluginInstanceDisposed(pluginId: String, sessionId: String) {
        toolRegistry.unregister(pluginId, sessionId)
    }

    // ---------------------------------------------------------------------------
    // MCP Server factory — creates a new Server instance per SSE connection
    // ---------------------------------------------------------------------------

    private fun createMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(name = "jetwhale", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )

        for (tool in builtInTools) {
            tool.register(server)
        }
        registerPluginTools(server)
        return server
    }

    /**
     * Registers plugin-defined tools from the [McpToolRegistry] at connection time.
     * Since tool lists are computed at connection-time, newly added tools appear on the
     * next client reconnect.
     */
    private fun registerPluginTools(server: Server) {
        for ((scopedName, descriptor) in toolRegistry.allRegistrations()) {
            val inputSchema = ToolSchema(
                properties = JsonObject(
                    descriptor.parameters.mapValues { (_, param) ->
                        buildJsonObject {
                            put("type", param.type)
                            put("description", param.description)
                        }
                    },
                ),
                required = descriptor.parameters.filterValues { it.required }.keys.toList(),
            )
            server.addTool(
                name = scopedName,
                description = descriptor.description,
                inputSchema = inputSchema,
            ) { request ->
                val arguments = request.arguments?.mapValues { (_, v) ->
                    (v as? JsonPrimitive)?.content ?: v.toString()
                } ?: emptyMap()
                val result = toolRegistry.dispatch(scopedName, arguments)
                CallToolResult(content = listOf(TextContent(result ?: "null")))
            }
        }
    }
}
