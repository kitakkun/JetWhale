package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkEvent
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class JetWhaleNetworkKtorPluginTest {

    @OptIn(InternalJetWhaleApi::class)
    private fun agentWithEvents(): Pair<JetWhaleNetworkAgentPlugin, MutableList<NetworkEvent>> {
        val agent = JetWhaleNetworkAgentPlugin()
        val events = mutableListOf<NetworkEvent>()
        agent.activate { messages -> messages.forEach { events += JetWhaleJson.decodeFromString<NetworkEvent>(it) } }
        return agent to events
    }

    @Test
    fun sseResponse_returnsWithoutBufferingBody() = runBlocking {
        val (agent, events) = agentWithEvents()
        // A channel that receives data but is never closed — save() would suspend on it forever.
        val stream = ByteChannel(autoFlush = true)
        stream.writeStringUtf8("data: hello\n\n")
        val client = HttpClient(
            MockEngine {
                respond(
                    content = stream,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            },
        ) {
            install(agent.ktorClientPlugin())
        }

        // Streaming execution (as the SSE plugin does) skips Ktor's own SaveBody plugin; without
        // the guard, the JetWhale plugin's save() would suspend on the endless channel forever.
        val statusCode = withTimeout(5_000) {
            client.prepareGet("http://example/sse").execute { response -> response.status.value }
        }

        assertEquals(200, statusCode)
        val received = events.last() as NetworkEvent.ResponseReceived
        assertEquals("<streaming response body>", received.response.body)
        assertEquals(false, received.response.bodyTruncated)
    }

    @Test
    fun regularResponse_isStillCaptured() = runBlocking {
        val (agent, events) = agentWithEvents()
        val client = HttpClient(
            MockEngine {
                respond(
                    content = "hello",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            },
        ) {
            install(agent.ktorClientPlugin())
        }

        val response = client.get("http://example/plain")

        assertEquals("hello", response.bodyAsText())
        val received = events.last() as NetworkEvent.ResponseReceived
        assertEquals("hello", received.response.body)
        assertEquals(false, received.response.bodyTruncated)
    }

    @Test
    fun `records a WebSocket upgrade without corrupting the frame stream`() = runBlocking {
        val (agent, events) = agentWithEvents()
        // A real server and engine are needed here: MockEngine can't perform a protocol upgrade.
        val server = embeddedServer(Netty, port = 0) {
            install(io.ktor.server.websocket.WebSockets)
            routing {
                webSocket("/ws") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) send(Frame.Text("echo: ${frame.readText()}"))
                    }
                }
            }
        }.start(wait = false)
        val port = server.engine.resolvedConnectors().first().port
        val client = HttpClient(CIO) {
            install(WebSockets)
            install(agent.ktorClientPlugin())
        }

        try {
            // Without the upgrade guard, save() would read live WebSocket frames as the HTTP
            // body, so the echo below would never arrive.
            val echoed = withTimeout(5_000.milliseconds) {
                client.webSocketSession("ws://127.0.0.1:$port/ws").run {
                    send(Frame.Text("hello"))
                    val reply = (incoming.receive() as Frame.Text).readText()
                    close()
                    reply
                }
            }

            assertEquals("echo: hello", echoed)
            val received = events.last() as NetworkEvent.ResponseReceived
            assertEquals(101, received.response.statusCode)
            assertEquals("<websocket upgrade>", received.response.body)
        } finally {
            client.close()
            server.stop()
        }
    }
}
