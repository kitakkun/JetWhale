package com.kitakkun.jetwhale.agent.runtime

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultClientSessionNegotiationStrategyTest {
    private val logs = RecordingLogWriter()
    private val previousLogWriters = Logger.config.logWriterList

    @BeforeTest
    fun captureLogs() {
        Logger.setLogWriters(logs)
        JetWhaleLogger.setLogLevel(LogLevel.VERBOSE)
    }

    @AfterTest
    fun restoreLogs() {
        Logger.setLogWriters(previousLogWriters)
        JetWhaleLogger.setLogLevel(LogLevel.WARN)
    }

    @Test
    fun `a new session request is logged without a session id and a resumed one with the id it resumes`() = testApplication {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
        routing {
            webSocket { acceptNegotiation(sessionId = "assigned-session") }
        }
        val client = createClient { configureWebSocketClient(json) }
        val strategy = DefaultClientSessionNegotiationStrategy(plugins = emptyList(), appMetadata = JetWhaleAppMetadata(deviceName = "test-device"))

        repeat(2) { client.webSocket("/") { with(strategy) { negotiate() } } }

        assertEquals(
            listOf("Sent session negotiation request", "Sent session negotiation request with sessionId: assigned-session"),
            logs.messages.filter { it.startsWith("Sent session negotiation request") },
        )
    }

    private suspend fun WebSocketServerSession.acceptNegotiation(sessionId: String) {
        val version = receiveDeserialized<JetWhaleAgentNegotiationRequest.ProtocolVersion>().version
        sendSerialized(JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept(version))
        receiveDeserialized<JetWhaleAgentNegotiationRequest.Session>()
        sendSerialized(JetWhaleHostNegotiationResponse.AcceptSession(sessionId))
        receiveDeserialized<JetWhaleAgentNegotiationRequest.Capabilities>()
        sendSerialized(JetWhaleHostNegotiationResponse.CapabilitiesResponse(capabilities = emptyMap()))
        receiveDeserialized<JetWhaleAgentNegotiationRequest.AvailablePlugins>()
        sendSerialized(JetWhaleHostNegotiationResponse.AvailablePluginsResponse(availablePlugins = emptyList(), incompatiblePlugins = emptyList()))
    }

    private class RecordingLogWriter : LogWriter() {
        val messages = CopyOnWriteArrayList<String>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            messages += message
        }
    }

    private companion object {
        @OptIn(InternalJetWhaleApi::class)
        val json = JetWhaleJson
    }
}
