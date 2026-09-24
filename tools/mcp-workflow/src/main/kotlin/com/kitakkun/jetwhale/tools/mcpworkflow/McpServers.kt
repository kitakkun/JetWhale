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

    suspend fun close()
}

class UnknownServerException(message: String) : Exception(message)

internal const val TOOL_VERSION = "0.1.0"

/** Connects to each configured server on first use and keeps the connection for later calls. */
class McpServers(private val configs: Map<String, ServerConfig>) : ToolCaller {
    private val clients = mutableMapOf<String, Client>()
    private val processes = mutableListOf<Process>()
    private val httpClients = mutableListOf<HttpClient>()
    private val lock = Mutex()

    override suspend fun listTools(server: String): List<Tool> = client(server).listTools().tools

    override suspend fun callTool(server: String, tool: String, arguments: JsonObject): CallToolResult = client(server).callTool(CallToolRequest(CallToolRequestParams(name = tool, arguments = arguments)))

    override suspend fun close() {
        lock.withLock {
            clients.values.forEach { runCatching { it.close() } }
            clients.clear()
            httpClients.forEach(HttpClient::close)
            httpClients.clear()
            processes.forEach(Process::destroy)
            processes.clear()
        }
    }

    private suspend fun client(server: String): Client = lock.withLock {
        clients.getOrPut(server) {
            val config = configs[server]
                ?: throw UnknownServerException("no server named '$server'; configured: ${configs.keys.joinToString().ifEmpty { "none" }}")
            connect(server, config)
        }
    }

    private suspend fun connect(name: String, config: ServerConfig): Client {
        val url = config.url
        return when {
            config.command != null -> connectStdio(config.command, config)
            url != null && (config.type == null || config.type == "sse") -> httpClient().mcpSse(url)
            url != null && (config.type == "http" || config.type == "streamable-http") -> httpClient().mcpStreamableHttp(url)
            else -> throw UnknownServerException("server '$name' needs either `command` or `url` (type sse or http)")
        }
    }

    private suspend fun connectStdio(command: String, config: ServerConfig): Client {
        val process = ProcessBuilder(listOf(command) + config.args)
            .apply { environment().putAll(config.env) }
            .start()
        processes += process
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
            error = process.errorStream.asSource().buffered(),
        )
        return Client(Implementation(name = "mcp-workflow", version = TOOL_VERSION)).apply { connect(transport) }
    }

    private fun httpClient(): HttpClient = HttpClient(CIO) { install(SSE) }.also(httpClients::add)
}
