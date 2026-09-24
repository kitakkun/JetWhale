package com.kitakkun.jetwhale.tools.mcpworkflow

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
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.util.concurrent.ConcurrentHashMap

internal fun newToolServer(name: String): Server = Server(
    serverInfo = Implementation(name = name, version = TOOL_VERSION),
    options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
)

/**
 * Serves MCP over stdio until the client disconnects — the transport AI clients launch servers with.
 * Nothing else may write to stdout meanwhile; the tool's own messages go to stderr.
 */
internal suspend fun serveStdio(server: Server) {
    val closed = CompletableDeferred<Unit>()
    val transport = StdioServerTransport(System.`in`.asSource().buffered(), System.out.asSink().buffered()) {}
    val session = server.createSession(transport)
    session.onClose { closed.complete(Unit) }
    closed.await()
}

/**
 * Serves MCP over SSE at `http://127.0.0.1:<port>/sse`, one [createServer] per connection. Port 0
 * picks a free port; [wait] blocks until the process is stopped.
 */
internal fun serveSse(port: Int, wait: Boolean, createServer: suspend () -> Server): EmbeddedServer<*, *> {
    val transports = ConcurrentHashMap<String, SseServerTransport>()
    return embeddedServer(Netty, host = "127.0.0.1", port = port) {
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SseServerTransport("/message", this)
                transports[transport.sessionId] = transport
                try {
                    createServer().createSession(transport)
                    awaitCancellation()
                } finally {
                    transports.remove(transport.sessionId)
                }
            }
            post("/message") {
                val transport = call.request.queryParameters["sessionId"]?.let(transports::get)
                    ?: return@post call.respondText("Session not found", status = HttpStatusCode.NotFound)
                transport.handlePostMessage(call)
            }
        }
    }.start(wait = wait)
}
