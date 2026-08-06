package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson

/**
 * A running JetWhale debug session: one connection to the host, which lists and groups it as a
 * single app under its device.
 *
 * An app that connects once and stays connected for its whole lifetime can ignore the handle. It
 * matters when the process owns more than one session, or wants to give one up on purpose.
 */
public interface JetWhaleSession {
    /**
     * Disconnects this session from the host and releases the resources it holds.
     *
     * Terminal: the reconnect loop is torn down and the registered plugins are dropped, so the
     * session cannot be revived. Call [startJetWhale] again — with fresh plugin instances, since a
     * plugin is bound to the session it was registered with — for a new one. Repeated calls are
     * ignored.
     *
     * Returns as soon as the teardown is scheduled, so the host observes the disconnect shortly
     * after rather than by the time this returns.
     */
    public fun stop()
}

/**
 * Starts the JetWhale Messaging Service with the provided configuration.
 *
 * @param configure A lambda function to configure the JetWhale service.
 * @return a handle to the started session. An app that connects once at startup and stays connected
 *   for as long as it runs can ignore it.
 */
@OptIn(InternalJetWhaleApi::class)
public fun startJetWhale(configure: JetWhaleConfigurationScope.() -> Unit): JetWhaleSession {
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
    service.startService(configuration.connection.endpointResolver())
    return MessagingServiceSession(service)
}

/** The resolver a configured `endpoint` amounts to, once `ssl {}` is known. */
internal fun JetWhaleConnectionConfiguration.endpointResolver(): EndpointResolver = when (val endpoint = endpoint) {
    is JetWhaleLiteralEndpoint -> FixedEndpointResolver(endpoint.resolved(sslConfiguration.isEnabled))

    is JetWhaleDiscoveredEndpoint -> MdnsEndpointResolver(
        discovery = HostDiscoveryConfig(
            hostNames = endpoint.hostNames,
            addresses = endpoint.addresses,
            // The connection uses wss exactly when SSL is configured, so a discovered host has to
            // advertise that scheme's port. Read here rather than in `discovered()` so `ssl {}` and
            // `endpoint =` can appear in either order.
            useWss = sslConfiguration.isEnabled,
        ),
        fallback = endpoint.fallback.resolved(sslConfiguration.isEnabled),
    )
}

/** @param sslConfigured what `ssl { }` decided, which every endpoint but [JetWhalePlainLoopbackEndpoint] follows. */
private fun JetWhaleLiteralEndpoint.resolved(sslConfigured: Boolean): ResolvedEndpoint = when (this) {
    is JetWhaleFixedEndpoint -> ResolvedEndpoint(host, port, useWss = sslConfigured)
    is JetWhalePlainLoopbackEndpoint -> ResolvedEndpoint(LOOPBACK_HOST, port, useWss = false)
}

/** The name loopback is dialled by. RFC 6761 reserves it, so it always resolves to the loopback interface. */
private const val LOOPBACK_HOST = "localhost"

private class MessagingServiceSession(private val service: JetWhaleMessagingService) : JetWhaleSession {
    override fun stop() {
        service.stopService()
    }
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
     * icons whose base64-encoded form exceeds 32KB are dropped so the negotiation payload stays small.
     */
    public var appIconPng: ByteArray?
}

@JetWhaleDsl
public interface JetWhaleConnectionConfigurationScope {
    /**
     * The JetWhale host this agent connects to. Assign either [at] for a literal address, or
     * [discovered] to find the host on the local network over mDNS/Bonjour.
     *
     * Defaults to `fixed("localhost", 8080)`.
     */
    public var endpoint: JetWhaleEndpoint

    @Deprecated(
        message = "Superseded by 'endpoint'. Assign endpoint = fixed(host, port) instead. " +
            "Setting host/port still works and is equivalent to that assignment.",
        level = DeprecationLevel.WARNING,
    )
    public var host: String

