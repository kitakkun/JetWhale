package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import com.kitakkun.test.annotations.IgnoreNative
import com.kitakkun.test.annotations.IgnoreWeb
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
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
    @Test
    fun `test fail to connect to non-existent server`() = testApplication {
        val webSocketClient = KtorWebSocketClient(
            json = json,
            negotiationStrategy = NoopClientSessionNegotiationStrategy(),
            httpClient = client,
        )

        assertFailsWith<Throwable> {
            webSocketClient.openConnection(
                host = TEST_SERVER_HOST,
                port = TEST_SERVER_PORT,
            )
        }
    }

    @Test
    fun `test connection established successfully`() = testApplication {
        configureTestServer()

        val webSocketClient = KtorWebSocketClient(
            json = json,
            negotiationStrategy = NoopClientSessionNegotiationStrategy(),
            httpClient = client,
        )

        webSocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )
    }

    @Test
    fun `test send message`() = testApplication {
        configureTestServer()

        val webSocketClient = KtorWebSocketClient(
            json = json,
            negotiationStrategy = NoopClientSessionNegotiationStrategy(),
            httpClient = client,
        )

        webSocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )

        webSocketClient.sendMessage(pluginId = "pluginId", message = "message")
    }

    @Test
    fun `test receive serverMessage`() = testApplication {
        configureTestServer {
            send(content = "serverMessage")
        }

        val webSocketClient = KtorWebSocketClient(
            json = json,
            negotiationStrategy = NoopClientSessionNegotiationStrategy(),
            httpClient = client,
        )

        val connectionResult = webSocketClient.openConnection(
            host = TEST_SERVER_HOST,
            port = TEST_SERVER_PORT,
        )

        assertEquals(
            expected = "serverMessage",
            actual = connectionResult.messageFlow.first()
        )
    }

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
