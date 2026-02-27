package com.kitakkun.jetwhale.agent.runtime

import platform.UIKit.UIDevice

internal actual fun getDeviceModelName(): String = UIDevice.currentDevice.name
