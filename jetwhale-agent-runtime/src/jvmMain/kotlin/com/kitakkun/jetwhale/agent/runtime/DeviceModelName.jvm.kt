package com.kitakkun.jetwhale.agent.runtime

internal actual fun getDeviceModelName(): String = System.getProperty("os.name")
