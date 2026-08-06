package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RESOLUTION_TIMEOUT_MILLIS = 10_000L

/** Comfortably under the shortest backoff (1s), so waiting one out would blow this window. */
private const val BACKOFF_FREE_WINDOW_MILLIS = 800L

/** Records every address dialled, and refuses all of them except those named in [reachable]. */
private class RecordingSocketClient(private val reachable: Set<ResolvedEndpoint> = emptySet()) : JetWhaleSocketClient {
    val attempts: MutableList<ResolvedEndpoint> = Collections.synchronizedList(mutableListOf())
    val attemptCount: Channel<ResolvedEndpoint> = Channel(Channel.UNLIMITED)
    val connected: CompletableDeferred<ResolvedEndpoint> = CompletableDeferred()

    private var debuggerEvents: Channel<JetWhaleDebuggerEvent> = Channel(Channel.UNLIMITED)

    override suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent) = Unit

    override suspend fun openConnection(host: String, port: Int): JetWhaleConnection {
        val endpoint = ResolvedEndpoint(host, port)
        attempts.add(endpoint)
        attemptCount.send(endpoint)
        if (endpoint !in reachable) throw IllegalStateException("unreachable")
        connected.complete(endpoint)
        debuggerEvents = Channel(Channel.UNLIMITED)
        return JetWhaleConnection(
            negotiationResult = ClientSessionNegotiationResult.Success(availablePluginIds = emptyList()),
            debuggerEventFlow = debuggerEvents.receiveAsFlow(),
        )
    }

    override suspend fun closeConnection() {
        debuggerEvents.close()
    }
}

/** Hands out each candidate list in turn, standing on the last once they run out. */
private class ScriptedEndpointResolver(private val rounds: List<List<ResolvedEndpoint>>) : EndpointResolver {
    private var index = 0

    override suspend fun resolve(): List<ResolvedEndpoint> = rounds[minOf(index++, rounds.lastIndex)]
}

@OptIn(InternalJetWhaleApi::class)
class MessagingServiceResolutionTest {
    private fun service(socketClient: JetWhaleSocketClient, resolver: EndpointResolver) = DefaultJetWhaleMessagingService(
        socketClient = socketClient,
        pluginService = JetWhaleAgentPluginService(plugins = emptyList()),
    ).also { it.startService(resolver) }

    @Test
    fun `a candidate that refuses is passed over for the next one in the same round`() = runBlocking {
        // The point of the whole list: a host that answers discovery but refuses connections must not
        // strand the session. Both are dialled before any backoff is owed.
        val unreachable = ResolvedEndpoint("unreachable", 1)
        val reachable = ResolvedEndpoint("reachable", 2)
        val socketClient = RecordingSocketClient(reachable = setOf(reachable))
        val service = service(socketClient, ScriptedEndpointResolver(listOf(listOf(unreachable, reachable))))

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) { socketClient.connected.await() }
        } finally {
            service.stopService()
        }

        assertEquals(listOf(unreachable, reachable), socketClient.attempts.take(2))
    }

    @Test
    fun `the fallback is reached when the discovered candidate refuses`() = runBlocking {
        // Resolving once per round is not enough on its own: before the chain, a discovered host that
        // refused was re-picked every round and the configured fallback was never dialled at all.
        val discovered = ResolvedEndpoint("192.168.3.26", 5443)
        val fallback = ResolvedEndpoint("localhost", 5443)
        val socketClient = RecordingSocketClient(reachable = setOf(fallback))
        val service = service(socketClient, ScriptedEndpointResolver(listOf(listOf(discovered, fallback))))

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) { socketClient.connected.await() }
        } finally {
            service.stopService()
        }

        assertEquals(fallback, socketClient.connected.getCompleted())
    }

    @Test
    fun `a session that ends reconnects without waiting out a backoff`() = runBlocking {
        // Refusals before the candidate that worked are not the round's verdict, so an ended session
        // is not treated as a failed round: it reconnects at once, as it did before candidates were
        // tried in turn.
        val refused = ResolvedEndpoint("refused", 1)
        val reachable = ResolvedEndpoint("reachable", 2)
        val socketClient = RecordingSocketClient(reachable = setOf(reachable))
        val service = service(socketClient, ScriptedEndpointResolver(listOf(listOf(refused, reachable))))

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) {
                socketClient.connected.await()
                // End the session; a backoff-free reconnect dials the whole round again promptly.
                socketClient.closeConnection()
                withTimeout(BACKOFF_FREE_WINDOW_MILLIS) {
                    while (socketClient.attempts.size < 4) socketClient.attemptCount.receive()
                }
            }
        } finally {
            service.stopService()
        }

        assertEquals(listOf(refused, reachable, refused, reachable), socketClient.attempts.take(4))
    }

    @Test
    fun `a resolver that throws is a failed round, not the end of the session`() = runBlocking {
        val reachable = ResolvedEndpoint("reachable", 1)
        val socketClient = RecordingSocketClient(reachable = setOf(reachable))
        var firstCall = true
        val service = service(socketClient) {
            // Resolution is not supposed to throw, but one escaping used to take the loop down with
            // it, leaving the agent silently dead for the life of the process.
            if (firstCall) {
                firstCall = false
                throw IllegalStateException("resolver blew up")
            }
            listOf(reachable)
        }

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) { socketClient.connected.await() }
        } finally {
            service.stopService()
        }

        assertEquals(reachable, socketClient.connected.getCompleted())
    }

    @Test
    fun `a spent round is resolved again rather than retried as it was`() = runBlocking {
        // A host started after the app is only reached by browsing again, so each round asks the
        // resolver afresh instead of reusing what the last one produced.
        val socketClient = RecordingSocketClient()
        val resolver = ScriptedEndpointResolver(
            listOf(
                listOf(ResolvedEndpoint("first-round", 1)),
                listOf(ResolvedEndpoint("second-round", 2)),
            ),
        )
        val service = service(socketClient, resolver)

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) {
                socketClient.attemptCount.receive()
                socketClient.attemptCount.receive()
            }
        } finally {
            service.stopService()
        }

        assertTrue(socketClient.attempts.size >= 2, "expected a second round, got ${socketClient.attempts}")
        assertEquals(ResolvedEndpoint("first-round", 1), socketClient.attempts[0])
        assertEquals(ResolvedEndpoint("second-round", 2), socketClient.attempts[1])
    }
}
