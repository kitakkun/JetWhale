package com.kitakkun.jetwhale.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.singleWindowApplication
import com.kitakkun.jetwhale.demo.shared.App
import com.kitakkun.jetwhale.demo.shared.initializeJetWhale
import com.kitakkun.jetwhale.plugins.semantics.agent.JetWhaleSemanticsProbe

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeJetWhale()
    startDemoApiServer()

    singleWindowApplication {
        JetWhaleSemanticsProbe()
        App()
    }
}
