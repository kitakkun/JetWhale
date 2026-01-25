package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale

fun initializeJetWhale() {
    startJetWhale {
        connection {
            host = "localhost"
            port = 5080
        }

        logging {
            enabled = true
            logLevel = LogLevel.WARN
            ktorLogLevel = KtorLogLevel.NONE
        }

        plugins {
            register(DIModule.exampleAgentPlugin)
        }
    }
}
