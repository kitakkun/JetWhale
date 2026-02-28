package com.kitakkun.jetwhale.demo

import androidx.compose.ui.window.singleWindowApplication
import com.kitakkun.jetwhale.demo.shared.App
import com.kitakkun.jetwhale.demo.shared.initializeJetWhale

fun main() {
    initializeJetWhale()

    singleWindowApplication {
        App()
    }
}
