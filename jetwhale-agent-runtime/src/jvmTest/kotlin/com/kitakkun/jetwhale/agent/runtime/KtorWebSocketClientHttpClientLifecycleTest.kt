package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import com.kitakkun.test.annotations.IgnoreNative
import com.kitakkun.test.annotations.IgnoreWeb
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/** How long a released client is given to finish winding down before the test calls it a leak. */
private const val RELEASE_TIMEOUT_MILLIS = 5_000L

/**
 * The `HttpClient` is built per connection, so one that is never released strands its engine's
 * thread pool — and the reconnect loop allocates another on every attempt.
 *
 * Native and Web targets are ignored because the Ktor WebSocket test engine is not supported there.
 */
@IgnoreWeb
@IgnoreNative
class KtorWebSocketClientHttpClientLifecycleTest {
    @Test
    fun `releasing an owned client closes it`() {
        val client = HttpClient(CIO)

        ConnectionHttpClient(client = client, owned = true).releaseIfOwned()

        assertFalse(client.isActive)
    }

    @Test
    fun `releasing a borrowed client leaves it alone`() {
        val client = HttpClient(CIO)

        try {
            ConnectionHttpClient(client = client, owned = false).releaseIfOwned()

            assertTrue(client.isActive, "a client owned by the caller must survive the connection")
        } finally {
            client.close()
        }
    }

    @Test
    fun `closing the connection releases the client it was opened with`() = testApplication {
        configureTestServer()
        val provider = RecordingProvider(client)
        val webSocketClient = webSocketClient(provider)
        webSocketClient.openConnection(host = TEST_SERVER_HOST, port = TEST_SERVER_PORT)

        webSocketClient.closeConnection()

        assertReleased(provider.handedOut.single(), "the connection's own client was left open")
    }

    @Test
    fun `a failed connection attempt releases the client it allocated`() = testApplication {
        // No server is configured, so the attempt fails before there is a session to close later.
        val provider = RecordingProvider(client)
        val webSocketClient = webSocketClient(provider)

        assertFailsWith<Throwable> {
            webSocketClient.openConnection(host = TEST_SERVER_HOST, port = TEST_SERVER_PORT)
        }

        assertReleased(provider.handedOut.single(), "a refused attempt stranded its client")
    }

    @Test
    fun `a connection that ended on its own is released before the next one opens`() = testApplication {
        // The server hangs up straight away, so the connection ends without closeConnection ever
        // being called. That is the reconnect path, and the one that can strand a client unnoticed.
        configureTestServer(hangUpImmediately = true)
        val provider = RecordingProvider(client)
        val webSocketClient = webSocketClient(provider)

        val connection = webSocketClient.openConnection(host = TEST_SERVER_HOST, port = TEST_SERVER_PORT)
        connection.debuggerEventFlow.collect { }

        webSocketClient.openConnection(host = TEST_SERVER_HOST, port = TEST_SERVER_PORT)

        assertEquals(2, provider.handedOut.size)
        assertReleased(provider.handedOut.first(), "the ended connection's client was left open")
    }

    @Test
    fun `a borrowed client keeps serving connections after one of them is closed`() = testApplication {
        configureTestServer()
        val webSocketClient = KtorWebSocketClient(
            json = json,
            negotiationStrategy = NoopClientSessionNegotiationStrategy(),
            httpClient = client,
        )

        webSocketClient.openConnection(host = TEST_SERVER_HOST, port = TEST_SERVER_PORT)
        webSocketClient.closeConnection()

        // Every connection is served by the same view of the borrowed client, so closing one that
        // was never ours to close would leave this second connection nothing to run on.
        webSocketClient.openConnection(host = TEST_SERVER_HOST, port = TEST_SERVER_PORT)
    }

    /**
     * A closed client's job only completes once the requests under it do, so a released client is
     * awaited rather than sampled — sampling races the socket's own teardown.
     */
    private suspend fun assertReleased(client: HttpClient, message: String) {
        val released = withTimeoutOrNull(RELEASE_TIMEOUT_MILLIS) {
            client.coroutineContext.job.join()
            true
        }
        assertTrue(released == true, message)
    }

    private fun webSocketClient(provider: RecordingProvider) = KtorWebSocketClient(
        json = json,
        negotiationStrategy = NoopClientSessionNegotiationStrategy(),
        sslConfiguration = JetWhaleSslConfiguration(),
        httpClientProvider = provider,
    )

    /**
     * Stands in for the production provider: hands out a client per connection that the socket client
     * owns, and keeps every one of them so its state can be inspected afterwards.
     */
    private class RecordingProvider(
        private val engineSource: HttpClient,
    ) : (JetWhaleSslConfiguration) -> ConnectionHttpClient {
        val handedOut: MutableList<HttpClient> = mutableListOf()

        override fun invoke(configuration: JetWhaleSslConfiguration): ConnectionHttpClient {
            val client = engineSource.config {
                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(json)
                }
            }
            handedOut += client
            return ConnectionHttpClient(client = client, owned = true)
        }
    }

    private fun ApplicationTestBuilder.configureTestServer(hangUpImmediately: Boolean = false) {
        engine {
            connector {
                host = TEST_SERVER_HOST
                port = TEST_SERVER_PORT
            }
        }

        install(ServerWebSockets.Plugin) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }

        routing {
            webSocket {
                if (!hangUpImmediately) closeReason.await()
            }
        }
    }

    companion object Companion {
        private const val TEST_SERVER_HOST = "localhost"
        private const val TEST_SERVER_PORT = 50027

        @OptIn(InternalJetWhaleApi::class)
        private val json = JetWhaleJson
    }
}
