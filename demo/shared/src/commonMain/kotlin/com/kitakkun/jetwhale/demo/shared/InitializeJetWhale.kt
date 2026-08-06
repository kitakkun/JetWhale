package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi

@OptIn(ExperimentalJetWhaleApi::class)
fun initializeJetWhale() {
    startJetWhale {
        connection {
            // Tried in order. Loopback first, because everything that can reach it — emulators,
            // simulators, ADB-forwarded devices, the desktop app, the browser — is already there and
            // need not wait out a network browse. In the clear, because it never leaves the machine
            // and a browser cannot pin a locally-issued CA at all.
            //
            // A physical device on the network reaches neither, so it falls through to discovery and
            // connects over wss.
            endpoints {
                ws("localhost", 5080)

                // The machine that compiled this — no browse needed to learn its address. Only the
                // build machine's own host is reachable this way, so discovery still follows for the
                // case where the host runs somewhere else on the network.
                buildMachineWss(5443)

                discoverWss {
                    // The demo cannot know the machine it will be run from, so it takes any host it
                    // finds. A real app on a shared network should name its own with allowHostName.
                    allowAll()
                }
            }
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
