package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/** Editing and submitting a text field. */
internal object TextActions {
    object SetText : SemanticsActionHandler {
        override val runsOnDisabledNode = false

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult {
            val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.SetText, "text")
            // A text field only accepts programmatic edits while it holds focus, exactly as when a
            // user types into it, so take focus first when the node offers it.
            node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
            return node.config.invokeAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
        }
    }

    object InsertText : SemanticsActionHandler {
        override val runsOnDisabledNode = false

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult {
            val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.InsertText, "text")
            node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
            return node.config.invokeAction(SemanticsActions.InsertTextAtCursor) { it(AnnotatedString(text)) }
        }
    }

    object ImeAction : SemanticsActionHandler {
        override val runsOnDisabledNode = false

        override fun perform(node: SemanticsNode, request: PerformNodeAction, revealInHost: (Rect) -> Boolean): NodeActionResult = node.config.invokeAction(SemanticsActions.OnImeAction) { it() }
    }
}
