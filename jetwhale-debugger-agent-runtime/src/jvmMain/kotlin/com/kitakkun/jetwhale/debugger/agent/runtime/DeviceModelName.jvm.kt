package com.kitakkun.jetwhale.debugger.agent.runtime

internal actual fun getDeviceModelName(): String {
    return System.getProperty("os.name")
}
