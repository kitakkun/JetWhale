package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlin.math.roundToInt

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
    if (request.action.requiresEnabledView && !isEnabled) {
        return NodeActionResult(performed = false, message = "the view is disabled")
    }

    return when (request.action) {
        NodeAction.Click -> {
            if (!isClickable) return notSupported("the view is not clickable")
            performed(performClick(), "performClick() returned false")
        }

        NodeAction.LongClick -> {
            if (!isLongClickable) return notSupported("the view is not long-clickable")
            performed(performLongClick(), "performLongClick() returned false")
        }

        NodeAction.SetText -> {
            val text = request.text ?: return missingText("SetText")
            val field = this as? EditText ?: return notSupported("the view is not an EditText")
            // The same order a user goes through, and the one an app's focus-driven validation
            // expects: take focus first, then write.
            field.requestFocus()
            field.setText(text)
            NodeActionResult(performed = true)
        }

        NodeAction.InsertText -> {
            val text = request.text ?: return missingText("InsertText")
            val field = this as? EditText ?: return notSupported("the view is not an EditText")
            field.requestFocus()
            field.text.insert(field.selectionEnd.coerceAtLeast(0), text)
            NodeActionResult(performed = true)
        }

        NodeAction.ImeAction -> {
            val field = this as? TextView ?: return notSupported("the view is not a TextView")
            // A field that declares no IME action still submits on Done, which is what the platform
            // shows for it — so that is what the fallback sends.
            val imeAction = (field.imeOptions and EditorInfo.IME_MASK_ACTION)
                .takeIf { it != EditorInfo.IME_ACTION_UNSPECIFIED && it != EditorInfo.IME_ACTION_NONE }
                ?: EditorInfo.IME_ACTION_DONE
            field.onEditorAction(imeAction)
            NodeActionResult(performed = true)
        }

        NodeAction.ScrollBy -> {
            if (!isScrollable()) return notSupported("the view has nothing to scroll")
            scrollBy(request.scrollX.roundToInt(), request.scrollY.roundToInt())
            NodeActionResult(performed = true)
        }

        NodeAction.RequestFocus -> {
            if (!isFocusable) return notSupported("the view is not focusable")
            performed(requestFocus(), "requestFocus() returned false")
        }

        NodeAction.Dismiss,
        NodeAction.Expand,
        NodeAction.Collapse,
        -> notSupported("${request.action} is not supported on a View node")
    }
}

/**
 * Actions a disabled view still answers — focus and scrolling stay meaningful — are excluded, so
 * only the ones a user could not trigger either are rejected up front.
 */
private val NodeAction.requiresEnabledView: Boolean
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

private fun performed(handled: Boolean, declined: String): NodeActionResult = NodeActionResult(performed = handled, message = if (handled) null else declined)

private fun notSupported(reason: String): NodeActionResult = NodeActionResult(performed = false, message = reason)

private fun missingText(action: String): NodeActionResult = NodeActionResult(performed = false, message = "$action requires the 'text' argument")
