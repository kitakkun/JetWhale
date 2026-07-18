package com.kitakkun.jetwhale.host.data.server

import androidx.compose.ui.util.fastMap
import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugWebSocketServer(
    private val adbAutoWiringService: ADBAutoWiringService,
    private val sessionRepository: DebugSessionRepository,
    private val pluginInstanceService: PluginInstanceService,
    private val settingsRepository: DebuggerSettingsRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val ktorWebSocketServer: KtorWebSocketServer,
) : DebugWebSocketServer {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    override val sessionClosedFlow: Flow<String> get() = ktorWebSocketServer.sessionClosedFlow
    private val mutableServerStoppedFlow: MutableSharedFlow<Unit> = MutableSharedFlow()
    override val serverStoppedFlow: Flow<Unit> = mutableServerStoppedFlow

    private var serverMonitoringJob: Job? = null

    override suspend fun start(host: String, port: Int, wssPort: Int?) {
        subscribeServerEvents()
        ktorWebSocketServer.start(
            host = host,
            port = port,
            wssPort = wssPort,
        )
    }

    override suspend fun stop() {
        serverMonitoringJob?.cancel()
        serverMonitoringJob = null
        ktorWebSocketServer.stop()
        sessionRepository.markAllSessionsInactive()
        pluginInstanceService.clearAllPluginInstances()
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
        if (!settingsRepository.adbAutoPortMappingEnabledFlow.value) return

        var wiredPorts: List<Int> = emptyList()
        ktorWebSocketServer.statusFlow.collect { status ->
            when (status) {
                is DebugWebSocketServerStatus.Started -> {
                    // Wire both connectors so an on-device app can reach ws and wss alike through
                    // localhost, whichever it is configured for.
                    wiredPorts = listOfNotNull(status.port, status.wssPort)
                    wiredPorts.forEach { adbAutoWiringService.startAutoWiring(it) }
                }

                is DebugWebSocketServerStatus.Stopped -> {
                    wiredPorts.forEach { adbAutoWiringService.stopAutoWiring(it) }
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
                        sessions.filter { it.isActive }
                    },
                ) { enabledPluginIds, activeSessions ->
                    enabledPluginIds to activeSessions
                }.collect { (enabledPluginIds, activeSessions) ->
                    enabledPluginIds.forEach { pluginId ->
                        // A host-only plugin (requiresAgent = false) has no agent counterpart, so it is
                        // available for every active session regardless of negotiation; an agent-backed
                        // plugin is only for sessions whose agent advertised it.
                        val requiresAgent = pluginFactoryRepository.loadedPlugins[pluginId]?.manifest?.requiresAgent ?: true
                        val targetSessionIds = if (requiresAgent) {
                            activeSessions
                                .filter { session -> session.installedPlugins.any { it.pluginId == pluginId } }
                                .fastMap { it.id }
                                .toSet()
                        } else {
                            activeSessions.fastMap { it.id }.toSet()
                        }
                        // Initialize plugin instances for the target sessions so the plugin is activated
                        // immediately after being enabled.
                        val pluginActivatedSessionIds = pluginInstanceService.initializePluginInstancesForSessionsIfNeeded(
                            pluginId = pluginId,
                            sessionIds = targetSessionIds,
                        )
                        // Plugin instances are also initialized when a new session is created, so some
                        // sessions may already have the instance. Only notify the agent for the sessions
                        // initialized in this step, and only for agent-backed plugins (host-only plugins
                        // have no agent to activate).
                        if (requiresAgent) {
                            pluginActivatedSessionIds.forEach { sessionId ->
                                ktorWebSocketServer.sendToSession(
                                    sessionId = sessionId,
                                    event = JetWhaleDebuggerEvent.PluginActivated(pluginId = pluginId),
                                )
                            }
                        }
                    }
                }
            }

            launch {
                enabledPluginsRepository.disabledPluginIdFlow.collect { pluginId ->
                    // Host-only plugins have no agent to deactivate.
                    val requiresAgent = pluginFactoryRepository.loadedPlugins[pluginId]?.manifest?.requiresAgent ?: true
                    if (requiresAgent) {
                        ktorWebSocketServer.broadcastToSessions(JetWhaleDebuggerEvent.PluginDeactivated(pluginId = pluginId))
                    }
                    // Unload plugin instances for all sessions to ensure that the plugin is deactivated in all sessions immediately after being disabled.
                    pluginInstanceService.unloadPluginInstancesForPlugin(pluginId)
                }
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
