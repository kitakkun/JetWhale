package com.kitakkun.jetwhale.plugins.network.agent.okhttp

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkEvent
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkMethod
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JetWhaleNetworkOkHttpInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var agent: JetWhaleNetworkAgentPlugin
    private lateinit var events: MutableList<NetworkEvent>

    @OptIn(InternalJetWhaleApi::class)
    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        agent = JetWhaleNetworkAgentPlugin()
        // The interceptor also runs on OkHttp's WebSocket threads, not just the test thread.
        events = Collections.synchronizedList(mutableListOf())
        agent.activate { messages -> messages.forEach { events += JetWhaleJson.decodeFromString<NetworkEvent>(it) } }
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()

    /** Client whose interceptor captures at most [SMALL_BODY_CAP] chars, for truncation tests. */
    private fun clientWithSmallBodyCap() = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor(maxBodyChars = SMALL_BODY_CAP)).build()

    @Test
    fun `records request and response with a matching txId`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        val request = Request.Builder().url(server.url("/hello")).build()
        val response = client().newCall(request).execute()

        assertEquals("hello", response.body!!.string())

        assertEquals(2, events.size)
        val sent = events[0] as NetworkEvent.RequestSent
        val received = events[1] as NetworkEvent.ResponseReceived
        assertEquals(sent.request.txId, received.response.txId)
        assertEquals("GET", sent.request.method)
        assertEquals(200, received.response.statusCode)
        assertEquals("hello", received.response.body)
        assertEquals(false, received.response.fromMock)
    }

    @Test
    fun `captures the request body and its content type`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val body = "ping".toRequestBody("text/plain".toMediaType())
        val request = Request.Builder().url(server.url("/echo")).post(body).build()
        client().newCall(request).execute().close()

        val sent = events[0] as NetworkEvent.RequestSent
        assertEquals("ping", sent.request.body)
        assertTrue(sent.request.headers.keys.any { it.equals("Content-Type", ignoreCase = true) })
    }

    @Test
    fun `records a failure when the connection fails`() {
        val url = server.url("/gone")
        server.shutdown()

        val request = Request.Builder().url(url).build()

        assertFailsWith<IOException> { client().newCall(request).execute() }

        assertEquals(2, events.size)
        val sent = events[0] as NetworkEvent.RequestSent
        val failed = events[1] as NetworkEvent.RequestFailed
        assertEquals(sent.request.txId, failed.failure.txId)
    }

    @Test
    fun `serves a mock response without hitting the server`() {
        runBlocking {
            agent.onReceiveMethod(
                NetworkMethod.SetMockRules(
                    listOf(
                        MockRule(
                            id = "/users",
                            matcher = MockMatcher(urlPattern = "/users"),
                            response = MockResponseSpec(
                                statusCode = 418,
                                headers = mapOf("Content-Type" to "application/json"),
                                body = "{\"mock\":true}",
                            ),
                        ),
                    ),
                ),
            )
        }
        val request = Request.Builder().url(server.url("/users")).build()
        val response = client().newCall(request).execute()

        assertEquals(418, response.code)
        assertEquals("{\"mock\":true}", response.body!!.string())
        assertEquals(0, server.requestCount)

        val received = events.last() as NetworkEvent.ResponseReceived
        assertEquals(true, received.response.fromMock)
        assertEquals(listOf("application/json"), received.response.headers["Content-Type"])
    }

    @Test
    fun `truncates response bodies longer than maxBodyChars`() {
        val longBody = "x".repeat(500)
        server.enqueue(MockResponse().setResponseCode(200).setBody(longBody))
        val request = Request.Builder().url(server.url("/big")).build()
        val response = clientWithSmallBodyCap().newCall(request).execute()

        assertEquals(longBody, response.body!!.string())

        val received = events.last() as NetworkEvent.ResponseReceived
        assertEquals(true, received.response.bodyTruncated)
        assertEquals(SMALL_BODY_CAP, received.response.body?.length)
    }

    @Test
    fun `records a WebSocket upgrade without corrupting the frame stream`() {
        val serverMessageSent = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        webSocket.send("hello from server")
                        serverMessageSent.countDown()
                    }
                },
            ),
        )
        var webSocket: WebSocket? = null
        try {
            val clientReceived = CountDownLatch(1)
            var receivedText: String? = null
            val request = Request.Builder().url(server.url("/ws")).build()
            webSocket = client().newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        receivedText = text
                        clientReceived.countDown()
                    }
                },
            )

            assertTrue(serverMessageSent.await(5, TimeUnit.SECONDS), "server never opened the socket")
            assertTrue(clientReceived.await(5, TimeUnit.SECONDS), "client never received the WebSocket message")
            // The real regression check: the interceptor must not have stolen bytes from the frame stream.
            assertEquals("hello from server", receivedText)

            val received = events.last() as NetworkEvent.ResponseReceived
            assertEquals(101, received.response.statusCode)
            assertEquals("<websocket upgrade>", received.response.body)
            assertEquals(false, received.response.bodyTruncated)
        } finally {
            webSocket?.cancel()
        }
    }

    @Test
    fun `flags a multi-byte response body hitting the byte cap as truncated`() {
        // 200 chars × 3 bytes (UTF-8) = 600 bytes. The 100-byte peek cap yields ~33 decoded chars,
        // under the 100-char limit — a char-only check would report this cut body as not truncated.
        val longBody = "あ".repeat(200)
        server.enqueue(MockResponse().setResponseCode(200).setBody(longBody))
        val request = Request.Builder().url(server.url("/jp")).build()
        clientWithSmallBodyCap().newCall(request).execute().close()

        val received = events.last() as NetworkEvent.ResponseReceived
        assertEquals(true, received.response.bodyTruncated)
    }

    @Test
    fun `truncates request bodies longer than maxBodyChars`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val longBody = "y".repeat(1_000_000)
        val request = Request.Builder().url(server.url("/upload")).post(longBody.toRequestBody("text/plain".toMediaType())).build()
        clientWithSmallBodyCap().newCall(request).execute().close()

        val sent = events[0] as NetworkEvent.RequestSent
        assertEquals(true, sent.request.bodyTruncated)
        assertEquals("y".repeat(SMALL_BODY_CAP), sent.request.body)
    }

    companion object {
        private const val SMALL_BODY_CAP = 100
    }
}
