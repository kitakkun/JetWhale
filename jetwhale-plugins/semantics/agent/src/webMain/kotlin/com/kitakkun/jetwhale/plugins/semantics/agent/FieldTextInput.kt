package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Typing into one viewport's text fields the way Compose for Web reads a keyboard: through the
 * `<input>` or `<textarea>` it keeps next to the canvas while a field has focus, and the
 * `beforeinput` events the browser fires on it.
 *
 * Compose keeps one such input per viewport, for the focused field, and places it at that field's
 * cursor. A field is focused through the click Compose wires into its element, and its input is
 * the one placed over its node: going by position rather than by which input is newest is what
 * keeps the text out of another field when this one cannot take focus.
 *
 * Nothing here reads a field's content. A field's text is replaced by selecting all of its input,
 * not by measuring what it holds.
 */
internal class FieldTextInput(private val accessibilityDom: AccessibilityDom) {
    /** Replaces the content of [element]'s field with [text]. */
    suspend fun replaceText(element: HTMLElement, text: String): NodeActionResult {
        if (!element.isEditableNode) return NOT_A_FIELD
        val input = focusInputOver(element) ?: return FIELD_WITHOUT_INPUT
        when (input) {
            is HTMLInputElement -> input.select()
            is HTMLTextAreaElement -> input.select()
        }
        // For `insertReplacementText` Compose replaces the range selected in its input; for
        // `insertText` it keeps to a selection of its own when it has one.
        input.dispatchEvent(beforeInputEvent(inputType = "insertReplacementText", data = text))
        return NodeActionResult(performed = true)
    }

    /** Types [text] into [element]'s field at its cursor, replacing the selection if there is one. */
    suspend fun insertTextAtCursor(element: HTMLElement, text: String): NodeActionResult {
        if (!element.isEditableNode) return NOT_A_FIELD
        val input = focusInputOver(element) ?: return FIELD_WITHOUT_INPUT
        input.dispatchEvent(beforeInputEvent(inputType = "insertText", data = text))
        return NodeActionResult(performed = true)
    }

    /**
     * Clicks [element] to focus its field, then waits up to [FIELD_INPUT_WAIT] for Compose's input to
     * sit over the field: Compose opens it a frame or two after the focus and places it after the
     * next layout, and the node's bounds catch up on the DOM's next write if focusing moved it.
     * `null` when the input never sits over the field, as for a read-only or disabled one.
     */
    private suspend fun focusInputOver(element: HTMLElement): HTMLElement? {
        accessibilityDom.dispatchNonBubblingClick(element)
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow() < FIELD_INPUT_WAIT) {
            val input = fieldInput()
            val nodeBounds = accessibilityDom.boundsOf(element)
            if (input != null && nodeBounds != null && fieldInputBox()?.isPlacedOver(nodeBounds) == true) return input
            delay(POLL_INTERVAL)
        }
        return null
    }

    /**
     * The input Compose keeps for the focused field, which it marks with `inputmode`. The other
     * `<textarea>` it puts there, briefly, to catch clipboard events, has no such attribute.
     */
    private fun fieldInput(): HTMLElement? = accessibilityDom.canvasContainer?.children?.asList()?.firstOrNull { it.hasAttribute("inputmode") } as? HTMLElement

    /**
     * Where Compose places its input, from the composition's origin, as it sets it on the canvas's
     * container. The input's own rectangle is no substitute: its CSS keeps it inside the browser's
     * viewport, wherever the field is.
     */
    private fun fieldInputBox(): NodeBounds? {
        val style = (accessibilityDom.canvasContainer as? HTMLElement)?.style ?: return null
        val left = style.getPropertyValue("--compose-internal-web-backing-input-left").toFloatOrNull() ?: return null
        val top = style.getPropertyValue("--compose-internal-web-backing-input-top").toFloatOrNull() ?: return null
        val width = style.getPropertyValue("--compose-internal-web-backing-input-width").toFloatOrNull() ?: return null
        val height = style.getPropertyValue("--compose-internal-web-backing-input-height").toFloatOrNull() ?: return null
        return NodeBounds(left = left, top = top, right = left + width, bottom = top + height)
    }
}

/** Whether this box has been placed, which Compose marks by giving it a height, with its center in [bounds]. */
private fun NodeBounds.isPlacedOver(bounds: NodeBounds): Boolean = height > 0f && centerX in bounds.left..bounds.right && centerY in bounds.top..bounds.bottom

private val NOT_A_FIELD: NodeActionResult = NodeActionResult.notSupported("the node is not a text field")

private val FIELD_WITHOUT_INPUT: NodeActionResult = NodeActionResult(
    performed = false,
    message = "the field took no text input: it may be read-only or disabled, which the page does not say",
)

/** Compose writes the DOM at least once a second while the layout keeps changing, so a field focusing moved has its new bounds by then. */
private val FIELD_INPUT_WAIT: Duration = 1200.milliseconds

private val POLL_INTERVAL: Duration = 20.milliseconds

@OptIn(ExperimentalWasmJsInterop::class)
private fun beforeInputEvent(inputType: String, data: String): Event = js("new InputEvent('beforeinput', { inputType: inputType, data: data, cancelable: true })")
