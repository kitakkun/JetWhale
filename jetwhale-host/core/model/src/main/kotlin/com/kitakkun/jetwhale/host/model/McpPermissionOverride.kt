package com.kitakkun.jetwhale.host.model

/**
 * Permission decisions handed to this launch on the command line, taking precedence over the stored
 * ones.
 *
 * [allowAll] exists for automated QA: a run that has to enable a plugin or restart a server would
 * otherwise stop at a checkbox only a human can tick, which defeats the point of driving the host
 * from an agent. It applies to the launch only and is never written back, so the developer's own
 * host keeps whatever they chose.
 *
 * Setting it needs the ability to start the host process, which is already more than the
 * unauthenticated MCP port grants — so this widens no boundary that was not open to that caller
 * already. [McpPermissions.allOverriddenBy] keeps it visible in the settings screen and in
 * `jetwhale.getStatus` rather than silently disagreeing with what the checkboxes show.
 */
data class McpPermissionOverride(val allowAll: Boolean) {
    companion object {
        val None = McpPermissionOverride(allowAll = false)
    }
}
