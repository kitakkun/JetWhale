package com.kitakkun.jetwhale.tools.mcpworkflow

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject

/** Calls tools on named MCP servers. The runner, the recorder and serve mode all go through this. */
interface ToolCaller {
    suspend fun listTools(server: String): List<Tool>

    suspend fun callTool(server: String, tool: String, arguments: JsonObject): CallToolResult

    /** Drops the connection to [server]; the next call opens a new one. */
    suspend fun reconnect(server: String)

    suspend fun close()
}

class ServerConfigException(message: String) : Exception(message)

internal const val MCP_WORKFLOW_VERSION = "0.1.0"

/** Connects to each configured server on first use and keeps the connection for later calls. */
class McpServerConnections(private val configs: Map<String, ServerConfig>) : ToolCaller {
    private val clients = mutableMapOf<String, Client>()
    private val processes = mutableMapOf<String, Process>()
    private val httpClients = mutableListOf<HttpClient>()
    private val lock = Mutex()

    override suspend fun listTools(server: String): List<Tool> = getOrConnectClient(server).listTools().tools

    override suspend fun callTool(server: String, tool: String, arguments: JsonObject): CallToolResult = getOrConnectClient(server).callTool(CallToolRequest(CallToolRequestParams(name = tool, arguments = arguments)))

    override suspend fun reconnect(server: String) {
        val (client, process) = lock.withLock { clients.remove(server) to processes.remove(server) }
        client?.let { runCatching { it.close() } }
        process?.destroy()
    }

    override suspend fun close() {
        lock.withLock {
            clients.values.forEach { runCatching { it.close() } }
            clients.clear()
            httpClients.forEach(HttpClient::close)
            httpClients.clear()
            processes.values.forEach(Process::destroy)
            processes.clear()
        }
    }

    private suspend fun getOrConnectClient(server: String): Client = lock.withLock {
        clients.getOrPut(server) {
            val config = configs[server]
                ?: throw ServerConfigException("no server named '$server'; configured: ${configs.keys.joinToString().ifEmpty { "none" }}")
            connect(server, config)
        }
    }

    private suspend fun connect(server: String, config: ServerConfig): Client {
        val url = config.url
        return when {
            config.command != null -> connectStdio(server, config.command, config)
            url != null && (config.type == null || config.type == "sse") -> newHttpClient().mcpSse(url)
            url != null && (config.type == "http" || config.type == "streamable-http") -> newHttpClient().mcpStreamableHttp(url)
            else -> throw ServerConfigException("server '$server' needs either `command` or `url` (type sse or http)")
        }
    }

    private suspend fun connectStdio(server: String, command: String, config: ServerConfig): Client {
        val process = ProcessBuilder(listOf(command) + config.args)
            .apply { environment().putAll(config.env) }
            .start()
        processes[server] = process
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
            error = process.errorStream.asSource().buffered(),
        )
        return Client(Implementation(name = "mcp-workflow", version = MCP_WORKFLOW_VERSION)).apply { connect(transport) }
    }

    private fun newHttpClient(): HttpClient = HttpClient(CIO) { install(SSE) }.also(httpClients::add)
}
