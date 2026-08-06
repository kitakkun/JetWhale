package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import com.kitakkun.jetwhale.protocol.serialization.decodeFromStringOrNull
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Ktor-based implementation of [JetWhaleSocketClient].
 *
 * The [HttpClient] is built by [httpClientProvider] at connection time rather than construction
 * time. This is required for two reasons: SSL must be configured via the engine{} block when the
 * client is built (Ktor ignores engine{} blocks applied through HttpClient.config{} afterwards),
 * and the trusted CA may only become known at connect time when it is fetched from the host.
 *
 * A client therefore serves exactly one connection and is owned by this class: [httpClientProvider]
 * hands over a client to dispose of, and every path out of [openConnection] goes through
 * [releaseHttpClient]. Without that, each attempt would strand an engine's thread pool.
 */
internal class KtorWebSocketClient(
    private val json: Json,
    private val negotiationStrategy: ClientSessionNegotiationStrategy,
    private val sslConfiguration: JetWhaleSslConfiguration,
    private val httpClientProvider: (JetWhaleSslConfiguration) -> HttpClient,
) : JetWhaleSocketClient {
    private var session: DefaultClientWebSocketSession? = null
    private var httpClient: HttpClient? = null

    /** Production constructor: builds the engine with SSL configured at construction time. */
    constructor(
        json: Json,
        negotiationStrategy: ClientSessionNegotiationStrategy,
        sslConfiguration: JetWhaleSslConfiguration = JetWhaleSslConfiguration(),
    ) : this(
        json = json,
        negotiationStrategy = negotiationStrategy,
        sslConfiguration = sslConfiguration,
        httpClientProvider = { resolvedConfiguration ->
            // One client, configured in full here: an engine{} block only takes effect while the
            // client is being built, so the SSL setup cannot be applied to it afterwards.
            HttpClient(defaultKtorEngineFactory()) {
                engine {
                    configureSsl(resolvedConfiguration)
                }
                configureWebSocketClient(json)
            }
        },
    )

    override suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent) {
        session?.sendSerialized(event)
    }

    override suspend fun closeConnection() {
        val session = this.session
        this.session = null
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "JetWhale session stopped"))
        releaseHttpClient()
    }

    override suspend fun openConnection(endpoint: ResolvedEndpoint): JetWhaleConnection {
        // A connection that ended on its own (host disconnect, error) never reached closeConnection,
        // so its client is still held here. Release it before this attempt allocates another.
        releaseHttpClient()

        // A plain endpoint has no certificate to fetch or pin, so none of the trust machinery runs for
        // it — including the CA fetch, which on a browser is two failed requests before every attempt.
        val resolvedConfiguration = if (endpoint.useWss) {
            resolveSslConfiguration(endpoint.host, endpoint.port)
        } else {
            JetWhaleSslConfiguration()
        }
        val client = httpClientProvider(resolvedConfiguration)
        httpClient = client

        try {
            val session = client.webSocketSession(
                host = endpoint.host,
                port = endpoint.port,
            ) {
                url {
                    // From the endpoint, not from whether trust material was obtained: a wss endpoint
                    // whose CA could not be fetched must fail visibly on trust, not quietly dial plain
                    // text at a TLS port and fail there instead.
                    protocol = if (endpoint.useWss) URLProtocol.WSS else URLProtocol.WS
                }
            }
            this.session = session
            return session.configureSession()
        } catch (e: Throwable) {
            // A refused host and a failed negotiation both land here, and the reconnect loop will
            // try again; without this every attempt would strand an engine's thread pool.
            releaseHttpClient()
            throw e
        }
    }

    private fun releaseHttpClient() {
        val client = httpClient ?: return
        httpClient = null
        client.close()
    }

    /**
     * Produces the SSL configuration effective for this connection. When
     * [JetWhaleSslConfiguration.trustServerCertificate] is set, the host's active CA is fetched and
     * pinned; on failure only the manually configured certificates (if any) remain. The scheme is not
     * revisited either way — it comes from the endpoint, so a failed fetch surfaces as a trust failure
     * rather than as traffic quietly sent in the clear.
     */
    private suspend fun resolveSslConfiguration(host: String, port: Int): JetWhaleSslConfiguration {
        val fetchedCaPem = if (sslConfiguration.trustServerCertificate) {
            fetchCaCertificate(host, port)
        } else {
            null
        }

        return JetWhaleSslConfiguration().apply {
            sslConfiguration.trustedCertificates.forEach { trustCertificate(it) }
            fetchedCaPem?.let { trustCertificate(it) }
        }
    }

    /**
     * Fetches the host's active CA certificate PEM from the well-known `/jetwhale/ca` endpoint,
     * probing the same [port] the wss connection targets in two topologies:
     *
     * 1. `http://<host>:<port>/jetwhale/ca` — the plain channel. Cheap and works when [port] is the
     *    host's plain-ws port (localhost / ADB port forwarding), where the plain server also serves
     *    the route.
     * 2. `https://<host>:<port>/jetwhale/ca` **with certificate verification disabled** — used when
     *    the plain fetch is unreachable, e.g. a LAN device connecting to the TLS server on the wss
     *    port (the plain server is bound to loopback). The TLS server serves the same route. Skipping
     *    verification here is security-equivalent to the plain fetch: both are trust-on-first-use and
     *    the fetched CA still pins the subsequent wss session.
     *
     * Returns null when neither attempt succeeds, leaving the caller with nothing to pin.
     */
    private suspend fun fetchCaCertificate(host: String, port: Int): String? {
        fetchCaCertificate("http://$host:$port/jetwhale/ca", disableVerification = false)?.let { return it }
        return fetchCaCertificate("https://$host:$port/jetwhale/ca", disableVerification = true)
    }

    private suspend fun fetchCaCertificate(url: String, disableVerification: Boolean): String? {
        val httpClient = HttpClient(defaultKtorEngineFactory()) {
            if (disableVerification) {
                engine { disableCertificateVerification() }
            }
        }
        return try {
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                response.bodyAsText().also {
                    JetWhaleLogger.i("Fetched CA certificate from $url for trust-on-first-use pinning")
                }
            } else {
                JetWhaleLogger.w("Host returned ${response.status} for the CA certificate at $url")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            JetWhaleLogger.w("Failed to fetch CA certificate from $url", e)
            null
        } finally {
            httpClient.close()
        }
    }

    @OptIn(InternalJetWhaleApi::class)
    private suspend fun DefaultClientWebSocketSession.configureSession(): JetWhaleConnection {
        JetWhaleLogger.v("Configuring WebSocket session")

        val negotiationResult = with(negotiationStrategy) { negotiate() }

        when (negotiationResult) {
            is ClientSessionNegotiationResult.Success -> {
                JetWhaleLogger.d("Session negotiation succeeded: $negotiationResult")
            }

            is ClientSessionNegotiationResult.Failure -> {
                JetWhaleLogger.e("Session negotiation failed: ${negotiationResult.reason}")
                throw IllegalStateException("Session negotiation failed: ${negotiationResult.reason}")
            }
        }

        closeReason.invokeOnCompletion {
            JetWhaleLogger.i("WebSocket session closed")
            session = null
        }

        JetWhaleLogger.i("WebSocket session established")

        val debuggerEventFlow = incoming.consumeAsFlow().filterIsInstance<Frame.Text>().mapNotNull {
            json.decodeFromStringOrNull<JetWhaleDebuggerEvent>(it.readText())
        }

        return JetWhaleConnection(
            negotiationResult = negotiationResult,
            debuggerEventFlow = debuggerEventFlow,
        )
    }
}

/**
 * Installs what [KtorWebSocketClient] needs from a client. Applied while the client is being built,
 * so a connection costs exactly one client rather than one plus a `config { }` derivative.
 */
internal fun HttpClientConfig<*>.configureWebSocketClient(json: Json) {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(json)
    }

    install(Logging) {
        logger = JetWhaleLogger
        level = when (JetWhaleLogger.ktorLogLevel) {
            KtorLogLevel.ALL -> LogLevel.ALL
            KtorLogLevel.HEADERS -> LogLevel.HEADERS
            KtorLogLevel.BODY -> LogLevel.BODY
            KtorLogLevel.NONE -> LogLevel.NONE
        }
    }
}
