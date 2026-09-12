package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * `performClick()` rather than a synthesised tap: the listener the app registered still runs, with
 * no coordinates involved and no chance of landing on whatever moved into that spot.
 */
internal object AndroidViewPointerActions {
    object Click : AndroidViewActionHandler {
        override val runsOnDisabledView = false

        override fun isOfferedBy(view: View) = view.isClickable

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            if (!view.isClickable) return NodeActionResult.notSupported("the view is not clickable")
            return NodeActionResult.performedIf(view.performClick(), "performClick() returned false")
        }
    }

    object LongClick : AndroidViewActionHandler {
        override val runsOnDisabledView = false

        override fun isOfferedBy(view: View) = view.isLongClickable

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            if (!view.isLongClickable) return NodeActionResult.notSupported("the view is not long-clickable")
            return NodeActionResult.performedIf(view.performLongClick(), "performLongClick() returned false")
        }
    }
}
