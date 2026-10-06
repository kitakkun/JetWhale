package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.AdbAutoPortMappingService
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.HostDiscoveryAdvertiser
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginReconciliationEvent
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugWebSocketServer(
    private val adbAutoPortMappingService: AdbAutoPortMappingService,
    private val sessionRepository: DebugSessionRepository,
    private val pluginInstanceService: PluginInstanceService,
    private val settingsRepository: DebuggerSettingsRepository,
    private val reconciliationService: PluginSessionReconciliationService,
    private val ktorWebSocketServer: KtorWebSocketServer,
    private val hostDiscoveryAdvertiser: HostDiscoveryAdvertiser,
) : DebugWebSocketServer {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    override val statusFlow: StateFlow<DebugWebSocketServerStatus> get() = ktorWebSocketServer.statusFlow
    override val sessionClosedFlow: Flow<String> get() = ktorWebSocketServer.sessionClosedFlow
    private val mutableServerStoppedFlow: MutableSharedFlow<Unit> = MutableSharedFlow()
    override val serverStoppedFlow: Flow<Unit> = mutableServerStoppedFlow

    private var serverMonitoringJob: Job? = null

    override suspend fun start(host: String, port: Int, wssPort: Int?) {
        subscribeServerEvents()
        hostDiscoveryAdvertiser.start()
        ktorWebSocketServer.start(
            host = host,
            port = port,
            wssPort = wssPort,
        )
    }

    override suspend fun stop() {
        serverMonitoringJob?.cancel()
        serverMonitoringJob = null
        hostDiscoveryAdvertiser.stop()
        ktorWebSocketServer.stop()
        sessionRepository.markAllSessionsInactive()
        pluginInstanceService.clearAppSessionPluginInstances()
        mutableServerStoppedFlow.emit(Unit)
    }

    private fun subscribeServerEvents() {
        serverMonitoringJob?.cancel()

        serverMonitoringJob = coroutineScope.launch {
            launch { monitorAdbAutoWiring() }
            launch { monitorNegotiationCompleted() }
            launch { monitorSessionClosed() }
            launch { monitorEnabledPluginChanges() }
            launch { monitorPluginFrames() }
        }
    }

    private suspend fun monitorAdbAutoWiring() {
        if (!settingsRepository.readAdbAutoPortMappingEnabled()) return

        var mappedPorts: List<Int> = emptyList()
        ktorWebSocketServer.statusFlow.collect { status ->
            when (status) {
                is DebugWebSocketServerStatus.Started -> {
                    mappedPorts = listOfNotNull(status.port, status.wssPort)
                    mappedPorts.forEach(adbAutoPortMappingService::startPortMapping)
                }

                is DebugWebSocketServerStatus.Stopped -> {
                    mappedPorts.forEach(adbAutoPortMappingService::stopPortMapping)
                }

                else -> Unit
            }
        }
    }

    private suspend fun monitorNegotiationCompleted() {
        ktorWebSocketServer.negotiationCompletedFlow.collect { opened ->
            sessionRepository.registerDebugSession(
                sessionId = opened.result.session.sessionId,
                sessionName = opened.result.session.sessionName,
                transportSecurity = opened.transportSecurity,
                installedPlugins = opened.result.plugin.requestedPlugins,
                appMetadata = opened.result.session.appMetadata,
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
        reconciliationService.reconciliationEvents().collect { event ->
            when (event) {
                is PluginReconciliationEvent.Activated -> event.sessionIds.forEach { sessionId ->
                    ktorWebSocketServer.sendToSession(
                        sessionId = sessionId,
                        event = JetWhaleDebuggerEvent.PluginActivated(pluginId = event.pluginId),
                    )
                }

                is PluginReconciliationEvent.Deactivated ->
                    ktorWebSocketServer.broadcastToSessions(JetWhaleDebuggerEvent.PluginDeactivated(pluginId = event.pluginId))
            }
        }
    }

    private suspend fun monitorPluginFrames() {
        ktorWebSocketServer.debuggeeEventFlow.collect { (sessionId, event) ->
            if (event !is JetWhaleDebuggeeEvent.PluginFrameMessage) return@collect
            pluginInstanceService.routeFrame(sessionId = sessionId, frame = event.frame)
        }
    }
}
