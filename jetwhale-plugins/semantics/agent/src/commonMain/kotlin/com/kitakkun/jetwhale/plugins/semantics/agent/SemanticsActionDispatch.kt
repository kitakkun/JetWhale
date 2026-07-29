package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * Invokes the semantics action [request] names on this node.
 *
 * Going through the node's own action rather than synthesising input is what makes this usable for
 * driving an app: it needs no window coordinates, cannot land on whatever moved into that spot
 * meanwhile, and reports back whether the node actually handled it.
 *
 * Must be called on the thread that owns the composition.
 */
internal fun SemanticsNode.performSemanticsAction(request: PerformNodeAction): NodeActionResult {
    val config = config
    if (request.action.requiresEnabled && config.getOrNull(SemanticsProperties.Disabled) != null) {
        return NodeActionResult(performed = false, message = "the node is disabled")
    }

    return when (request.action) {
        NodeAction.Click -> config.invokeAction(SemanticsActions.OnClick) { it() }

        NodeAction.LongClick -> config.invokeAction(SemanticsActions.OnLongClick) { it() }

        NodeAction.SetText -> {
            val text = request.text ?: return missingText("SetText")
            // A text field only accepts programmatic edits while it holds focus, exactly as when a
            // user types into it, so take focus first when the node offers it.
            config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
            config.invokeAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
        }

        NodeAction.InsertText -> {
            val text = request.text ?: return missingText("InsertText")
            config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
            config.invokeAction(SemanticsActions.InsertTextAtCursor) { it(AnnotatedString(text)) }
        }

        NodeAction.ImeAction -> config.invokeAction(SemanticsActions.OnImeAction) { it() }

        NodeAction.ScrollBy -> config.invokeAction(SemanticsActions.ScrollBy) { it(request.scrollX, request.scrollY) }

        NodeAction.RequestFocus -> config.invokeAction(SemanticsActions.RequestFocus) { it() }

        NodeAction.Dismiss -> config.invokeAction(SemanticsActions.Dismiss) { it() }

        NodeAction.Expand -> config.invokeAction(SemanticsActions.Expand) { it() }

        NodeAction.Collapse -> config.invokeAction(SemanticsActions.Collapse) { it() }
    }
}

/**
 * Actions a disabled node still answers — focus, dismissal and scrolling stay meaningful — are
 * excluded, so only the ones a user could not trigger either are rejected up front.
 */
private val NodeAction.requiresEnabled: Boolean
    get() = when (this) {
        NodeAction.Click,
        NodeAction.LongClick,
        NodeAction.SetText,
        NodeAction.InsertText,
        NodeAction.ImeAction,
        NodeAction.Expand,
        NodeAction.Collapse,
        -> true

        NodeAction.ScrollBy,
        NodeAction.RequestFocus,
        NodeAction.Dismiss,
        -> false
    }

private fun <T : Function<Boolean>> SemanticsConfiguration.invokeAction(
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

private fun missingText(action: String): NodeActionResult = NodeActionResult(performed = false, message = "$action requires the 'text' argument")
