package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugWebSocketServer(
    private val adbAutoWiringService: ADBAutoWiringService,
    private val sessionRepository: DebugSessionRepository,
    private val pluginsRepository: PluginFactoryRepository,
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

    @OptIn(InternalJetWhaleApi::class)
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

    @OptIn(InternalJetWhaleApi::class)
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
        var previousEnabledPluginIds: Set<String> = enabledPluginsRepository.enabledPluginIdsFlow.first()

        enabledPluginsRepository.enabledPluginIdsFlow.collect { enabledPluginIds ->
            val newlyEnabledPluginIds = enabledPluginIds - previousEnabledPluginIds
            val newlyDisabledPluginIds = previousEnabledPluginIds - enabledPluginIds
            previousEnabledPluginIds = enabledPluginIds

            newlyEnabledPluginIds.forEach { pluginId ->
                val loadedFactories = pluginsRepository.loadedPluginFactories
                val factory = loadedFactories[pluginId] ?: return@forEach
                ktorWebSocketServer.broadcastToSessions(
                    event = JetWhaleDebuggerEvent.PluginActivated(
                        pluginId = pluginId,
                        pluginVersion = factory.meta.version, // TODO: do we really need this?
                    )
                )
            }

            newlyDisabledPluginIds.forEach { pluginId ->
                ktorWebSocketServer.broadcastToSessions(JetWhaleDebuggerEvent.PluginDeactivated(pluginId = pluginId))
            }
        }
    }

    private suspend fun monitorPluginMessages() {
        ktorWebSocketServer.debuggeeEventFlow.collect { (sessionId, event) ->
            if (event !is JetWhaleDebuggeeEvent.PluginMessage) return@collect

            val pluginId = event.pluginId

            if (!enabledPluginsRepository.isPluginEnabled(pluginId)) return@collect

            val pluginFactory = pluginsRepository.loadedPluginFactories[pluginId] ?: return@collect

            val pluginInstance = pluginInstanceService.getOrPutPluginInstanceForSession(
                pluginId = pluginId,
                sessionId = sessionId,
                pluginFactory = pluginFactory,
            )
            pluginInstance.onRawEvent(event.payload)
        }
    }

    companion object {
        private const val METHOD_RESULT_WAIT_TIMEOUT = 5000L
    }
}
