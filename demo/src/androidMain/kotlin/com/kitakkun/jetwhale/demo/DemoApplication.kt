package com.kitakkun.jetwhale.demo

import android.app.Application

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeJetWhale()
    }
}
