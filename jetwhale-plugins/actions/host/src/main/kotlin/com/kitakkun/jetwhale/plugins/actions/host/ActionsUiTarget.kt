package com.kitakkun.jetwhale.plugins.actions.host

/** A part of the Debug Actions UI that an MCP command operates, ringed while the command runs. */
internal sealed interface ActionsUiTarget {
    data object ActionList : ActionsUiTarget

    data class ActionRow(val actionId: String) : ActionsUiTarget
}
