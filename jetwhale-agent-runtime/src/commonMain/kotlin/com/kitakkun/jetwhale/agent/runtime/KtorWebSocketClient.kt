package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * A Ktor-based implementation of [JetWhaleSocketClient].
 */
internal class KtorWebSocketClient(
    private val json: Json,
    private val sessionNegotiator: SessionNegotiator,
    httpClient: HttpClient
) : JetWhaleSocketClient {
    private var session: DefaultClientWebSocketSession? = null

    private val client: HttpClient = httpClient.config {
        configureHttpClient()
    }

    override suspend fun sendMessage(pluginId: String, message: String) {
        session?.send(message)
    }

    override suspend fun openConnection(
        host: String,
        port: Int,
    ): Flow<String> {
        val session = client.webSocketSession(
            host = host,
            port = port,
        )
        this.session = session
        return session.configureSession()
    }

    private suspend fun DefaultClientWebSocketSession.configureSession(): Flow<String> {
        JetWhaleLogger.v("Configuring WebSocket session")

        with(sessionNegotiator) { negotiate() }

        closeReason.invokeOnCompletion {
            JetWhaleLogger.i("WebSocket session closed")
            session = null
        }

        JetWhaleLogger.i("WebSocket session established")

        return incoming.consumeAsFlow().filterIsInstance<Frame.Text>().map { it.readText() }
    }

    private fun HttpClientConfig<*>.configureHttpClient() {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }

        install(Logging) {
            logger = JetWhaleLogger
            level = when (JetWhaleLogger.ktorLogLevel) {
                KtorLogLevel.ALL -> LogLevel.ALL
                KtorLogLevel.HEADERS -> LogLevel.HEADERS
                KtorLogLevel.BODY -> LogLevel.BODY
                KtorLogLevel.NONE -> LogLevel.NONE
            }
        }
    }
}
