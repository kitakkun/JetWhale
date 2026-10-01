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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
                val outcome = runRound(resolver)
                if (outcome is RoundResult.Served) continue

                reportRoundFailed((outcome as RoundResult.Failed).summary)
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
            connectionJob.cancelAndJoin()
            withContext(NonCancellable) {
                socketClient.closeConnection()
                pluginService.disconnectAll()
            }
        }.invokeOnCompletion {
            coroutineScope.cancel()
        }
    }

    /**
     * Resolves the candidates and works down them until one connects.
     *
     * A connection that succeeds keeps this suspended for as long as it lasts, and the candidates
     * after it are never dialled; the ones refused before it are not the round's verdict.
     */
    private suspend fun runRound(resolver: EndpointResolver): RoundResult {
        val candidates = try {
            resolver.resolve()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            JetWhaleLogger.d("Working out where to connect failed", e)
            return RoundResult.Failed("Could not work out where to connect: $e")
        }
        if (candidates.isEmpty()) return RoundResult.Failed("No address to connect to.")

        val failures = mutableListOf<CandidateFailure>()
        for (candidate in candidates) {
            try {
                // Covers establishment only, never the session. CA fetch, TLS handshake, upgrade
                // and negotiation have been measured at 13s on a physical device over Wi-Fi, so the
                // cap is generous: it bounds a candidate that drops packets rather than refusing
                // them.
                val connection = withTimeoutOrNull(CANDIDATE_TIMEOUT_MILLIS) {
                    socketClient.openConnection(candidate)
                }
                if (connection == null) {
                    failures += CandidateFailure(candidate, "timed out after ${CANDIDATE_TIMEOUT_MILLIS}ms")
                    JetWhaleLogger.d("Gave up on $candidate after ${CANDIDATE_TIMEOUT_MILLIS}ms")
                } else {
                    val startedAt = TimeSource.Monotonic.markNow()
                    serveConnection(connection)
                    val lasted = startedAt.elapsedNow()
                    if (lasted >= MIN_SESSION_TO_COUNT) {
                        retryCount = 0
                        lastReportedFailure = null
                        return RoundResult.Served
                    }
                    failures += CandidateFailure(candidate, "closed after $lasted")
                    JetWhaleLogger.d("$candidate closed the session after $lasted")
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                failures += CandidateFailure(candidate, e.toString())
                JetWhaleLogger.d("Could not connect to $candidate", e)
            }
        }
        val listed = failures.joinToString { "${it.endpoint} (${it.reason})" }
        return RoundResult.Failed("No JetWhale host accepted a connection. Tried ${failures.size}: $listed")
    }

    /**
     * Reports a round in which nothing accepted a connection. The socket client rethrows without
     * logging, so this is the only place a refused host, a wrong port or a failed TLS handshake
     * becomes visible — and at the default WARN level, the only sign the agent is doing anything.
     *
     * One line per round rather than one per candidate, so the same set of failures repeating is a
     * repeating message and stays suppressed until something about it changes.
     */
    private fun reportRoundFailed(summary: String) {
        if (summary == lastReportedFailure) return
        lastReportedFailure = summary
        JetWhaleLogger.w("$summary. Retrying with backoff until one does.")
    }

    /** Runs an established connection until it ends. */
    private suspend fun serveConnection(connection: JetWhaleConnection) {
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
            pluginService.disconnectAll()
        }
    }

    /** Why one candidate did not take the connection, kept so the round can report itself as a whole. */
    private data class CandidateFailure(val endpoint: ResolvedEndpoint, val reason: String)

    /**
     * How a round ended, decided once it has. Both cases are reached with nothing connected — a served
     * round reports a session that ran and has since ended — so this says what the round achieved,
     * not what the connection is doing now.
     */
    private sealed interface RoundResult {
        /** A session ran and held. Nothing is owed: the next round starts at once. */
        data object Served : RoundResult

        /** Nothing took the connection, so the round is reported and a backoff follows. */
        data class Failed(val summary: String) : RoundResult
    }

    companion object {
        private const val RETRY_DELAY_INCREMENT_MILLIS = 1000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 5000L
        private const val CANDIDATE_TIMEOUT_MILLIS = 30_000L

        /**
         * How long a session has to hold before it counts as having worked. Establishment alone takes
         * seconds, so anything ending inside this window ended on the host's side straight away.
         */
        private val MIN_SESSION_TO_COUNT = 2.seconds
    }
}
