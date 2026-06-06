package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.example.agent.ExampleAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.ktor.ktorClientPlugin
import io.ktor.client.HttpClient

object DIModule {
    val exampleAgentPlugin: ExampleAgentPlugin by lazy { ExampleAgentPlugin() }

    val networkAgentPlugin: JetWhaleNetworkAgentPlugin by lazy { JetWhaleNetworkAgentPlugin() }

    /** A demo Ktor client wired to the Network Inspector so its traffic shows up in the debugger. */
    val httpClient: HttpClient by lazy {
        HttpClient {
            install(networkAgentPlugin.ktorClientPlugin())
        }
    }
}
