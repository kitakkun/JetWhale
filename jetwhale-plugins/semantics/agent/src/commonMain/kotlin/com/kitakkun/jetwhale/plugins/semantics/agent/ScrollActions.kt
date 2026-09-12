package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/** Moving a scrollable, or moving the scrollables around a node so that it shows. */
internal object ScrollActions {
    object ScrollBy : SemanticsActionHandler {
        override val runsOnDisabledNode = true

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.ScrollBy) { it(request.scrollX, request.scrollY) }
    }

    object ScrollToIndex : SemanticsActionHandler {
        override val runsOnDisabledNode = true

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult {
            val index = request.index ?: return missingIndex()
            // A lazy container throws on an index outside its item count instead of answering false.
            return try {
                node.config.invokeAction(SemanticsActions.ScrollToIndex) { it(index) }
            } catch (e: IllegalArgumentException) {
                NodeActionResult(performed = false, message = e.message ?: "index $index is out of bounds")
            }
        }
    }

    object BringIntoView : SemanticsActionHandler {
        override val runsOnDisabledNode = true

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.scrollIntoView(revealInHost)
    }
}
