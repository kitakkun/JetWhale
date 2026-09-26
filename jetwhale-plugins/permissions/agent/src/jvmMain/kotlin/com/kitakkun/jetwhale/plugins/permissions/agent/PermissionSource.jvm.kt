package com.kitakkun.jetwhale.plugins.permissions.agent

internal actual fun platformPermissionSource(): PermissionSource = UnsupportedPermissionSource(
    platform = "JVM",
    reason = "A desktop JVM app has no permission model of its own; the operating system's privacy settings (macOS TCC, for one) cannot be read from inside the app.",
)
