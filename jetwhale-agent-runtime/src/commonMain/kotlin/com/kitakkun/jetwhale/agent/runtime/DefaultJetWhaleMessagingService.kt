package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleMessagingService
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalJetWhaleApi::class)
internal class DefaultJetWhaleMessagingService(
    private val socketClient: JetWhaleSocketClient,
    private val pluginService: JetWhaleAgentPluginService,
) : JetWhaleMessagingService {
    private val coroutineScope: CoroutineScope = CoroutineScope(messagingServiceCoroutineDispatcher() + SupervisorJob())
    private var keepAwakeJob: Job? = null
    private var retryCount = 0

    override fun startService(host: String, port: Int) {
        JetWhaleLogger.i("Starting JetWhale Messaging Service")
        keepAwakeJob?.cancel()
        keepAwakeJob = coroutineScope.launch {
            while (isActive) {
                try {
                    openConnection(host, port)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    pluginService.deactivateAllPlugins()
                    retryCount++
                    val delayMillis = (retryCount * RETRY_DELAY_INCREMENT_MILLIS).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
                    delay(delayMillis)
                }
            }
        }
    }

    private suspend fun openConnection(host: String, port: Int) {
        val connection = socketClient.openConnection(host, port)

        retryCount = 0

        val sendPluginMessageEvent: PluginAwareMessageSender = { pluginId: String, pluginMessages: Array<out String> ->
            coroutineScope.launch {
                pluginMessages.forEach {
                    socketClient.sendDebuggeeEvent(
                        JetWhaleDebuggeeEvent.PluginMessage(
                            pluginId = pluginId,
                            payload = it,
                        ),
                    )
                }
            }
        }

        pluginService.activatePlugins(
            ids = connection.negotiationResult.availablePluginIds.toTypedArray(),
            sender = sendPluginMessageEvent,
        )

        connection.debuggerEventFlow.collect { event ->
            when (event) {
                is JetWhaleDebuggerEvent.PluginActivated -> pluginService.activatePlugins(event.pluginId, sender = sendPluginMessageEvent)

                is JetWhaleDebuggerEvent.PluginDeactivated -> pluginService.deactivatePlugins(event.pluginId)

                is JetWhaleDebuggerEvent.MethodRequest -> {
                    val plugin = pluginService.getPluginById(event.pluginId) ?: return@collect
                    val methodResult = plugin.onRawMethod(event.payload)

                    coroutineScope.launch {
                        socketClient.sendDebuggeeEvent(
                            event = JetWhaleDebuggeeEvent.MethodResultResponse.Success(
                                requestId = event.requestId,
                                payload = methodResult,
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val RETRY_DELAY_INCREMENT_MILLIS = 1000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 5000L
    }
}
