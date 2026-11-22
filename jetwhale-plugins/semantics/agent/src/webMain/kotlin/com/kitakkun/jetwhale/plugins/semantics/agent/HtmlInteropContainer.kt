package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.HtmlNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.DOMRect
import org.w3c.dom.END
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.MutationObserver
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

    /**
     * The part of the element [nodeId] names that shows now, in CSS pixels from [canvas]'s corner:
     * its rectangle cut down to Compose's clip on its wrapper and to every element between the two
     * that clips its overflow. Empty while none of it shows or CSS hides it, and `null` once the
     * element is gone.
     */
    fun visibleBoundsOf(nodeId: Int, canvas: Element): NodeBounds? {
        val element = htmlNodeIds.elementOf(nodeId) ?: return null
        if (window.getComputedStyle(element).visibility != "visible") return NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
        var visibleBounds = element.getBoundingClientRect().toNodeBounds()
        var ancestor = element.parentElement
        while (ancestor != null && ancestor.parentElement != containerElement) {
            visibleBounds = visibleBounds.clippedByOverflowOf(ancestor)
            ancestor = ancestor.parentElement
        }
        (ancestor as? HTMLElement)?.let { wrapper -> visibleBounds = visibleBounds.intersect(wrapper.clipPathBounds()) }
        if (visibleBounds.isEmpty) return visibleBounds
        val canvasRect = canvas.getBoundingClientRect()
        return NodeBounds(
            left = visibleBounds.left - canvasRect.left.toFloat(),
            top = visibleBounds.top - canvasRect.top.toFloat(),
            right = visibleBounds.right - canvasRect.left.toFloat(),
            bottom = visibleBounds.bottom - canvasRect.top.toFloat(),
        )
    }

    /**
     * Calls [onChangeOrScroll] after each change under the container and each scroll inside it,
     * either of which can move an embedded element without Compose laying anything out, until the
     * returned handle is closed.
     */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun watchChangesAndScrolls(onChangeOrScroll: () -> Unit): AutoCloseable {
        val observer = MutationObserver { _, _ -> onChangeOrScroll() }
        observer.observe(containerElement, subtreeMutationObserverInit(includeAttributeAndTextChanges = true))
        val scrollWatch = containerElement.watchScrolls(onChangeOrScroll)
        return AutoCloseable {
            observer.disconnect()
            scrollWatch.close()
        }
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
 * keeps no cursor — an `<input type="email">`, or a `contenteditable` element the page's selection
 * is not in — takes it at the end. The text goes in before the field takes focus, which would move
 * the selection into a `contenteditable` element that did not hold it.
 */
private fun insertIntoField(element: Element, text: String) {
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

        else -> if (!replaceSelectionInside(element, text)) element.appendChild(document.createTextNode(text))
    }
    (element as? HTMLElement)?.focus()
    dispatchTypingEvents(element, inputType = "insertText", data = text)
}

/**
 * Puts [text] in place of the page's selection, and the caret after it, when the selection lies
 * inside [editingHost]; reports whether it did.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun replaceSelectionInside(editingHost: Element, text: String): Boolean = js(
    """(() => {
        const selection = window.getSelection();
        if (!selection || selection.rangeCount === 0) return false;
        const range = selection.getRangeAt(0);
        if (!editingHost.contains(range.commonAncestorContainer)) return false;
        range.deleteContents();
        const inserted = document.createTextNode(text);
        range.insertNode(inserted);
        range.setStartAfter(inserted);
        range.collapse(true);
        selection.removeAllRanges();
        selection.addRange(range);
        return true;
    })()""",
)

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
    val wholeRect = getBoundingClientRect().toNodeBounds()
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

private fun DOMRect.toNodeBounds(): NodeBounds = NodeBounds(left = left.toFloat(), top = top.toFloat(), right = right.toFloat(), bottom = bottom.toFloat())

@OptIn(ExperimentalWasmJsInterop::class)
private fun inputEvent(inputType: String, data: String): Event = js("new InputEvent('input', { bubbles: true, inputType: inputType, data: data })")

@OptIn(ExperimentalWasmJsInterop::class)
private fun changeEvent(): Event = js("new Event('change', { bubbles: true })")
