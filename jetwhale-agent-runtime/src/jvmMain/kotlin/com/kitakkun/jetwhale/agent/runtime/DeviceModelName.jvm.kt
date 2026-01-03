package com.kitakkun.jetwhale.agent.runtime

internal actual fun getDeviceModelName(): String {
    return System.getProperty("os.name")
}
