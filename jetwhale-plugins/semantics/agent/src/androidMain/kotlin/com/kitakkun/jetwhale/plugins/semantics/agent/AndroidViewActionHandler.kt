package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * How one [NodeAction] runs on an Android `View` — the counterpart of [SemanticsActionHandler],
 * grouped the same way: [AndroidViewPointerActions], [AndroidViewTextActions], [AndroidViewScrollActions],
 * [AndroidViewStateActions]. [androidViewHandler] is the one registry that binds an action to its handler.
 */
internal interface AndroidViewActionHandler {
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

internal val NodeAction.androidViewHandler: AndroidViewActionHandler
    get() = when (this) {
        NodeAction.Click -> AndroidViewPointerActions.Click
        NodeAction.LongClick -> AndroidViewPointerActions.LongClick
        NodeAction.SetText -> AndroidViewTextActions.SetText
        NodeAction.InsertText -> AndroidViewTextActions.InsertText
        NodeAction.ImeAction -> AndroidViewTextActions.ImeAction
        NodeAction.ScrollBy -> AndroidViewScrollActions.ScrollBy
        NodeAction.ScrollToIndex -> AndroidViewScrollActions.ScrollToIndex
        NodeAction.BringIntoView -> AndroidViewScrollActions.BringIntoView
        NodeAction.RequestFocus -> AndroidViewStateActions.RequestFocus
        NodeAction.Dismiss -> AndroidViewStateActions.Dismiss
        NodeAction.Expand -> AndroidViewStateActions.Expand
        NodeAction.Collapse -> AndroidViewStateActions.Collapse
    }

/** A handler for an action a `View` has no counterpart for; it reports that it did not run rather than pretending. */
internal class UnsupportedOnAndroidView(private val action: NodeAction) : AndroidViewActionHandler {
    override val runsOnDisabledView = true

    override fun isOfferedBy(view: View) = false

    override fun perform(view: View, request: PerformNodeAction): NodeActionResult = NodeActionResult.notSupported("$action is not supported on a View node")
}
