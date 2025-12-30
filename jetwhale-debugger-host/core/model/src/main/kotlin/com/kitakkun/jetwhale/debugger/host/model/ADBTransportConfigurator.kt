package com.kitakkun.jetwhale.debugger.host.model

interface ADBTransportConfigurator {
    fun mapLocalPortToDevicePort(localPort: Int)
    fun resetPortMappingForHostPort(port: Int)
}
