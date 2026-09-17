package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import platform.UIKit.UIAccessibilityCustomAction
import platform.UIKit.UIView
import platform.UIKit.accessibilityCustomActions
import platform.UIKit.accessibilityPerformEscape
import platform.darwin.NSObject

/** Focus on a view, escape on anything, and the custom actions a toolkit names Expand and Collapse. */
internal object AppleNodeStateActions {
    object RequestFocus : AppleNodeActionHandler {
        override val runsOnDisabledNode = true

        override fun isOfferedBy(node: NSObject) = (node as? UIView)?.canBecomeFirstResponder ?: false

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val view = node as? UIView ?: return NodeActionResult.notSupported("only a UIView can take focus; this node is a bare accessibility element")
            if (!view.canBecomeFirstResponder) return NodeActionResult.notSupported("the view cannot become first responder")
            return NodeActionResult.performedIf(view.becomeFirstResponder(), "becomeFirstResponder() returned false")
        }
    }

    /**
     * `accessibilityPerformEscape` is VoiceOver's two-finger scrub, the gesture that dismisses a
     * modal. Nothing says in advance whether an object implements it, so it is not advertised and
     * simply tried.
     */
    object Dismiss : AppleNodeActionHandler {
        override val runsOnDisabledNode = true

        override fun isOfferedBy(node: NSObject) = false

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult = NodeActionResult.performedIf(node.accessibilityPerformEscape(), "the node did not handle accessibilityPerformEscape")
    }

    val Expand: AppleNodeActionHandler = CustomActionByName(NodeAction.Expand)
    val Collapse: AppleNodeActionHandler = CustomActionByName(NodeAction.Collapse)

    /** A custom action whose name is the action's, which is what SwiftUI's `accessibilityAction(named:)` and Compose's custom actions surface as. */
    private class CustomActionByName(private val action: NodeAction) : AppleNodeActionHandler {
        override val runsOnDisabledNode = false

        override fun isOfferedBy(node: NSObject) = node.customAction()?.actionHandler != null

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val custom = node.customAction() ?: return NodeActionResult.notSupported("the node has no custom action named $action")
            val handler = custom.actionHandler
                ?: return NodeActionResult.notSupported("the custom action $action has no handler block; target/selector actions are not invoked")
            return NodeActionResult.performedIf(handler(custom), "the $action handler returned false")
        }

        private fun NSObject.customAction(): UIAccessibilityCustomAction? = accessibilityCustomActions
            ?.map { it as UIAccessibilityCustomAction }
            ?.firstOrNull { it.name.equals(action.name, ignoreCase = true) }
    }
}
