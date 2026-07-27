package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class JetWhaleNetworkKtorPluginTest {

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

    @Test
    fun `keeps a single entry when the request and the body spell a header differently`() = runBlocking {
        val (agent, events) = agentWithEvents()
        val client = HttpClient(MockEngine { respond(content = "ok", status = HttpStatusCode.OK) }) {
            install(agent.ktorClientPlugin())
        }

        client.post("http://example/echo") {
            header("X-Trace", "from-request")
            setBody(TracedContent("hi"))
        }

        // Header names are case-insensitive, so a body-level header must not be added again under
        // the request's spelling — the inspector would show the same header twice.
        val headers = (events.first() as RequestSent).request.headers
        assertEquals(
            listOf("X-Trace" to listOf("from-request")),
            headers.entries.filter { it.key.equals("x-trace", ignoreCase = true) }.map { it.key to it.value },
        )
    }

    @Test
    fun `a plugin installed before the monitor wraps it`() = runBlocking {
        val (agent, events) = agentWithEvents()
        val trace = mutableListOf<String>()
        val client = HttpClient(MockEngine { respond(content = "hello", status = HttpStatusCode.OK) }) {
            install(tracingPlugin(trace) { events.size })
            install(agent.ktorClientPlugin())
        }

        client.get("http://example/plain").bodyAsText()

        // HttpSend wraps interceptors in reverse registration order, so the plugin installed first
        // is the outermost one: nothing is recorded when it starts, and by the time it regains
        // control the monitor has already recorded both the request and the response.
        assertEquals(listOf("enter@0", "exit@2"), trace)
    }

    @Test
    fun `a plugin installed after the monitor runs inside it`() = runBlocking {
        val (agent, events) = agentWithEvents()
        val trace = mutableListOf<String>()
        val client = HttpClient(MockEngine { respond(content = "hello", status = HttpStatusCode.OK) }) {
            install(agent.ktorClientPlugin())
            install(tracingPlugin(trace) { events.size })
        }

        client.get("http://example/plain").bodyAsText()

        // Installed second means innermost: the monitor has already recorded the request when the
        // tracer starts, and records the response only after the tracer returns.
        assertEquals(listOf("enter@1", "exit@1"), trace)
    }

    @Test
    fun `records each redirect hop as its own transaction`() = runBlocking {
        val (agent, events) = agentWithEvents()
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/from") {
                    respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(HttpHeaders.Location, "http://example/to"),
                    )
                } else {
                    respond(content = "arrived", status = HttpStatusCode.OK)
                }
            },
        ) {
            install(agent.ktorClientPlugin())
        }

        assertEquals("arrived", client.get("http://example/from").bodyAsText())

        // HttpRedirect is installed before any user plugin, so the monitor always sits inside it
        // and sees every hop separately — install order in the config block can't change this.
        assertEquals(
            listOf("http://example/from", "http://example/to"),
            events.filterIsInstance<RequestSent>().map { it.request.url },
        )
        assertEquals(
            listOf(302, 200),
            events.filterIsInstance<ResponseReceived>().map { it.response.statusCode },
        )
    }

    @Test
    fun `installing the plugin twice records the transaction once`() = runBlocking {
        val (agent, events) = agentWithEvents()
        val client = HttpClient(MockEngine { respond(content = "hello", status = HttpStatusCode.OK) }) {
            // createClientPlugin keys on the plugin name and AttributeKey is a data class, so both
            // calls produce the same key and HttpClientConfig keeps only the first installation.
            install(agent.ktorClientPlugin())
            install(agent.ktorClientPlugin())
        }

        client.get("http://example/plain").bodyAsText()

        assertEquals(1, events.filterIsInstance<RequestSent>().size)
        assertEquals(1, events.filterIsInstance<ResponseReceived>().size)
    }
}

/** A body that carries its own header, spelled differently from the one the request sets. */
private class TracedContent(private val text: String) : OutgoingContent.ByteArrayContent() {
    override val contentType: ContentType = ContentType.Text.Plain
    override val headers: Headers = headersOf("x-trace", "from-body")
    override fun bytes(): ByteArray = text.encodeToByteArray()
}

/** A Send-hook plugin that appends the number of events recorded so far when it is entered and left. */
private fun tracingPlugin(trace: MutableList<String>, recordedCount: () -> Int): ClientPlugin<Unit> = createClientPlugin("Tracer") {
    on(Send) { request ->
        trace += "enter@${recordedCount()}"
        val call = proceed(request)
        trace += "exit@${recordedCount()}"
        call
    }
}
