package com.kitakkun.jetwhale.plugins.semantics.host

/** A part of the inspector that an MCP command operates, ringed while the command runs. */
internal sealed interface SemanticsUiTarget {
    data object NodeTree : SemanticsUiTarget

    /** The node an agent acts on: its row in the tree and, while it is selected, its actions. */
    data class Node(val rootId: String, val nodeId: Int) : SemanticsUiTarget

    /** The View attributes panel while it shows the View node [nodeId] of [rootId]. */
    data class ViewAttributes(val rootId: String, val nodeId: Int) : SemanticsUiTarget
}
