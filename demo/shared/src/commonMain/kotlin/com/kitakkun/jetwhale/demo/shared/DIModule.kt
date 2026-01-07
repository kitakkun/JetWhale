package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.example.agent.ExampleAgentPlugin

object DIModule {
    val exampleAgentPlugin: ExampleAgentPlugin by lazy { ExampleAgentPlugin() }
}
