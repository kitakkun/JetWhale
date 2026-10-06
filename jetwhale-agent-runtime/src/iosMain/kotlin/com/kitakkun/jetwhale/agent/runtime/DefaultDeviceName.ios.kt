package com.kitakkun.jetwhale.agent.runtime

import platform.UIKit.UIDevice

internal actual fun resolveDefaultDeviceName(): String = UIDevice.currentDevice.name
