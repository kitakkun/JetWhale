package com.kitakkun.jetwhale.demo

import android.app.Application
import com.kitakkun.jetwhale.demo.shared.initializeJetWhale
import com.kitakkun.jetwhale.plugins.semantics.agent.installJetWhaleSemanticsProbe

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Installed before any activity exists, so the probe sees every Compose root the app
        // creates — including the separate one a Dialog composes into (the "Compose nodes" tab
        // opens one). The alternative is JetWhaleSemanticsProbe() inside a composition, which
        // registers only that composition's own root.
        installJetWhaleSemanticsProbe(this)
        initializeJetWhale()
    }
}
