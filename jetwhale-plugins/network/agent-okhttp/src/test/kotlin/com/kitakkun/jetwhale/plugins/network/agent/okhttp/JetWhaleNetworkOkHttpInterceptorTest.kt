package com.kitakkun.jetwhale.plugins.network.agent.okhttp

import com.kitakkun.jetwhale.agent.sdk.messaging.JetWhaleOfflineCapableMessenger
import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockRules
import com.kitakkun.jetwhale.plugins.network.protocol.SetNetworkConditions
import com.kitakkun.jetwhale.protocol.messaging.DefaultJetWhaleMessagingFormat
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.StringFormat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class JetWhaleNetworkOkHttpInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var agent: JetWhaleNetworkAgentPlugin
    private lateinit var events: MutableList<Any>

    @OptIn(InternalJetWhaleApi::class)
    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        agent = JetWhaleNetworkAgentPlugin()
        // The interceptor also runs on OkHttp's WebSocket threads, not just the test thread.
        events = Collections.synchronizedList(mutableListOf())
        agent.bindMessenger(RecordingMessenger(events))
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `records request and response with a matching txId`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        val request = Request.Builder().url(server.url("/hello")).build()
        val response = client().newCall(request).execute()

        assertEquals("hello", response.body.string())

        assertEquals(2, events.size)
        val sent = events[0] as RequestSent
        val received = events[1] as ResponseReceived
        assertEquals(sent.request.txId, received.response.txId)
        assertEquals("GET", sent.request.method)
        assertEquals(200, received.response.statusCode)
        assertEquals("hello", received.response.body)
        assertEquals(false, received.response.fromMock)
    }

    private fun client() = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()

    @Test
    fun `captures the request body and its content type`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val body = "ping".toRequestBody("text/plain".toMediaType())
        val request = Request.Builder().url(server.url("/echo")).post(body).build()
        client().newCall(request).execute().close()

        val sent = events[0] as RequestSent
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
        val sent = events[0] as RequestSent
        val failed = events[1] as RequestFailed
        assertEquals(sent.request.txId, failed.failure.txId)
    }

    @Test
    fun `serves a mock response without hitting the server`() {
        applyMockRules(
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
        )
        val request = Request.Builder().url(server.url("/users")).build()
        val response = client().newCall(request).execute()

        assertEquals(418, response.code)
        assertEquals("{\"mock\":true}", response.body.string())
        assertEquals(0, server.requestCount)

        val received = events.last() as ResponseReceived
        assertEquals(true, received.response.fromMock)
        assertEquals(listOf("application/json"), received.response.headers["Content-Type"])
    }

    @Test
    fun `truncates response bodies longer than maxBodyChars`() {
        val longBody = "x".repeat(500)
        server.enqueue(MockResponse().setResponseCode(200).setBody(longBody))
        val request = Request.Builder().url(server.url("/big")).build()
        val response = clientWithSmallBodyCap().newCall(request).execute()

        assertEquals(longBody, response.body.string())

        val received = events.last() as ResponseReceived
        assertEquals(true, received.response.bodyTruncated)
        assertEquals(SMALL_BODY_CAP, received.response.body?.length)
    }

    /** Client whose interceptor captures at most [SMALL_BODY_CAP] chars, for truncation tests. */
    private fun clientWithSmallBodyCap() = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor(maxBodyChars = SMALL_BODY_CAP)).build()

    @Test
    fun `records a WebSocket upgrade without corrupting the frame stream`() {
        val serverMessageSent = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
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

            val received = events.last() as ResponseReceived
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

        val received = events.last() as ResponseReceived
        assertEquals(true, received.response.bodyTruncated)
    }

    @Test
    fun `captures an image response as Base64 bytes`() {
        server.enqueue(imageResponse(IMAGE_BYTES))
        val request = Request.Builder().url(server.url("/logo.png")).build()
        client().newCall(request).execute().close()

        val received = events.last() as ResponseReceived
        assertEquals(BodyEncoding.BASE64, received.response.bodyEncoding)
        assertEquals(false, received.response.bodyTruncated)
        assertContentEquals(IMAGE_BYTES, Base64.decode(assertNotNull(received.response.body)))
    }

    @Test
    fun `replaces an image response over maxImageBytes with a marker instead of truncating it`() {
        server.enqueue(imageResponse(IMAGE_BYTES))
        val request = Request.Builder().url(server.url("/logo.png")).build()
        val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor(maxImageBytes = IMAGE_BYTES.size - 1)).build()
        client.newCall(request).execute().close()

        val received = events.last() as ResponseReceived
        // A partial image cannot be decoded, so the capture says so rather than shipping half of it.
        assertEquals(BodyEncoding.TEXT, received.response.bodyEncoding)
        assertEquals("<image/png body over the ${IMAGE_BYTES.size - 1}-byte maxImageBytes limit>", received.response.body)
    }

    @Test
    fun `captures an uploaded image as Base64 bytes`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder()
            .url(server.url("/upload"))
            .post(IMAGE_BYTES.toRequestBody("image/png".toMediaType()))
            .build()
        client().newCall(request).execute().close()

        val sent = events[0] as RequestSent
        assertEquals(BodyEncoding.BASE64, sent.request.bodyEncoding)
        assertContentEquals(IMAGE_BYTES, Base64.decode(assertNotNull(sent.request.body)))
    }

    @Test
    fun `serves a Base64 mock body as raw bytes`() {
        applyMockRules(
            listOf(
                MockRule(
                    id = "image",
                    matcher = MockMatcher(urlPattern = "/logo.png"),
                    response = MockResponseSpec(
                        headers = mapOf("Content-Type" to "image/png"),
                        body = Base64.encode(IMAGE_BYTES),
                        bodyEncoding = BodyEncoding.BASE64,
                    ),
                ),
            ),
        )
        val request = Request.Builder().url(server.url("/logo.png")).build()
        val response = client().newCall(request).execute()

        assertContentEquals(IMAGE_BYTES, response.body.bytes())
    }

    private fun imageResponse(bytes: ByteArray) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "image/png")
        .setBody(Buffer().write(bytes))

    @Test
    fun `truncates request bodies longer than maxBodyChars`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val longBody = "y".repeat(1_000_000)
        val request = Request.Builder().url(server.url("/upload")).post(longBody.toRequestBody("text/plain".toMediaType())).build()
        clientWithSmallBodyCap().newCall(request).execute().close()

        val sent = events[0] as RequestSent
        assertEquals(true, sent.request.bodyTruncated)
        assertEquals("y".repeat(SMALL_BODY_CAP), sent.request.body)
    }

    @Test
    fun `an offline condition fails at once with the exception OkHttp throws for no network`() {
        applyNetworkConditions(listOf(conditionRule(NetworkCondition(offline = true))))
        val request = Request.Builder().url(server.url("/items")).build()

        assertFailsWith<UnknownHostException> { client().newCall(request).execute() }

        assertEquals(0, server.requestCount)
        val failed = events.filterIsInstance<RequestFailed>().single()
        assertEquals(true, failed.failure.condition?.offline)
    }

    @Test
    fun `an injected timeout surfaces as a socket timeout without reaching the server`() {
        applyNetworkConditions(listOf(conditionRule(NetworkCondition(failureRate = 1.0, failure = InjectedFailure.TIMEOUT))))
        val request = Request.Builder().url(server.url("/items")).build()

        assertFailsWith<SocketTimeoutException> { client().newCall(request).execute() }

        assertEquals(0, server.requestCount)
        assertEquals(InjectedFailure.TIMEOUT, events.filterIsInstance<RequestFailed>().single().failure.condition?.injectedFailure)
    }

    @Test
    fun `a download cap paces the body the caller reads`() {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(PACED_BODY_BYTES))))
        applyNetworkConditions(listOf(conditionRule(NetworkCondition(downloadBytesPerSecond = PACED_RATE))))
        val request = Request.Builder().url(server.url("/large")).build()

        val started = System.nanoTime()
        val bytes = client().newCall(request).execute().use { it.body.bytes() }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(PACED_BODY_BYTES, bytes.size)
        assertTrue(elapsedMs >= PACED_MIN_MS, "read took ${elapsedMs}ms")
        assertEquals(PACED_RATE, events.filterIsInstance<ResponseReceived>().single().response.condition?.downloadBytesPerSecond)
    }

    @Test
    fun `an upload cap paces the request body`() {
        server.enqueue(MockResponse().setResponseCode(200))
        applyNetworkConditions(listOf(conditionRule(NetworkCondition(uploadBytesPerSecond = PACED_RATE))))
        val body = ByteArray(PACED_BODY_BYTES).toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder().url(server.url("/upload")).post(body).build()

        val started = System.nanoTime()
        client().newCall(request).execute().close()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(PACED_BODY_BYTES.toLong(), server.takeRequest().bodySize)
        assertTrue(elapsedMs >= PACED_MIN_MS, "upload took ${elapsedMs}ms")
    }

    @Test
    fun `a WebSocket upgrade is not paced by a download cap`() {
        // One byte a second would starve the frame stream if the upgrade's body were paced.
        applyNetworkConditions(listOf(conditionRule(NetworkCondition(downloadBytesPerSecond = 1))))
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("hello under a cap")
                    }
                },
            ),
        )
        val received = CountDownLatch(1)
        var receivedText: String? = null
        val webSocket = client().newWebSocket(
            Request.Builder().url(server.url("/ws")).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    receivedText = text
                    received.countDown()
                }
            },
        )
        try {
            assertTrue(received.await(5, TimeUnit.SECONDS), "the client never received the WebSocket message")
            assertEquals("hello under a cap", receivedText)
        } finally {
            webSocket.close(1000, null)
        }
    }

    @Test
    fun `a mocked response still travels through the simulated latency`() {
        applyMockRules(listOf(MockRule(id = "m", matcher = MockMatcher(urlPattern = "/mocked"), response = MockResponseSpec(body = "canned"))))
        applyNetworkConditions(listOf(conditionRule(NetworkCondition(latencyMs = LATENCY_MS))))
        val request = Request.Builder().url(server.url("/mocked")).build()

        val body = client().newCall(request).execute().use { it.body.string() }

        val received = events.filterIsInstance<ResponseReceived>().single().response
        assertEquals("canned", body)
        assertEquals(true, received.fromMock)
        assertEquals(LATENCY_MS, received.condition?.addedLatencyMs)
        assertTrue(received.durationMs >= LATENCY_MS)
    }

    @OptIn(InternalJetWhaleApi::class)
    @Test
    fun `conditions are dropped when the host disconnects`() {
        server.enqueue(MockResponse().setResponseCode(200))
        applyNetworkConditions(listOf(conditionRule(NetworkCondition(offline = true))))

        runBlocking { agent.dispatchDisconnected() }
        client().newCall(Request.Builder().url(server.url("/items")).build()).execute().close()

        assertNull(events.filterIsInstance<ResponseReceived>().single().response.condition)
    }

    private fun conditionRule(condition: NetworkCondition) = NetworkConditionRule(id = "rule", name = "Test network", enabled = true, matcher = null, condition = condition)

    /** Delivers a SetNetworkConditions request to the agent's handlers through a real peer pair. */
    @OptIn(InternalJetWhaleApi::class)
    private fun applyNetworkConditions(rules: List<NetworkConditionRule>) = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        lateinit var agentPeer: JetWhalePluginPeer
        val hostPeer = JetWhalePluginPeer(JetWhaleNetworkAgentPlugin.PLUGIN_ID, scope, sendFrame = { agentPeer.onFrame(it) })
        agentPeer = JetWhalePluginPeer(JetWhaleNetworkAgentPlugin.PLUGIN_ID, scope, sendFrame = hostPeer::onFrame)
        agentPeer.configure { agent.registerHandlers(this) }
        hostPeer.messenger.request(SetNetworkConditions(rules))
        scope.cancel()
    }

    /** Delivers a SetMockRules request to the agent's handlers through a real peer pair. */
    @OptIn(InternalJetWhaleApi::class)
    private fun applyMockRules(rules: List<MockRule>) = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        lateinit var agentPeer: JetWhalePluginPeer
        val hostPeer = JetWhalePluginPeer(JetWhaleNetworkAgentPlugin.PLUGIN_ID, scope, sendFrame = { agentPeer.onFrame(it) })
        agentPeer = JetWhalePluginPeer(JetWhaleNetworkAgentPlugin.PLUGIN_ID, scope, sendFrame = hostPeer::onFrame)
        agentPeer.configure { agent.registerHandlers(this) }
        hostPeer.messenger.request(SetMockRules(rules))
        scope.cancel()
    }

    companion object {
        private const val SMALL_BODY_CAP = 100

        /** Paced at [PACED_RATE], this body takes 600 ms; the assertions allow for timer slack. */
        private const val PACED_BODY_BYTES = 60_000
        private const val PACED_RATE = 100_000L
        private const val PACED_MIN_MS = 500L

        private const val LATENCY_MS = 300L

        /** Not valid UTF-8: decoding these as text would destroy them, which is the point of Base64. */
        private val IMAGE_BYTES = ByteArray(300) { (it * 7).toByte() }
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
