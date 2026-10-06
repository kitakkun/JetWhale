package com.kitakkun.jetwhale.host.model

interface AdbAutoPortMappingService {
    fun startPortMapping(port: Int)
    fun stopPortMapping(port: Int)
}
