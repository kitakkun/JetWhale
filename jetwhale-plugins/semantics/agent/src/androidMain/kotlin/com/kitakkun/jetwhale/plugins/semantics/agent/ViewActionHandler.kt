package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * How one [NodeAction] runs on an Android `View` — the counterpart of [SemanticsActionHandler],
 * grouped the same way: [ViewPointerActions], [ViewTextActions], [ViewScrollActions],
 * [ViewStateActions]. [viewHandler] is the one registry that binds an action to its handler.
 */
internal interface ViewActionHandler {
    /**
     * Whether a disabled view still answers. Focus and scrolling stay meaningful on a disabled
     * view. So does an action no view supports at all: gating that on the view being enabled would
     * answer "the view is disabled" to a caller whose real problem is that the action does not
     * exist on this side of the tree.
     */
    val runsOnDisabledView: Boolean

    /** Whether [view] lists this action under [NodeAction.advertisedAs] in its node's `actions`. */
    fun isOfferedBy(view: View): Boolean

    /** Must be called on the main thread. */
    fun perform(view: View, request: PerformNodeAction): NodeActionResult
}

internal val NodeAction.viewHandler: ViewActionHandler
    get() = when (this) {
        NodeAction.Click -> ViewPointerActions.Click
        NodeAction.LongClick -> ViewPointerActions.LongClick
        NodeAction.SetText -> ViewTextActions.SetText
        NodeAction.InsertText -> ViewTextActions.InsertText
        NodeAction.ImeAction -> ViewTextActions.ImeAction
        NodeAction.ScrollBy -> ViewScrollActions.ScrollBy
        NodeAction.ScrollToIndex -> ViewScrollActions.ScrollToIndex
        NodeAction.BringIntoView -> ViewScrollActions.BringIntoView
        NodeAction.RequestFocus -> ViewStateActions.RequestFocus
        NodeAction.Dismiss -> ViewStateActions.Dismiss
        NodeAction.Expand -> ViewStateActions.Expand
        NodeAction.Collapse -> ViewStateActions.Collapse
    }

/** A handler for an action a `View` has no counterpart for; it reports that it did not run rather than pretending. */
internal class UnsupportedOnView(private val action: NodeAction) : ViewActionHandler {
    override val runsOnDisabledView = true

    override fun isOfferedBy(view: View) = false

    override fun perform(view: View, request: PerformNodeAction): NodeActionResult = NodeActionResult.notSupported("$action is not supported on a View node")
}
