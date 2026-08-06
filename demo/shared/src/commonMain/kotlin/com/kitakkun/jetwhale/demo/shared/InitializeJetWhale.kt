package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale

fun initializeJetWhale() {
    startJetWhale {
        connection {
            // Zero-config discovery of the host over mDNS for physical LAN devices, which reach it
            // over wss. Everything that can only reach loopback — emulators, simulators, ADB-forwarded
            // devices, and the browser, which cannot pin a local CA at all — falls back to the plain
            // ws port. One configuration, every target.
            endpoint = discovered(fallback = plainLoopback(5080))
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
            register(DIModule.nav3AgentPlugin)
            register(DIModule.semanticsAgentPlugin)
        }
    }
}
