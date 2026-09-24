package com.kitakkun.jetwhale.demo

import android.app.Application
import com.kitakkun.jetwhale.demo.shared.initializeJetWhale
import com.kitakkun.jetwhale.demo.shared.startDemoBackgroundWork
import com.kitakkun.jetwhale.plugins.semantics.agent.installJetWhaleSemanticsProbe

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installJetWhaleSemanticsProbe(this)
        startDemoBackgroundWork(this)
        initializeJetWhale()
    }
}
