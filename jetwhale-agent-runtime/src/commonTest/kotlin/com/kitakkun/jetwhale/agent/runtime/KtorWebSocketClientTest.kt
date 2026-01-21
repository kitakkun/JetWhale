package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
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
import io.ktor.websocket.send
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Native and Web targets are ignored because the Ktor WebSocket test engine is not supported there.
 */
@IgnoreWeb
@IgnoreNative
class KtorWebSocketClientTest {
    @Suppress("UnusedFlow")
    @Test
    fun `test fail to connect to non-existent server`() = testApplication {
        val webSocketClient = KtorWebSocketClient(json, client)

        assertFailsWith<Throwable> {
            webSocketClient.openConnection(
                host = TEST_SERVER_HOST,
                port = TEST_SERVER_PORT,
            )
        }
    }

    @Suppress("UnusedFlow")
    @Test
    fun `test connection established successfully`() = testApplication {
        configureTestServer()

        val webSocketClient = KtorWebSocketClient(json, client)

        webSocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )
    }

    @Suppress("UnusedFlow")
    @Test
    fun `test session Id generated from server`() = testApplication {
        configureTestServer()

        val webSocketClient = KtorWebSocketClient(json, client)

        webSocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )

        assertEquals(webSocketClient.sessionId, "session-1")
    }

    @Suppress("UnusedFlow")
    @Test
    fun `test session Id reused after resuming connection`() = testApplication {
        configureTestServer()

        val websocketClient = KtorWebSocketClient(json, client)

        websocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )

        val firstSessionId = websocketClient.sessionId!!

        // emulate unexpected disconnection
        client.close()

        websocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )

        val secondSessionId = websocketClient.sessionId!!

        assertEquals(firstSessionId, secondSessionId)
    }

    @Suppress("UnusedFlow")
    @Test
    fun `test send message`() = testApplication {
        configureTestServer()

        val webSocketClient = KtorWebSocketClient(json, client)

        webSocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )

        webSocketClient.sendMessage("pluginId", "message")
    }

    @Test
    fun `test receive serverMessage`() = testApplication {
        configureTestServer {
            send("serverMessage")
        }

        val webSocketClient = KtorWebSocketClient(json, client)

        val messageFlow = webSocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )

        assertEquals(
            expected = "serverMessage",
            actual = messageFlow.first()
        )
    }

    private fun ApplicationTestBuilder.configureTestServer(
        extraWebSocketSessionHandler: suspend WebSocketServerSession.() -> Unit = {},
    ) {
        var sessionNumber = 1

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
                val protocolVersionRequest: JetWhaleAgentNegotiationRequest.ProtocolVersion = receiveDeserialized()
                sendSerialized(
                    JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept(
                        version = protocolVersionRequest.version
                    )
                )

                val sessionNegotiationRequest: JetWhaleAgentNegotiationRequest.Session = receiveDeserialized()
                val sessionId = sessionNegotiationRequest.sessionId ?: "session-${sessionNumber++}"
                sendSerialized(JetWhaleHostNegotiationResponse.AcceptSession(sessionId))

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
