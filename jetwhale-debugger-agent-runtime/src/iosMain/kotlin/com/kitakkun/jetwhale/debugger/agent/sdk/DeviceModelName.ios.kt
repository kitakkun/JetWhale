package com.kitakkun.jetwhale.debugger.agent.sdk

import platform.UIKit.UIDevice

internal actual fun getDeviceModelName(): String {
    return UIDevice.currentDevice.name
}
