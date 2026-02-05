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
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import com.kitakkun.jetwhale.protocol.serialization.decodeFromStringOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugWebSocketServer(
    private val json: Json,
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

    /**
     * Tracks available plugins for each session.
     * Key: sessionId, Value: List of JetWhalePluginInfo representing plugins available for that session.
     */
    private val sessionAvailablePlugins: ConcurrentHashMap<String, List<JetWhalePluginInfo>> = ConcurrentHashMap()

    override suspend fun start(host: String, port: Int) {
        setupAdbAutoWiringIfNeeded()
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
            message = json.encodeToString(
                JetWhaleDebuggerEvent.MethodRequest(
                    pluginId = pluginId,
                    requestId = requestId,
                    payload = payload,
                )
            )
        )

        return withTimeoutOrNull(METHOD_RESULT_WAIT_TIMEOUT) {
            val response = ktorWebSocketServer.receivedMessageFlow
                .filter { it.first == sessionId }
                .mapNotNull { json.decodeFromStringOrNull<JetWhaleDebuggeeEvent>(it.second) }
                .filterIsInstance<JetWhaleDebuggeeEvent.MethodResultResponse>()
                .filter { it.requestId == requestId }
                .first()

            when (response) {
                is JetWhaleDebuggeeEvent.MethodResultResponse.Failure -> {
                    null
                }

                is JetWhaleDebuggeeEvent.MethodResultResponse.Success -> {
                    response.payload
                }
            }
        }
    }

    override fun getCoroutineScopeForSession(sessionId: String): CoroutineScope {
        return CoroutineScope(ktorWebSocketServer.getSessionCoroutineContext(sessionId))
    }

    @OptIn(InternalJetWhaleApi::class)
    private fun setupAdbAutoWiringIfNeeded() {
        serverMonitoringJob?.cancel()

        serverMonitoringJob = coroutineScope.launch {
            launch {
                if (!settingsRepository.adbAutoPortMappingEnabledFlow.value) return@launch
                var port: Int? = null
                ktorWebSocketServer.statusFlow.collect { status ->
                    if (status is DebugWebSocketServerStatus.Started) {
                        port = status.port
                        adbAutoWiringService.startAutoWiring(status.port)
                    } else if (status is DebugWebSocketServerStatus.Stopped) {
                        port?.let {
                            adbAutoWiringService.stopAutoWiring(it)
                        }
                        cancel()
                    }
                }
            }

            launch {
                ktorWebSocketServer.negotiationCompletedFlow.collect {
                    val sessionId = it.session.sessionId
                    val requestedPlugins = it.plugin.requestedPlugins
                    val loadedPluginIds = pluginsRepository.loadedPluginFactories.keys
                    val availablePlugins = requestedPlugins.filter { plugin -> plugin.pluginId in loadedPluginIds }

                    sessionAvailablePlugins[sessionId] = availablePlugins

                    sessionRepository.registerDebugSession(
                        sessionId = sessionId,
                        sessionName = it.session.sessionName,
                        installedPlugins = requestedPlugins,
                    )
                }
            }

            launch {
                ktorWebSocketServer.sessionClosedFlow.collect { sessionId ->
                    sessionAvailablePlugins.remove(sessionId)
                    sessionRepository.unregisterDebugSession(sessionId)
                    pluginInstanceService.unloadPluginInstanceForSession(sessionId)
                }
            }

            launch {
                monitorPluginChanges()
            }

            launch {
                monitorEnabledPluginChanges()
            }

            launch {
                ktorWebSocketServer.receivedMessageFlow
                    .mapNotNull { (sessionId, payload) ->
                        val pluginMessage = json.decodeFromStringOrNull<JetWhaleDebuggeeEvent.PluginMessage>(payload) ?: return@mapNotNull null
                        sessionId to pluginMessage
                    }
                    .collect { (sessionId, pluginMessage) ->
                        val pluginId = pluginMessage.pluginId

                        // Skip if the plugin is not enabled
                        if (!enabledPluginsRepository.isPluginEnabled(pluginId)) {
                            return@collect
                        }

                        val pluginFactory = pluginsRepository.loadedPluginFactories[pluginId] ?: return@collect

                        val pluginInstance = pluginInstanceService.getOrPutPluginInstanceForSession(
                            pluginId = pluginId,
                            sessionId = sessionId,
                            pluginFactory = pluginFactory,
                        )
                        pluginInstance.onRawEvent(pluginMessage.payload)
                    }
            }
        }
    }

    @OptIn(InternalJetWhaleApi::class)
    private suspend fun monitorPluginChanges() {
        var previousLoadedPluginIds: Set<String> = pluginsRepository.loadedPluginFactories.keys

        pluginsRepository.loadedPluginFactoriesFlow.collect { loadedFactories ->
            val currentLoadedPluginIds = loadedFactories.keys

            val newlyLoadedPluginIds = currentLoadedPluginIds - previousLoadedPluginIds
            val unloadedPluginIds = previousLoadedPluginIds - currentLoadedPluginIds

            previousLoadedPluginIds = currentLoadedPluginIds

            // For each session, check which plugins should be activated or deactivated
            for ((sessionId, requestedPlugins) in sessionAvailablePlugins) {
                // Plugins that are newly loaded and requested by this session
                for (pluginId in newlyLoadedPluginIds) {
                    val pluginInfo = requestedPlugins.find { it.pluginId == pluginId }
                    if (pluginInfo != null) {
                        val factory = loadedFactories[pluginId]
                        if (factory != null) {
                            ktorWebSocketServer.sendToSession(
                                sessionId = sessionId,
                                message = json.encodeToString(
                                    JetWhaleDebuggerEvent.PluginActivated(
                                        pluginId = pluginId,
                                        pluginVersion = factory.meta.version,
                                    )
                                )
                            )
                        }
                    }
                }

                // Plugins that are unloaded and were available to this session
                for (pluginId in unloadedPluginIds) {
                    val wasAvailable = requestedPlugins.any { it.pluginId == pluginId }
                    if (wasAvailable) {
                        ktorWebSocketServer.sendToSession(
                            sessionId = sessionId,
                            message = json.encodeToString(
                                JetWhaleDebuggerEvent.PluginDeactivated(
                                    pluginId = pluginId,
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    @OptIn(InternalJetWhaleApi::class)
    private suspend fun monitorEnabledPluginChanges() {
        var previousEnabledPluginIds: Set<String> = enabledPluginsRepository.enabledPluginIdsFlow.first()

        enabledPluginsRepository.enabledPluginIdsFlow.collect { enabledPluginIds ->
            val newlyEnabledPluginIds = enabledPluginIds - previousEnabledPluginIds
            val newlyDisabledPluginIds = previousEnabledPluginIds - enabledPluginIds

            previousEnabledPluginIds = enabledPluginIds

            val loadedFactories = pluginsRepository.loadedPluginFactories

            for ((sessionId, requestedPlugins) in sessionAvailablePlugins) {
                // Plugins that are newly enabled and requested by this session
                for (pluginId in newlyEnabledPluginIds) {
                    val pluginInfo = requestedPlugins.find { it.pluginId == pluginId }
                    val factory = loadedFactories[pluginId]
                    if (pluginInfo != null && factory != null) {
                        ktorWebSocketServer.sendToSession(
                            sessionId = sessionId,
                            message = json.encodeToString(
                                JetWhaleDebuggerEvent.PluginActivated(
                                    pluginId = pluginId,
                                    pluginVersion = factory.meta.version,
                                )
                            )
                        )
                    }
                }

                // Plugins that are newly disabled and were available to this session
                for (pluginId in newlyDisabledPluginIds) {
                    val wasAvailable = requestedPlugins.any { it.pluginId == pluginId }
                    if (wasAvailable && loadedFactories.containsKey(pluginId)) {
                        ktorWebSocketServer.sendToSession(
                            sessionId = sessionId,
                            message = json.encodeToString(
                                JetWhaleDebuggerEvent.PluginDeactivated(
                                    pluginId = pluginId,
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val METHOD_RESULT_WAIT_TIMEOUT = 5000L
    }
}
