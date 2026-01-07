package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.agent.runtime.startJetWhale

fun initializeJetWhale() {
    startJetWhale {
        connection {
            host = "localhost"
            port = 5080
        }

        plugins {
            register(DIModule.exampleAgentPlugin)
        }
    }
}
