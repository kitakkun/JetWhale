package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import org.w3c.dom.HTMLElement

/**
 * Hands out the [ComposeNode.id] each element of one viewport's accessibility DOM is reported under,
 * and resolves one back.
 *
 * The page carries no semantics id, but Compose keeps one element per semantics node for as long as
 * the node exists, rewriting it in place, so the element is as stable a handle as the id would be.
 * Ids are assigned here on first sight, counting up from `0`: still non-negative like the semantics
 * ids every other platform reports for a Compose node.
 *
 * An id stays valid for as long as its element is in [accessibilityDom], whether or not the latest
 * capture reached it: a capture cut short by its depth limit says nothing about the nodes below.
 * [releaseElementsRemovedFromDom] lets go of the elements Compose has removed, so an id never keeps
 * a dead element alive past the next capture.
 */
internal class AccessibilityElementIds(private val accessibilityDom: AccessibilityDom) {
    private val idsByElement = HashMap<HTMLElement, Int>()
    private val elementsById = HashMap<Int, HTMLElement>()
    private var nextId = 0

    /** The id for [element], assigning one on first sight. */
    fun idOf(element: HTMLElement): Int = idsByElement.getOrPut(element) {
        val id = nextId++
        elementsById[id] = element
        id
    }

    /** The element [id] names, or `null` once Compose has removed it from the accessibility DOM. */
    fun elementOf(id: Int): HTMLElement? = elementsById[id]?.takeIf(accessibilityDom::contains)

    fun releaseElementsRemovedFromDom() {
        val removedElements = idsByElement.keys.filterNot(accessibilityDom::contains)
        removedElements.forEach { element -> idsByElement.remove(element)?.let(elementsById::remove) }
    }
}
