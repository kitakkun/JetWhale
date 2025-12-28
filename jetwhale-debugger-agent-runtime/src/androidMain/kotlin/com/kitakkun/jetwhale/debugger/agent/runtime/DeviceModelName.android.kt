package com.kitakkun.jetwhale.debugger.agent.runtime

internal actual fun getDeviceModelName(): String {
    return android.os.Build.MODEL ?: "Unknown Android Device"
}
