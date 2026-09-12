package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * How one [NodeAction] runs on a Compose semantics node.
 *
 * The handlers live in objects grouped by purpose — [PointerActions], [TextActions],
 * [ScrollActions], [StateActions] — and [semanticsHandler] is the one registry that binds an action
 * to its handler, so adding an action means writing a handler and registering it there.
 */
internal interface SemanticsActionHandler {
    /**
     * Whether a disabled node still answers. Focus, dismissal and scrolling stay meaningful on a
     * disabled node; the actions a user could not trigger either are rejected before they run.
     */
    val runsOnDisabledNode: Boolean

    /**
     * Must be called on the thread that owns the composition.
     *
     * @param revealInHost how [NodeAction.BringIntoView] reaches past the composition, see
     *   [scrollIntoView]. Every other handler ignores it.
     */
    fun perform(
        node: SemanticsNode,
        request: PerformNodeAction,
        revealInHost: (boundsInRoot: Rect) -> Boolean,
    ): NodeActionResult
}

internal val NodeAction.semanticsHandler: SemanticsActionHandler
    get() = when (this) {
        NodeAction.Click -> PointerActions.Click
        NodeAction.LongClick -> PointerActions.LongClick
        NodeAction.SetText -> TextActions.SetText
        NodeAction.InsertText -> TextActions.InsertText
        NodeAction.ImeAction -> TextActions.ImeAction
        NodeAction.ScrollBy -> ScrollActions.ScrollBy
        NodeAction.ScrollToIndex -> ScrollActions.ScrollToIndex
        NodeAction.BringIntoView -> ScrollActions.BringIntoView
        NodeAction.RequestFocus -> StateActions.RequestFocus
        NodeAction.Dismiss -> StateActions.Dismiss
        NodeAction.Expand -> StateActions.Expand
        NodeAction.Collapse -> StateActions.Collapse
    }

internal fun <T : Function<Boolean>> SemanticsConfiguration.invokeAction(
    key: SemanticsPropertyKey<AccessibilityAction<T>>,
    invoke: (T) -> Boolean,
): NodeActionResult {
    // An AccessibilityAction may advertise a label with no handler behind it (a node that says it
    // is clickable but delegates the click elsewhere), so the handler is what decides.
    val handler = getOrNull(key)?.action
        ?: return NodeActionResult(performed = false, message = "the node does not expose ${key.name}")
    val performed = invoke(handler)
    return NodeActionResult(
        performed = performed,
        message = if (performed) null else "${key.name} ran but reported that it did not handle the request",
    )
}

internal fun missingText(action: NodeAction): NodeActionResult = NodeActionResult(performed = false, message = "$action requires the 'text' argument")

internal fun missingIndex(): NodeActionResult = NodeActionResult(performed = false, message = "ScrollToIndex requires the 'index' argument")
