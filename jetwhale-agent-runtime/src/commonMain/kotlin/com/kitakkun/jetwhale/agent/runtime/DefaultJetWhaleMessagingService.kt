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
import kotlin.coroutines.cancellation.CancellationException

internal class DefaultJetWhaleMessagingService(
    private val socketClient: JetWhaleSocketClient,
    private val pluginService: JetWhaleAgentPluginService,
) : JetWhaleMessagingService {
    private val coroutineScope: CoroutineScope = CoroutineScope(messagingServiceCoroutineDispatcher() + SupervisorJob())
    private var keepAwakeJob: Job? = null
    private var retryCount = 0

    override fun startService(resolver: EndpointResolver) {
        JetWhaleLogger.i("Starting JetWhale Messaging Service")
        keepAwakeJob?.cancel()
        keepAwakeJob = coroutineScope.launch {
            while (isActive) {
                try {
                    // Asked per attempt rather than once up front: an address that only becomes
                    // correct later is reached without restarting the session.
                    val resolved = resolver.resolve()
                    openConnection(resolved.host, resolved.port)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    pluginService.disconnectAll()
                    retryCount++
                    val delayMillis = (retryCount * RETRY_DELAY_INCREMENT_MILLIS).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
                    delay(delayMillis)
                }
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

    private suspend fun openConnection(host: String, port: Int) {
        val connection = socketClient.openConnection(host, port)

        retryCount = 0

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
    }
}
