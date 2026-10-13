package com.kitakkun.jetwhale.demo.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.kitakkun.jetwhale.plugins.semantics.agent.installJetWhaleSemanticsProbe
import platform.UIKit.UIViewController

fun cmpAppViewController(): UIViewController {
    installJetWhaleSemanticsProbe()
    return ComposeUIViewController {
        App()
    }
}
