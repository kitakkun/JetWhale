package com.kitakkun.jetwhale.plugins.nav3.host

/** A pane of the navigator that an MCP command operates, ringed while the command runs. */
internal sealed interface Nav3UiTarget {
    /** The back stack pane while it shows the stack registered as [stackId]. */
    data class BackStack(val stackId: String) : Nav3UiTarget

    /** The pane with the key types to push. */
    data object PushPane : Nav3UiTarget
}
