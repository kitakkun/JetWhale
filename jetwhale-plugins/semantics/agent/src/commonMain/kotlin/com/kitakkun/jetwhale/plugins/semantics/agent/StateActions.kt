package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/** Changing what state the node is in: focused, dismissed, expanded or collapsed. */
internal object StateActions {
    object RequestFocus : SemanticsActionHandler {
        override val runsOnDisabledNode = true

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.RequestFocus) { it() }
    }

    object Dismiss : SemanticsActionHandler {
        override val runsOnDisabledNode = true

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.Dismiss) { it() }
    }

    object Expand : SemanticsActionHandler {
        override val runsOnDisabledNode = false

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.Expand) { it() }
    }

    object Collapse : SemanticsActionHandler {
        override val runsOnDisabledNode = false

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.Collapse) { it() }
    }
}
