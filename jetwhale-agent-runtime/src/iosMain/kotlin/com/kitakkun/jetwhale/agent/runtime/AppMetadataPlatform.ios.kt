package com.kitakkun.jetwhale.agent.runtime

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

internal actual fun getDeviceId(): String? = UIDevice.currentDevice.identifierForVendor?.UUIDString

internal actual fun resolveDefaultAppName(): String? {
    val info = NSBundle.mainBundle.infoDictionary ?: return null
    return (info["CFBundleDisplayName"] ?: info["CFBundleName"]) as? String
}
