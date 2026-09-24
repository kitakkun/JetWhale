package com.kitakkun.jetwhale.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.singleWindowApplication
import com.kitakkun.jetwhale.demo.shared.App
import com.kitakkun.jetwhale.demo.shared.initializeJetWhale
import com.kitakkun.jetwhale.plugins.semantics.agent.JetWhaleSemanticsProbe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
fun main() {
    DebugProbes.install()
    initializeJetWhale()
    startDemoApiServer()

    singleWindowApplication {
        JetWhaleSemanticsProbe()
        App()
    }
}
