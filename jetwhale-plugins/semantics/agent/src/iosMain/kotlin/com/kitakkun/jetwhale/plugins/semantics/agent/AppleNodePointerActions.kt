package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.accessibilityActivate
import platform.darwin.NSObject

/**
 * `accessibilityActivate()` is what VoiceOver's double-tap sends, and every toolkit answers it with
 * the node's primary action: a SwiftUI `Button` runs its closure, a Compose clickable its `onClick`,
 * a `UIControl` its touch-up actions. A control that declines is sent its touch-up actions
 * directly, which is the same thing a tap ends in.
 */
internal object AppleNodePointerActions {
    object Click : AppleNodeActionHandler {
        override val runsOnDisabledNode = false

        override fun isOfferedBy(node: NSObject) = node.isClickable()

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            if (!node.isClickable()) return NodeActionResult.notSupported("the node is not clickable")
            if (node.accessibilityActivate()) return NodeActionResult(performed = true)
            val control = node as? UIControl
                ?: return NodeActionResult.notSupported("accessibilityActivate() returned false")
            control.sendActionsForControlEvents(UIControlEventTouchUpInside)
            return NodeActionResult(performed = true, message = "sent touchUpInside; accessibilityActivate() had returned false")
        }
    }

    val LongClick: AppleNodeActionHandler = UnsupportedOnAppleNode(NodeAction.LongClick)
}
