package com.kitakkun.jetwhale.agent.runtime

// No reliable stable device id or application name on Windows native; provide them explicitly if needed.
internal actual fun getDeviceId(): String? = null

internal actual fun resolveDefaultAppName(): String? = null

// No launcher icon to resolve on Windows native. Provide one explicitly through the `app { }` DSL when needed.
internal actual fun resolveDefaultAppIconPng(): ByteArray? = null
