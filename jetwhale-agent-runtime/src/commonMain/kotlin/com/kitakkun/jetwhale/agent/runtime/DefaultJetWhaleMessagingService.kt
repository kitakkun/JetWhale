package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

internal class DefaultJetWhaleMessagingService(
    private val socketClient: JetWhaleSocketClient,
    private val pluginService: JetWhaleAgentPluginService,
) : JetWhaleMessagingService {
    private val coroutineScope: CoroutineScope = CoroutineScope(messagingServiceCoroutineDispatcher() + SupervisorJob())
    private var keepAwakeJob: Job? = null
    private var retryCount = 0
    private var lastReportedFailure: String? = null

    override fun startService(resolver: EndpointResolver) {
        JetWhaleLogger.i("Starting JetWhale Messaging Service")
        keepAwakeJob?.cancel()
        keepAwakeJob = coroutineScope.launch {
            while (isActive) {
                // Resolved per round rather than once up front: an address that only becomes correct
                // later is reached without restarting the session.
                val failures = tryEachCandidate(resolver.resolve())
                // Only once the whole round is spent: a reachable host further down the list should
                // not be kept waiting by the backoff owed to the ones before it.
                reportRoundFailed(failures)
                pluginService.disconnectAll()
                retryCount++
                delay((retryCount * RETRY_DELAY_INCREMENT_MILLIS).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS))
            }
        }
    }

    override fun stopService() {
        JetWhaleLogger.i("Stopping JetWhale Messaging Service")
        val connectionJob = keepAwakeJob ?: return
        keepAwakeJob = null
        coroutineScope.launch {
            // Join, don't just cancel: the loop must be fully wound down before the teardown below,
            // or the connection's own `finally` would interleave with it and drop the peers twice.
            connectionJob.cancelAndJoin()
            // A cancelled connection cannot run its own suspending teardown, so close the socket and
            // drop the peers here, out of reach of the cancellation.
            withContext(NonCancellable) {
                socketClient.closeConnection()
                pluginService.disconnectAll()
            }
        }.invokeOnCompletion {
            coroutineScope.cancel()
        }
    }

    /**
     * Works down [candidates] until one connects, and returns why each of them did not.
     *
     * Returns only once the list is spent — a connection that succeeds keeps this suspended for as
     * long as it lasts, and the candidates after it are never dialled.
     */
    private suspend fun tryEachCandidate(candidates: List<ResolvedEndpoint>): List<CandidateFailure> {
        val failures = mutableListOf<CandidateFailure>()
        for (candidate in candidates) {
            try {
                // The cap covers establishment only — never the session that follows, which is meant
                // to last. Establishment is the CA fetch, the TLS handshake, the upgrade and the
                // negotiation together, and that has been measured at 13s on a physical device over
                // Wi-Fi, half of it spent in the CA fetch's plain-channel probe. The cap is therefore
                // generous: its job is to bound a candidate that swallows packets — a firewall that
                // drops rather than refuses — not to be tight.
                val connection = withTimeoutOrNull(CANDIDATE_TIMEOUT_MILLIS) {
                    socketClient.openConnection(candidate.host, candidate.port)
                }
                if (connection == null) {
                    failures += CandidateFailure(candidate, "timed out after ${CANDIDATE_TIMEOUT_MILLIS}ms")
                    JetWhaleLogger.d("Gave up on $candidate after ${CANDIDATE_TIMEOUT_MILLIS}ms")
                } else {
                    // Suspends for as long as the connection lasts. Once it ends, the next round starts
                    // from the top of the list so a preferred candidate gets its turn back.
                    serveConnection(connection)
                    return failures
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failures += CandidateFailure(candidate, e.toString())
                JetWhaleLogger.d("Could not connect to $candidate", e)
            }
        }
        return failures
    }

    /**
     * Reports a round in which nothing accepted a connection. The socket client rethrows without
     * logging, so this is the only place a refused host, a wrong port or a failed TLS handshake
     * becomes visible — and at the default WARN level, the only sign the agent is doing anything.
     *
     * One line per round rather than one per candidate, so the same set of failures repeating is a
     * repeating message and stays suppressed until something about it changes.
     */
    private fun reportRoundFailed(failures: List<CandidateFailure>) {
        if (failures.isEmpty()) return
        val listed = failures.joinToString { "${it.endpoint} (${it.reason})" }
        val message = "No JetWhale host accepted a connection. Tried ${failures.size}: $listed"
        if (message == lastReportedFailure) return
        lastReportedFailure = message
        JetWhaleLogger.w("$message. Retrying with backoff until one does.")
    }

    /** Runs an established connection until it ends. */
    private suspend fun serveConnection(connection: JetWhaleConnection) {
        retryCount = 0
        lastReportedFailure = null

        pluginService.startConnection(
            scope = coroutineScope,
            sendFrame = { frame ->
                socketClient.sendDebuggeeEvent(JetWhaleDebuggeeEvent.PluginFrameMessage(frame))
            },
        )

        try {
            pluginService.syncActivePlugins(connection.negotiationResult.availablePluginIds.toSet())

            connection.debuggerEventFlow.collect { event ->
                when (event) {
                    is JetWhaleDebuggerEvent.PluginActivated -> pluginService.activatePlugin(event.pluginId)
                    is JetWhaleDebuggerEvent.PluginDeactivated -> pluginService.deactivatePlugin(event.pluginId)
                    is JetWhaleDebuggerEvent.PluginFrameMessage -> pluginService.onFrame(event.frame)
                }
            }
        } finally {
            // The connection ended (closed or errored); drop this connection's peers so the next
            // connection re-establishes them against a fresh socket. Plugins stay activated.
            pluginService.disconnectAll()
        }
    }

    /** Why one candidate did not take the connection, kept so the round can report itself as a whole. */
    private data class CandidateFailure(val endpoint: ResolvedEndpoint, val reason: String)

    companion object {
        private const val RETRY_DELAY_INCREMENT_MILLIS = 1000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 5000L
        private const val CANDIDATE_TIMEOUT_MILLIS = 30_000L
    }
}
