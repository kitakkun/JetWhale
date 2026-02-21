package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationResult
import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationStrategy
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.serialization.decodeFromStringOrNull
import dev.zacsweers.metro.AppScope
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
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.util.logging.Logger
import io.ktor.websocket.Frame
import io.ktor.websocket.closeExceptionally
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * A Ktor-based WebSocket server for handling debug sessions.
 * This class mainly focuses on WebSocket connection management and message routing.
 */
@SingleIn(AppScope::class)
@Inject
class KtorWebSocketServer(
    private val json: Json,
    private val negotiationStrategy: ServerSessionNegotiationStrategy,
) {
    private var embeddedServer: EmbeddedServer<*, *>? = null
    private val sessions: ConcurrentHashMap<String, WebSocketServerSession> = ConcurrentHashMap()

    private val mutableStatusFlow: MutableStateFlow<DebugWebSocketServerStatus> = MutableStateFlow(DebugWebSocketServerStatus.Stopped)
    val statusFlow: StateFlow<DebugWebSocketServerStatus> = mutableStatusFlow

    private val mutableDebuggeeEventFlow: MutableSharedFlow<Pair<String, JetWhaleDebuggeeEvent>> = MutableSharedFlow()
    val debuggeeEventFlow: SharedFlow<Pair<String, JetWhaleDebuggeeEvent>> = mutableDebuggeeEventFlow

    private val mutableSessionClosedFlow: MutableSharedFlow<String> = MutableSharedFlow()
    val sessionClosedFlow: SharedFlow<String> = mutableSessionClosedFlow

    private val mutableNegotiationCompletedFlow: MutableSharedFlow<ServerSessionNegotiationResult.Success> = MutableSharedFlow()
    val negotiationCompletedFlow: SharedFlow<ServerSessionNegotiationResult.Success> = mutableNegotiationCompletedFlow

    suspend fun start(host: String, port: Int) {
        mutableStatusFlow.update { DebugWebSocketServerStatus.Starting }
        try {
            embeddedServer(
                factory = Netty,
                host = host,
                port = port,
                module = { configureWebSocket(host, port) },
            ).also {
                it.startSuspend()
                embeddedServer = it
            }
        } catch (e: Throwable) {
            mutableStatusFlow.update { DebugWebSocketServerStatus.Error(e.message ?: "Unknown error") }
        }
    }

    suspend fun stop() {
        mutableStatusFlow.update { DebugWebSocketServerStatus.Stopping }
        embeddedServer?.stopSuspend()
        embeddedServer = null
        mutableStatusFlow.update { DebugWebSocketServerStatus.Stopped }
    }

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
        }

        monitor.subscribe(ApplicationStopped) {
            mutableStatusFlow.update {
                DebugWebSocketServerStatus.Stopped
            }
        }

        routing {
            webSocket {
                context(log) {
                    configureSession()
                }
            }
        }
    }

    context(log: Logger)
    private suspend fun DefaultWebSocketServerSession.configureSession() {
        val negotiationResult = with(negotiationStrategy) { negotiate() }

        when (negotiationResult) {
            is ServerSessionNegotiationResult.Failure -> {
                log.info("negotiation failed: ${negotiationResult.error.localizedMessage}")
                closeExceptionally(negotiationResult.error)
                return
            }

            is ServerSessionNegotiationResult.Success -> {
                val sessionId = negotiationResult.session.sessionId
                sessions[sessionId] = this

                val receiveJob = launch {
                    incoming.receiveAsFlow()
                        .filterIsInstance<Frame.Text>()
                        .mapNotNull { json.decodeFromStringOrNull<JetWhaleDebuggeeEvent>(it.readText()) }
                        .collect {
                            mutableDebuggeeEventFlow.emit(sessionId to it)
                        }
                }

                mutableNegotiationCompletedFlow.emit(negotiationResult)

                closeReason.await().also {
                    println("closed: ${it?.message}")
                }

                receiveJob.cancel()
                sessions.remove(sessionId)

                println("session $sessionId closed")

                mutableSessionClosedFlow.emit(sessionId)
            }
        }
    }

    suspend fun sendToSession(sessionId: String, event: JetWhaleDebuggerEvent) {
        sessions[sessionId]?.sendSerialized(event)
    }

    suspend fun broadcastToSessions(event: JetWhaleDebuggerEvent) {
        sessions.values.forEach { session -> session.sendSerialized(event) }
    }

    fun getSessionCoroutineContext(sessionId: String): CoroutineContext {
        return sessions[sessionId]?.coroutineContext ?: throw IllegalArgumentException("No session with ID $sessionId")
    }
}
