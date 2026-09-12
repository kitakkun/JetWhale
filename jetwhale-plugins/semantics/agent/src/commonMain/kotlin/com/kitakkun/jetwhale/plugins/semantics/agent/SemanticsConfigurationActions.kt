package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult

/** Runs the handler behind the [key] action, or reports why it could not. */
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
