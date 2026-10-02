package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import android.widget.Checkable
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * `performClick()` rather than a synthesized tap: the listener the app registered still runs, with
 * no coordinates involved and no chance of landing on whatever moved into that spot.
 */
internal object AndroidViewPointerActions {
    object Click : AndroidViewActionHandler {
        override val runsOnDisabledView = false

        override fun isOfferedBy(view: View) = view.isClickable

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            if (!view.isClickable) return NodeActionResult.notSupported("the view is not clickable")
            val checkedBefore = (view as? Checkable)?.isChecked
            val clickHandled = view.performClick()
            // CompoundButton.performClick() toggles, then returns false when no OnClickListener is set.
            val toggled = (view as? Checkable)?.isChecked != checkedBefore
            return NodeActionResult.performedIf(clickHandled || toggled, "performClick() returned false")
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
