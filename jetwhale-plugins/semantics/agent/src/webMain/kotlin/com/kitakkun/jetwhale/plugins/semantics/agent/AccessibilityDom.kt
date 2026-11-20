package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver

/**
 * The accessibility DOM of one `ComposeViewport`: the `#cmp_a11y_root` element Compose keeps in the
 * viewport's shadow root for screen readers, holding one element per node of the merged semantics
 * tree, nested as the nodes are.
 *
 * Compose places every element with `position: fixed` where its node was in the page when it last
 * wrote the DOM, so an element's rectangle goes stale as soon as the page scrolls. The root node's
 * element was placed at the same moment at the composition's origin, so a rectangle measured from
 * it is where the node sits in the composition however the page has moved since.
 */
internal class AccessibilityDom(private val accessibilityRoot: HTMLElement) {
    /** `false` once the viewport has left the page. */
    val isConnected: Boolean get() = accessibilityRoot.isConnected

    /** The element of the semantics tree's root node, or `null` before Compose first writes the DOM. */
    val semanticsRootElement: HTMLElement? get() = accessibilityRoot.firstElementChild as? HTMLElement

    /** The element Compose renders the canvas into, whose origin the composition's is. */
    val canvasContainer: Element? get() = accessibilityRoot.parentElement

    /** Whether [element] is in the DOM right now, not only in the last capture. */
    fun contains(element: HTMLElement): Boolean = accessibilityRoot.contains(element)

    /** Calls [onChange] after each write Compose makes to the DOM, until the returned handle is closed. */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun watchChanges(onChange: () -> Unit): AutoCloseable {
        val observer = MutationObserver { _, _ -> onChange() }
        observer.observe(accessibilityRoot, subtreeMutationObserverInit(includeAttributeAndTextChanges = true))
        return AutoCloseable(observer::disconnect)
    }

    /** Where [element]'s node sits in the composition, in CSS pixels, or `null` when the DOM has no root element. */
    fun boundsOf(element: HTMLElement): NodeBounds? {
        val origin = semanticsRootElement?.getBoundingClientRect() ?: return null
        val rect = element.getBoundingClientRect()
        return NodeBounds(
            left = (rect.left - origin.left).toFloat(),
            top = (rect.top - origin.top).toFloat(),
            right = (rect.right - origin.left).toFloat(),
            bottom = (rect.bottom - origin.top).toFloat(),
        )
    }
}
