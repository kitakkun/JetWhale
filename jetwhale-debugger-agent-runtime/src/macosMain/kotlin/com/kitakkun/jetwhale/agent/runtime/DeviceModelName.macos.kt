package com.kitakkun.jetwhale.agent.runtime

import platform.Foundation.NSHost

internal actual fun getDeviceModelName(): String {
    return NSHost.currentHost().localizedName.orEmpty()
}
