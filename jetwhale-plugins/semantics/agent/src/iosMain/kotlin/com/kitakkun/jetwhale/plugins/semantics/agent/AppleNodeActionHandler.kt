package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import platform.darwin.NSObject

/**
 * How one [NodeAction] runs on an iOS accessibility object — the counterpart of
 * [SemanticsActionHandler], grouped the same way: [AppleNodePointerActions], [AppleNodeTextActions],
 * [AppleNodeScrollActions], [AppleNodeStateActions]. [appleNodeHandler] is the one registry that
 * binds an action to its handler.
 *
 * A handler sees an `NSObject` because that is the whole of what the tree holds: a `UIView`, or a
 * bare element SwiftUI or Compose published. Where the view API is richer than the accessibility
 * protocol — a `UITextField`'s `text`, a `UIScrollView`'s `contentOffset` — a handler uses it when
 * the object is such a view, and the protocol otherwise.
 */
internal interface AppleNodeActionHandler {
    /**
     * Whether a disabled node still answers. Focus, dismissal and scrolling stay meaningful on a
     * disabled node. So does an action no node supports at all: gating that on the node being
     * enabled would answer "the node is disabled" to a caller whose real problem is that the action
     * does not exist on this side of the tree.
     */
    val runsOnDisabledNode: Boolean

    /** Whether [node] lists this action under [NodeAction.advertisedAs] in its node's `actions`. */
    fun isOfferedBy(node: NSObject): Boolean

    /** Must be called on the main thread. */
    fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult
}

internal val NodeAction.appleNodeHandler: AppleNodeActionHandler
    get() = when (this) {
        NodeAction.Click -> AppleNodePointerActions.Click
        NodeAction.LongClick -> AppleNodePointerActions.LongClick
        NodeAction.SetText -> AppleNodeTextActions.SetText
        NodeAction.InsertText -> AppleNodeTextActions.InsertText
        NodeAction.ImeAction -> AppleNodeTextActions.ImeAction
        NodeAction.ScrollBy -> AppleNodeScrollActions.ScrollBy
        NodeAction.ScrollToIndex -> AppleNodeScrollActions.ScrollToIndex
        NodeAction.BringIntoView -> AppleNodeScrollActions.BringIntoView
        NodeAction.RequestFocus -> AppleNodeStateActions.RequestFocus
        NodeAction.Dismiss -> AppleNodeStateActions.Dismiss
        NodeAction.Expand -> AppleNodeStateActions.Expand
        NodeAction.Collapse -> AppleNodeStateActions.Collapse
    }

/**
 * Runs [request]'s action on this object, the way the object itself would run it.
 *
 * Going through the node's own action — `accessibilityActivate()`, a control's own event — rather
 * than synthesising a touch is what makes this usable for driving an app: no coordinates, no
 * chance of landing on whatever moved into that spot, and an answer saying whether the node took it.
 *
 * Must be called on the main thread.
 */
internal fun NSObject.performAppleNodeAction(request: PerformNodeAction): NodeActionResult {
    val handler = request.action.appleNodeHandler
    if (!handler.runsOnDisabledNode && !isEnabled()) {
        return NodeActionResult(performed = false, message = "the node is disabled")
    }
    return handler.perform(this, request)
}

/** A handler for an action no iOS node has a counterpart for; it reports that it did not run rather than pretending. */
internal class UnsupportedOnAppleNode(private val action: NodeAction) : AppleNodeActionHandler {
    override val runsOnDisabledNode = true

    override fun isOfferedBy(node: NSObject) = false

    override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult =
        NodeActionResult.notSupported("$action is not supported on an iOS node")
}
