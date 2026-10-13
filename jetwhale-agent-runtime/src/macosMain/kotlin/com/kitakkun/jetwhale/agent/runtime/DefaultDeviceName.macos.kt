package com.kitakkun.jetwhale.agent.runtime

import platform.Foundation.NSHost

internal actual fun resolveDefaultDeviceName(): String = NSHost.currentHost().localizedName.orEmpty()
