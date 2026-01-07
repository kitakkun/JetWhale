package com.kitakkun.jetwhale.demo

import android.app.Application
import com.kitakkun.jetwhale.demo.shared.initializeJetWhale

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeJetWhale()
    }
}
