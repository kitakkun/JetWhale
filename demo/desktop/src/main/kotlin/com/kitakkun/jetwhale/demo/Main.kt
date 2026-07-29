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
        // Desktop has no process-wide root callback, so the probe is scoped to this window. Dialogs
        // and popups rendered inside it are picked up on their own.
        JetWhaleSemanticsProbe()
        App()
    }
}
