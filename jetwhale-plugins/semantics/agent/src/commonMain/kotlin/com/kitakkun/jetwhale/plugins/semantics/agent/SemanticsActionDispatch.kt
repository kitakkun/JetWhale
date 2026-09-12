package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
 *
 * @param revealInHost how [NodeAction.BringIntoView] reaches past the composition, see
 *   [ScrollActions.BringIntoView].
 */
internal fun SemanticsNode.performSemanticsAction(
    request: PerformNodeAction,
    revealInHost: (boundsInRoot: Rect) -> Boolean,
): NodeActionResult {
    val handler = request.action.semanticsHandler
    if (!handler.runsOnDisabledNode && config.getOrNull(SemanticsProperties.Disabled) != null) {
        return NodeActionResult(performed = false, message = "the node is disabled")
    }
    return handler.perform(this, request, revealInHost)
}
