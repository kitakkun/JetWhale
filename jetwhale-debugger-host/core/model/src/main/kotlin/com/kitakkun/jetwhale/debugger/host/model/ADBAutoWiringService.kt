package com.kitakkun.jetwhale.debugger.host.model

interface ADBAutoWiringService {
    fun startAutoWiring(port: Int)
    fun stopAutoWiring(port: Int)
}
