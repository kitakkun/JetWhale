package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var lifecycleObserverJob: Job? = null

    private val toolRegistry = McpToolRegistry(pluginInstanceService)
    private var ktorServer: EmbeddedServer<*, *>? = null
    private val running = AtomicBoolean(false)

    private val _statusFlow = MutableStateFlow<McpServerStatus>(McpServerStatus.Stopped)
    override val statusFlow: StateFlow<McpServerStatus> = _statusFlow.asStateFlow()

    override suspend fun start(host: String, port: Int) {
        if (!running.compareAndSet(false, true)) return

        // Register plugin instances that were already created before the MCP server started.
        pluginInstanceService.getLoadedPluginInstances().forEach { (pluginId, sessionId, plugin) ->
            if (plugin is JetWhaleMcpCapablePlugin) {
                toolRegistry.register(pluginId, sessionId, plugin)
            }
        }

        lifecycleObserverJob = coroutineScope.launch {
            pluginInstanceService.pluginInstanceEventFlow.collect { event ->
                when (event) {
                    is PluginInstanceEvent.Ready -> onPluginInstanceReady(event.pluginId, event.sessionId)
                    is PluginInstanceEvent.Disposed -> onPluginInstanceDisposed(event.pluginId, event.sessionId)
                }
            }
        }

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
        lifecycleObserverJob?.cancel()
        lifecycleObserverJob = null
        _statusFlow.value = McpServerStatus.Stopping
        ktorServer?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        ktorServer = null
        _statusFlow.value = McpServerStatus.Stopped
    }

    private fun onPluginInstanceReady(pluginId: String, sessionId: String) {
        val plugin = pluginInstanceService.getPluginInstanceForSession(pluginId, sessionId)
        if (plugin is JetWhaleMcpCapablePlugin) {
            toolRegistry.register(pluginId, sessionId, plugin)
        }
    }

    private fun onPluginInstanceDisposed(pluginId: String, sessionId: String) {
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
     *
     * A `sessionId` parameter is automatically injected into every plugin tool's schema
     * so the caller can identify which session to route the call to.
     */
    private fun registerPluginTools(server: Server) {
        for ((toolName, descriptor) in toolRegistry.allRegistrations()) {
            val sessionIdProperty = buildJsonObject {
                put("type", "string")
                put("description", "Session ID of the target device (from jetwhale.listSessions)")
            }
            val pluginProperties = descriptor.parameters.mapValues { (_, param) ->
                buildJsonObject {
                    put("type", param.type)
                    put("description", param.description)
                }
            }
            val inputSchema = ToolSchema(
                properties = JsonObject(mapOf("sessionId" to sessionIdProperty) + pluginProperties),
                required = listOf("sessionId") + descriptor.parameters.filterValues { it.required }.keys,
            )
            server.addTool(
                name = toolName,
                description = descriptor.description,
                inputSchema = inputSchema,
            ) { request ->
                val arguments = request.arguments?.mapValues { (_, v) ->
                    (v as? JsonPrimitive)?.content ?: v.toString()
                } ?: emptyMap()
                val result = toolRegistry.dispatch(toolName, arguments)
                CallToolResult(content = listOf(TextContent(result ?: "null")))
            }
        }
    }
}
