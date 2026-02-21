package com.kitakkun.jetwhale.host.data.server

import androidx.compose.ui.util.fastMap
import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugWebSocketServer(
    private val adbAutoWiringService: ADBAutoWiringService,
    private val sessionRepository: DebugSessionRepository,
    private val pluginInstanceService: PluginInstanceService,
    private val settingsRepository: DebuggerSettingsRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val ktorWebSocketServer: KtorWebSocketServer,
) : DebugWebSocketServer {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    override val sessionClosedFlow: Flow<String> get() = ktorWebSocketServer.sessionClosedFlow

    private var serverMonitoringJob: Job? = null

    override suspend fun start(host: String, port: Int) {
        subscribeServerEvents()
        ktorWebSocketServer.start(
            host = host,
            port = port,
        )
    }

    override suspend fun stop() {
        serverMonitoringJob?.cancel()
        serverMonitoringJob = null
        ktorWebSocketServer.stop()
    }

    override suspend fun sendMethod(
        pluginId: String,
        sessionId: String,
        payload: String
    ): String? {
        val requestId = UUID.randomUUID().toString()

        ktorWebSocketServer.sendToSession(
            sessionId = sessionId,
            event = JetWhaleDebuggerEvent.MethodRequest(
                pluginId = pluginId,
                requestId = requestId,
                payload = payload,
            )
        )

        return withTimeoutOrNull(METHOD_RESULT_WAIT_TIMEOUT) {
            val response = ktorWebSocketServer.debuggeeEventFlow
                .filter { it.first == sessionId }
                .filter { it.second is JetWhaleDebuggeeEvent.MethodResultResponse }
                .first { (it.second as JetWhaleDebuggeeEvent.MethodResultResponse).requestId == requestId }
                .second as JetWhaleDebuggeeEvent.MethodResultResponse

            when (response) {
                is JetWhaleDebuggeeEvent.MethodResultResponse.Failure -> null
                is JetWhaleDebuggeeEvent.MethodResultResponse.Success -> response.payload
            }
        }
    }

    override fun getCoroutineScopeForSession(sessionId: String): CoroutineScope {
        return CoroutineScope(ktorWebSocketServer.getSessionCoroutineContext(sessionId))
    }

    private fun subscribeServerEvents() {
        serverMonitoringJob?.cancel()

        serverMonitoringJob = coroutineScope.launch {
            launch { monitorAdbAutoWiring() }
            launch { monitorNegotiationCompleted() }
            launch { monitorSessionClosed() }
            launch { monitorEnabledPluginChanges() }
            launch { monitorPluginMessages() }
        }
    }

    private suspend fun monitorAdbAutoWiring() {
        if (!settingsRepository.adbAutoPortMappingEnabledFlow.value) return

        var port: Int? = null
        ktorWebSocketServer.statusFlow.collect { status ->
            when (status) {
                is DebugWebSocketServerStatus.Started -> {
                    port = status.port
                    adbAutoWiringService.startAutoWiring(status.port)
                }

                is DebugWebSocketServerStatus.Stopped -> {
                    port?.let { adbAutoWiringService.stopAutoWiring(it) }
                }

                else -> Unit
            }
        }
    }

    private suspend fun monitorNegotiationCompleted() {
        ktorWebSocketServer.negotiationCompletedFlow.collect { result ->
            sessionRepository.registerDebugSession(
                sessionId = result.session.sessionId,
                sessionName = result.session.sessionName,
                installedPlugins = result.plugin.requestedPlugins,
            )
        }
    }

    private suspend fun monitorSessionClosed() {
        ktorWebSocketServer.sessionClosedFlow.collect { sessionId ->
            sessionRepository.unregisterDebugSession(sessionId)
            pluginInstanceService.unloadPluginInstanceForSession(sessionId)
        }
    }

    private suspend fun monitorEnabledPluginChanges() {
        coroutineScope {
            launch {
                combine(
                    enabledPluginsRepository.enabledPluginIdsFlow,
                    sessionRepository.debugSessionsFlow.map { sessions ->
                        sessions
                            .filter { it.isActive }
                            .fastMap { it.id }
                    }
                ) { enabledPluginIds, activeSessionIds ->
                    enabledPluginIds to activeSessionIds.toSet()
                }.collect { (enabledPluginIds, activeSessionIds) ->
                    enabledPluginIds.forEach {
                        // Initialize plugin instances for all existing sessions
                        // to ensure that the plugin is activated in all sessions immediately after being enabled.
                        val pluginActivatedSessionIds = pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                            pluginId = it,
                            sessionIds = activeSessionIds,
                        )
                        // Plugin instances are also initialized when a new session is created,
                        // so it's possible that some sessions already have the plugin instance initialized when the plugin is enabled.
                        // Only send the activation event to sessions for which the plugin instance is initialized
                        // in this step to avoid sending duplicate activation events to sessions that already have the plugin instance initialized.
                        pluginActivatedSessionIds.forEach { sessionId ->
                            ktorWebSocketServer.sendToSession(
                                sessionId = sessionId,
                                event = JetWhaleDebuggerEvent.PluginActivated(pluginId = it),
                            )
                        }
                    }
                }
            }

            launch {
                enabledPluginsRepository.disabledPluginIdFlow.collect { pluginId ->
                    ktorWebSocketServer.broadcastToSessions(JetWhaleDebuggerEvent.PluginDeactivated(pluginId = pluginId))
                    // Unload plugin instances for all sessions to ensure that the plugin is deactivated in all sessions immediately after being disabled.
                    pluginInstanceService.unloadPluginInstancesForPlugin(pluginId)
                }
            }
        }
    }

    private suspend fun monitorPluginMessages() {
        ktorWebSocketServer.debuggeeEventFlow.collect { (sessionId, event) ->
            if (event !is JetWhaleDebuggeeEvent.PluginMessage) return@collect

            val pluginId = event.pluginId

            val pluginInstance = pluginInstanceService.getPluginInstanceForSession(
                pluginId = pluginId,
                sessionId = sessionId,
            )

            if (pluginInstance == null) {
                println(
                    """
                        Received message for pluginId=$pluginId in sessionId=$sessionId, but plugin instance is not found. Skipping message processing.
                        This may happen when the message is received before the plugin instance is initialized, or when the plugin is disabled after the message is sent.
                    """.trimIndent()
                )
                return@collect
            }

            pluginInstance.onRawEvent(event.payload)
        }
    }

    companion object {
        private const val METHOD_RESULT_WAIT_TIMEOUT = 5000L
    }
}
