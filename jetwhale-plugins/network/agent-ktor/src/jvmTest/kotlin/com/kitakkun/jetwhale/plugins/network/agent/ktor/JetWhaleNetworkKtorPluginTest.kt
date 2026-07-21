package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.agent.sdk.messaging.JetWhaleOfflineCapableMessenger
import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.protocol.messaging.DefaultJetWhaleMessagingFormat
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
import kotlinx.coroutines.flow.MutableStateFlow
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

    // The agent's mock rules are set by the host over messaging in production; a unit test can't
    // drive that internal path, so seed the state directly for the mock-serving case.
    @Suppress("UNCHECKED_CAST")
    private fun JetWhaleNetworkAgentPlugin.seedMockRules(rules: List<MockRule>) {
        val field = JetWhaleNetworkAgentPlugin::class.java.getDeclaredField("mockRules").apply { isAccessible = true }
        (field.get(this) as MutableStateFlow<List<MockRule>>).value = rules
    }

    @Test
    fun `serves a mock response whose body reads back without a coroutine-job cast crash`() = runBlocking {
        val (agent, _) = agentWithEvents()
        agent.seedMockRules(
            listOf(
                MockRule(
                    id = "1",
                    matcher = MockMatcher(urlPattern = "/todos/1"),
                    response = MockResponseSpec(statusCode = 200, body = "{\"ok\":true}"),
                ),
            ),
        )
        val client = HttpClient(
            // The real engine must never be hit — the mock is served before proceed().
            MockEngine { respond(content = "unmocked", status = HttpStatusCode.InternalServerError) },
        ) {
            install(agent.ktorClientPlugin())
        }

        val response = client.get("http://example/todos/1")

        // Before the fix, the synthesized call carried the Send-pipeline's StandaloneCoroutine as its
        // callContext Job, so reading the mocked body threw "StandaloneCoroutine cannot be cast to
        // CompletableJob".
        assertEquals(200, response.status.value)
        assertEquals("{\"ok\":true}", response.bodyAsText())
    }

    @Test
    fun `defaults a headerless mock's Content-Type to application json`() = runBlocking {
        val (agent, _) = agentWithEvents()
        agent.seedMockRules(
            listOf(
                MockRule(
                    id = "1",
                    matcher = MockMatcher(urlPattern = "/todos/1"),
                    response = MockResponseSpec(statusCode = 200, body = "{\"ok\":true}"),
                ),
            ),
        )
        val client = HttpClient(
            MockEngine { respond(content = "unmocked", status = HttpStatusCode.InternalServerError) },
        ) {
            install(agent.ktorClientPlugin())
        }

        val response = client.get("http://example/todos/1")

        // Without the default, a headerless mock synthesizes Content-Type: null and a
        // ContentNegotiation client rejects the body with NoTransformationFoundException.
        assertEquals("application/json", response.headers[HttpHeaders.ContentType])
    }

    @Test
    fun `preserves an explicit Content-Type on a mock`() = runBlocking {
        val (agent, _) = agentWithEvents()
        agent.seedMockRules(
            listOf(
                MockRule(
                    id = "1",
                    matcher = MockMatcher(urlPattern = "/plain"),
                    response = MockResponseSpec(
                        statusCode = 200,
                        headers = mapOf("content-type" to "text/plain"),
                        body = "hello",
                    ),
                ),
            ),
        )
        val client = HttpClient(
            MockEngine { respond(content = "unmocked", status = HttpStatusCode.InternalServerError) },
        ) {
            install(agent.ktorClientPlugin())
        }

        val response = client.get("http://example/plain")

        // A Content-Type the mock already sets (even lower-cased) is never overridden by the default.
        assertEquals("text/plain", response.headers[HttpHeaders.ContentType])
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
private class RecordingMessenger(val events: MutableList<Any>) : JetWhaleOfflineCapableMessenger {
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
