package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationResult
import com.kitakkun.jetwhale.host.data.server.negotiation.ServerSessionNegotiationStrategy
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.model.SslCertificateManager
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.serialization.decodeFromStringOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Ktor-based WebSocket server for handling debug sessions.
 * This class mainly focuses on WebSocket connection management and message routing.
 *
 * Two independent embedded servers back the listeners so the TLS certificate can be hot-swapped
 * without disturbing plain-ws traffic:
 * - the **plain server** hosts the ws connector on [host] (localhost) plus the `GET /jetwhale/ca`
 *   route, and
 * - the **TLS server** hosts the wss connector on `0.0.0.0:<wssPort>` backed by the active
 *   certificate.
 *
 * Both instances install the same application module ([configureWebSocket]) and therefore share the
 * same session map and event flows. When the active certificate changes while the TLS server is
 * running, only the TLS server is stopped and restarted with the new keystore
 * (see [restartTlsServer]); plain-ws sessions are unaffected while wss sessions drop and agents
 * reconnect against the new certificate.
 */
@SingleIn(AppScope::class)
@Inject
class KtorWebSocketServer(
    private val json: Json,
    private val negotiationStrategy: ServerSessionNegotiationStrategy,
    private val sslCertificateManager: SslCertificateManager,
) {
    private val logger = LoggerFactory.getLogger(KtorWebSocketServer::class.java)

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var plainServer: EmbeddedServer<*, *>? = null
    private var tlsServer: EmbeddedServer<*, *>? = null

    // Serializes TLS server lifecycle transitions (initial start and hot-swap restarts) so rapid
    // certificate changes cannot race a stop against a start.
    private val tlsServerMutex: Mutex = Mutex()
    private var certificateObserverJob: Job? = null

    private var currentHost: String? = null
    private var currentPort: Int? = null
    private var currentWssPort: Int? = null

    private val sessions: ConcurrentHashMap<String, WebSocketServerSession> = ConcurrentHashMap()

    private val mutableStatusFlow: MutableStateFlow<DebugWebSocketServerStatus> = MutableStateFlow(DebugWebSocketServerStatus.Stopped)
    val statusFlow: StateFlow<DebugWebSocketServerStatus> = mutableStatusFlow

    private val mutableDebuggeeEventFlow: MutableSharedFlow<Pair<String, JetWhaleDebuggeeEvent>> = MutableSharedFlow()
    val debuggeeEventFlow: SharedFlow<Pair<String, JetWhaleDebuggeeEvent>> = mutableDebuggeeEventFlow

    private val mutableSessionClosedFlow: MutableSharedFlow<String> = MutableSharedFlow()
    val sessionClosedFlow: SharedFlow<String> = mutableSessionClosedFlow

    private val mutableNegotiationCompletedFlow: MutableSharedFlow<SessionOpened> = MutableSharedFlow()
    val negotiationCompletedFlow: SharedFlow<SessionOpened> = mutableNegotiationCompletedFlow

    suspend fun start(host: String, port: Int, wssPort: Int?) {
        mutableStatusFlow.update { DebugWebSocketServerStatus.Starting }
        try {
            currentHost = host
            currentPort = port
            currentWssPort = wssPort

            buildPlainServer(host, port).also {
                it.startSuspend()
                plainServer = it
            }

            val tlsStarted = if (wssPort != null) {
                tlsServerMutex.withLock { startTlsServerLocked(wssPort) }
            } else {
                false
            }

            if (wssPort != null && tlsStarted) {
                startCertificateObserver()
            }

            // Started reflects both listeners. The wss port is only reported when the TLS server is
            // actually up, so a certificate that fails to load surfaces as a plain-only server.
            mutableStatusFlow.update {
                DebugWebSocketServerStatus.Started(host, port, wssPort.takeIf { tlsStarted })
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
        certificateObserverJob?.cancel()
        certificateObserverJob = null
        tlsServerMutex.withLock {
            tlsServer?.stopSuspend()
            tlsServer = null
        }
        plainServer?.stopSuspend()
        plainServer = null
        currentHost = null
        currentPort = null
        currentWssPort = null
        mutableStatusFlow.update { DebugWebSocketServerStatus.Stopped }
    }

    /** Builds the plain-ws server: the ws connector on [host] plus the `GET /jetwhale/ca` route. */
    private fun buildPlainServer(host: String, port: Int): EmbeddedServer<*, *> = embeddedServer(
        factory = Netty,
        host = host,
        port = port,
        module = { configureWebSocket() },
    )

    /**
     * Builds the TLS (wss) server backed by [keyStore]/[keyAlias]. Unlike the plain ws connector
     * (kept on localhost, reachable only via ADB forwarding), the TLS connector listens on all
     * interfaces so physical devices on the same network (e.g. iPhones) can connect. The channel is
     * encrypted and clients pin the local CA, so LAN exposure is limited to the encrypted endpoint.
     */
    private fun buildTlsServer(wssPort: Int, keyStore: KeyStore, keyAlias: String): EmbeddedServer<*, *> = embeddedServer(
        factory = Netty,
        environment = applicationEnvironment {},
        configure = {
            sslConnector(
                keyStore = keyStore,
                keyAlias = keyAlias,
                keyStorePassword = { sslCertificateManager.getKeyStorePassword() },
                privateKeyPassword = { sslCertificateManager.getKeyStorePassword() },
            ) {
                this.host = "0.0.0.0"
                this.port = wssPort
            }
        },
        module = { configureWebSocket() },
    )

    /**
     * Starts the TLS server on [wssPort] backed by the currently active certificate, generating one
     * on demand when none exists. Returns true when the connector started; returns false (leaving
     * the plain server untouched) when the active certificate cannot be loaded, so ADB-forwarded
     * local debugging keeps working.
     *
     * Must be called while holding [tlsServerMutex].
     */
    private suspend fun startTlsServerLocked(wssPort: Int): Boolean {
        if (!sslCertificateManager.hasCertificate()) {
            sslCertificateManager.generateAndAddCertificate(null)
        }

        val keyStore = sslCertificateManager.getActiveKeyStore()
        val keyAlias = sslCertificateManager.getActiveKeyAlias()
        if (keyStore == null || keyAlias == null) {
            logger.error(
                "wss was enabled on port $wssPort but the active certificate could not be loaded; " +
                    "starting without the TLS connector. Re-generate a certificate from the server settings.",
            )
            return false
        }

        buildTlsServer(wssPort, keyStore, keyAlias).also {
            it.startSuspend()
            tlsServer = it
        }
        return true
    }

    /**
     * Observes the active certificate and hot-swaps the TLS server whenever it changes while the
     * server is running. The current active certificate the TLS server already started with is
     * dropped so only subsequent changes trigger a restart.
     */
    private fun startCertificateObserver() {
        certificateObserverJob?.cancel()
        certificateObserverJob = coroutineScope.launch {
            sslCertificateManager.certificatesFlow
                .map { certificates -> certificates.find { it.isActive }?.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { restartTlsServer() }
        }
    }

    /**
     * Stops and restarts only the TLS server with the currently active certificate. The overall
     * status stays [DebugWebSocketServerStatus.Started] throughout (the plain server is unaffected),
     * so the reported wss port does not flap during the sub-second swap. In-flight wss sessions drop
     * and agents reconnect against the new certificate; plain-ws sessions are untouched. Restarts
     * are serialized on [tlsServerMutex] so rapid certificate changes cannot race.
     */
    private suspend fun restartTlsServer() {
        tlsServerMutex.withLock {
            val wssPort = currentWssPort ?: return
            tlsServer?.stopSuspend()
            tlsServer = null
            startTlsServerLocked(wssPort)
        }
    }

    private fun Application.configureWebSocket() {
        install(WebSockets.Plugin) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }

        routing {
            // Serves the active CA certificate so agents can fetch and pin it at connect time
            // (trust-on-first-use) without hardcoding a PEM. Both servers install this module, so the
            // route is reachable over the plain port (localhost / ADB) and over the TLS port on
            // 0.0.0.0 (LAN devices such as iPhones that cannot reach the loopback-bound plain server).
            // The CA certificate is public trust-anchor material, so exposing it is not a secret leak.
            get("/jetwhale/ca") {
                val caCertificatePem = sslCertificateManager.getActiveCertificate()?.caCertificatePem
                if (caCertificatePem == null) {
                    call.respond(HttpStatusCode.NotFound, "No active certificate")
                } else {
                    call.respondText(caCertificatePem, ContentType.parse("application/x-pem-file"))
                }
            }
            webSocket {
                context(log) {
                    configureSession()
                }
            }
        }
    }

    context(log: Logger)
    private suspend fun DefaultWebSocketServerSession.configureSession() {
        val transportSecurity = when {
            // Arrived through the TLS (wss) connector: encrypted end to end.
            call.request.origin.scheme == "https" -> SessionTransportSecurity.TLS

            // Plain ws whose peer is loopback: traffic never leaves the machine (the ADB-forwarded
            // case), so it is effectively secure.
            call.request.origin.remoteHost in LOOPBACK_HOSTS -> SessionTransportSecurity.LOOPBACK

            else -> SessionTransportSecurity.PLAINTEXT
        }

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

                mutableNegotiationCompletedFlow.emit(SessionOpened(negotiationResult, transportSecurity))

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

/** Hosts treated as loopback for [SessionTransportSecurity.LOOPBACK] classification. */
private val LOOPBACK_HOSTS = setOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost")

/**
 * A completed session negotiation together with transport metadata the negotiation itself does not
 * carry.
 *
 * @property result The successful negotiation outcome.
 * @property transportSecurity Security of the transport carrying the session.
 */
data class SessionOpened(
    val result: ServerSessionNegotiationResult.Success,
    val transportSecurity: SessionTransportSecurity,
)
