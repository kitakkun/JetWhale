package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val AWAIT_TIMEOUT_MILLIS = 5_000L

/** How long a reconnect is given to (not) happen after the service was stopped. */
private const val RECONNECT_WINDOW_MILLIS = 500L

/**
 * A socket client whose connections never end on their own, so the only thing that can take one
 * down is the service itself.
 */
private class FakeSocketClient : JetWhaleSocketClient {
    val openedConnections: Channel<Unit> = Channel(Channel.UNLIMITED)
    val closed: CompletableDeferred<Unit> = CompletableDeferred()

    private var debuggerEvents: Channel<JetWhaleDebuggerEvent> = Channel(Channel.UNLIMITED)

    override suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent) = Unit

    override suspend fun openConnection(endpoint: ResolvedEndpoint): JetWhaleConnection {
        debuggerEvents = Channel(Channel.UNLIMITED)
        openedConnections.send(Unit)
        return JetWhaleConnection(
            negotiationResult = ClientSessionNegotiationResult.Success(availablePluginIds = listOf(PLUGIN_ID)),
            debuggerEventFlow = debuggerEvents.receiveAsFlow(),
        )
    }

    override suspend fun closeConnection() {
        // Ending the event flow is what a real close does to the collector: a reconnect loop that is
        // still running would immediately open the next connection.
        debuggerEvents.close()
        closed.complete(Unit)
    }
}

private const val PLUGIN_ID = "stop-test"

private class RecordingPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String = PLUGIN_ID
    override val pluginVersion: String = "1.0.0"

    val prepared: CompletableDeferred<Unit> = CompletableDeferred()
    val disconnected: CompletableDeferred<Unit> = CompletableDeferred()

    override suspend fun onPrepare() {
        prepared.complete(Unit)
    }

    override suspend fun onDisconnected() {
        disconnected.complete(Unit)
    }
}

@OptIn(InternalJetWhaleApi::class)
class MessagingServiceStopTest {
    private fun startedService(
        socketClient: FakeSocketClient,
        plugin: RecordingPlugin,
    ): JetWhaleMessagingService = DefaultJetWhaleMessagingService(
        socketClient = socketClient,
        pluginService = JetWhaleAgentPluginService(plugins = listOf(plugin)),
    ).also { it.startService(FixedEndpointResolver(ResolvedEndpoint("localhost", 1, useWss = false))) }

    @Test
    fun `stopService closes the socket so the host sees the session go away`() = runBlocking {
        val socketClient = FakeSocketClient()
        val plugin = RecordingPlugin()
        val service = startedService(socketClient, plugin)

        withTimeout(AWAIT_TIMEOUT_MILLIS) { plugin.prepared.await() }

        service.stopService()

        withTimeout(AWAIT_TIMEOUT_MILLIS) { socketClient.closed.await() }
    }

    @Test
    fun `stopService disconnects the plugins`() = runBlocking {
        val socketClient = FakeSocketClient()
        val plugin = RecordingPlugin()
        val service = startedService(socketClient, plugin)

        withTimeout(AWAIT_TIMEOUT_MILLIS) { plugin.prepared.await() }

        service.stopService()

        withTimeout(AWAIT_TIMEOUT_MILLIS) { plugin.disconnected.await() }
    }

    @Test
    fun `stopService leaves no reconnect loop behind`() = runBlocking {
        val socketClient = FakeSocketClient()
        val plugin = RecordingPlugin()
        val service = startedService(socketClient, plugin)

        withTimeout(AWAIT_TIMEOUT_MILLIS) { socketClient.openedConnections.receive() }
        withTimeout(AWAIT_TIMEOUT_MILLIS) { plugin.prepared.await() }

        service.stopService()
        withTimeout(AWAIT_TIMEOUT_MILLIS) { socketClient.closed.await() }

        assertNull(
            withTimeoutOrNull(RECONNECT_WINDOW_MILLIS) { socketClient.openedConnections.receive() },
            "the stopped service opened another connection",
        )
    }

    @Test
    fun `stopService is idempotent`() = runBlocking {
        val socketClient = FakeSocketClient()
        val plugin = RecordingPlugin()
        val service = startedService(socketClient, plugin)

        withTimeout(AWAIT_TIMEOUT_MILLIS) { socketClient.openedConnections.receive() }
        withTimeout(AWAIT_TIMEOUT_MILLIS) { plugin.prepared.await() }

        service.stopService()
        withTimeout(AWAIT_TIMEOUT_MILLIS) { socketClient.closed.await() }
        service.stopService()

        assertTrue(socketClient.closed.isCompleted)
        assertNull(
            withTimeoutOrNull(RECONNECT_WINDOW_MILLIS) { socketClient.openedConnections.receive() },
            "the second stop restarted the connection loop",
        )
    }
}
