package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode

/** Identifies one node across the whole snapshot; node ids are only unique within their root. */
internal data class NodeKey(val rootId: String, val nodeId: Int)

/**
 * Keeps the nodes [predicate] accepts, plus every ancestor of a kept node.
 *
 * Ancestors are kept even when they fail the predicate: a filter that dropped them would reparent
 * the matches and lose the structure that makes the tree readable in the first place. Returns
 * `null` when nothing in this subtree matched.
 */
internal fun UiNode.filterTree(predicate: (UiNode) -> Boolean): UiNode? {
    val keptChildren = children.mapNotNull { it.filterTree(predicate) }
    return when {
        keptChildren.isNotEmpty() -> withChildren(keptChildren)
        predicate(this) -> withChildren(emptyList())
        else -> null
    }
}

private fun UiNode.withChildren(children: List<UiNode>): UiNode = when (this) {
    is ComposeNode -> copy(children = children)
    is ViewNode -> copy(children = children)
}

/** Depth-first walk, this node first. */
internal fun UiNode.asSequence(): Sequence<UiNode> = sequence {
    yield(this@asSequence)
    children.forEach { yieldAll(it.asSequence()) }
}

internal fun ComposeRoot.findNode(nodeId: Int): UiNode? = node?.asSequence()?.firstOrNull { it.id == nodeId }

/**
 * The root holding [nodeId], for resolving a node the caller named without saying where. Searched
 * newest root first: ids are only unique within a root, and the newest window (a dialog over the
 * screen that opened it) is the one a caller is looking at.
 */
internal fun NodeTreeSnapshot.findRootOf(nodeId: Int): ComposeRoot? = roots.lastOrNull { it.findNode(nodeId) != null }

internal fun NodeTreeSnapshot.nodeCount(): Int = roots.sumOf { it.node?.asSequence()?.count() ?: 0 }

/**
 * `true` when the node offers something to do: an action, editable content, or scrolling.
 *
 * This is the filter that answers "what can be operated here" — the question both the tree view's
 * *interactive only* toggle and an AI agent driving the app are actually asking.
 */
internal val UiNode.isInteractive: Boolean
    get() = actions.isNotEmpty() || isClickable || isEditable || isScrollable

/** How a node should read in a list: its own label if it has one, otherwise its role or id. */
internal fun UiNode.displayLabel(): String {
    val label = text ?: contentDescription ?: editableText ?: (this as? ComposeNode)?.testTag
    return when (this) {
        // A View is named by its class the way a Compose node is named by its role: it is what says
        // what the thing is. The resource id comes next, because that is what the app calls it.
        is ViewNode -> buildString {
            append(viewClass.substringAfterLast('.'))
            resourceId?.let { append(" · @id/$it") }
            label?.let { append(" · $it") }
        }

        is ComposeNode -> {
            val role = role
            when {
                role != null && label != null -> "$role · $label"
                role != null -> role
                label != null -> label
                else -> "#$id"
            }
        }
    }
}

/** Matcher shared by the tree view's search box and the `findNodes` MCP tool. */
internal data class NodeQuery(
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    /** Always compared whole, whatever [exact] says: a resource id is an identifier, not a label. */
    val resourceId: String? = null,
    val role: String? = null,
    val interactiveOnly: Boolean = false,
    /** Compare whole values instead of substrings. Substring matching is the default because a
     *  caller usually knows part of a label, not its exact composition. */
    val exact: Boolean = false,
) {
    val isEmpty: Boolean
        get() = text == null && contentDescription == null && testTag == null && resourceId == null && role == null && !interactiveOnly
}

/**
 * A criterion only one node type carries — `testTag` and `role` on a [ComposeNode], `resourceId` on
 * a [ViewNode] — rejects every node of the other type, the same way an absent value does: the caller
 * asked for something this node does not have.
 */
internal fun UiNode.matches(query: NodeQuery): Boolean {
    if (query.interactiveOnly && !isInteractive) return false
    if (!fieldMatches(query.text, listOfNotNull(text, editableText), query.exact)) return false
    if (!fieldMatches(query.contentDescription, listOfNotNull(contentDescription), query.exact)) return false
    if (!fieldMatches(query.testTag, listOfNotNull((this as? ComposeNode)?.testTag), query.exact)) return false
    if (!fieldMatches(query.resourceId, listOfNotNull((this as? ViewNode)?.resourceId), exact = true)) return false
    if (!fieldMatches(query.role, listOfNotNull((this as? ComposeNode)?.role), query.exact)) return false
    return true
}

/** A free-text search over everything a node displays, for the tree view's search box. */
internal fun UiNode.matchesFreeText(term: String): Boolean {
    if (term.isBlank()) return true
    val identifiers = when (this) {
        is ComposeNode -> listOfNotNull(testTag, role)
        is ViewNode -> listOfNotNull(resourceId, viewClass)
    }
    val haystack = listOfNotNull(text, editableText, contentDescription, id.toString()) + identifiers
    return haystack.any { it.contains(term, ignoreCase = true) }
}

private fun fieldMatches(expected: String?, candidates: List<String>, exact: Boolean): Boolean {
    if (expected == null) return true
    return candidates.any { if (exact) it.equals(expected, ignoreCase = true) else it.contains(expected, ignoreCase = true) }
}