    @Deprecated(
        message = "Superseded by 'endpoint'. Assign endpoint = fixed(host, port) instead. " +
            "Setting host/port still works and is equivalent to that assignment.",
        level = DeprecationLevel.WARNING,
    )
    public var port: Int

    /**
     * A literal address to connect to, e.g. `localhost` for an emulator/simulator or an
     * ADB-forwarded device.
     *
     * @param host The hostname or IP address of the JetWhale host.
     * @param port The port the JetWhale host's debug server listens on.
     */
    public fun fixed(host: String, port: Int): JetWhaleFixedEndpoint

    /**
     * The loopback interface, reached in the clear on [port] whatever `ssl { }` says.
     *
     * There is no host parameter, and that is the point: plain text is safe here because it cannot
     * leave the machine, and a signature that cannot name anything else cannot be pointed at the
     * network by accident. The host serves its plain-ws port on loopback only, for the same reason.
     *
     * Use it as the fallback for targets that cannot do wss but can reach the host locally. A browser
     * is the clear case: TLS trust belongs to the browser there, so `trustServerCertificate()` has
     * nothing to pin with and no wss connection is possible — while `ws://localhost` is unremarkable.
     * Emulators and ADB-forwarded devices reach loopback too, so one shared configuration covers them
     * alongside a discovered host for physical devices.
     *
     * Nothing about `ssl { }` is skipped for other endpoints; this is the one exception, and it is
     * limited to loopback by construction.
     *
     * @param port The host's plain-ws port.
     */
    public fun plainLoopback(port: Int): JetWhalePlainLoopbackEndpoint

    /**
     * A host found by zero-config discovery over mDNS/DNS-SD (Bonjour): the agent browses the local
     * network for the `_jetwhale._tcp` service the host advertises while its debug server runs, and
     * connects to the address and port it advertises. Use this for physical LAN devices, which cannot
     * reach the host over `localhost`.
     *
     * The advertised wss port is used when `ssl {}` is configured, otherwise the plain-ws port — so
     * discovery resolves the port too, and [fallback]'s port applies only when discovery finds nothing.
     *
     * Discovery is best-effort, which is why [fallback] is required: when no matching host is found
     * within a short timeout, or the platform does not support mDNS (JS/Wasm/Linux/Windows), the
     * connection falls back to it with a warning log.
     *
     * iOS requires `_jetwhale._tcp` to be listed under `NSBonjourServices` in `Info.plist` alongside
     * `NSLocalNetworkUsageDescription`, otherwise the OS blocks the browse.
     *
     * When several JetWhale hosts advertise on the network, narrow the selection with [configure]
     * (see [JetWhaleDiscoveredEndpointScope]); with no filter the first discovered host is used and a
     * warning listing all discovered hosts is logged when more than one is found.
     *
     * @param fallback Used when discovery finds no matching host in time, or the platform lacks mDNS.
     * @param configure Optional filters narrowing which discovered host is accepted.
     */
    public fun discovered(
        fallback: JetWhaleLiteralEndpoint,
        configure: JetWhaleDiscoveredEndpointScope.() -> Unit = {},
    ): JetWhaleEndpoint

    /**
     * Configures SSL settings for the connection. When at least one trusted certificate is
     * registered, the connection is established over wss instead of plain ws.
     *
     * @param configure A lambda function to configure SSL settings.
     */
    public fun ssl(configure: JetWhaleSslConfigurationScope.() -> Unit)
}

/**
 * Where the agent connects to. Build one with [JetWhaleConnectionConfigurationScope.at] or
 * [JetWhaleConnectionConfigurationScope.discovered].
 */
public sealed interface JetWhaleEndpoint

/**
 * An endpoint written down rather than looked up, and so usable as a discovery fallback.
 *
 * Discovery cannot fall back to more discovery, which is why the fallback is typed to this rather
 * than to [JetWhaleEndpoint].
 */
public sealed interface JetWhaleLiteralEndpoint : JetWhaleEndpoint

