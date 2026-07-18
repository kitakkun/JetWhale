package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationResult
import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationStrategy
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.SslCertificateManager
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
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
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
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Ktor-based WebSocket server for handling debug sessions.
 * This class mainly focuses on WebSocket connection management and message routing.
 */
@SingleIn(AppScope::class)
@Inject
class KtorWebSocketServer(
    private val json: Json,
    private val negotiationStrategy: ServerSessionNegotiationStrategy,
    private val sslCertificateManager: SslCertificateManager,
) {
    private val logger = LoggerFactory.getLogger(KtorWebSocketServer::class.java)

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

    suspend fun start(host: String, port: Int, wssPort: Int?) {
        mutableStatusFlow.update { DebugWebSocketServerStatus.Starting }
        try {
            createServer(host = host, port = port, wssPort = wssPort).also {
                it.startSuspend()
                embeddedServer = it
            }
        } catch (e: CancellationException) {
            // Never swallow cancellation: re-throw so the coroutine cancellation mechanism keeps working.
            throw e
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

    /**
     * Builds the embedded server. When [wssPort] is non-null, an additional TLS (wss) connector is
     * configured next to the plain ws connector, backed by the active certificate from
     * [SslCertificateManager] (generated on demand when none exists yet).
     */
    private fun createServer(host: String, port: Int, wssPort: Int?): EmbeddedServer<*, *> {
        if (wssPort != null && !sslCertificateManager.hasCertificate()) {
            sslCertificateManager.generateAndAddCertificate(null)
        }

        val keyStore = if (wssPort != null) sslCertificateManager.getActiveKeyStore() else null
        val keyAlias = sslCertificateManager.getActiveKeyAlias()

        if (wssPort != null && (keyStore == null || keyAlias == null)) {
            // wss was requested but the active certificate could not be loaded (e.g. corrupt or
            // missing files). Surface the failure instead of pretending wss is up; the plain ws
            // connector still starts so ADB-forwarded local debugging keeps working.
            logger.error(
                "wss was enabled on port $wssPort but the active certificate could not be loaded; " +
                    "starting without the TLS connector. Re-generate a certificate from the server settings.",
            )
        }

        return if (wssPort != null && keyStore != null && keyAlias != null) {
            embeddedServer(
                factory = Netty,
                environment = applicationEnvironment {},
                configure = {
                    connector {
                        this.host = host
                        this.port = port
                    }
                    sslConnector(
                        keyStore = keyStore,
                        keyAlias = keyAlias,
                        keyStorePassword = { sslCertificateManager.getKeyStorePassword() },
                        privateKeyPassword = { sslCertificateManager.getKeyStorePassword() },
                    ) {
                        this.host = host
                        this.port = wssPort
                    }
                },
                module = { configureWebSocket(host, port, wssPort) },
            )
        } else {
            embeddedServer(
                factory = Netty,
                host = host,
                port = port,
                module = { configureWebSocket(host, port, null) },
            )
        }
    }

    private fun Application.configureWebSocket(
        host: String,
        port: Int,
        wssPort: Int?,
    ) {
        install(WebSockets.Plugin) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }

        monitor.subscribe(ApplicationStarted) {
            mutableStatusFlow.update {
                DebugWebSocketServerStatus.Started(host, port, wssPort)
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

    fun getSessionCoroutineContext(sessionId: String): CoroutineContext = sessions[sessionId]?.coroutineContext ?: throw IllegalArgumentException("No session with ID $sessionId")
}
