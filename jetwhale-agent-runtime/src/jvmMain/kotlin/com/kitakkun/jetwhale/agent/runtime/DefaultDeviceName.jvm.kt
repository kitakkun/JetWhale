package com.kitakkun.jetwhale.agent.runtime

internal actual fun resolveDefaultDeviceName(): String = System.getProperty("os.name")
