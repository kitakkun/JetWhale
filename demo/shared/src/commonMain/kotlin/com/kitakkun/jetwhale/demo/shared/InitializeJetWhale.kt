package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.generated.applyJetWhaleBuildEnvironment

fun initializeJetWhale() {
    // Registers the build machine's LAN addresses (captured at build time by the JetWhale Gradle
    // plugin) as connection candidates, so no host/IP has to be written below. A physical device on
    // the LAN reaches the build machine directly; emulators/simulators fall through to localhost.
    applyJetWhaleBuildEnvironment()

    startJetWhale {
        connection {
            // No host set: build-injected candidates are tried first, then localhost as the fallback.
            port = 5443
            ssl {
                // Fetches the host's active CA over the plain channel (via ADB forwarding) and pins
                // the wss connection to it, so the app never has to hardcode a CA certificate.
                trustServerCertificate()
            }
        }

        logging {
            enabled = true
            logLevel = LogLevel.INFO
            ktorLogLevel = KtorLogLevel.NONE
        }

        plugins {
            register(DIModule.exampleAgentPlugin)
            register(DIModule.networkAgentPlugin)
        }
    }
}
