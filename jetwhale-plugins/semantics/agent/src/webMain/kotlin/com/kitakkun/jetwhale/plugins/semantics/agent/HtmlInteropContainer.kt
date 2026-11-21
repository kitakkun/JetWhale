package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.HtmlNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.browser.document
import org.w3c.dom.END
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.SelectionMode
import org.w3c.dom.asList
import org.w3c.dom.events.Event

/**
 * The interop container of one `ComposeViewport`: the element beside the viewport's shadow host
 * that Compose puts each `HtmlElementView` in. Every view gets a `div` of its own there, which
 * Compose positions over the canvas from the composition's origin and clips to what the composition
 * shows of it; the element the app created is that `div`'s child.
 */
internal class HtmlInteropContainer(private val containerElement: Element) {
    private val htmlNodeIds = HtmlNodeIds(containerElement)

    /**
     * The elements apps handed `HtmlElementView`, each with its subtree, as nodes at [depth], in the
     * order Compose stacks them: the last is drawn on top.
     *
     * @param canvasLeft where the canvas sits in the browser's viewport now, in CSS pixels; with
     *   [canvasTop], what an element's bounds in the composition are measured from.
     */
    fun htmlNodes(options: NodeTreeCaptureOptions, depth: Int, canvasLeft: Float, canvasTop: Float): List<HtmlNode> {
        val capture = HtmlElementCapture(options = options, htmlNodeIds = htmlNodeIds, canvasLeft = canvasLeft, canvasTop = canvasTop)
        val nodes = containerElement.children.asList().filterIsInstance<HTMLElement>().flatMap { wrapper ->
            val visibleArea = wrapper.clipBounds().intersect(wrapper.clipPathBounds())
            wrapper.children.asList().mapNotNull { capture.htmlNodeOf(it, depth, visibleArea) }
        }
        htmlNodeIds.releaseElementsRemovedFromContainer()
        return nodes
    }

    /** Where the element [nodeId] names sits in the composition now, in CSS pixels from [canvas]'s corner, or `null` once it is gone. */
    fun boundsOf(nodeId: Int, canvas: Element): NodeBounds? {
        val element = htmlNodeIds.elementOf(nodeId) ?: return null
        val canvasRect = canvas.getBoundingClientRect()
        val rect = element.getBoundingClientRect()
        return NodeBounds(
            left = (rect.left - canvasRect.left).toFloat(),
            top = (rect.top - canvasRect.top).toFloat(),
            right = (rect.right - canvasRect.left).toFloat(),
            bottom = (rect.bottom - canvasRect.top).toFloat(),
        )
    }

    fun performAction(request: PerformNodeAction): NodeActionResult {
        val element = htmlNodeIds.elementOf(request.nodeId)
            ?: return NodeActionResult(
                performed = false,
                message = "unknown nodeId: ${request.nodeId} (the element may have left the page; capture the tree again)",
            )
        return when (request.action) {
            NodeAction.Click -> click(element)

            NodeAction.SetText -> {
                val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.SetText, "text")
                refusalToTypeInto(element)?.let { return it }
                replaceFieldContent(element, text)
                NodeActionResult(performed = true)
            }

            NodeAction.InsertText -> {
                val text = request.text ?: return NodeActionResult.missingArgument(NodeAction.InsertText, "text")
                refusalToTypeInto(element)?.let { return it }
                insertIntoField(element, text)
                NodeActionResult(performed = true)
            }

            else -> NodeActionResult.notSupported("${request.action} is not available on an HTML element: Click, SetText and InsertText are")
        }
    }
}

/**
 * Clicks [element] the way a user's click would reach it: `click()` bubbles, which is what a page
 * that handles its clicks on an ancestor relies on, and runs the element's own behavior — a checkbox
 * toggles, a link is followed.
 */
