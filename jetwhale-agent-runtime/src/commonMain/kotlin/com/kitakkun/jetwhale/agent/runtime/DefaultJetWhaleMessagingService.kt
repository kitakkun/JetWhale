package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleMessagingService
import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.serialization.decodeFromStringOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(InternalJetWhaleApi::class)
internal class DefaultJetWhaleMessagingService(
    private val socketClient: JetWhaleSocketClient,
    private val plugins: List<AgentPlugin>,
    private val json: Json,
) : JetWhaleMessagingService {
    private val coroutineScope: CoroutineScope = CoroutineScope(messagingServiceCoroutineDispatcher())
    private var keepAwakeJob: Job? = null
    private var reconnectDelayMillis: Long = DEFAULT_RECONNECT_DELAY_MILLIS
    private var hasConnectedOnce: Boolean = false

    override fun startService(host: String, port: Int) {
        JetWhaleLogger.i("Starting JetWhale service...")
        keepAwakeJob?.cancel()
        keepAwakeJob = coroutineScope.launch {
            try {
                openConnectionAndCollectServerMessages(host, port)
            } catch (e: Throwable) {
                // show as warning only if the connection was established at least once to reduce noise
                if (hasConnectedOnce) {
                    JetWhaleLogger.w("Connection closed: ${e.message}", e)
                } else {
                    JetWhaleLogger.d("Connection failed: ${e.message}. Is the debugger running? Is the port correct?")
                }
            } finally {
                JetWhaleLogger.v("Detaching senders...")
                detachSenderFromPlugins()
            }

            reconnectDelayMillis = (reconnectDelayMillis + RECONNECT_DELAY_INCREMENT_MILLIS).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
            JetWhaleLogger.v("Reconnecting in $reconnectDelayMillis ms")
            delay(reconnectDelayMillis)
            startService(host, port)
        }
    }

    private suspend fun CoroutineScope.openConnectionAndCollectServerMessages(host: String, port: Int) {
        val serverRawJsonMessageFlow = socketClient.openConnection(host, port)
        hasConnectedOnce = true
        JetWhaleLogger.d("Connection established to $host:$port")
        reconnectDelayMillis = DEFAULT_RECONNECT_DELAY_MILLIS

        attachSenderToPlugins()
        collectServerMessages(serverRawJsonMessageFlow)
    }

    private fun CoroutineScope.attachSenderToPlugins() {
        JetWhaleLogger.v("Attaching sender to each plugin")
        plugins.forEach { plugin ->
            plugin.attachSender { payload ->
                val event = JetWhaleDebuggeeEvent.PluginMessage(
                    pluginId = plugin.pluginId,
                    payload = payload,
                )
                val message = json.encodeToString(event)
                launch {
                    socketClient.sendMessage(
                        pluginId = plugin.pluginId,
                        message = message,
                    )
                }
            }
        }
    }

    private fun detachSenderFromPlugins() {
        plugins.forEach { plugin -> plugin.detachSender() }
    }

    private suspend fun collectServerMessages(serverRawJsonMessageFlow: Flow<String>) {
        JetWhaleLogger.v("Start listening server messages...")
        serverRawJsonMessageFlow
            .mapNotNull { json.decodeFromStringOrNull<JetWhaleDebuggerEvent>(it) }
            .collect { event ->
                JetWhaleLogger.v("Received event: $event")
                handleDebuggerEvent(event)
            }
    }

    private suspend fun handleDebuggerEvent(event: JetWhaleDebuggerEvent) {
        when (event) {
            is JetWhaleDebuggerEvent.MethodRequest -> {
                val plugin = plugins.firstOrNull { it.pluginId == event.pluginId } ?: return
                val methodResult = plugin.onRawMethod(event.payload)

                socketClient.sendMessage(
                    pluginId = event.pluginId,
                    message = json.encodeToString(
                        JetWhaleDebuggeeEvent.MethodResultResponse.Success(
                            requestId = event.requestId,
                            payload = methodResult,
                        )
                    )
                )
                JetWhaleLogger.v("Sent method result for request: ${event.requestId}")
            }
        }
    }

    companion object {
        private const val DEFAULT_RECONNECT_DELAY_MILLIS = 1000L
        private const val RECONNECT_DELAY_INCREMENT_MILLIS = 1000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 5000L
    }
}
