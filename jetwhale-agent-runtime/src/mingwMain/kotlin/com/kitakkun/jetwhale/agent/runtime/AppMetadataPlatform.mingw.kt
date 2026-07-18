package com.kitakkun.jetwhale.agent.runtime

// No reliable stable device id or application name on Windows native; provide them explicitly if needed.
internal actual fun getDeviceId(): String? = null

internal actual fun resolveDefaultAppName(): String? = null
