package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleMessagingService
import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.serialization.decodeFromStringOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalJetWhaleApi::class)
internal class DefaultJetWhaleMessagingService(
    private val socketClient: JetWhaleSocketClient,
    private val pluginService: JetWhaleAgentPluginService,
    private val json: Json,
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

        val sendPluginMessage: (String, String) -> Unit = { pluginId: String, pluginMessagePayload: String ->
            val event = JetWhaleDebuggeeEvent.PluginMessage(
                pluginId = pluginId,
                payload = pluginMessagePayload,
            )
            val message = json.encodeToString(event)
            coroutineScope.launch {
                socketClient.sendMessage(
                    pluginId = pluginId,
                    message = message,
                )
            }
        }

        pluginService.activatePlugins(
            ids = connection.negotiationResult.availablePluginIds.toTypedArray(),
            baseSender = sendPluginMessage,
        )

        connection.messageFlow
            .mapNotNull { json.decodeFromStringOrNull<JetWhaleDebuggerEvent>(it) }
            .collect { event ->
                when (event) {
                    is JetWhaleDebuggerEvent.PluginActivated -> pluginService.activatePlugins(event.pluginId, baseSender = sendPluginMessage)

                    is JetWhaleDebuggerEvent.PluginDeactivated -> pluginService.deactivatePlugins(event.pluginId)

                    is JetWhaleDebuggerEvent.MethodRequest -> {
                        val plugin = pluginService.getPluginById(event.pluginId) ?: return@collect
                        val methodResult = plugin.onRawMethod(event.payload)

                        coroutineScope.launch {
                            socketClient.sendMessage(
                                pluginId = event.pluginId,
                                message = json.encodeToString(
                                    JetWhaleDebuggeeEvent.MethodResultResponse.Success(
                                        requestId = event.requestId,
                                        payload = methodResult,
                                    )
                                )
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
