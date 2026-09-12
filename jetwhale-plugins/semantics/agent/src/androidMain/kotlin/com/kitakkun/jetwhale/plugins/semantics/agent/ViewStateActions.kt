package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/** Focus is the one state change a `View` offers; dismissal, expansion and collapse have no counterpart. */
internal object ViewStateActions {
    object RequestFocus : ViewActionHandler {
        override val runsOnDisabledView = true

        override fun isOfferedBy(view: View) = view.isFocusable

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            if (!view.isFocusable) return NodeActionResult.notSupported("the view is not focusable")
            return NodeActionResult.performedIf(view.requestFocus(), "requestFocus() returned false")
        }
    }

    val Dismiss: ViewActionHandler = UnsupportedOnView(NodeAction.Dismiss)
    val Expand: ViewActionHandler = UnsupportedOnView(NodeAction.Expand)
    val Collapse: ViewActionHandler = UnsupportedOnView(NodeAction.Collapse)
}
