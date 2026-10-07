package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.plugin.DefaultPluginInstanceService
import com.kitakkun.jetwhale.host.data.plugin.DefaultPluginSessionReconciliationService
import com.kitakkun.jetwhale.host.data.plugin.SinglePluginFactoryRepository
import com.kitakkun.jetwhale.host.data.server.negotiation.PluginNegotiationResult
import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationResult
import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationStrategy
import com.kitakkun.jetwhale.host.data.server.negotiation.SessionNegotiationResult
import com.kitakkun.jetwhale.host.data.session.DefaultDebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostDiscoveryAdvertiser
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.util.logging.Logger
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class DefaultDebugWebSocketServerTest {
    private val ktorWebSocketServer = KtorWebSocketServer(
        json = JetWhaleJson,
        negotiationStrategy = AcceptingNegotiationStrategy(),
        sslCertificateManager = mock(),
    )
    private val sessionRepository = DefaultDebugSessionRepository()
    private val enabledPluginsRepository = FakeEnabledPluginsRepository(setOf(PLUGIN_ID))
    private val pluginFactoryRepository = SinglePluginFactoryRepository(
        LoadedHostPlugin(
            manifest = JetWhaleHostPluginManifest(
                pluginId = PLUGIN_ID,
                pluginName = "Test",
                version = "1.0.0",
                factoryClass = "com.example.TestFactory",
            ),
            factory = object : JetWhaleHostPluginFactory {
                override fun createPlugin(): JetWhaleHostPlugin = RequestingOnPreparePlugin()
            },
        ),
    )
    private val pluginInstanceService = DefaultPluginInstanceService(
        pluginFactoryRepository = pluginFactoryRepository,
        frameSender = DefaultHostPluginFrameSender(ktorWebSocketServer),
        pluginDataStoreRepository = mock<PluginDataStoreRepository> {
            every { storageFor(any()) } returns mock()
        },
    )
    private val server = DefaultDebugWebSocketServer(
        adbAutoPortMappingService = mock(),
        sessionRepository = sessionRepository,
        pluginInstanceService = pluginInstanceService,
        settingsRepository = mock<DebuggerSettingsRepository> {
            everySuspend { readAdbAutoPortMappingEnabled() } returns false
        },
        reconciliationService = DefaultPluginSessionReconciliationService(
            sessionRepository = sessionRepository,
            enabledPluginsRepository = enabledPluginsRepository,
            pluginFactoryRepository = pluginFactoryRepository,
            pluginInstanceService = pluginInstanceService,
        ),
        ktorWebSocketServer = ktorWebSocketServer,
        hostDiscoveryAdvertiser = mock<HostDiscoveryAdvertiser> {
            every { start() } returns Unit
            every { stop() } returns Unit
        },
    )

    @Test
    fun `re-enabling a plugin tells every agent it is active before the plugin's first request`() = runBlocking {
        val port = ServerSocket(0).use(ServerSocket::getLocalPort)
        val client = HttpClient(CIO) { install(WebSockets) }
        server.start(host = "localhost", port = port, wssPort = null)
        try {
            val eventSummariesPerAgent = List(AGENT_COUNT) { connectAgent(client, port) }
            eventSummariesPerAgent.forEach { assertEquals(listOf(ACTIVATED, PREPARE_REQUEST), it.receive(2)) }

            enabledPluginsRepository.setPluginEnabled(PLUGIN_ID, false)
            eventSummariesPerAgent.forEach { assertEquals(listOf(DEACTIVATED), it.receive(1)) }

            enabledPluginsRepository.setPluginEnabled(PLUGIN_ID, true)
            eventSummariesPerAgent.forEach { assertEquals(listOf(ACTIVATED, PREPARE_REQUEST), it.receive(2)) }
        } finally {
            coroutineContext.cancelChildren()
            client.close()
            server.stop()
        }
    }

    /** Connects one agent to the server and returns what it receives, in arrival order. */
    private fun CoroutineScope.connectAgent(client: HttpClient, port: Int): ReceiveChannel<String> {
        val received = Channel<String>(Channel.UNLIMITED)
        launch {
            client.webSocket(host = "localhost", port = port, path = "/") {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    received.send(JetWhaleJson.decodeFromString<JetWhaleDebuggerEvent>(frame.readText()).summary())
                }
            }
        }
        return received
    }

    private suspend fun ReceiveChannel<String>.receive(count: Int): List<String> = withTimeout(10.seconds) { List(count) { receive() } }

    private fun JetWhaleDebuggerEvent.summary(): String = when (this) {
        is JetWhaleDebuggerEvent.PluginActivated -> "activated $pluginId"

        is JetWhaleDebuggerEvent.PluginDeactivated -> "deactivated $pluginId"

        is JetWhaleDebuggerEvent.PluginFrameMessage -> when (val frame = frame) {
            is PluginFrame.Request -> "request ${frame.messageType} for ${frame.pluginId}"
            is PluginFrame.Notification -> "notification ${frame.messageType} for ${frame.pluginId}"
            is PluginFrame.Reply -> "reply for ${frame.pluginId}"
        }
    }

    private class RequestingOnPreparePlugin : JetWhaleMessagingHostPlugin() {
        override suspend fun onPrepare() {
            messenger.requestRaw(messageType = PREPARE_REQUEST_TYPE, payload = "{}", timeout = null)
        }
    }

    private class AcceptingNegotiationStrategy : ServerSessionNegotiationStrategy {
        context(logger: Logger)
        override suspend fun DefaultWebSocketServerSession.negotiate(): ServerSessionNegotiationResult = ServerSessionNegotiationResult.Success(
            session = SessionNegotiationResult(sessionId = UUID.randomUUID().toString(), sessionName = "test", appMetadata = JetWhaleAppMetadata()),
            plugin = PluginNegotiationResult(listOf(JetWhalePluginInfo(PLUGIN_ID, "1.0.0"))),
        )
    }

    private class FakeEnabledPluginsRepository(enabledPluginIds: Set<String>) : EnabledPluginsRepository {
        override val enabledPluginIdsFlow: MutableStateFlow<Set<String>> = MutableStateFlow(enabledPluginIds)
        override val disabledPluginIdFlow: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)

        override suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
            if (enabled) {
                enabledPluginIdsFlow.value += pluginId
            } else {
                enabledPluginIdsFlow.value -= pluginId
                disabledPluginIdFlow.emit(pluginId)
            }
        }

        override suspend fun isPluginEnabled(pluginId: String): Boolean = pluginId in enabledPluginIdsFlow.value
    }

    private companion object {
        const val PLUGIN_ID = "com.example.plugin"

        /** Several agents, as in a host serving more than one app: each instance created races the activations sent after it. */
        const val AGENT_COUNT = 4
        const val PREPARE_REQUEST_TYPE = "com.example.plugin/prepare"
        const val ACTIVATED = "activated $PLUGIN_ID"
        const val DEACTIVATED = "deactivated $PLUGIN_ID"
        const val PREPARE_REQUEST = "request $PREPARE_REQUEST_TYPE for $PLUGIN_ID"
    }
}
