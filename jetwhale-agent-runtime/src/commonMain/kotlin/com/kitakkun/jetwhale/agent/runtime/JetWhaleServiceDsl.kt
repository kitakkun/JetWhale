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
     * Application icon as PNG bytes. Auto-resolved from the app's own icon on Android, iOS and
     * macOS when left null. Provide an image already downscaled to at most 64x64 pixels; icons whose
     * base64-encoded form exceeds 32KB are dropped so the negotiation payload stays small.
     */
    public var appIconPng: ByteArray?
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
