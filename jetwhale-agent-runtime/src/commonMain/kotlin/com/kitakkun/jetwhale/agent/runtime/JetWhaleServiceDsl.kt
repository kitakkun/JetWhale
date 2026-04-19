package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.agent.sdk.JetWhaleMethodAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import io.ktor.client.HttpClient

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
    val service: JetWhaleMessagingService =
        DefaultJetWhaleMessagingService(
            socketClient = KtorWebSocketClient(
                json = json,
                httpClient = HttpClient(defaultKtorEngineFactory()),
                negotiationStrategy = DefaultClientSessionNegotiationStrategy(configuration.plugins.plugins),
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
}

@JetWhaleDsl
public interface JetWhaleConnectionConfigurationScope {
    public var host: String
    public var port: Int
}

@JetWhaleDsl
public interface JetWhaleLoggingConfigurationScope {
    public var enabled: Boolean
    public var logLevel: LogLevel
    public var ktorLogLevel: KtorLogLevel
}

@JetWhaleDsl
public interface JetWhalePluginConfigurationScope {
    public fun register(plugin: JetWhaleAgentPlugin<*, *>)
    public fun register(plugin: JetWhaleMethodAgentPlugin<*, *, *>)
}

private class JetWhaleConfiguration : JetWhaleConfigurationScope {
    val connection: JetWhaleConnectionConfiguration = JetWhaleConnectionConfiguration()
    val logging: JetWhaleLoggingConfiguration = JetWhaleLoggingConfiguration()
    val plugins: JetWhalePluginConfiguration = JetWhalePluginConfiguration()

    override fun connection(configure: JetWhaleConnectionConfigurationScope.() -> Unit) {
        connection.configure()
    }

    override fun logging(configure: JetWhaleLoggingConfigurationScope.() -> Unit) {
        logging.configure()
    }

    override fun plugins(configure: JetWhalePluginConfigurationScope.() -> Unit) {
        plugins.configure()
    }
}

private class JetWhaleConnectionConfiguration : JetWhaleConnectionConfigurationScope {
    override var host: String = "localhost"
    override var port: Int = 8080
}

private class JetWhaleLoggingConfiguration : JetWhaleLoggingConfigurationScope {
    override var enabled: Boolean = true
    override var logLevel: LogLevel = LogLevel.WARN
    override var ktorLogLevel: KtorLogLevel = KtorLogLevel.NONE
}

@OptIn(InternalJetWhaleApi::class)
private class JetWhalePluginConfiguration : JetWhalePluginConfigurationScope {
    val plugins: MutableList<AgentPlugin> = mutableListOf()

    override fun register(plugin: JetWhaleAgentPlugin<*, *>) {
        plugins.add(plugin)
    }

    override fun register(plugin: JetWhaleMethodAgentPlugin<*, *, *>) {
        plugins.add(plugin)
    }
}
