package com.kitakkun.jetwhale.demo.shared

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.plugins.example.agent.ExampleAgentPlugin
import com.kitakkun.jetwhale.plugins.nav3.agent.JetWhaleNav3AgentPlugin
import com.kitakkun.jetwhale.plugins.nav3.agent.Nav3KeyCodec
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.ktor.ktorClientPlugin
import com.kitakkun.jetwhale.plugins.semantics.agent.JetWhaleSemanticsAgentPlugin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header

object DIModule {
    val exampleAgentPlugin: ExampleAgentPlugin by lazy { ExampleAgentPlugin() }

    val networkAgentPlugin: JetWhaleNetworkAgentPlugin by lazy { JetWhaleNetworkAgentPlugin() }

    val nav3AgentPlugin: JetWhaleNav3AgentPlugin<NavKey> by lazy {
        JetWhaleNav3AgentPlugin(Nav3KeyCodec.openPolymorphic(demoNavKeySerializersModule))
    }

    /**
     * Answers the Compose Semantics Inspector's tree captures. It reads whatever roots a probe has
     * registered, so a platform where none is installed reports an empty tree — the probe is wired
     * up per platform, in `demo/android` and `demo/desktop`.
     */
    val semanticsAgentPlugin: JetWhaleSemanticsAgentPlugin by lazy { JetWhaleSemanticsAgentPlugin() }

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
