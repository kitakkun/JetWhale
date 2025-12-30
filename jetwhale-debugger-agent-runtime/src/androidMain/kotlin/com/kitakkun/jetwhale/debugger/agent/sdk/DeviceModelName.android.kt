package com.kitakkun.jetwhale.debugger.agent.sdk

internal actual fun getDeviceModelName(): String {
    return android.os.Build.MODEL ?: "Unknown Android Device"
}
