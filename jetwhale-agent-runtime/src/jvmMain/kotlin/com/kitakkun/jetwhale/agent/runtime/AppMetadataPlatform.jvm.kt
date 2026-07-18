package com.kitakkun.jetwhale.agent.runtime

// On the JVM there is no reliable stable device id or application name available, so both are
// left unresolved. Provide them explicitly through the `app { }` DSL when needed.
internal actual fun getDeviceId(): String? = null

internal actual fun resolveDefaultAppName(): String? = null
