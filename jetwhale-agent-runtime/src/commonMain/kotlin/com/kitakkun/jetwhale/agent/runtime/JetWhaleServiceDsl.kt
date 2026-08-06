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

/** The resolver a configured `endpoints { }` amounts to, in the order its candidates were declared. */
internal fun JetWhaleConnectionConfiguration.endpointResolver(): EndpointResolver {
    val declared = candidates.ifEmpty {
        // Nothing declared: the deprecated host/port carry whatever they hold, defaults included, and
        // take their scheme from `ssl { }` the way the whole connection used to.
        @Suppress("DEPRECATION")
        listOf(EndpointCandidate.Static(host, port, useWss = sslConfiguration.isEnabled))
    }
    declared.forEach { it.warnIfPlainOffMachine() }
    return CandidateListResolver(declared.map { it.resolver() })
}

private fun EndpointCandidate.resolver(): EndpointResolver = when (this) {
    is EndpointCandidate.Static -> FixedEndpointResolver(ResolvedEndpoint(host, port, useWss = useWss))

    is EndpointCandidate.Dynamic -> MdnsEndpointResolver(
        HostDiscoveryConfig(hostNames = hostNames, addresses = addresses, acceptsAnyHost = acceptsAnyHost),
    )
}

/**
 * Says so when a candidate will send in the clear to something that is not this machine.
 *
 * Not an error: `ws` is what the caller asked for, and a debug tool may take it. But plain text
 * leaving the machine is worth one line in the log rather than none. Discovered hosts cannot reach
 * this state — they are always dialled over wss — so only a written-out address is checked.
 */
private fun EndpointCandidate.warnIfPlainOffMachine() {
    if (this !is EndpointCandidate.Static || useWss || isLoopbackHost(host)) return
    JetWhaleLogger.w("Configured to connect to $host:$port in the clear — plain text will leave this machine.")
}

/** RFC 6761 reserves `localhost` for the loopback interface, so the name is as good as the addresses. */
private fun isLoopbackHost(host: String): Boolean = host == "localhost" || host == "::1" || host.startsWith("127.")

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
     * Where this agent will look for the host, best first.
     *
     * **Declaration order is dial order.** The agent works down the list from the top, and the first
     * candidate that accepts a connection keeps it — the rest are never tried. When none accepts, it
     * waits and starts again from the top, so a host that comes up later is still found. A
     * `discoverWss` entry expands to every host it finds, each tried in browse order before whatever
     * was declared after it.
     *
     * Put the cheapest and most likely first. Declaring several is how one configuration serves
     * targets that reach the host differently: an emulator over loopback, a physical device over the
     * network, a browser which can only ever do plain ws.
     *
     * ```
     * endpoints {
     *     ws("localhost", 5080)
     *     discoverWss { allowHostName("my-macbook") }
     * }
     * ```
     *
     * Repeated blocks add to the list rather than replacing it. With no `endpoints { }` at all the
     * deprecated [host]/[port] apply, defaults included.
     */
    public fun endpoints(configure: JetWhaleEndpointScope.() -> Unit)

    @Deprecated(
        message = "Superseded by 'endpoints { }'. Declare endpoints { wss(host, port) } instead. " +
            "Setting host/port still works and amounts to a single candidate taking its scheme from ssl { }.",
        level = DeprecationLevel.WARNING,
    )
    public var host: String

    @Deprecated(
        message = "Superseded by 'endpoints { }'. Declare endpoints { wss(host, port) } instead. " +
            "Setting host/port still works and amounts to a single candidate taking its scheme from ssl { }.",
        level = DeprecationLevel.WARNING,
    )
    public var port: Int

    /**
     * Configures SSL settings for the connection. When at least one trusted certificate is
     * registered, the connection is established over wss instead of plain ws.
     *
     * @param configure A lambda function to configure SSL settings.
     */
    public fun ssl(configure: JetWhaleSslConfigurationScope.() -> Unit)
}

/**
 * Declares where to look for the host, in order.
 *
 * Each candidate names its own scheme, because the targets one configuration serves do not agree on
 * it: a browser cannot do wss against a locally-issued CA at all, while a physical device on the
 * network can do nothing else. What TLS trusts, once it is spoken, is the separate concern `ssl { }`
 * answers.
 */
@JetWhaleDsl
public interface JetWhaleEndpointScope {
    /**
     * A written-out `ws://host:port` — no TLS.
     *
     * The host serves plain ws on loopback alone, so this is for the targets that are already on the
     * machine: emulators, simulators, ADB-forwarded devices, the desktop app, and browsers, which
     * cannot pin a locally-issued CA and so have no other way in. Anywhere else the traffic leaves
     * the machine unencrypted, which is logged as a warning.
     *
     * @param host The hostname or IP address of the JetWhale host.
     * @param port The port serving plain ws. The host listens for ws and wss on different ports, and
     *   dialling the wrong one fails the handshake.
     */
    public fun ws(host: String, port: Int)

    /**
     * A written-out `wss://host:port`.
     *
     * @param host The hostname or IP address of the JetWhale host.
     * @param port The port serving wss. The host listens for ws and wss on different ports, and
     *   dialling the wrong one fails the handshake.
     */
    public fun wss(host: String, port: Int)

