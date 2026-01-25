package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.PluginRepository
import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
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

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugWebSocketServer(
    private val json: Json,
    private val adbAutoWiringService: ADBAutoWiringService,
    private val sessionRepository: DebugSessionRepository,
    private val pluginsRepository: PluginRepository,
    private val settingsRepository: DebuggerSettingsRepository,
    private val ktorWebSocketServer: KtorWebSocketServer,
) : DebugWebSocketServer {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    override val sessionClosedFlow: Flow<String> get() = ktorWebSocketServer.sessionClosedFlow

    private var serverMonitoringJob: Job? = null

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
            coroutineScope.launch {
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

            coroutineScope.launch {
                ktorWebSocketServer.negotiationCompletedFlow.collect {
                    sessionRepository.registerDebugSession(
                        sessionId = it.session.sessionId,
                        sessionName = it.session.sessionName,
                        installedPlugins = it.plugin.requestedPlugins,
                    )
                }
            }

            coroutineScope.launch {
                ktorWebSocketServer.sessionClosedFlow.collect { sessionId ->
                    sessionRepository.unregisterDebugSession(sessionId)
                    pluginsRepository.unloadPluginInstanceForSession(sessionId)
                }
            }

            coroutineScope.launch {
                ktorWebSocketServer.receivedMessageFlow
                    .mapNotNull { (sessionId, payload) ->
                        val pluginMessage = json.decodeFromStringOrNull<JetWhaleDebuggeeEvent.PluginMessage>(payload) ?: return@mapNotNull null
                        sessionId to pluginMessage
                    }
                    .collect { (sessionId, pluginMessage) ->
                        val pluginInstance = pluginsRepository.getOrPutPluginInstanceForSession(
                            pluginId = pluginMessage.pluginId,
                            sessionId = sessionId
                        )
                        pluginInstance.onRawEvent(pluginMessage.payload)
                    }
            }
        }
    }

    companion object {
        private const val METHOD_RESULT_WAIT_TIMEOUT = 5000L
    }
}
