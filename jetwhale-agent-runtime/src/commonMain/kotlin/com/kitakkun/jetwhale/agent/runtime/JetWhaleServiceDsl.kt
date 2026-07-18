package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson

/**
 * Starts the JetWhale Messaging Service with the provided configuration.
 *
 * @param configure A lambda function to configure the JetWhale service.
 */
@OptIn(InternalJetWhaleApi::class)
public fun startJetWhale(configure: JetWhaleConfigurationScope.() -> Unit) {
    val configuration = JetWhaleConfiguration().apply(configure)

    JetWhaleLogger.setEnabled(configuration.logging.enabled)
    JetWhaleLogger.setLogLevel(configuration.logging.logLevel)
    JetWhaleLogger.setKtorLogLevel(configuration.logging.ktorLogLevel)

    val json = JetWhaleJson
    val appMetadata = resolveAppMetadata(configuration.app.toResolvedConfiguration())
    val service: JetWhaleMessagingService =
        DefaultJetWhaleMessagingService(
            socketClient = KtorWebSocketClient(
                json = json,
                negotiationStrategy = DefaultClientSessionNegotiationStrategy(
                    plugins = configuration.plugins.plugins,
                    appMetadata = appMetadata,
                ),
                sslConfiguration = configuration.connection.sslConfiguration,
            ),
            pluginService = JetWhaleAgentPluginService(
                plugins = configuration.plugins.plugins,
            ),
        )
    service.startService(
        host = configuration.connection.host,
        port = configuration.connection.port,
    )
}

@DslMarker
internal annotation class JetWhaleDsl

@JetWhaleDsl
public interface JetWhaleConfigurationScope {
    public fun connection(configure: JetWhaleConnectionConfigurationScope.() -> Unit)
    public fun logging(configure: JetWhaleLoggingConfigurationScope.() -> Unit)
    public fun plugins(configure: JetWhalePluginConfigurationScope.() -> Unit)

    /**
     * Configures the application/device metadata reported to the host during session negotiation.
     * All values are optional: unset values are auto-resolved per platform on a best-effort basis.
     */
    public fun app(configure: JetWhaleAppConfigurationScope.() -> Unit)
}

/**
 * DSL scope for the application/device metadata reported to the host.
 * Explicit values set here always take precedence over auto-resolved defaults.
 */
@JetWhaleDsl
public interface JetWhaleAppConfigurationScope {
    /** Human-readable application name. Auto-resolved on Android/iOS/macOS when left null. */
    public var appName: String?

    /** Stable per-device identifier used by the host to group sessions. Auto-resolved on Android/iOS when left null. */
    public var deviceId: String?

    /** Human-readable device name. Defaults to the platform device/host name when left null. */
    public var deviceName: String?

    /**
     * Application icon as PNG bytes. Provide an image already downscaled to at most 64x64 pixels;
     * icons whose encoded PNG exceeds 32KB are dropped so the negotiation payload stays small.
     */
    public var appIconPng: ByteArray?
}

@JetWhaleDsl
public interface JetWhaleConnectionConfigurationScope {
    public var host: String
    public var port: Int

    /**
     * Configures SSL settings for the connection. When at least one trusted certificate is
     * registered, the connection is established over wss instead of plain ws.
     *
     * @param configure A lambda function to configure SSL settings.
     */
    public fun ssl(configure: JetWhaleSslConfigurationScope.() -> Unit)
}

@JetWhaleDsl
public interface JetWhaleSslConfigurationScope {
    /**
     * Adds a trusted certificate in PEM format.
     * This certificate will be used to verify the server's identity.
     *
     * @param pem The certificate in PEM format (including -----BEGIN CERTIFICATE----- and -----END CERTIFICATE----- markers).
     */
    public fun trustCertificate(pem: String)

