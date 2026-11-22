package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.HtmlNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.Node
import org.w3c.dom.asList
import org.w3c.dom.css.CSSStyleDeclaration

/**
 * Converts the elements under one viewport's interop container into the transport model, one
 * element and its subtree at a time.
 *
 * One capture reports at most [MAX_CAPTURED_ELEMENTS] elements and nothing deeper than
 * [MAX_ELEMENT_DEPTH] in the tree, so a whole page embedded in a viewport cannot hold the capture
 * up; the elements past either bound are left out.
 *
 * @param canvasLeft where the canvas sits in the browser's viewport now, in CSS pixels; with
 *   [canvasTop], what an element's bounds in the composition are measured from.
 */
internal class HtmlElementCapture(
    private val options: NodeTreeCaptureOptions,
    private val htmlNodeIds: HtmlNodeIds,
    private val canvasLeft: Float,
    private val canvasTop: Float,
) {
    private var capturedElementCount = 0

    /**
     * The node [element] stands for, at [depth], with its subtree, or `null` when it is filtered out
     * the way a Compose node is — it has no area on screen and
     * [NodeTreeCaptureOptions.includeInvisible] is off, or it sits past
     * [NodeTreeCaptureOptions.maxDepth] — or a bound above has been reached.
     *
     * @param visibleArea where [element] can be seen, in the browser viewport's coordinates, as far
     *   as its ancestors' clipping goes.
     */
    fun htmlNodeOf(element: Element, depth: Int, visibleArea: NodeBounds): HtmlNode? {
        val depthLimit = minOf(options.maxDepth ?: MAX_ELEMENT_DEPTH, MAX_ELEMENT_DEPTH)
        if (depth > depthLimit || capturedElementCount >= MAX_CAPTURED_ELEMENTS) return null
        capturedElementCount++

        val nodeId = htmlNodeIds.idOf(element)
        val childHtmlNodes = if (depth < depthLimit && element.hasChildElementsToReport) {
            val childVisibleArea = visibleArea.clippedByOverflowOf(element)
            element.children.asList().mapNotNull { htmlNodeOf(it, depth + 1, childVisibleArea) }
        } else {
            emptyList()
        }

        val computedStyle = window.getComputedStyle(element)
        val elementBounds = boundsOf(element, computedStyle, visibleArea)
        if (!options.keepsNode(isVisible = elementBounds.isVisible, hasKeptChildren = childHtmlNodes.isNotEmpty())) return null
        return element.toHtmlNode(nodeId = nodeId, computedStyle = computedStyle, elementBounds = elementBounds, childHtmlNodes = childHtmlNodes)
    }

    private fun boundsOf(element: Element, computedStyle: CSSStyleDeclaration, visibleArea: NodeBounds): HtmlElementBounds {
        val rect = element.getBoundingClientRect()
        val boundsInViewport = NodeBounds(left = rect.left.toFloat(), top = rect.top.toFloat(), right = rect.right.toFloat(), bottom = rect.bottom.toFloat())
        return HtmlElementBounds(
            bounds = NodeBounds(
                left = boundsInViewport.left - canvasLeft,
                top = boundsInViewport.top - canvasTop,
                right = boundsInViewport.right - canvasLeft,
                bottom = boundsInViewport.bottom - canvasTop,
            ),
            boundsInScreen = if (computedStyle.visibility == "visible") {
                boundsInViewport.intersect(visibleArea)
            } else {
                NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
            },
        )
    }
}

/** Where an element sits in the composition, and the part of it that can be seen on screen. */
private data class HtmlElementBounds(val bounds: NodeBounds, val boundsInScreen: NodeBounds) {
    val isVisible: Boolean get() = !bounds.isEmpty && !boundsInScreen.isEmpty
}

private fun Element.toHtmlNode(nodeId: Int, computedStyle: CSSStyleDeclaration, elementBounds: HtmlElementBounds, childHtmlNodes: List<HtmlNode>): HtmlNode {
    val editable = isTextEntryElement
    val masked = isMasked(computedStyle)
    val clickable = isClickableElement
    return HtmlNode(
        id = nodeId,
        tagName = localName.lowercase(),
        elementId = id.takeIf(String::isNotEmpty),
        cssClasses = getAttribute("class").orEmpty().split(WHITESPACE).filter(String::isNotEmpty),
        attributes = CAPTURED_ATTRIBUTE_NAMES.mapNotNull { attributeName -> getAttribute(attributeName)?.let { attributeName to it } }.toMap(),
        text = if (editable || masked) null else ownText(),
        editableText = if (editable && !masked) fieldContent() else null,
        contentDescription = getAttribute("aria-label")?.takeIf(String::isNotEmpty),
        toggleableState = toggleableState(),
        bounds = elementBounds.bounds,
        boundsInScreen = elementBounds.boundsInScreen,
        actions = buildList {
            if (clickable) add(NodeAction.Click)
            if (editable) addAll(listOf(NodeAction.SetText, NodeAction.InsertText))
        }.mapNotNull(NodeAction::advertisedAs),
        isEnabled = !matches(":disabled"),
        isClickable = clickable,
        isFocused = this == document.activeElement,
        isSelected = getAttribute("aria-selected") == "true",
        isEditable = editable,
        isScrollable = isScrollable(computedStyle),
        isVisible = elementBounds.isVisible,
        children = childHtmlNodes,
    )
}

