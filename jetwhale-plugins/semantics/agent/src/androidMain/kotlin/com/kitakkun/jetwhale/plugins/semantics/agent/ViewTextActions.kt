package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/** Editing and submitting an `EditText`. */
internal object ViewTextActions {
    object SetText : ViewActionHandler {
        override val runsOnDisabledView = false

        override fun isOfferedBy(view: View) = view is EditText

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.SetText, "text")
            val field = view as? EditText ?: return NodeActionResult.notSupported("the view is not an EditText")
            field.focusForEditing()
            field.setText(text)
            return NodeActionResult(performed = true)
        }
    }

    object InsertText : ViewActionHandler {
        override val runsOnDisabledView = false

        override fun isOfferedBy(view: View) = view is EditText

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.InsertText, "text")
            val field = view as? EditText ?: return NodeActionResult.notSupported("the view is not an EditText")
            field.focusForEditing()
            field.text.insert(field.selectionEnd.coerceAtLeast(0), text)
            return NodeActionResult(performed = true)
        }
    }

    object ImeAction : ViewActionHandler {
        override val runsOnDisabledView = false

        override fun isOfferedBy(view: View) = view is EditText

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            val field = view as? TextView ?: return NodeActionResult.notSupported("the view is not a TextView")
            // A field that declares no IME action still submits on Done, which is what the platform
            // shows for it — so that is what the fallback sends.
            val imeAction = (field.imeOptions and EditorInfo.IME_MASK_ACTION)
                .takeIf { it != EditorInfo.IME_ACTION_UNSPECIFIED && it != EditorInfo.IME_ACTION_NONE }
                ?: EditorInfo.IME_ACTION_DONE
            field.onEditorAction(imeAction)
            return NodeActionResult(performed = true)
        }
    }

    /**
     * The same order a user goes through, and the one an app's focus-driven validation expects:
     * take focus first, then write.
     */
    private fun EditText.focusForEditing() {
        requestFocus()
    }
}
