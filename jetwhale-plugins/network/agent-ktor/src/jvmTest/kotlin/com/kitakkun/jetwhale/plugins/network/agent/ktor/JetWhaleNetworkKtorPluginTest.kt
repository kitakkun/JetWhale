package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.protocol.messaging.DefaultJetWhaleMessagingFormat
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger
import com.kitakkun.jetwhale.protocol.messaging.OfflineSendPolicy
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.StringFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JetWhaleNetworkKtorPluginTest {

    @OptIn(InternalJetWhaleApi::class)
    private fun agentWithEvents(): Pair<JetWhaleNetworkAgentPlugin, MutableList<Any>> {
        val agent = JetWhaleNetworkAgentPlugin()
        val recorder = RecordingMessenger(java.util.Collections.synchronizedList(mutableListOf()))
        agent.bindMessenger(recorder)
        return agent to recorder.events
    }

    @Test
    fun `returns an SSE response without buffering its body`() = runBlocking {
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
        val statusCode = withTimeout(5_000.milliseconds) {
            client.prepareGet("http://example/sse").execute { response -> response.status.value }
        }

        assertEquals(200, statusCode)
        val received = events.last() as ResponseReceived
        assertEquals("<streaming response body>", received.response.body)
        assertEquals(false, received.response.bodyTruncated)
    }

    @Test
    fun `captures a regular response body without breaking the caller's read`() = runBlocking {
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
        val received = events.last() as ResponseReceived
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
            val echoed = withTimeout(5_000) {
                client.webSocketSession("ws://127.0.0.1:$port/ws").run {
                    send(Frame.Text("hello"))
                    val reply = (incoming.receive() as Frame.Text).readText()
                    close()
                    reply
                }
            }

            assertEquals("echo: hello", echoed)
            val received = events.last() as ResponseReceived
            assertEquals(101, received.response.statusCode)
            assertEquals("<websocket upgrade>", received.response.body)
        } finally {
            client.close()
            server.stop()
        }
    }
}

/** Records every event the plugin sends, decoded back to its typed form. */
private class RecordingMessenger(val events: MutableList<Any>) : JetWhaleMessenger {
    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
    override val payloadFormat: StringFormat = DefaultJetWhaleMessagingFormat

    override fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean {
        events += when (messageType) {
            "network/request_sent" -> payloadFormat.decodeFromString(RequestSent.serializer(), payload)
            "network/response_received" -> payloadFormat.decodeFromString(ResponseReceived.serializer(), payload)
            "network/request_failed" -> payloadFormat.decodeFromString(RequestFailed.serializer(), payload)
            else -> error("Unexpected message type: $messageType")
        }
        return true
    }

    override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String = error("The network agent plugin never requests the host in these tests.")
}