/** A literal host/port, as built by [JetWhaleConnectionConfigurationScope.fixed]. */
public class JetWhaleFixedEndpoint internal constructor(
    internal val host: String,
    internal val port: Int,
) : JetWhaleLiteralEndpoint

/** A loopback port reached in the clear, as built by [JetWhaleConnectionConfigurationScope.plainLoopback]. */
public class JetWhalePlainLoopbackEndpoint internal constructor(
    internal val port: Int,
) : JetWhaleLiteralEndpoint

/**
 * Allowlists narrowing which discovered host is accepted. Each is repeatable and independently
 * optional; a host has to satisfy every allowlist that has entries, and one that does not is skipped
 * rather than connected to.
 */
@JetWhaleDsl
public interface JetWhaleDiscoveredEndpointScope {
    /**
     * Adds a hostname to the allowlist: a discovered host is accepted only when its advertised
     * hostname equals one of the added names, compared case-insensitively. Repeatable. The compared
     * value is the host machine's hostname (the `hostName` TXT record, falling back to the mDNS
     * instance name).
     *
     * @param name A hostname a discovered host is allowed to advertise.
     */
    public fun allowHostName(name: String)

    /**
     * Adds an IP address to the allowlist: a discovered host is accepted only when it resolves to one
     * of the added addresses. Repeatable. Use this to pin discovery to a specific machine, e.g. your
     * build machine's LAN IP.
     *
     * @param ip An IP address a discovered host is allowed to resolve to.
     */
    public fun allowAddress(ip: String)
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

/** A host to be discovered over mDNS, as built by [JetWhaleConnectionConfigurationScope.discovered]. */
internal class JetWhaleDiscoveredEndpoint(
    val hostNames: List<String>,
    val addresses: List<String>,
    val fallback: JetWhaleLiteralEndpoint,
) : JetWhaleEndpoint

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class JetWhaleConnectionConfiguration : JetWhaleConnectionConfigurationScope {
    override var host: String = "localhost"
    override var port: Int = 8080
    val sslConfiguration: JetWhaleSslConfiguration = JetWhaleSslConfiguration()

    private var assignedEndpoint: JetWhaleEndpoint? = null

    // Falling back to host/port keeps the deprecated properties working: leaving `endpoint` unassigned
    // resolves to whatever they hold, defaults included.
    override var endpoint: JetWhaleEndpoint
        get() = assignedEndpoint ?: fixed(host, port)
        set(value) {
            assignedEndpoint = value
        }

    override fun fixed(host: String, port: Int): JetWhaleFixedEndpoint = JetWhaleFixedEndpoint(host, port)

    override fun plainLoopback(port: Int): JetWhalePlainLoopbackEndpoint = JetWhalePlainLoopbackEndpoint(port)

    override fun discovered(
        fallback: JetWhaleLiteralEndpoint,
        configure: JetWhaleDiscoveredEndpointScope.() -> Unit,
    ): JetWhaleEndpoint {
        val filters = JetWhaleDiscoveredEndpointConfiguration().apply(configure)
        return JetWhaleDiscoveredEndpoint(
            hostNames = filters.hostNames,
            addresses = filters.addresses,
            fallback = fallback,
        )
    }

    override fun ssl(configure: JetWhaleSslConfigurationScope.() -> Unit) {
        sslConfiguration.configure()
    }
}

private class JetWhaleDiscoveredEndpointConfiguration : JetWhaleDiscoveredEndpointScope {
    private val mutableHostNames: MutableList<String> = mutableListOf()
    val hostNames: List<String> get() = mutableHostNames

    private val mutableAddresses: MutableList<String> = mutableListOf()
    val addresses: List<String> get() = mutableAddresses

    override fun allowHostName(name: String) {
        mutableHostNames.add(name)
    }

    override fun allowAddress(ip: String) {
        mutableAddresses.add(ip)
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
