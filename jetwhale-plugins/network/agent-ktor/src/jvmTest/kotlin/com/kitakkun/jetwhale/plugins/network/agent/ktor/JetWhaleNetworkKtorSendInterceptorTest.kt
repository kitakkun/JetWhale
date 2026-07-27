package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class JetWhaleNetworkKtorSendInterceptorTest {

    @Test
    fun `records a round trip on a client that was built without installing the plugin`() = runBlocking {
        val (agent, events) = agentWithEvents()
        // Built first, instrumented after — this is the whole point of the interceptor entry point.
        val client = HttpClient(
            MockEngine {
                respond(
                    content = "hello",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            },
        )
        client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client))

        val response = client.get("http://example/plain")

        assertEquals("hello", response.bodyAsText())
        val sent = events.first() as RequestSent
        assertEquals("http://example/plain", sent.request.url)
        val received = events.last() as ResponseReceived
        assertEquals("hello", received.response.body)
        assertEquals(sent.request.txId, received.response.txId)
    }

    @Test
    fun `serves a mock through the interceptor without reaching the engine`() = runBlocking {
        val (agent, events) = agentWithEvents()
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
            // The real engine must never be hit — the mock is served before execute().
            MockEngine { respond(content = "unmocked", status = HttpStatusCode.InternalServerError) },
        )
        client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client))

        val response = client.get("http://example/todos/1")

        // The mocked call is synthesized from the HttpClient handed to ktorSendInterceptor, since
        // HttpSend's Sender doesn't expose the client it sends through.
        assertEquals(200, response.status.value)
        assertEquals("{\"ok\":true}", response.bodyAsText())
        assertEquals(true, (events.last() as ResponseReceived).response.fromMock)
    }

    @Test
    fun `registering the interceptor twice records the transaction twice`() = runBlocking {
        val (agent, events) = agentWithEvents()
        val client = HttpClient(MockEngine { respond(content = "hello", status = HttpStatusCode.OK) })
        // HttpSend rejects neither duplicates nor offers removal, so a double registration double
        // records. Pinning the documented consequence of calling this more than once per client.
        client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client))
        client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client))

        client.get("http://example/plain").bodyAsText()

        assertEquals(2, events.filterIsInstance<RequestSent>().size)
        assertEquals(2, events.filterIsInstance<ResponseReceived>().size)
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
        )
        client.plugin(HttpSend).intercept(agent.ktorSendInterceptor(client))

        assertEquals("arrived", client.get("http://example/from").bodyAsText())

        // HttpSend runs interceptors outermost-first in registration order, so one added after the
        // client was built sits inside HttpRedirect and sees every hop separately.
        assertEquals(
            listOf("http://example/from", "http://example/to"),
            events.filterIsInstance<RequestSent>().map { it.request.url },
        )
        assertEquals(
            listOf(302, 200),
            events.filterIsInstance<ResponseReceived>().map { it.response.statusCode },
        )
    }
}
