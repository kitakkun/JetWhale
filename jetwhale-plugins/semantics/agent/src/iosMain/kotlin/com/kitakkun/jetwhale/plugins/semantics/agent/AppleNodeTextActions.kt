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
import platform.UIKit.UITextFieldDelegateProtocol
import platform.UIKit.UITextView
import platform.UIKit.UITextViewTextDidChangeNotification
import platform.UIKit.UIView
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
                    if (!node.editable) return NodeActionResult.notSupported("the text view is read-only")
                    node.text = text
                    node.notifyTextDidChange()
                    NodeActionResult(performed = true)
                }

                else -> NodeActionResult.notSupported(NOT_A_TEXT_VIEW)
            }
        }
    }

    /**
     * `insertText` is `UIKeyInput`, the route a keystroke takes, and a keystroke lands in the first
     * responder — so the field is focused first, the same order a user goes through and the one
     * the Android handler follows.
     */
    object InsertText : AppleNodeActionHandler {
        override val runsOnDisabledNode = false

        override fun isOfferedBy(node: NSObject) = node.isTextView()

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.InsertText, "text")
            val view = node as? UIView ?: return NodeActionResult.notSupported(NOT_A_TEXT_VIEW)
            if (!view.isFirstResponder() && !view.becomeFirstResponder()) {
                return NodeActionResult.notSupported("the field declined to become first responder, so it would not take a keystroke")
            }
            return when (node) {
                is UITextField -> {
                    node.insertText(text)
                    NodeActionResult(performed = true)
                }

                is UITextView -> {
                    if (!node.editable) return NodeActionResult.notSupported("the text view is read-only")
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

        override fun isOfferedBy(node: NSObject) = (node as? UITextField)?.returnHandler() != null

        override fun perform(node: NSObject, request: PerformNodeAction): NodeActionResult {
            val field = node as? UITextField ?: return NodeActionResult.notSupported("the node is not a UITextField")
            val delegate = field.returnHandler()
                ?: return NodeActionResult.notSupported("the field has no delegate handling return")
            return NodeActionResult.performedIf(delegate.textFieldShouldReturn(field), "the field's delegate declined the return")
        }

        // An optional protocol method is checked for before it is called: calling one the delegate
        // does not implement would throw rather than answer.
        private val RETURN_SELECTOR = NSSelectorFromString("textFieldShouldReturn:")

        private fun UITextField.returnHandler(): UITextFieldDelegateProtocol? = delegate?.takeIf { (it as NSObject).respondsToSelector(RETURN_SELECTOR) }
    }

    private const val NOT_A_TEXT_VIEW = "the node is not a UITextField or UITextView; a SwiftUI TextField is one underneath, a Compose text field is not reachable through accessibility"

    private fun NSObject.isTextView(): Boolean = this is UITextField || (this is UITextView && editable)

    private fun UITextView.notifyTextDidChange() {
        val delegate = delegate as? NSObject
        val selector = NSSelectorFromString("textViewDidChange:")
        if (delegate != null && delegate.respondsToSelector(selector)) {
            delegate.performSelector(selector, withObject = this)
        }
        NSNotificationCenter.defaultCenter.postNotificationName(UITextViewTextDidChangeNotification, this)
    }
}
