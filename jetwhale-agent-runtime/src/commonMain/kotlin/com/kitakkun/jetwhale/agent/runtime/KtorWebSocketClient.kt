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
import io.ktor.websocket.Frame
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
 */
internal class KtorWebSocketClient(
    private val json: Json,
    private val negotiationStrategy: ClientSessionNegotiationStrategy,
    private val sslConfiguration: JetWhaleSslConfiguration,
    private val httpClientProvider: (JetWhaleSslConfiguration) -> HttpClient,
) : JetWhaleSocketClient {
    private var session: DefaultClientWebSocketSession? = null

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
            HttpClient(defaultKtorEngineFactory()) {
                engine {
                    configureSsl(resolvedConfiguration)
                }
            }
        },
    )

    /** Test constructor: uses a prebuilt [HttpClient] (e.g. the Ktor test client) as-is. */
    constructor(
        json: Json,
        negotiationStrategy: ClientSessionNegotiationStrategy,
        httpClient: HttpClient,
    ) : this(
        json = json,
        negotiationStrategy = negotiationStrategy,
        sslConfiguration = JetWhaleSslConfiguration(),
        httpClientProvider = { httpClient },
    )

    override suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent) {
        session?.sendSerialized(event)
    }

    override suspend fun openConnection(
        host: String,
        port: Int,
    ): JetWhaleConnection {
        val resolvedConfiguration = resolveSslConfiguration(host, port)
        val client = httpClientProvider(resolvedConfiguration).config {
            configureHttpClient()
        }

        val session = client.webSocketSession(
            host = host,
            port = port,
        ) {
            url {
                protocol = if (resolvedConfiguration.isEnabled) URLProtocol.WSS else URLProtocol.WS
            }
        }
        this.session = session
        return session.configureSession()
    }

    /**
     * Produces the SSL configuration effective for this connection. When
     * [JetWhaleSslConfiguration.trustServerCertificate] is set, the host's active CA is fetched and
     * pinned; on failure the manually configured certificates (if any) are used, otherwise the
     * connection falls back to plain ws.
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
     * Returns null when neither attempt succeeds, so the caller falls back to plain ws.
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

    private fun HttpClientConfig<*>.configureHttpClient() {
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
}
