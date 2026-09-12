package com.kitakkun.jetwhale.plugins.semantics.agent

import android.graphics.Rect
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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

        NodeAction.ScrollToIndex -> {
            val index = request.index ?: return NodeActionResult(performed = false, message = "ScrollToIndex requires the 'index' argument")
            scrollToIndex(index)
        }

        NodeAction.BringIntoView -> bringIntoView()

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
 * only the ones a user could not trigger either are rejected up front. So are the ones no view
 * supports at all: gating those on the view being enabled would answer "the view is disabled" to a
 * caller whose real problem is that the action does not exist on this side of the tree.
 */
private val NodeAction.requiresEnabledView: Boolean
    get() = when (this) {
        NodeAction.Click,
        NodeAction.LongClick,
        NodeAction.SetText,
        NodeAction.InsertText,
        NodeAction.ImeAction,
        -> true

        NodeAction.ScrollBy,
        NodeAction.ScrollToIndex,
        NodeAction.BringIntoView,
        NodeAction.RequestFocus,
        NodeAction.Dismiss,
        NodeAction.Expand,
        NodeAction.Collapse,
        -> false
    }

/**
 * `requestRectangleOnScreen` is the platform's own "show on screen": every scrolling parent —
 * `ScrollView`, `RecyclerView`, and the holder Compose puts around an `AndroidView { }` — answers
 * `requestChildRectangleOnScreen`, so the one call climbs out through Compose containers too.
 * Immediate rather than animated, so the next capture already sees the result.
 */
private fun View.bringIntoView(): NodeActionResult {
    val whole = Rect(0, 0, width, height)
    if (whole.isEmpty) return notSupported("the view has no size to bring into view")
    val visible = Rect()
    if (getLocalVisibleRect(visible) && visible == whole) {
        return NodeActionResult(performed = true, message = "the view is already in view")
    }
    return performed(requestRectangleOnScreen(whole, true), "no ancestor scrolled the view into view")
}

/**
 * The item-position scroll of the two list widgets the platform and AndroidX offer. `RecyclerView`
 * is checked only when the app ships it: the agent compiles against it without bundling it, and
 * an `is` check on a class the app does not have would blow up rather than answer `false`.
 */
private fun View.scrollToIndex(index: Int): NodeActionResult = when {
    this is AbsListView -> {
        if (index !in 0 until count) {
            notSupported("index $index is out of bounds [0, $count)")
        } else {
            setSelection(index)
            NodeActionResult(performed = true)
        }
    }

    isRecyclerViewAvailable && this is RecyclerView -> {
        val itemCount = adapter?.itemCount ?: 0
        if (index !in 0 until itemCount) {
            notSupported("index $index is out of bounds [0, $itemCount)")
        } else {
            scrollToPosition(index)
            NodeActionResult(performed = true)
        }
    }

    else -> notSupported("the view is not a RecyclerView or a ListView")
}

private fun performed(handled: Boolean, declined: String): NodeActionResult = NodeActionResult(performed = handled, message = if (handled) null else declined)

private fun notSupported(reason: String): NodeActionResult = NodeActionResult(performed = false, message = reason)

private fun missingText(action: String): NodeActionResult = NodeActionResult(performed = false, message = "$action requires the 'text' argument")
