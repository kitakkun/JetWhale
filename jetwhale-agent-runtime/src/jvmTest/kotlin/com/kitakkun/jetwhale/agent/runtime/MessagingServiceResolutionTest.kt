package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RESOLUTION_TIMEOUT_MILLIS = 10_000L

/** A socket client that never connects, so the service keeps retrying and keeps re-resolving. */
private class UnreachableSocketClient : JetWhaleSocketClient {
    val attemptedEndpoints: MutableList<ResolvedEndpoint> = Collections.synchronizedList(mutableListOf())
    val secondAttempt: CompletableDeferred<Unit> = CompletableDeferred()

    override suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent) = Unit

    override suspend fun openConnection(host: String, port: Int): JetWhaleConnection {
        attemptedEndpoints.add(ResolvedEndpoint(host, port))
        if (attemptedEndpoints.size >= 2) secondAttempt.complete(Unit)
        throw IllegalStateException("unreachable")
    }

    override suspend fun closeConnection() = Unit
}

/** Hands out each address in turn, standing on the last one once they run out. */
private class ScriptedEndpointResolver(private val script: List<ResolvedEndpoint>) : EndpointResolver {
    private var index = 0

    override suspend fun resolve(): ResolvedEndpoint = script[minOf(index++, script.lastIndex)]
}

@OptIn(InternalJetWhaleApi::class)
class MessagingServiceResolutionTest {
    private fun service(socketClient: JetWhaleSocketClient, resolver: EndpointResolver) = DefaultJetWhaleMessagingService(
        socketClient = socketClient,
        pluginService = JetWhaleAgentPluginService(plugins = emptyList()),
    ).also { it.startService(resolver) }

    @Test
    fun `the address is resolved again for every connection attempt`() = runBlocking {
        val socketClient = UnreachableSocketClient()
        val resolver = ScriptedEndpointResolver(listOf(ResolvedEndpoint("first", 1), ResolvedEndpoint("second", 2)))
        val service = service(socketClient, resolver)

        try {
            // Wait for the second *attempt*, not the second resolution: resolve() returns before the
            // address it produced has been dialled, so waiting on the resolver would leave a window
            // where only one attempt has been recorded.
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) { socketClient.secondAttempt.await() }
        } finally {
            service.stopService()
        }

        // A resolver consulted once per session would have produced "first" twice.
        assertTrue(socketClient.attemptedEndpoints.size >= 2, "expected a retry, got ${socketClient.attemptedEndpoints}")
        assertEquals(ResolvedEndpoint("first", 1), socketClient.attemptedEndpoints[0])
        assertEquals(ResolvedEndpoint("second", 2), socketClient.attemptedEndpoints[1])
    }

    @Test
    fun `an address that only becomes reachable later is still dialled`() = runBlocking {
        // The host was not up at startup, so the first resolution yields the unreachable fallback and a
        // later one yields the host that has since appeared. Without per-attempt resolution the session
        // would stay pinned to the fallback for good.
        val socketClient = UnreachableSocketClient()
        val resolver = ScriptedEndpointResolver(
            listOf(ResolvedEndpoint("localhost", 5443), ResolvedEndpoint("192.168.3.26", 5443)),
        )
        val service = service(socketClient, resolver)

        try {
            withTimeout(RESOLUTION_TIMEOUT_MILLIS) { socketClient.secondAttempt.await() }
        } finally {
            service.stopService()
        }

        assertEquals(ResolvedEndpoint("192.168.3.26", 5443), socketClient.attemptedEndpoints[1])
    }
}
