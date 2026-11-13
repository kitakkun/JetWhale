package com.kitakkun.jetwhale.plugins.permissions.agent

internal actual fun platformPermissionSource(): PermissionSource = UnsupportedPermissionSource(
    platform = "Web",
    reason = "Browser permissions are not reported yet: the Permissions API answers asynchronously per permission name, which this agent does not query.",
)
