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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JetWhaleNetworkOkHttpInterceptorTest {

    @OptIn(InternalJetWhaleApi::class)
    private fun agentWithEvents(): Pair<JetWhaleNetworkAgentPlugin, MutableList<NetworkEvent>> {
        val agent = JetWhaleNetworkAgentPlugin()
        // The interceptor also runs on OkHttp's WebSocket threads, not just the test thread.
        val events = Collections.synchronizedList(mutableListOf<NetworkEvent>())
        agent.activate { messages -> messages.forEach { events += JetWhaleJson.decodeFromString<NetworkEvent>(it) } }
        return agent to events
    }

    private fun mockRule(pattern: String, spec: MockResponseSpec) = MockRule(id = pattern, matcher = MockMatcher(urlPattern = pattern), response = spec)

    @Test
    fun capturesRequestAndResponse_withMatchingTxId() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        server.start()
        try {
            val (agent, events) = agentWithEvents()
            val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()
            val request = Request.Builder().url(server.url("/hello")).build()
            val response = client.newCall(request).execute()

            assertEquals("hello", response.body!!.string())

            assertEquals(2, events.size)
            val sent = events[0] as NetworkEvent.RequestSent
            val received = events[1] as NetworkEvent.ResponseReceived
            assertEquals(sent.request.txId, received.response.txId)
            assertEquals("GET", sent.request.method)
            assertEquals(200, received.response.statusCode)
            assertEquals("hello", received.response.body)
            assertEquals(false, received.response.fromMock)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun capturesRequestBody() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val (agent, events) = agentWithEvents()
            val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()
            val body = "ping".toRequestBody("text/plain".toMediaType())
            val request = Request.Builder().url(server.url("/echo")).post(body).build()
            client.newCall(request).execute().close()

            val sent = events[0] as NetworkEvent.RequestSent
            assertEquals("ping", sent.request.body)
            assertTrue(sent.request.headers.keys.any { it.equals("Content-Type", ignoreCase = true) })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun recordsFailure_onConnectionError() {
        val server = MockWebServer()
        server.start()
        val url = server.url("/gone")
        server.shutdown()

        val (agent, events) = agentWithEvents()
        val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()
        val request = Request.Builder().url(url).build()

        assertFailsWith<IOException> { client.newCall(request).execute() }

        assertEquals(2, events.size)
        val sent = events[0] as NetworkEvent.RequestSent
        val failed = events[1] as NetworkEvent.RequestFailed
        assertEquals(sent.request.txId, failed.failure.txId)
    }

    @Test
    fun mockShortCircuits_serverNeverHit() {
        val server = MockWebServer()
        server.start()
        try {
            val (agent, events) = agentWithEvents()
            runBlocking {
                agent.onReceiveMethod(
                    NetworkMethod.SetMockRules(
                        listOf(
                            mockRule(
                                "/users",
                                MockResponseSpec(
                                    statusCode = 418,
                                    headers = mapOf("Content-Type" to "application/json"),
                                    body = "{\"mock\":true}",
                                ),
                            ),
                        ),
                    ),
                )
            }
            val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()
            val request = Request.Builder().url(server.url("/users")).build()
            val response = client.newCall(request).execute()

            assertEquals(418, response.code)
            assertEquals("{\"mock\":true}", response.body!!.string())
            assertEquals(0, server.requestCount)

            val received = events.last() as NetworkEvent.ResponseReceived
            assertEquals(true, received.response.fromMock)
            assertEquals(listOf("application/json"), received.response.headers["Content-Type"])
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun truncatesLongBodies() {
        val longBody = "x".repeat(500)
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(longBody))
        server.start()
        try {
            val (agent, events) = agentWithEvents()
            val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor(maxBodyChars = 100)).build()
            val request = Request.Builder().url(server.url("/big")).build()
            val response = client.newCall(request).execute()

            assertEquals(longBody, response.body!!.string())

            val received = events.last() as NetworkEvent.ResponseReceived
            assertEquals(true, received.response.bodyTruncated)
            assertEquals(100, received.response.body?.length)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun capturesWebSocketUpgrade_withoutCorruptingFrames() {
        val server = MockWebServer()
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
        server.start()
        var webSocket: WebSocket? = null
        try {
            val (agent, events) = agentWithEvents()
            val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()
            val clientReceived = CountDownLatch(1)
            var receivedText: String? = null
            val request = Request.Builder().url(server.url("/ws")).build()
            webSocket = client.newWebSocket(
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
            server.shutdown()
        }
    }

    @Test
    fun truncatesLongRequestBodies() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val (agent, events) = agentWithEvents()
            val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor(maxBodyChars = 100)).build()
            val longBody = "y".repeat(1_000_000)
            val request = Request.Builder().url(server.url("/upload")).post(longBody.toRequestBody("text/plain".toMediaType())).build()
            client.newCall(request).execute().close()

            val sent = events[0] as NetworkEvent.RequestSent
            assertEquals(true, sent.request.bodyTruncated)
            assertEquals("y".repeat(100), sent.request.body)
        } finally {
            server.shutdown()
        }
    }
}