/**
 * Whether this element's child elements are reported. A field's are its content, reported as its
 * editable text, and an `<svg>`'s are the shapes it draws: neither holds anything to operate.
 */
private val Element.hasChildElementsToReport: Boolean
    get() = !isTextEntryElement && localName != "svg"

/**
 * Whether the page shows that this element takes a click: an element that is interactive by its tag
 * or its ARIA role, a field, or one with an `onclick` handler. A listener added with
 * `addEventListener`, which is how most frameworks handle clicks, cannot be seen from the page.
 */
internal val Element.isClickableElement: Boolean
    get() = when (localName) {
        "a" -> hasAttribute("href")
        "input" -> (this as? HTMLInputElement)?.type != "hidden"
        else -> localName in CLICKABLE_TAG_NAMES
    } ||
        getAttribute("role") in CLICKABLE_ROLES ||
        isEditingHost ||
        hasOnclickHandler(this)

/** Whether this element takes typed text: an `<input>` of a text type, a `<textarea>`, or the root of a `contenteditable` region. */
internal val Element.isTextEntryElement: Boolean
    get() = when (this) {
        is HTMLTextAreaElement -> true
        is HTMLInputElement -> type in TEXT_ENTRY_INPUT_TYPES
        else -> isEditingHost
    }

private val Element.isEditingHost: Boolean
    get() = getAttribute("contenteditable")?.lowercase() in EDITING_HOST_CONTENTEDITABLE_VALUES

/**
 * Whether the page masks what this element shows: a password field, or text drawn as discs through
 * `-webkit-text-security`, which is inherited, so it masks a field's surroundings as well.
 */
private fun Element.isMasked(computedStyle: CSSStyleDeclaration): Boolean {
    if ((this as? HTMLInputElement)?.type == "password") return true
    val textSecurity = computedStyle.getPropertyValue("-webkit-text-security")
    return textSecurity.isNotEmpty() && textSecurity != "none"
}

/** What a field holds now, as typed: an `<input>`'s or `<textarea>`'s value, or the text of a `contenteditable` element. */
private fun Element.fieldContent(): String? = when (this) {
    is HTMLInputElement -> value
    is HTMLTextAreaElement -> value
    is HTMLElement -> innerText
    else -> null
}

/** The element's own text nodes, with runs of whitespace read as one space the way the browser lays them out, or `null` when there are none. */
private fun Element.ownText(): String? = childNodes.asList()
    .filter { it.nodeType == Node.TEXT_NODE }
    .joinToString(separator = "") { it.nodeValue.orEmpty() }
    .replace(WHITESPACE, " ")
    .trim()
    .takeIf(String::isNotEmpty)

/** `On`, `Off` or `Indeterminate` for a checkbox or radio button, native or marked with `aria-checked`; `null` for anything else. */
private fun Element.toggleableState(): String? {
    val input = this as? HTMLInputElement
    if (input != null && input.type in CHECKABLE_INPUT_TYPES) {
        return when {
            input.type == "checkbox" && input.indeterminate -> "Indeterminate"
            input.checked -> "On"
            else -> "Off"
        }
    }
    return when (getAttribute("aria-checked")) {
        "true" -> "On"
        "false" -> "Off"
        "mixed" -> "Indeterminate"
        else -> null
    }
}

/** Whether the element scrolls its overflow and has some to scroll. */
private fun Element.isScrollable(computedStyle: CSSStyleDeclaration): Boolean {
    val scrollsX = computedStyle.getPropertyValue("overflow-x") in SCROLLING_OVERFLOW_VALUES && scrollWidth > clientWidth
    val scrollsY = computedStyle.getPropertyValue("overflow-y") in SCROLLING_OVERFLOW_VALUES && scrollHeight > clientHeight
    return scrollsX || scrollsY
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun hasOnclickHandler(element: Element): Boolean = js("typeof element.onclick === 'function'")

private const val MAX_CAPTURED_ELEMENTS: Int = 1000

/** The depth in the tree below which no element is reported; the viewport's root node is depth 0. */
private const val MAX_ELEMENT_DEPTH: Int = 32

private val CAPTURED_ATTRIBUTE_NAMES: List<String> = listOf("type", "name", "href", "role", "placeholder", "title", "alt")

private val CLICKABLE_TAG_NAMES: Set<String> = setOf("button", "select", "textarea", "label", "summary")

private val CLICKABLE_ROLES: Set<String> = setOf(
    "button",
    "link",
    "checkbox",
    "radio",
    "switch",
    "tab",
    "menuitem",
    "menuitemcheckbox",
    "menuitemradio",
    "option",
    "treeitem",
)

/** The `type`s an `<input>` reports for a field that takes typed text; the browser reports an unknown type as `text`. */
private val TEXT_ENTRY_INPUT_TYPES: Set<String> = setOf("text", "search", "email", "url", "tel", "password", "number")

private val CHECKABLE_INPUT_TYPES: Set<String> = setOf("checkbox", "radio")

/** The `contenteditable` values that make an element editable; `""` is the attribute written without a value. */
private val EDITING_HOST_CONTENTEDITABLE_VALUES: Set<String> = setOf("", "true", "plaintext-only")

private val SCROLLING_OVERFLOW_VALUES: Set<String> = setOf("auto", "scroll")

private val WHITESPACE: Regex = Regex("\\s+")
