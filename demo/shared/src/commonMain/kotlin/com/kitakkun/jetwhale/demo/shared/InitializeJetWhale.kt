package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi

@OptIn(ExperimentalJetWhaleApi::class)
fun initializeJetWhale() {
    startJetWhale {
        app {
            appName = "JetWhale Demo"
        }
        connection {
            // Loopback first, because emulators, simulators, ADB-forwarded devices, the desktop app
            // and the browser all reach it. In the clear, because it never leaves the machine and a
            // browser cannot pin a locally issued CA. A physical device reaches neither and falls
            // through to discovery over wss.
            endpoints {
                ws("localhost", 5080)

                buildMachineWss(5443)

                discoverWss {
                    allowAll()
                }
            }
            ssl {
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
            register(DIModule.storageAgentPlugin)
            register(DIModule.debugActionsAgentPlugin)
            register(DIModule.backgroundWorkAgentPlugin)
        }
    }
}
