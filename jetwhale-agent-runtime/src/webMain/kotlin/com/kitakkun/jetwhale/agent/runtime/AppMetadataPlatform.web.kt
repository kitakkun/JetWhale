package com.kitakkun.jetwhale.agent.runtime

internal actual fun getDeviceId(): String? = null

internal actual fun resolveDefaultAppName(): String? = null

// A favicon is not necessarily a PNG the agent can read back, so no icon is resolved. Provide one explicitly through the `app { }` DSL when needed.
internal actual fun resolveDefaultAppIconPng(): ByteArray? = null
