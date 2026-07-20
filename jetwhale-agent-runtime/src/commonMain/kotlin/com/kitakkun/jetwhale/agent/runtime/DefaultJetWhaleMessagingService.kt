package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

internal class DefaultJetWhaleMessagingService(
    private val socketClient: JetWhaleSocketClient,
    private val pluginService: JetWhaleAgentPluginService,
) : JetWhaleMessagingService {
    private val coroutineScope: CoroutineScope = CoroutineScope(messagingServiceCoroutineDispatcher() + SupervisorJob())
    private var keepAwakeJob: Job? = null
    private var retryCount = 0

    override fun startService(candidates: List<HostCandidate>) {
        JetWhaleLogger.i("Starting JetWhale Messaging Service; ${candidates.size} host candidate(s)")
        keepAwakeJob?.cancel()
        keepAwakeJob = coroutineScope.launch {
            while (isActive) {
                val connected = connectWalkingCandidates(candidates)
                if (!connected) {
                    // No candidate was reachable this pass; back off before walking the list again.
                    pluginService.disconnectAll()
                    retryCount++
                    val delayMillis = (retryCount * RETRY_DELAY_INCREMENT_MILLIS).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
                    delay(delayMillis)
                }
            }
        }
    }

    /**
     * Tries each candidate in order with a short per-candidate timeout. On the first that connects,
     * runs the session until it ends and returns true. Returns false when no candidate connected.
     */
    private suspend fun connectWalkingCandidates(candidates: List<HostCandidate>): Boolean {
        for (candidate in candidates) {
            val connection = try {
                withTimeoutOrNull(PER_CANDIDATE_TIMEOUT_MILLIS) {
                    socketClient.openConnection(candidate.host, candidate.port)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                JetWhaleLogger.d("Candidate ${candidate.host}:${candidate.port} (${candidate.source}) failed: ${e.message}")
                null
            } ?: continue

            JetWhaleLogger.i("Connected to ${candidate.host}:${candidate.port} (${candidate.source})")
            retryCount = 0
            runSession(connection)
            return true
        }
        return false
    }

    private suspend fun runSession(connection: JetWhaleConnection) {
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

    companion object {
        private const val RETRY_DELAY_INCREMENT_MILLIS = 1000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 5000L

        // Per-candidate connect timeout: short so an unreachable/stale address falls through to the
        // next candidate quickly instead of stalling the whole walk.
        private const val PER_CANDIDATE_TIMEOUT_MILLIS = 2000L
    }
}