private fun click(element: Element): NodeActionResult {
    if (element.matches(":disabled")) return NodeActionResult.notSupported("the element is disabled, and a disabled element ignores clicks")
    if (element is HTMLElement) element.click() else element.dispatchEvent(bubblingClickEvent())
    return NodeActionResult(
        performed = true,
        message = if (element.isClickableElement) {
            null
        } else {
            "the page does not show whether this element takes a click: the click was sent, and does nothing if no listener handles it"
        },
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun bubblingClickEvent(): Event = js("new MouseEvent('click', { bubbles: true, cancelable: true })")

/** Why [element] cannot be typed into, or `null` when it can. */
private fun refusalToTypeInto(element: Element): NodeActionResult? = when {
    !element.isTextEntryElement -> NodeActionResult.notSupported("the element takes no text: only an <input> of a text type, a <textarea> or a contenteditable element does")
    element.matches(":read-only") -> NodeActionResult.notSupported("the field is disabled or read-only")
    else -> null
}

/** Replaces what the field holds with [text], in focus, then tells the page as typing would. */
private fun replaceFieldContent(element: Element, text: String) {
    (element as? HTMLElement)?.focus()
    if (element is HTMLInputElement || element is HTMLTextAreaElement) {
        writeValueThroughElementPrototype(element, text)
    } else {
        element.textContent = text
    }
    dispatchTypingEvents(element, inputType = "insertReplacementText", data = text)
}

/**
 * Puts [text] in at the field's cursor, in focus, then tells the page as typing would. A field that
 * keeps no cursor — an `<input type="email">` or a `contenteditable` element — takes it at the end.
 */
private fun insertIntoField(element: Element, text: String) {
    (element as? HTMLElement)?.focus()
    when (element) {
        is HTMLInputElement -> {
            val selectionStart = element.selectionStart
            val selectionEnd = element.selectionEnd
            if (selectionStart != null && selectionEnd != null) {
                element.setRangeText(text, selectionStart, selectionEnd, SelectionMode.END)
            } else {
                writeValueThroughElementPrototype(element, element.value + text)
            }
        }

        is HTMLTextAreaElement -> element.setRangeText(text, element.selectionStart ?: element.value.length, element.selectionEnd ?: element.value.length, SelectionMode.END)

        else -> element.appendChild(document.createTextNode(text))
    }
    dispatchTypingEvents(element, inputType = "insertText", data = text)
}

/** The `input` event typing fires, then the `change` event a field fires once its value is committed; a `contenteditable` element has none. */
private fun dispatchTypingEvents(element: Element, inputType: String, data: String) {
    element.dispatchEvent(inputEvent(inputType = inputType, data = data))
    if (element is HTMLInputElement || element is HTMLTextAreaElement) element.dispatchEvent(changeEvent())
}

/**
 * Sets a field's value through the `value` setter of `HTMLInputElement` or `HTMLTextAreaElement`
 * itself. React defines a `value` property on the field to track what it last rendered and drops an
 * `input` event whose value matches it, so a value written through that property would never reach
 * the app's `onChange`.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun writeValueThroughElementPrototype(field: Element, value: String) {
    js("Object.getOwnPropertyDescriptor(field instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set.call(field, value)")
}

/**
 * The part of this interop wrapper that shows, in the browser viewport's coordinates. Compose
 * clips an `HtmlElementView` to what the composition shows of it — scrolled partly out of a list,
 * say — by writing `inset(...)` lengths into the wrapper's `clip-path`, which no rectangle the
 * browser reports takes into account. Anything else there is not Compose's and clips nothing here.
 */
private fun HTMLElement.clipPathBounds(): NodeBounds {
    val rect = getBoundingClientRect()
    val wholeRect = NodeBounds(left = rect.left.toFloat(), top = rect.top.toFloat(), right = rect.right.toFloat(), bottom = rect.bottom.toFloat())
    val insetLengths = INSET_CLIP_PATH.matchEntire(style.getPropertyValue("clip-path").trim())
        ?.groupValues?.get(1)
        ?.split(' ')
        ?.filter(String::isNotEmpty)
        ?.map { it.removeSuffix("px").toFloatOrNull() ?: return wholeRect }
        ?: return wholeRect
    // The browser serializes the four lengths Compose writes in their shortest form, as it does the
    // margin shorthand: a lone length is every side's, and otherwise a side left out takes the
    // length of the side opposite it.
    val topInset = insetLengths.getOrNull(0) ?: return wholeRect
    val rightInset = insetLengths.getOrNull(1) ?: topInset
    val bottomInset = insetLengths.getOrNull(2) ?: topInset
    val leftInset = insetLengths.getOrNull(3) ?: rightInset
    return NodeBounds(
        left = wholeRect.left + leftInset,
        top = wholeRect.top + topInset,
        right = wholeRect.right - rightInset,
        bottom = wholeRect.bottom - bottomInset,
    )
}

private val INSET_CLIP_PATH: Regex = Regex("""inset\(([^)]*)\)""")

@OptIn(ExperimentalWasmJsInterop::class)
private fun inputEvent(inputType: String, data: String): Event = js("new InputEvent('input', { bubbles: true, inputType: inputType, data: data })")

@OptIn(ExperimentalWasmJsInterop::class)
private fun changeEvent(): Event = js("new Event('change', { bubbles: true })")
