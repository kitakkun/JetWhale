package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.serialization.decodeFromStringOrNull
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

/**
 * A Ktor-based implementation of [JetWhaleSocketClient].
 */
internal class KtorWebSocketClient(
    private val json: Json,
    private val negotiationStrategy: ClientSessionNegotiationStrategy,
    httpClient: HttpClient
) : JetWhaleSocketClient {
    private var session: DefaultClientWebSocketSession? = null

    private val client: HttpClient = httpClient.config {
        configureHttpClient()
    }

    override suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent) {
        session?.sendSerialized(event)
    }

    override suspend fun openConnection(
        host: String,
        port: Int,
    ): JetWhaleConnection {
        val session = client.webSocketSession(
            host = host,
            port = port,
        )
        this.session = session
        return session.configureSession()
    }

    @OptIn(InternalJetWhaleApi::class)
    private suspend fun DefaultClientWebSocketSession.configureSession(): JetWhaleConnection {
        JetWhaleLogger.v("Configuring WebSocket session")

        val negotiationResult = with(negotiationStrategy) { negotiate() }

        when (negotiationResult) {
            is ClientSessionNegotiationResult.Success -> {
                JetWhaleLogger.d("Session negotiation succeeded: $negotiationResult")
            }

            is ClientSessionNegotiationResult.Failure -> {
                JetWhaleLogger.e("Session negotiation failed: ${negotiationResult.reason}")
                throw IllegalStateException("Session negotiation failed: ${negotiationResult.reason}")
            }
        }

        closeReason.invokeOnCompletion {
            JetWhaleLogger.i("WebSocket session closed")
            session = null
        }

        JetWhaleLogger.i("WebSocket session established")

        val debuggerEventFlow = incoming.consumeAsFlow().filterIsInstance<Frame.Text>().mapNotNull {
            json.decodeFromStringOrNull<JetWhaleDebuggerEvent>(it.readText())
        }

        return JetWhaleConnection(
            negotiationResult = negotiationResult,
            debuggerEventFlow = debuggerEventFlow,
        )
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
