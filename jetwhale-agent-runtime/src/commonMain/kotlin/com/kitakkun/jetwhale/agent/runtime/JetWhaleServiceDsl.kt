package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleMessagingService
import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import io.ktor.client.HttpClient

/**
 * Starts the JetWhale Messaging Service with the provided configuration.
 *
 * @param configure A lambda function to configure the JetWhale service.
 */
@OptIn(InternalJetWhaleApi::class)
fun startJetWhale(configure: JetWhaleConfigurationScope.() -> Unit) {
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
                sessionNegotiator = DefaultClientSessionNegotiator(),
            ),
            plugins = configuration.plugins.plugins,
            json = json,
        )
    service.startService(
        host = configuration.connection.host,
        port = configuration.connection.port,
    )
}

@DslMarker
internal annotation class JetWhaleDsl

@JetWhaleDsl
interface JetWhaleConfigurationScope {
    fun connection(configure: JetWhaleConnectionConfigurationScope.() -> Unit)
    fun logging(configure: JetWhaleLoggingConfigurationScope.() -> Unit)
    fun plugins(configure: JetWhalePluginConfigurationScope.() -> Unit)
}

@JetWhaleDsl
interface JetWhaleConnectionConfigurationScope {
    var host: String
    var port: Int
}

@JetWhaleDsl
interface JetWhaleLoggingConfigurationScope {
    var enabled: Boolean
    var logLevel: LogLevel
    var ktorLogLevel: KtorLogLevel
}

@JetWhaleDsl
interface JetWhalePluginConfigurationScope {
    fun register(plugin: AgentPlugin)
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

private class JetWhalePluginConfiguration : JetWhalePluginConfigurationScope {
    val plugins: MutableList<AgentPlugin> = mutableListOf()

    override fun register(plugin: AgentPlugin) {
        plugins.add(plugin)
    }
}
