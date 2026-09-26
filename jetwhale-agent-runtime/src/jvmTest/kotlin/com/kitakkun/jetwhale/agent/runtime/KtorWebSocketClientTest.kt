package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import com.kitakkun.test.annotations.IgnoreNative
import com.kitakkun.test.annotations.IgnoreWeb
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Native and Web targets are ignored because the Ktor WebSocket test engine is not supported there.
 */
@IgnoreWeb
@IgnoreNative
class KtorWebSocketClientTest {
    @Test
    fun `test fail to connect to non-existent server`() = testApplication {
        val webSocketClient = webSocketClient()

        assertFailsWith<Throwable> {
            webSocketClient.openConnection(ResolvedEndpoint(TEST_SERVER_HOST, TEST_SERVER_PORT, useWss = false))
        }
    }

    @Test
    fun `test connection established successfully`() = testApplication {
        val serverSawSession = CompletableDeferred<Boolean>()
        configureTestServer { serverSawSession.complete(true) }

        val webSocketClient = webSocketClient()

        webSocketClient.openConnection(ResolvedEndpoint(TEST_SERVER_HOST, TEST_SERVER_PORT, useWss = false))

        assertTrue(withTimeout(5.seconds) { serverSawSession.await() })
    }

    @Test
    fun `test send message`() = testApplication {
        val received = CompletableDeferred<JetWhaleDebuggeeEvent>()
        configureTestServer { received.complete(receiveDeserialized<JetWhaleDebuggeeEvent>()) }
        val event = JetWhaleDebuggeeEvent.PluginFrameMessage(
            frame = PluginFrame.Notification(
                pluginId = "pluginId",
                messageType = "test/message",
                payload = "message",
            ),
        )

        val webSocketClient = webSocketClient()

        webSocketClient.openConnection(ResolvedEndpoint(TEST_SERVER_HOST, TEST_SERVER_PORT, useWss = false))

        webSocketClient.sendDebuggeeEvent(event)

        assertEquals(event, withTimeout(5.seconds) { received.await() })
    }

    @Test
    fun `test receive debugger event`() = testApplication {
        val expectedEvent = JetWhaleDebuggerEvent.PluginActivated(pluginId = "testPlugin")

        configureTestServer {
            sendSerialized(expectedEvent)
        }

        val webSocketClient = webSocketClient()

        val connectionResult = webSocketClient.openConnection(ResolvedEndpoint(TEST_SERVER_HOST, TEST_SERVER_PORT, useWss = false))

        assertEquals(
            expected = expectedEvent,
            actual = connectionResult.debuggerEventFlow.first(),
        )
    }

    /**
     * The client under test owns whatever the provider hands it and closes it when the connection
     * ends, so each connection gets a throwaway client rather than this application's own.
     */
    private fun ApplicationTestBuilder.webSocketClient() = KtorWebSocketClient(
        json = json,
        negotiationStrategy = NoopClientSessionNegotiationStrategy(),
        sslConfiguration = JetWhaleSslConfiguration(),
        httpClientProvider = { createClient { configureWebSocketClient(json) } },
    )

    private fun ApplicationTestBuilder.configureTestServer(
        extraWebSocketSessionHandler: suspend WebSocketServerSession.() -> Unit = {},
    ) {
        engine {
            connector {
                host = TEST_SERVER_HOST
                port = TEST_SERVER_PORT
            }
        }

        install(WebSockets.Plugin) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }

        routing {
            webSocket {
                extraWebSocketSessionHandler()
                closeReason.await()
            }
        }
    }

    companion object Companion {
        private const val TEST_SERVER_HOST = "localhost"
        private const val TEST_SERVER_PORT = 50026

        @OptIn(InternalJetWhaleApi::class)
        private val json = JetWhaleJson
    }
}
