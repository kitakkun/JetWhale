package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
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
    httpClient: HttpClient
) : JetWhaleSocketClient {
    private var session: DefaultClientWebSocketSession? = null

    var sessionId: String? = null
        private set

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
        JetWhaleLogger.d("Configuring WebSocket session")

        // protocol version negotiation
        JetWhaleLogger.d("Starting protocol version negotiation")
        sendSerialized(JetWhaleAgentNegotiationRequest.ProtocolVersion(JetWhaleProtocolVersion.Current))
        val protocolNegotiationResponse: JetWhaleHostNegotiationResponse.ProtocolVersionResponse = receiveDeserialized()
        JetWhaleLogger.d("Received protocol version negotiation response: $protocolNegotiationResponse")

        when (protocolNegotiationResponse) {
            is JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept -> {
                JetWhaleLogger.d("Protocol version accepted: ${protocolNegotiationResponse.version}")
            }

            is JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Reject -> {
                JetWhaleLogger.e("Incompatible protocol version. Rejected by host: ${protocolNegotiationResponse.reason}")
                throw IllegalStateException("Protocol version rejected by host: ${protocolNegotiationResponse.reason}")
            }
        }

        // sessionId negotiation
        JetWhaleLogger.d("Starting session negotiation")
        if (sessionId == null) {
            JetWhaleLogger.d("Requesting new session")
        } else {
            JetWhaleLogger.d("Resuming existing session with sessionId: $sessionId")
        }
        sendSerialized(
            JetWhaleAgentNegotiationRequest.Session(
                sessionId = sessionId,
                sessionName = getDeviceModelName(),
            )
        )
        JetWhaleLogger.d("Sent session negotiation request" + " with sessionId: $sessionId".takeIf { sessionId != null })

        sessionId = receiveDeserialized<JetWhaleHostNegotiationResponse.AcceptSession>().sessionId
        JetWhaleLogger.d("Received session negotiation accept with sessionId: $sessionId")

        // TODO: Capabilities negotiation (currently not implemented)

        // TODO: Available plugins negotiation (currently not implemented)

        closeReason.invokeOnCompletion {
            JetWhaleLogger.d("WebSocket session closed: $closeReason")
            session = null
        }

        JetWhaleLogger.d("WebSocket session configured successfully with sessionId: $sessionId. Listening for messages...")

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
