package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.example.agent.ExampleAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.ktor.ktorClientPlugin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header

object DIModule {
    val exampleAgentPlugin: ExampleAgentPlugin by lazy { ExampleAgentPlugin() }

    val networkAgentPlugin: JetWhaleNetworkAgentPlugin by lazy { JetWhaleNetworkAgentPlugin() }

    /** A demo Ktor client wired to the Network Inspector so its traffic shows up in the debugger. */
    val httpClient: HttpClient by lazy {
        HttpClient {
            // Without a timeout a hung request never throws, so the inspector would show it stuck
            // "pending" forever. With it, the agent records the timeout as a failure instead.
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
            }
            // A demo header so the inspector clearly shows application-level request headers
            // (not just the Ktor default Accept).
            install(DefaultRequest) {
                header("X-Demo-Client", "JetWhale-Demo")
            }
            install(networkAgentPlugin.ktorClientPlugin())
        }
    }
}
