package com.kitakkun.jetwhale.host.model

interface ADBAutoWiringService {
    fun startAutoWiring(port: Int)
    fun stopAutoWiring(port: Int)
}
