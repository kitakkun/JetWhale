package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/** What a tap or a long press on the node would do. */
internal object PointerActions {
    object Click : SemanticsActionHandler {
        override val runsOnDisabledNode = false

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.OnClick) { it() }
    }

    object LongClick : SemanticsActionHandler {
        override val runsOnDisabledNode = false

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.OnLongClick) { it() }
    }
}
