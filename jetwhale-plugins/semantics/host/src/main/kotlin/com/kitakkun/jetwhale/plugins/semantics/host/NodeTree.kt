package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeKind
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot

/** Identifies one node across the whole snapshot; node ids are only unique within their root. */
internal data class NodeKey(val rootId: String, val nodeId: Int)

/**
 * Keeps the nodes [predicate] accepts, plus every ancestor of a kept node.
 *
 * Ancestors are kept even when they fail the predicate: a filter that dropped them would reparent
 * the matches and lose the structure that makes the tree readable in the first place. Returns
 * `null` when nothing in this subtree matched.
 */
internal fun ComposeNode.filterTree(predicate: (ComposeNode) -> Boolean): ComposeNode? {
    val keptChildren = children.mapNotNull { it.filterTree(predicate) }
    return when {
        keptChildren.isNotEmpty() -> copy(children = keptChildren)
        predicate(this) -> copy(children = emptyList())
        else -> null
    }
}

/** Depth-first walk, this node first. */
internal fun ComposeNode.asSequence(): Sequence<ComposeNode> = sequence {
    yield(this@asSequence)
    children.forEach { yieldAll(it.asSequence()) }
}

internal fun ComposeRoot.findNode(nodeId: Int): ComposeNode? = node?.asSequence()?.firstOrNull { it.id == nodeId }

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
internal val ComposeNode.isInteractive: Boolean
    get() = actions.isNotEmpty() || isClickable || isEditable || isScrollable

/** How a node should read in a list: its own label if it has one, otherwise its role or id. */
internal fun ComposeNode.displayLabel(): String {
    val label = text ?: contentDescription ?: editableText ?: testTag
    // A View is named by its class the way a Compose node is named by its role: it is what says
    // what the thing is. The resource id comes next, because that is what the app calls it.
    if (kind == NodeKind.View) {
        return buildString {
            append(viewClass?.substringAfterLast('.') ?: "View")
            resourceId?.let { append(" · @id/$it") }
            label?.let { append(" · $it") }
        }
    }
    val role = role
    return when {
        role != null && label != null -> "$role · $label"
        role != null -> role
        label != null -> label
        else -> "#$id"
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

internal fun ComposeNode.matches(query: NodeQuery): Boolean {
    if (query.interactiveOnly && !isInteractive) return false
    if (!fieldMatches(query.text, listOfNotNull(text, editableText), query.exact)) return false
    if (!fieldMatches(query.contentDescription, listOfNotNull(contentDescription), query.exact)) return false
    if (!fieldMatches(query.testTag, listOfNotNull(testTag), query.exact)) return false
    if (!fieldMatches(query.resourceId, listOfNotNull(resourceId), exact = true)) return false
    if (!fieldMatches(query.role, listOfNotNull(role), query.exact)) return false
    return true
}

/** A free-text search over everything a node displays, for the tree view's search box. */
internal fun ComposeNode.matchesFreeText(term: String): Boolean {
    if (term.isBlank()) return true
    val haystack = listOfNotNull(text, editableText, contentDescription, testTag, resourceId, viewClass, role, id.toString())
    return haystack.any { it.contains(term, ignoreCase = true) }
}

private fun fieldMatches(expected: String?, candidates: List<String>, exact: Boolean): Boolean {
    if (expected == null) return true
    return candidates.any { if (exact) it.equals(expected, ignoreCase = true) else it.contains(expected, ignoreCase = true) }
}
