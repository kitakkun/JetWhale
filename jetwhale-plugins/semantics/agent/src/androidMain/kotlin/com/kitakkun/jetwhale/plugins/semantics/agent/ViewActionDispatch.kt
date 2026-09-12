package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * Runs [request]'s action on this view, the way the view itself would run it.
 *
 * The Compose side invokes a node's own semantics action; the closest equivalent for a `View` is its
 * own API — `performClick()` rather than a synthesised tap — so a click still runs the listener the
 * app registered, with no coordinates involved and no chance of landing on whatever moved into that
 * spot. Actions a `View` has no counterpart for report that they did not run rather than pretending.
 *
 * Must be called on the main thread.
 */
internal fun View.performViewAction(request: PerformNodeAction): NodeActionResult {
    val handler = request.action.viewHandler
    if (!handler.runsOnDisabledView && !isEnabled) {
        return NodeActionResult(performed = false, message = "the view is disabled")
    }
    return handler.perform(this, request)
}
