package com.kitakkun.jetwhale.agent.runtime

import platform.Foundation.NSBundle

// macOS does not expose a stable per-device id without extra entitlements, so device id is left
// unresolved; the bundle name is used as the application name when available.
internal actual fun getDeviceId(): String? = null

internal actual fun resolveDefaultAppName(): String? {
    val info = NSBundle.mainBundle.infoDictionary ?: return null
    return (info["CFBundleDisplayName"] ?: info["CFBundleName"]) as? String
}
