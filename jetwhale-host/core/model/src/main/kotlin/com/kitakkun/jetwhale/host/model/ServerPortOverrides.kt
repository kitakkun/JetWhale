package com.kitakkun.jetwhale.host.model

/**
 * Ports handed to this launch on the command line, taking precedence over the stored settings.
 *
 * A `null` member means "use the stored setting". Overrides are never written back to the settings
 * store, so several hosts (e.g. one per git worktree) can run side by side without fighting over the
 * default ports or over each other's saved configuration. They are applied by
 * [DebuggerSettingsRepository] rather than at each call site, so the servers, the settings screen and
 * the restart flows all agree on the ports actually in use.
 */
data class ServerPortOverrides(
    val serverPort: Int?,
    val wssPort: Int?,
    val mcpServerPort: Int?,
)
