package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.ShadowRoot

/**
 * Where this element can be seen right now, in CSS pixels from the browser viewport's corner: the
 * browser viewport, cut down by every ancestor that clips its overflow, through the viewport's
 * shadow root and up the app's own elements. Empty while CSS hides the element.
 *
 * The walk stops below `<body>`: the document's own overflow is the browser viewport's, which a
 * `<body>` with `overflow: hidden` hands on to it rather than clipping itself.
 */
internal fun HTMLElement.clipBounds(): NodeBounds {
    if (window.getComputedStyle(this).visibility != "visible") return NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
    var area = NodeBounds(left = 0f, top = 0f, right = window.innerWidth.toFloat(), bottom = window.innerHeight.toFloat())
    var ancestor = parentElementOrShadowHost()
    while (ancestor != null && ancestor != document.body && ancestor != document.documentElement) {
        area = area.clippedByOverflowOf(ancestor)
        ancestor = ancestor.parentElementOrShadowHost()
    }
    return area
}

/**
 * These bounds cut down to what [element] lets its overflow show, axis by axis: an axis whose
 * overflow is `visible` is not clipped, which `overflow: clip` allows on one axis alone. The clip is
 * the element's padding box, inside its borders and scrollbars.
 */
internal fun NodeBounds.clippedByOverflowOf(element: Element): NodeBounds {
    val style = window.getComputedStyle(element)
    val clipsX = style.getPropertyValue("overflow-x") != "visible"
    val clipsY = style.getPropertyValue("overflow-y") != "visible"
    if (!clipsX && !clipsY) return this
    val rect = element.getBoundingClientRect()
    val paddingBoxLeft = (rect.left + element.clientLeft).toFloat()
    val paddingBoxTop = (rect.top + element.clientTop).toFloat()
    return intersect(
        NodeBounds(
            left = if (clipsX) paddingBoxLeft else left,
            top = if (clipsY) paddingBoxTop else top,
            right = if (clipsX) paddingBoxLeft + element.clientWidth else right,
            bottom = if (clipsY) paddingBoxTop + element.clientHeight else bottom,
        ),
    )
}

private fun Element.parentElementOrShadowHost(): Element? = parentElement ?: (parentNode as? ShadowRoot)?.host
