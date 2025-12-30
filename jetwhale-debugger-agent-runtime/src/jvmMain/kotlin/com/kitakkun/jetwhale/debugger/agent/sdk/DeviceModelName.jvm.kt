package com.kitakkun.jetwhale.debugger.agent.sdk

internal actual fun getDeviceModelName(): String {
    return System.getProperty("os.name")
}
