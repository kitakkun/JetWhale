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
    private var hasLoggedInitialConnectionFailure: Boolean = false
    private val activatedPluginIds: MutableSet<String> = mutableSetOf()

    override fun startService(host: String, port: Int) {
        JetWhaleLogger.i("Starting JetWhale service...")
        keepAwakeJob?.cancel()
        keepAwakeJob = coroutineScope.launch {
            try {
                openConnectionAndCollectServerMessages(host, port)
            } catch (e: Throwable) {
                when {
                    hasConnectedOnce -> {
                        JetWhaleLogger.w("Connection closed: ${e.message}", e)
                    }

                    !hasLoggedInitialConnectionFailure -> {
                        hasLoggedInitialConnectionFailure = true
                        JetWhaleLogger.w("Connection failed: ${e.message}. Is the debugger running? Is the port correct?")
                    }

                    else -> {
                        JetWhaleLogger.d("Connection failed: ${e.message}")
                    }
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
        val connectionResult = socketClient.openConnection(host, port)
        hasConnectedOnce = true
        JetWhaleLogger.d("Connection established to $host:$port")
        reconnectDelayMillis = DEFAULT_RECONNECT_DELAY_MILLIS

        val availablePluginIds = connectionResult.negotiationResult.availablePluginIds

        activatedPluginIds.clear()
        activatedPluginIds.addAll(availablePluginIds)
        attachSenderToPlugins(availablePluginIds)
        collectServerMessages(connectionResult.messageFlow)
    }

    private fun CoroutineScope.attachSenderToPlugins(availablePluginIds: List<String>) {
        JetWhaleLogger.v("Attaching sender to available plugins: $availablePluginIds")
        plugins.filter { it.pluginId in availablePluginIds }.forEach { plugin ->
            attachSenderToPlugin(plugin)
        }
    }

    private fun CoroutineScope.attachSenderToPlugin(plugin: AgentPlugin) {
        JetWhaleLogger.v("Attaching sender to plugin: ${plugin.pluginId}")
        plugin.attachSender { payload ->
            if (plugin.pluginId !in activatedPluginIds) {
                JetWhaleLogger.w("Ignoring event from deactivated plugin: ${plugin.pluginId}")
                return@attachSender
            }
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
                if (event.pluginId !in activatedPluginIds) {
                    JetWhaleLogger.w("Ignoring method request for deactivated plugin: ${event.pluginId}")
                    return
                }
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

            is JetWhaleDebuggerEvent.PluginActivated -> {
                JetWhaleLogger.i("Plugin activated: ${event.pluginId}")
                if (activatedPluginIds.add(event.pluginId)) {
                    val plugin = plugins.firstOrNull { it.pluginId == event.pluginId }
                    if (plugin != null) {
                        with(coroutineScope) {
                            attachSenderToPlugin(plugin)
                        }
                    }
                }
            }

            is JetWhaleDebuggerEvent.PluginDeactivated -> {
                JetWhaleLogger.i("Plugin deactivated: ${event.pluginId}")
                if (activatedPluginIds.remove(event.pluginId)) {
                    val plugin = plugins.firstOrNull { it.pluginId == event.pluginId }
                    plugin?.detachSender()
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_RECONNECT_DELAY_MILLIS = 1000L
        private const val RECONNECT_DELAY_INCREMENT_MILLIS = 1000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 5000L
    }
}
