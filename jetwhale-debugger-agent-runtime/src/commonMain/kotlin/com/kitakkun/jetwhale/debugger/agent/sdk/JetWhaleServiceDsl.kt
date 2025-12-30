package com.kitakkun.jetwhale.debugger.agent.sdk

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.agent.sdk.JetWhaleMessagingService
import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.debugger.protocol.serialization.JetWhaleJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Starts the JetWhale Messaging Service with the provided configuration.
 *
 * @param configure A lambda function to configure the JetWhale service.
 */
@OptIn(InternalJetWhaleApi::class)
fun startJetWhale(configure: JetWhaleConfigurationScope.() -> Unit) {
    val configuration = JetWhaleConfiguration().apply(configure)
    val json = JetWhaleJson
    val service: JetWhaleMessagingService =
        DefaultJetWhaleMessagingService(
            socketClient = KtorWebSocketClient(
                json = json,
                httpClient = HttpClient(CIO)
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
    fun plugins(configure: JetWhalePluginConfigurationScope.() -> Unit)
}

@JetWhaleDsl
interface JetWhaleConnectionConfigurationScope {
    var host: String
    var port: Int
}

@JetWhaleDsl
interface JetWhalePluginConfigurationScope {
    fun register(plugin: JetWhaleAgentPlugin<*>)
}

private class JetWhaleConfiguration : JetWhaleConfigurationScope {
    val connection: JetWhaleConnectionConfiguration = JetWhaleConnectionConfiguration()
    val plugins: JetWhalePluginConfiguration = JetWhalePluginConfiguration()

    override fun connection(configure: JetWhaleConnectionConfigurationScope.() -> Unit) {
        connection.configure()
    }

    override fun plugins(configure: JetWhalePluginConfigurationScope.() -> Unit) {
        plugins.configure()
    }
}

private class JetWhaleConnectionConfiguration : JetWhaleConnectionConfigurationScope {
    override var host: String = "localhost"
    override var port: Int = 8080
}

private class JetWhalePluginConfiguration : JetWhalePluginConfigurationScope {
    val plugins: MutableList<JetWhaleAgentPlugin<*>> = mutableListOf()

    override fun register(plugin: JetWhaleAgentPlugin<*>) {
        plugins.add(plugin)
    }
}
