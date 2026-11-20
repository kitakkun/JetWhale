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
 * Elements are held for as long as the latest capture reported them, and
 * [runCaptureReleasingUnseenElements] lets go of the ones Compose has since removed, so an id never
 * keeps a dead element alive past the next capture.
 */
internal class AccessibilityElementIds {
    private val idsByElement = HashMap<HTMLElement, Int>()
    private val elementsById = HashMap<Int, HTMLElement>()
    private var nextId = 0
    private var seenInCapture: MutableSet<HTMLElement>? = null

    /**
     * Runs one capture. Every element [idOf] sees inside [block] is kept; every element the previous
     * capture reported and this one did not is released.
     *
     * A capture that throws releases nothing, so the ids of the snapshot the host still shows keep
     * resolving.
     */
    fun <T> runCaptureReleasingUnseenElements(block: () -> T): T {
        val seen = HashSet<HTMLElement>()
        seenInCapture = seen
        val result = try {
            block()
        } finally {
            seenInCapture = null
        }
        val unseen = idsByElement.keys.filter { it !in seen }
        unseen.forEach { element -> idsByElement.remove(element)?.let(elementsById::remove) }
        return result
    }

    /** The id for [element], assigning one on first sight. */
    fun idOf(element: HTMLElement): Int {
        seenInCapture?.add(element)
        return idsByElement.getOrPut(element) {
            val id = nextId++
            elementsById[id] = element
            id
        }
    }

    /** The element [id] names, or `null` once it has left the latest capture. */
    fun elementOf(id: Int): HTMLElement? = elementsById[id]
}