    /**
     * Fetches the host's active CA certificate at connect time and pins the resulting wss connection
     * to it, so no CA certificate has to be hardcoded in the app.
     *
     * The CA is downloaded from `/jetwhale/ca` before the wss handshake, probing the configured
     * `port` in two topologies:
     * 1. `http://<host>:<port>/jetwhale/ca` — works when `port` is the host's plain-ws port
     *    (localhost / ADB port forwarding).
     * 2. `https://<host>:<port>/jetwhale/ca` with certificate verification disabled — used when the
     *    plain fetch is unreachable, e.g. a LAN device (iPhone) connecting to the TLS server on the
     *    wss port while the host's plain server is bound to loopback.
     *
     * Both are a trust-on-first-use exchange: the fetch itself is not authenticated. Over ADB port
     * forwarding (the primary use case) the download is as trustworthy as the ADB link, because the
     * traffic never leaves the machine. On an untrusted LAN prefer [trustCertificate] with a
     * manually exported CA for strict pinning.
     *
     * When the CA cannot be fetched over either channel, the connection falls back to plain ws.
     */
    public fun trustServerCertificate()
}

@JetWhaleDsl
public interface JetWhaleLoggingConfigurationScope {
    public var enabled: Boolean
    public var logLevel: LogLevel
    public var ktorLogLevel: KtorLogLevel
}

@JetWhaleDsl
public interface JetWhalePluginConfigurationScope {
    public fun register(plugin: AgentPlugin)
}

private class JetWhaleConfiguration : JetWhaleConfigurationScope {
    val connection: JetWhaleConnectionConfiguration = JetWhaleConnectionConfiguration()
    val logging: JetWhaleLoggingConfiguration = JetWhaleLoggingConfiguration()
    val plugins: JetWhalePluginConfiguration = JetWhalePluginConfiguration()
    val app: JetWhaleAppConfiguration = JetWhaleAppConfiguration()

    override fun connection(configure: JetWhaleConnectionConfigurationScope.() -> Unit) {
        connection.configure()
    }

    override fun logging(configure: JetWhaleLoggingConfigurationScope.() -> Unit) {
        logging.configure()
    }

    override fun plugins(configure: JetWhalePluginConfigurationScope.() -> Unit) {
        plugins.configure()
    }

    override fun app(configure: JetWhaleAppConfigurationScope.() -> Unit) {
        app.configure()
    }
}

private class JetWhaleAppConfiguration : JetWhaleAppConfigurationScope {
    override var appName: String? = null
    override var deviceId: String? = null
    override var deviceName: String? = null
    override var appIconPng: ByteArray? = null

    fun toResolvedConfiguration(): ResolvedAppConfiguration = ResolvedAppConfiguration(
        appName = appName,
        deviceId = deviceId,
        deviceName = deviceName,
        appIconPng = appIconPng,
    )
}

private class JetWhaleConnectionConfiguration : JetWhaleConnectionConfigurationScope {
    override var host: String = "localhost"
    override var port: Int = 8080
    val sslConfiguration: JetWhaleSslConfiguration = JetWhaleSslConfiguration()

    override fun ssl(configure: JetWhaleSslConfigurationScope.() -> Unit) {
        sslConfiguration.configure()
    }
}

internal class JetWhaleSslConfiguration : JetWhaleSslConfigurationScope {
    private val mutableTrustedCertificates: MutableList<String> = mutableListOf()

    /** List of trusted certificates in PEM format. */
    val trustedCertificates: List<String>
        get() = mutableTrustedCertificates

    /**
     * True when the host's active CA certificate should be fetched over the plain channel and pinned
     * at connect time.
     */
    var trustServerCertificate: Boolean = false
        private set

    /**
     * True when SSL is enabled, i.e. at least one trusted certificate is configured or the CA is to
     * be fetched from the host at connect time.
     */
    val isEnabled: Boolean
        get() = mutableTrustedCertificates.isNotEmpty() || trustServerCertificate

    override fun trustCertificate(pem: String) {
        mutableTrustedCertificates.add(pem)
    }

    override fun trustServerCertificate() {
        trustServerCertificate = true
    }
}

private class JetWhaleLoggingConfiguration : JetWhaleLoggingConfigurationScope {
    override var enabled: Boolean = true
    override var logLevel: LogLevel = LogLevel.WARN
    override var ktorLogLevel: KtorLogLevel = KtorLogLevel.NONE
}

private class JetWhalePluginConfiguration : JetWhalePluginConfigurationScope {
    val plugins: MutableList<AgentPlugin> = mutableListOf()

    override fun register(plugin: AgentPlugin) {
        plugins.add(plugin)
    }
}
