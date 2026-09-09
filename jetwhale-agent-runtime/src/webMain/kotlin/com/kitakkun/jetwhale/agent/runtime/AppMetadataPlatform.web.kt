package com.kitakkun.jetwhale.agent.runtime

// Browser environments do not expose a stable per-device id or a meaningful app name without
// extra dependencies; provide them explicitly through the `app { }` DSL when needed.
internal actual fun getDeviceId(): String? = null

internal actual fun resolveDefaultAppName(): String? = null

// A favicon is not necessarily a PNG the agent can read back, so no icon is resolved. Provide one explicitly through the `app { }` DSL when needed.
internal actual fun resolveDefaultAppIconPng(): ByteArray? = null
