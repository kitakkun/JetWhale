package com.kitakkun.jetwhale.debugger.agent.runtime

import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.debugger.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.debugger.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.debugger.protocol.serialization.decodeFromStringOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(InternalJetWhaleApi::class)
internal class DefaultJetWhaleMessagingService(
    private val socketClient: JetWhaleSocketClient,
    private val plugins: List<JetWhaleAgentPlugin<*>>,
    private val json: Json,
) : JetWhaleMessagingService {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var keepAwakeJob: Job? = null
    private var reconnectDelayMillis: Long = DEFAULT_RECONNECT_DELAY_MILLIS

    override fun startService(host: String, port: Int) {
        JetWhaleLogger.d("Starting JetWhale service...")
        keepAwakeJob?.cancel()
        keepAwakeJob = coroutineScope.launch {
            try {
                openConnectionAndCollectServerMessages(host, port)
            } catch (e: Throwable) {
                JetWhaleLogger.d("Failed to open connection or connection closed: ${e.message}", e)
            } finally {
                JetWhaleLogger.d("Detaching senders...")
                detachSenderFromPlugins()
            }

            reconnectDelayMillis = (reconnectDelayMillis + RECONNECT_DELAY_INCREMENT_MILLIS).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
            JetWhaleLogger.i("Reconnecting in $reconnectDelayMillis ms")
            delay(reconnectDelayMillis)
            startService(host, port)
        }
    }

    private suspend fun CoroutineScope.openConnectionAndCollectServerMessages(host: String, port: Int) {
        val serverRawJsonMessageFlow = socketClient.openConnection(host, port)
        JetWhaleLogger.i("Connection established to $host:$port")
        reconnectDelayMillis = DEFAULT_RECONNECT_DELAY_MILLIS

        attachSenderToPlugins()
        collectServerMessages(serverRawJsonMessageFlow)
    }

    private fun CoroutineScope.attachSenderToPlugins() {
        JetWhaleLogger.d("Attaching sender to each plugin")
        plugins.forEach { plugin ->
            plugin.attachSender { payload ->
                launch {
                    val message = json.encodeToString(
                        JetWhaleDebuggeeEvent.PluginMessage(
                            pluginId = plugin.pluginId,
                            payload = payload,
                        )
                    )
                    socketClient.sendMessage(pluginId = plugin.pluginId, message = message)
                }
            }
        }
    }

    private fun detachSenderFromPlugins() {
        plugins.forEach { plugin -> plugin.detachSender() }
    }

    private suspend fun collectServerMessages(serverRawJsonMessageFlow: Flow<String>) {
        JetWhaleLogger.d("Start listening server messages...")
        serverRawJsonMessageFlow
            .mapNotNull { json.decodeFromStringOrNull<JetWhaleDebuggerEvent>(it) }
            .collect { event ->
                JetWhaleLogger.d("Received event: $event")
                handleDebuggerEvent(event)
            }
    }

    private suspend fun handleDebuggerEvent(event: JetWhaleDebuggerEvent) {
        when (event) {
            is JetWhaleDebuggerEvent.MethodRequest -> {
                val plugin = plugins.firstOrNull { it.pluginId == event.pluginId } ?: return
                val result = plugin.methodHandler.handle(event.payload)
                socketClient.sendMessage(
                    pluginId = event.pluginId,
                    message = json.encodeToString(
                        JetWhaleDebuggeeEvent.MethodResultResponse.Success(
                            requestId = event.requestId,
                            payload = result,
                        )
                    )
                )
                JetWhaleLogger.d("sent method result!")
            }
        }
    }

    private fun JetWhaleAgentPlugin<*>.detachSender() {
        (this.eventDispatcher as SenderAttachable).detachSender()
    }

    private fun JetWhaleAgentPlugin<*>.attachSender(sender: JetWhaleEventSender) {
        (this.eventDispatcher as SenderAttachable).attachSender(sender)
    }

    companion object {
        private const val DEFAULT_RECONNECT_DELAY_MILLIS = 1000L
        private const val RECONNECT_DELAY_INCREMENT_MILLIS = 1000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 5000L
    }
}
