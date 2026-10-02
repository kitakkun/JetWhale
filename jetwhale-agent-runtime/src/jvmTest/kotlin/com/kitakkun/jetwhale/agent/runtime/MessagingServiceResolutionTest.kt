@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RESOLUTION_TIMEOUT_MILLIS = 10_000L

/** Comfortably under the shortest backoff (1s), so waiting one out would blow this window. */
private const val BACKOFF_FREE_WINDOW_MILLIS = 800L

/** Comfortably over the two seconds a session has to last before it counts as having worked. */
private const val HELD_SESSION_MILLIS = 2_500L

/** Records every address dialled, and refuses all of them except those named in [reachable]. */
private class RecordingSocketClient(
    private val reachable: Set<ResolvedEndpoint> = emptySet(),
    private val closeImmediately: Boolean = false,
) : JetWhaleSocketClient {
    val attempts: MutableList<ResolvedEndpoint> = Collections.synchronizedList(mutableListOf())
    val attemptCount: Channel<ResolvedEndpoint> = Channel(Channel.UNLIMITED)
    val connected: CompletableDeferred<ResolvedEndpoint> = CompletableDeferred()

    private var debuggerEvents: Channel<JetWhaleDebuggerEvent> = Channel(Channel.UNLIMITED)

    override suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent) = Unit

    override suspend fun openConnection(endpoint: ResolvedEndpoint): JetWhaleConnection {
        attempts.add(endpoint)
        attemptCount.send(endpoint)
        if (endpoint !in reachable) throw IllegalStateException("unreachable")
        connected.complete(endpoint)
        debuggerEvents = Channel(Channel.UNLIMITED)
        if (closeImmediately) debuggerEvents.close()
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
    @Test
    fun `a candidate that refuses is passed over for the next one in the same round`() = runBlocking {
        val unreachable = ResolvedEndpoint("unreachable", 1, useWss = false)
        val reachable = ResolvedEndpoint("reachable", 2, useWss = false)
        val socketClient = RecordingSocketClient(reachable = setOf(reachable))
        val service = service(socketClient, ScriptedEndpointResolver(listOf(listOf(unreachable, reachable))))

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) { socketClient.connected.await() }
        } finally {
            service.stopService()
        }

        assertEquals(listOf(unreachable, reachable), socketClient.attempts.take(2))
    }

    private fun service(socketClient: JetWhaleSocketClient, resolver: EndpointResolver) = DefaultJetWhaleMessagingService(
        socketClient = socketClient,
        pluginService = JetWhaleAgentPluginService(plugins = emptyList()),
    ).also { it.startService(resolver) }

    @Test
    fun `the fallback is reached when the discovered candidate refuses`() = runBlocking {
        val discovered = ResolvedEndpoint("192.168.3.26", 5443, useWss = false)
        val fallback = ResolvedEndpoint("localhost", 5443, useWss = false)
        val socketClient = RecordingSocketClient(reachable = setOf(fallback))
        val service = service(socketClient, ScriptedEndpointResolver(listOf(listOf(discovered, fallback))))

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) { socketClient.connected.await() }
        } finally {
            service.stopService()
        }

        assertEquals(fallback, socketClient.connected.getCompleted())
    }

    // The service decides a session held by reading a monotonic clock, so the hold has to be real
    // time: under virtual time the session would measure as instant and the round as failed.
    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `a session that held reconnects without waiting out a backoff`() = runBlocking {
        val refused = ResolvedEndpoint("refused", 1, useWss = false)
        val reachable = ResolvedEndpoint("reachable", 2, useWss = false)
        val socketClient = RecordingSocketClient(reachable = setOf(reachable))
        val service = service(socketClient, ScriptedEndpointResolver(listOf(listOf(refused, reachable))))

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) {
                socketClient.connected.await()
                delay(HELD_SESSION_MILLIS)
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
    fun `a host that accepts and drops straight away does not spin the loop`() = runBlocking {
        val flapping = ResolvedEndpoint("flapping", 1, useWss = false)
        val socketClient = RecordingSocketClient(reachable = setOf(flapping), closeImmediately = true)
        val service = service(socketClient, ScriptedEndpointResolver(listOf(listOf(flapping))))

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) {
                socketClient.attemptCount.receive()
                socketClient.attemptCount.receive()
            }
            val third = withTimeoutOrNull(BACKOFF_FREE_WINDOW_MILLIS) { socketClient.attemptCount.receive() }
            assertEquals(null, third, "expected the loop to be backing off, got another attempt")
        } finally {
            service.stopService()
        }
    }

    @Test
    fun `a resolver that throws is a failed round, not the end of the session`() = runBlocking {
        val reachable = ResolvedEndpoint("reachable", 1, useWss = false)
        val socketClient = RecordingSocketClient(reachable = setOf(reachable))
        var firstCall = true
        val service = service(socketClient) {
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
        val socketClient = RecordingSocketClient()
        val resolver = ScriptedEndpointResolver(
            listOf(
                listOf(ResolvedEndpoint("first-round", 1, useWss = false)),
                listOf(ResolvedEndpoint("second-round", 2, useWss = false)),
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
        assertEquals(ResolvedEndpoint("first-round", 1, useWss = false), socketClient.attempts[0])
        assertEquals(ResolvedEndpoint("second-round", 2, useWss = false), socketClient.attempts[1])
    }
}
