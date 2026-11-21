package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.HtmlNode
import org.w3c.dom.Element

/**
 * Hands out the [HtmlNode.id] each element under one viewport's interop container is reported
 * under, and resolves one back.
 *
 * Ids are assigned on first sight, counting down from `-1`, so they never collide with the
 * non-negative ids the viewport's Compose nodes get. An id stays valid for as long as its element is
 * under [containerElement], whether or not the latest capture reached it, and
 * [releaseElementsRemovedFromContainer] lets go of the elements that have left it.
 */
internal class HtmlNodeIds(private val containerElement: Element) {
    private val idsByElement = HashMap<Element, Int>()
    private val elementsById = HashMap<Int, Element>()
    private var nextId = -1

    /** The id for [element], assigning one on first sight. */
    fun idOf(element: Element): Int = idsByElement.getOrPut(element) {
        val id = nextId--
        elementsById[id] = element
        id
    }

    /** The element [id] names, or `null` once it has left the interop container. */
    fun elementOf(id: Int): Element? = elementsById[id]?.takeIf(containerElement::contains)

    fun releaseElementsRemovedFromContainer() {
        val removedElements = idsByElement.keys.filterNot(containerElement::contains)
        removedElements.forEach { element -> idsByElement.remove(element)?.let(elementsById::remove) }
    }
}
