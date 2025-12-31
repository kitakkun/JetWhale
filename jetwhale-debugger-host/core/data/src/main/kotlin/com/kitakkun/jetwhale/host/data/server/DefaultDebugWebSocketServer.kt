package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.debugger.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.debugger.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.PluginRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugWebSocketServer(
    private val json: Json,
    private val sessionNegotiator: WebSocketSessionNegotiator,
    private val adbAutoWiringService: ADBAutoWiringService,
    private val pluginsRepository: PluginRepository,
    private val sessionRepository: DebugSessionRepository,
    private val settingsRepository: DebuggerSettingsRepository,
) : DebugWebSocketServer {
    private val mutableStatusFlow: MutableStateFlow<DebugWebSocketServerStatus> =
        MutableStateFlow(DebugWebSocketServerStatus.Stopped)
    override val statusFlow: StateFlow<DebugWebSocketServerStatus> = mutableStatusFlow
    private val mutableSessionClosedFlow: MutableSharedFlow<String> = MutableSharedFlow()
    override val sessionClosedFlow: Flow<String> = mutableSessionClosedFlow

    private var ktorEmbeddedServer: EmbeddedServer<*, *>? = null

    private val sessions: MutableMap<String, WebSocketServerSession> = mutableMapOf()
    private val methodRequestResultFlow: MutableSharedFlow<JetWhaleDebuggeeEvent.MethodResultResponse> = MutableSharedFlow()

    private var adbPortWiringPerformed: Boolean = false

    override suspend fun start(host: String, port: Int) {
        val server = embeddedServer(
            factory = Netty,
            host = host,
            port = port,
        ) {
            configureWebSocket(host, port)
        }

        ktorEmbeddedServer = server

        mutableStatusFlow.update { DebugWebSocketServerStatus.Starting }
        try {
            server.startSuspend()
        } catch (e: Throwable) {
            mutableStatusFlow.update { DebugWebSocketServerStatus.Error(e.message ?: "Unknown error") }
        }
    }

    override suspend fun stop() {
        mutableStatusFlow.update { DebugWebSocketServerStatus.Stopping }
        ktorEmbeddedServer?.stopSuspend()
        ktorEmbeddedServer = null
        mutableStatusFlow.update { DebugWebSocketServerStatus.Stopped }
    }

    override suspend fun sendMessage(
        pluginId: String,
        sessionId: String,
        message: String
    ): String? {
        logDebug("Sending message: $message")
        val session = sessions[sessionId] ?: return null
        val requestId = UUID.randomUUID().toString()
        logDebug("send method message with requestId: $requestId")
        session.sendSerialized(
            JetWhaleDebuggerEvent.MethodRequest(
                pluginId = pluginId,
                requestId = requestId,
                payload = message,
            )
        )

        return withTimeoutOrNull(METHOD_RESULT_WAIT_TIMEOUT) {
            logDebug("waiting for method result response...")
            val response = methodRequestResultFlow.filter { it.requestId == requestId }.first()
            logDebug("received!")

            when (response) {
                is JetWhaleDebuggeeEvent.MethodResultResponse.Failed -> {
                    logDebug(response.errorMessage)
                    null
                }

                is JetWhaleDebuggeeEvent.MethodResultResponse.Success -> {
                    response.payload
                }
            }
        }
    }

    @OptIn(InternalJetWhaleApi::class)
    private fun Application.configureWebSocket(
        host: String,
        port: Int,
    ) {
        install(WebSockets.Plugin) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }

        monitor.subscribe(ApplicationStarted) {
            mutableStatusFlow.update {
                DebugWebSocketServerStatus.Started(host, port)
            }
            if (settingsRepository.adbAutoPortMappingEnabledFlow.value) {
                adbAutoWiringService.startAutoWiring(port)
                adbPortWiringPerformed = true
            }
        }

        monitor.subscribe(ApplicationStopped) {
            mutableStatusFlow.update { DebugWebSocketServerStatus.Stopped }
            if (adbPortWiringPerformed) {
                adbAutoWiringService.stopAutoWiring(port)
            }
        }

        routing {
            webSocket {
                log.info("web socket started")


                val sessionId: String
                val sessionName: String?

                when (val negotiationResult = with(sessionNegotiator) { negotiate() }) {
                    is WebSocketSessionNegotiator.NegotiationResult.Failure -> {
                        log.info("negotiation failed")
                        close()
                        return@webSocket
                    }

                    is WebSocketSessionNegotiator.NegotiationResult.Success -> {
                        log.info("negotiation succeeded: ${negotiationResult.sessionId}")
                        sessionId = negotiationResult.sessionId
                        sessionName = negotiationResult.sessionName
                    }
                }

                sessions[sessionId] = this

                sessionRepository.registerDebugSession(
                    sessionId = sessionId,
                    sessionName = sessionName,
                )

                log.debug("start listening to PluginMessage event...")
                val receiveJob = launch {
                    do {
                        try {
                            when (val event = receiveDeserialized<JetWhaleDebuggeeEvent>()) {
                                is JetWhaleDebuggeeEvent.MethodResultResponse -> methodRequestResultFlow.emit(event)
                                is JetWhaleDebuggeeEvent.PluginMessage -> {
                                    log.debug("received message: {}", event)
                                    pluginsRepository.getOrPutPluginInstanceForSession(
                                        pluginId = event.pluginId,
                                        sessionId = sessionId
                                    ).onReceive(json, event.payload)
                                }
                            }
                        } catch (e: Throwable) {
                            log.error(e.message)
                        }
                    } while (this.isActive)
                }

                closeReason.await().also {
                    println(it?.message)
                }

                receiveJob.cancel()
                sessions.remove(sessionId)

                sessionRepository.unregisterDebugSession(sessionId)
                pluginsRepository.unloadPluginInstanceForSession(sessionId)

                mutableSessionClosedFlow.emit(sessionId)
            }
        }
    }

    private fun logDebug(message: String) {
        ktorEmbeddedServer?.environment?.log?.debug(message)
    }

    companion object {
        private const val METHOD_RESULT_WAIT_TIMEOUT = 5000L
    }
}
