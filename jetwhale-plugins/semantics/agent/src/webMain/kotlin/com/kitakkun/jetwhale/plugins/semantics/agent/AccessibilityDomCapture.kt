package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.asList

/**
 * Converts one viewport's accessibility DOM into the transport model.
 *
 * Compose writes into each element only what a screen reader needs: the node's text as the
 * element's own text, its content description as `aria-label`, its test tag as `id`, an ARIA `role`,
 * `contenteditable` on an editable node, and its bounds. A click listener runs the node's `OnClick`.
 * That is the whole of what a capture can report. The page says nothing of a node's enabled,
 * focused, selected, toggled or scrollable state, or of any other action, so those read as their
 * defaults.
 *
 * @param canvasLeft where the canvas sits in the browser's viewport now, in CSS pixels; with
 *   [canvasTop], what turns a node's bounds in the composition into its bounds on screen.
 * @param canvasClipBounds where the canvas can be seen, in the browser viewport's coordinates, which
 *   clips every node's on-screen bounds: a node scrolled out of the browser's viewport or clipped
 *   by an element around the canvas cannot be touched.
 */
internal class AccessibilityDomCapture(
    private val options: NodeTreeCaptureOptions,
    private val accessibilityDom: AccessibilityDom,
    private val elementIds: AccessibilityElementIds,
    private val canvasLeft: Float,
    private val canvasTop: Float,
    private val canvasClipBounds: NodeBounds,
) {
    /**
     * The node [element] stands for, with its subtree, or `null` when it is filtered out: it has no
     * area on screen and [NodeTreeCaptureOptions.includeInvisible] is off, or it sits past
     * [NodeTreeCaptureOptions.maxDepth]. An element kept only because a descendant survived stays
     * in the tree, as on every other platform.
     */
    fun composeNodeOf(element: HTMLElement, depth: Int): ComposeNode? {
        val id = elementIds.idOf(element)
        val maxDepth = options.maxDepth
        val children = if (maxDepth != null && depth >= maxDepth) {
            emptyList()
        } else {
            element.childSemanticsNodeElements().mapNotNull { composeNodeOf(it, depth + 1) }
        }

        val bounds = accessibilityDom.boundsOf(element) ?: return null
        val boundsInScreen = NodeBounds(
            left = bounds.left + canvasLeft,
            top = bounds.top + canvasTop,
            right = bounds.right + canvasLeft,
            bottom = bounds.bottom + canvasTop,
        ).intersect(canvasClipBounds)
        val visible = !bounds.isEmpty && !boundsInScreen.isEmpty
        if (!options.keepsNode(isVisible = visible, hasKeptChildren = children.isNotEmpty())) return null

        return ComposeNode(
            id = id,
            role = composeRoleOf(element.getAttribute("role")),
            // Compose writes an editable node's content as the element's text, a password field's
            // in plain text and unmarked, so no field's content is read.
            text = if (element.isEditableNode) null else element.ownText(),
            contentDescription = element.getAttribute("aria-label")?.takeIf(String::isNotEmpty),
            testTag = element.id.takeIf(String::isNotEmpty),
            bounds = bounds,
            boundsInScreen = boundsInScreen,
            actions = listOfNotNull(NodeAction.Click.advertisedAs.takeIf { element.isClickableNode }),
            isClickable = element.isClickableNode,
            isEditable = element.isEditableNode,
            isVisible = visible,
            children = children,
        )
    }
}

/**
 * Whether a capture keeps a node: it is visible, the options keep invisible nodes, or a node below
 * it was kept, which leaves it in the tree so the descendant is not reparented.
 */
internal fun NodeTreeCaptureOptions.keepsNode(isVisible: Boolean, hasKeptChildren: Boolean): Boolean = isVisible || includeInvisible || hasKeptChildren

/**
 * Whether the page shows that Compose wired a click listener into this element. It does for every
 * node with `OnClick`, and marks such a node `button` unless its role says it is a field, a heading
 * or a collection. A field always takes a click, which focuses it; a heading or a collection may or
 * may not, which the page does not say.
 */
internal val HTMLElement.isClickableNode: Boolean
    get() = getAttribute("role") == "button" || isEditableNode

private val HTMLElement.isEditableNode: Boolean
    get() = getAttribute("contenteditable") == "true"

/** The child elements that are nodes; the others are the `<br>`s Compose writes into a text with line breaks. */
private fun HTMLElement.childSemanticsNodeElements(): List<HTMLElement> = children.asList().mapNotNull { child -> (child as? HTMLElement)?.takeIf { it.tagName == "DIV" } }

/**
 * The node's text: the element's own text nodes, without the child elements' text that
 * `textContent` would add, and with the `<br>`s Compose writes for line breaks read back as `\n`.
 */
private fun HTMLElement.ownText(): String? = buildString {
    childNodes.asList().forEach { child ->
        when {
            child.nodeType == Node.TEXT_NODE -> append(child.nodeValue.orEmpty())
            (child as? Element)?.tagName == "BR" -> append('\n')
        }
    }
}.takeIf(String::isNotEmpty)

/**
 * The Compose role behind an ARIA role, inverting the mapping Compose writes. Compose marks every
 * clickable node `button`, a `Checkbox` included, so `Button` stands for any clickable here; the
 * roles it adds of its own (`heading`, `textbox`, `list`, `grid`) have no Compose role to report.
 */
private fun composeRoleOf(ariaRole: String?): String? = when (ariaRole) {
    "button" -> "Button"
    "checkbox" -> "Checkbox"
    "switch" -> "Switch"
    "radio" -> "RadioButton"
    "tab" -> "Tab"
    "img" -> "Image"
    "menu" -> "DropdownList"
    else -> null
}

internal fun NodeBounds.intersect(other: NodeBounds): NodeBounds {
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    return if (right <= left || bottom <= top) {
        NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
    } else {
        NodeBounds(left = left, top = top, right = right, bottom = bottom)
    }
}
