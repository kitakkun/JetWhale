package com.kitakkun.jetwhale.agent.runtime

import platform.Foundation.NSHost

internal actual fun getDeviceModelName(): String = NSHost.currentHost().localizedName.orEmpty()
