package com.kitakkun.jetwhale.debugger.agent.sdk

import platform.Foundation.NSHost

internal actual fun getDeviceModelName(): String {
    return NSHost.currentHost().localizedName.orEmpty()
}
