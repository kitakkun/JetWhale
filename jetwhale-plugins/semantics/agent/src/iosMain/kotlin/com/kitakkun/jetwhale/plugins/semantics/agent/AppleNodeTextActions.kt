@file:OptIn(ExperimentalForeignApi::class)

package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UITextField
import platform.UIKit.UITextView
import platform.UIKit.UITextViewTextDidChangeNotification
import platform.darwin.NSObject

/**
 * Editing and submitting a `UITextField` or `UITextView`.
 *
 * A SwiftUI `TextField` is a `UITextField` underneath, so writing to the view reaches the binding.
 * A Compose text field is a bare element whose `accessibilityValue` is read-only, and the protocol
 * offers no other way in; those report not performed, saying why.
 */
internal object AppleNodeTextActions {
    object SetText : AppleNodeActionHandler {
        override val runsOnDisabledNode = false

        override fun isOfferedBy(node: NSObject) = node.isTextView()

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.SetText, "text")
            return when (node) {
                is UITextField -> {
                    node.text = text
                    // Assigning `text` bypasses the editing events a keystroke would send, and the
                    // SwiftUI binding listens on exactly those.
                    node.sendActionsForControlEvents(UIControlEventEditingChanged)
                    NodeActionResult(performed = true)
                }

                is UITextView -> {
                    node.text = text
                    node.notifyTextDidChange()
                    NodeActionResult(performed = true)
                }

                else -> NodeActionResult.notSupported(NOT_A_TEXT_VIEW)
            }
        }
    }

    object InsertText : AppleNodeActionHandler {
        override val runsOnDisabledNode = false

        override fun isOfferedBy(node: NSObject) = node.isTextView()

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.InsertText, "text")
            return when (node) {
                // `insertText` is `UIKeyInput`, the route a keystroke takes, so the editing events
                // follow on their own.
                is UITextField -> {
                    node.insertText(text)
                    NodeActionResult(performed = true)
                }

                is UITextView -> {
                    node.insertText(text)
                    NodeActionResult(performed = true)
                }

                else -> NodeActionResult.notSupported(NOT_A_TEXT_VIEW)
            }
        }
    }

    /**
     * Return on a `UITextField` goes to its delegate's `textFieldShouldReturn:`, which is where
     * SwiftUI's `onSubmit` and a UIKit app's submit handler both live. A `UITextView` has no return
     * key action; return inserts a newline there.
     */
    object ImeAction : AppleNodeActionHandler {
        override val runsOnDisabledNode = false

        override fun isOfferedBy(node: NSObject) = node is UITextField

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val field = node as? UITextField ?: return NodeActionResult.notSupported("the node is not a UITextField")
            val delegate = field.delegate as? NSObject
                ?: return NodeActionResult.notSupported("the field has no delegate to handle return")
            // Optional protocol methods are reached by selector: calling one the delegate does not
            // implement would throw rather than answer.
            val selector = NSSelectorFromString("textFieldShouldReturn:")
            if (!delegate.respondsToSelector(selector)) {
                return NodeActionResult.notSupported("the field's delegate does not handle return")
            }
            delegate.performSelector(selector, withObject = field)
            return NodeActionResult(performed = true)
        }
    }

    private const val NOT_A_TEXT_VIEW = "the node is not a UITextField or UITextView; a SwiftUI TextField is one underneath, a Compose text field is not reachable through accessibility"

    private fun NSObject.isTextView(): Boolean = this is UITextField || this is UITextView

    private fun UITextView.notifyTextDidChange() {
        val delegate = delegate as? NSObject
        val selector = NSSelectorFromString("textViewDidChange:")
        if (delegate != null && delegate.respondsToSelector(selector)) {
            delegate.performSelector(selector, withObject = this)
        }
        NSNotificationCenter.defaultCenter.postNotificationName(UITextViewTextDidChangeNotification, this)
    }
}
