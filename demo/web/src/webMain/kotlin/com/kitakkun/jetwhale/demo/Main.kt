package com.kitakkun.jetwhale.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.kitakkun.jetwhale.demo.shared.App
import com.kitakkun.jetwhale.demo.shared.initializeJetWhale

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeJetWhale()
    ComposeViewport {
        App()
    }
}
