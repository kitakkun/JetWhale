package com.kitakkun.jetwhale.demo.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.kitakkun.jetwhale.plugins.semantics.agent.installJetWhaleSemanticsProbe
import platform.UIKit.UIViewController

fun cmpAppViewController(): UIViewController {
    // The plugin module is not exported from the framework, so Swift cannot install the probe
    // itself; this is the first Kotlin the app calls on the main thread. Idempotent, so a second
    // controller costs nothing.
    installJetWhaleSemanticsProbe()
    return ComposeUIViewController {
        App()
    }
}
