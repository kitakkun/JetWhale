package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
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
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * How often the server probes that an MCP client is still attached. Short because the AI activity
 * indicator is only as truthful as this interval, and the connection is local.
 */
private val CLIENT_LIVENESS_PROBE_PERIOD = 5.seconds

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultMcpServerService(
    private val pluginInstanceService: PluginInstanceService,
    private val mcpActivityRepository: McpActivityRepository,
    private val mcpPermissionsRepository: McpPermissionsRepository,
    private val builtInTools: Set<JetWhaleMcpTool>,
    private val statusHolder: McpServerStatusHolder,
) : McpServerService {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var lifecycleObserverJob: Job? = null

    private val toolRegistry = McpToolRegistry(pluginInstanceService)
    private var ktorServer: EmbeddedServer<*, *>? = null
    private val running = AtomicBoolean(false)

    override val statusFlow: StateFlow<McpServerStatus> get() = statusHolder.statusFlow

    override val mcpCapablePluginsFlow: StateFlow<McpCapablePlugins> = toolRegistry.mcpCapablePluginsFlow

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

        statusHolder.update(McpServerStatus.Starting)
        val transports = java.util.concurrent.ConcurrentHashMap<String, SseServerTransport>()
        val server = embeddedServer(Netty, host = host, port = port) {
            install(SSE)
            routing {
                sse("/sse") {
                    // An idle MCP connection carries no server-to-client traffic, so without a
                    // periodic write the server never learns that the agent went away and the UI
                    // would keep claiming an agent is attached. The comment-only event is ignored
                    // by SSE clients; the failing write is what surfaces the disconnect.
                    heartbeat { period = CLIENT_LIVENESS_PROBE_PERIOD }
                    val transport = SseServerTransport("/message", this)
                    // transport.sessionId: MCP-library-assigned UUID per SSE connection (not a JetWhale device session)
                    transports[transport.sessionId] = transport
                    val mcpServer = createMcpServer()
                    // A departing client surfaces either as the session closing or as this block
                    // being cancelled, depending on how the connection dropped. Both paths lead
                    // here, and only whichever arrives first may take effect.
                    val disconnected = AtomicBoolean(false)
                    fun markDisconnected() {
                        if (!disconnected.compareAndSet(false, true)) return
                        transports.remove(transport.sessionId)
                        mcpActivityRepository.clientDisconnected()
                    }
                    mcpActivityRepository.clientConnected()
                    try {
                        mcpServer.createSession(transport).onClose { markDisconnected() }
                        awaitCancellation()
                    } finally {
                        markDisconnected()
                    }
                }
                post("/message") {
                    // sessionId here is the MCP transport session ID (matches transport.sessionId above),
                    // not a JetWhale device session ID. Used to route the POST body to the correct SSE channel.
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
            statusHolder.update(McpServerStatus.Running(host = host, port = port))
        } catch (e: CancellationException) {
            // Never swallow cancellation: undo this attempt, then re-throw so the coroutine
            // cancellation mechanism keeps working. The status stays untouched — the caller went
            // away, the server did not fail.
            rollbackFailedStart(server)
            throw e
        } catch (e: Throwable) {
            rollbackFailedStart(server)
            statusHolder.update(McpServerStatus.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Undoes everything [start] set up before the bind attempt. [stop] bails out early once
     * [running] is false, so a failed start has to release the observer job and the tool
     * registrations itself or a later retry piles another collector on top of the leaked one.
     */
    private suspend fun rollbackFailedStart(server: EmbeddedServer<*, *>) {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 0)
        ktorServer = null
        lifecycleObserverJob?.cancel()
        lifecycleObserverJob = null
        toolRegistry.clear()
        running.set(false)
    }

    override suspend fun stop() {
        if (!running.compareAndSet(true, false)) return
        lifecycleObserverJob?.cancel()
        lifecycleObserverJob = null
        toolRegistry.clear()
        statusHolder.update(McpServerStatus.Stopping)
        ktorServer?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        ktorServer = null
        mcpActivityRepository.clear()
        statusHolder.update(McpServerStatus.Stopped)
    }

    private fun onPluginInstanceReady(pluginId: String, sessionId: String?) {
        val plugin = when (sessionId) {
            null -> pluginInstanceService.getHostScopedInstance(pluginId)
            else -> pluginInstanceService.getPluginInstanceForSession(pluginId, sessionId)
        }
        if (plugin is JetWhaleMcpCapablePlugin) {
            toolRegistry.register(pluginId, sessionId, plugin)
        }
    }

    private fun onPluginInstanceDisposed(pluginId: String, sessionId: String?) {
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

        val registrar = McpToolRegistrar(server, mcpActivityRepository, mcpPermissionsRepository)
        for (tool in builtInTools) {
            tool.register(registrar)
        }
        registerPluginTools(registrar)
        return server
    }

    /**
     * Registers plugin-defined tools from the [McpToolRegistry] at connection time.
     * Since tool lists are computed at connection-time, newly added tools appear on the
     * next client reconnect — a host-scoped plugin that becomes ready after a client connected
     * included.
     *
     * A `sessionId` parameter is automatically injected into every session-scoped plugin tool's
     * schema so the caller can identify which session to route the call to. A host-scoped plugin's
     * tools belong to no session and are registered with exactly the parameters they declare.
     */
    private fun registerPluginTools(registrar: McpToolRegistrar) {
        for ((toolName, descriptor) in toolRegistry.allRegistrations()) {
            val inputSchema = descriptor.toToolSchema(
                leadingProperties = mapOf(
                    "sessionId" to stringProperty("Session ID of the target device (from jetwhale.listSessions)"),
                ),
            )
            registrar.addPluginTool(
                name = toolName,
                description = descriptor.description,
                inputSchema = inputSchema,
                resolvePluginIdForSession = { sessionId -> toolRegistry.pluginIdFor(toolName, sessionId) },
            ) { request ->
                dispatchPluginTool(toolName, request)
            }
        }
        for (registration in toolRegistry.allHostScopedRegistrations()) {
            registrar.addHostScopedPluginTool(
                name = registration.name,
                description = registration.descriptor.description,
                inputSchema = registration.descriptor.toToolSchema(),
                pluginId = registration.pluginId,
            ) { request ->
                dispatchPluginTool(registration.name, request)
            }
        }
    }

    private suspend fun dispatchPluginTool(toolName: String, request: CallToolRequest): CallToolResult {
        // Forward the arguments as raw JSON so structured (object/array) parameters keep their
        // shape; the command's parameter DSL decodes each value by its declared type.
        val result = toolRegistry.dispatch(toolName, request.arguments ?: emptyMap())
            ?: return CallToolResult(content = listOf(TextContent("null")))
        return CallToolResult(content = result.toCallToolContent())
    }
}