    /**
     * Whatever hosts are found by zero-config discovery over mDNS/DNS-SD (Bonjour), each dialled over
     * wss: the agent browses the local network for the `_jetwhale._tcp` service a host advertises
     * while its debug server runs. Use this for physical devices, which cannot reach the host over
     * loopback.
     *
     * There is no plain-ws counterpart, and not only because sending in the clear across a network is
     * a poor idea: the host binds ws to loopback, so a discovered address would refuse the connection
     * anyway.
     *
     * Every host that answers and advertises a wss port becomes a candidate of its own, in browse
     * order, before whatever is declared next — answering mDNS says a host is advertising, not that
     * it will accept a connection. Where mDNS is unavailable (JS/Wasm, Linux, Windows) this
     * contributes nothing and the next candidate is reached immediately.
     *
     * iOS requires `_jetwhale._tcp` to be listed under `NSBonjourServices` in `Info.plist` alongside
     * `NSLocalNetworkUsageDescription`, otherwise the OS blocks the browse.
     *
     * @param configure Which discovered hosts are acceptable. Required, and not merely as a matter of
     *   taste: a block stating nothing accepts nothing, because discovery reaches every JetWhale host
     *   on the network and on a shared one those belong to other people.
     */
    public fun discoverWss(configure: JetWhaleDiscoveryScope.() -> Unit)
}

/**
 * Which discovered hosts this agent will connect to.
 *
 * Something has to be stated: a block that says nothing accepts nothing, and logs why. The allowlists
 * are repeatable and independent — a host has to satisfy every allowlist that has entries, and one
 * that does not is skipped rather than connected to.
 *
 * They choose between hosts; they do not authenticate one. An mDNS advertisement is unauthenticated,
 * so anything on the network can claim any hostname — matching one proves nothing about who answered.
 * That is what pinning a certificate with `trustCertificate` is for.
 */
@JetWhaleDsl
public interface JetWhaleDiscoveryScope {
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

    /**
     * Accepts every host advertising the service, wherever it is and whoever runs it.
     *
     * This is the one way to browse without qualification, and it is stated rather than assumed: on a
     * shared network — an office, a coworking space — an unqualified browse reaches colleagues' hosts
     * too, and whichever answers first wins. That would hand this app's debug traffic to someone
     * else's window. Naming the machine with [allowHostName] or [allowAddress] is the better answer
     * wherever the network is not exclusively yours; reach for this on one that is.
     */
    public fun allowAll()
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

/** One place to look for the host, as declared in `endpoints { }`. */
internal sealed interface EndpointCandidate {
    data class Static(val host: String, val port: Int, val useWss: Boolean) : EndpointCandidate

    /** Always wss: the host serves plain ws on loopback, which discovery never returns. */
    data class Dynamic(
        val hostNames: List<String>,
        val addresses: List<String>,
        val acceptsAnyHost: Boolean,
    ) : EndpointCandidate
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class JetWhaleConnectionConfiguration : JetWhaleConnectionConfigurationScope {
    override var host: String = "localhost"
    override var port: Int = 8080
    val sslConfiguration: JetWhaleSslConfiguration = JetWhaleSslConfiguration()

    private val declaredCandidates: MutableList<EndpointCandidate> = mutableListOf()
    val candidates: List<EndpointCandidate> get() = declaredCandidates

    // Repeated blocks add to the list rather than replacing it, so the order candidates were written
    // in is the order they are tried, wherever they were written.
    override fun endpoints(configure: JetWhaleEndpointScope.() -> Unit) {
        JetWhaleEndpointConfiguration(declaredCandidates).configure()
    }

    override fun ssl(configure: JetWhaleSslConfigurationScope.() -> Unit) {
        sslConfiguration.configure()
    }
}

private class JetWhaleEndpointConfiguration(
    private val candidates: MutableList<EndpointCandidate>,
) : JetWhaleEndpointScope {
    override fun ws(host: String, port: Int) {
        candidates += EndpointCandidate.Static(host, port, useWss = false)
    }

    override fun wss(host: String, port: Int) {
        candidates += EndpointCandidate.Static(host, port, useWss = true)
    }

    override fun discoverWss(configure: JetWhaleDiscoveryScope.() -> Unit) {
        val policy = JetWhaleDiscoveryConfiguration().apply(configure)
        candidates += EndpointCandidate.Dynamic(policy.hostNames, policy.addresses, policy.acceptsAnyHost)
    }
}

private class JetWhaleDiscoveryConfiguration : JetWhaleDiscoveryScope {
    private val mutableHostNames: MutableList<String> = mutableListOf()
    val hostNames: List<String> get() = mutableHostNames

    private val mutableAddresses: MutableList<String> = mutableListOf()
    val addresses: List<String> get() = mutableAddresses

    var acceptsAnyHost: Boolean = false
        private set

    override fun allowHostName(name: String) {
        mutableHostNames.add(name)
    }

    override fun allowAddress(ip: String) {
        mutableAddresses.add(ip)
    }

    override fun allowAll() {
        acceptsAnyHost = true
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
