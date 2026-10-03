package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode

/**
 * Matcher shared by the tree view's search box and the `findNodes` MCP tool.
 *
 * @property resourceId Always compared whole, whatever [exact] says: a resource id is an
 *   identifier, not a label.
 * @property operableOnly Keep only nodes the user could operate right now — see
 *   [UiNode.isOperable].
 * @property exact Compare whole values instead of substrings. Substring matching is the default
 *   because a caller usually knows part of a label, not its exact composition.
 */
internal data class NodeQuery(
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    val resourceId: String? = null,
    val role: String? = null,
    val interactiveOnly: Boolean = false,
    val operableOnly: Boolean = false,
    val exact: Boolean = false,
) {
    val isEmpty: Boolean
        get() = text == null && contentDescription == null && testTag == null && resourceId == null && role == null &&
            !interactiveOnly && !operableOnly
}

/**
 * A criterion only one node type carries — `testTag` and `role` on a [ComposeNode], `resourceId` on
 * a [ViewNode] — rejects every node of the other type, the same way an absent value does: the caller
 * asked for something this node does not have. The exception is `testTag`, which an [AppleNode]
 * answers with its `accessibilityIdentifier`: that is where a Compose `testTag` lands on iOS, so a
 * caller looking for a tag finds the node whichever toolkit rendered it.
 */
internal fun UiNode.matches(query: NodeQuery): Boolean {
    if (query.interactiveOnly && !isInteractive) return false
    if (query.operableOnly && !isOperable) return false
    if (!fieldMatches(query.text, listOfNotNull(text, editableText), query.exact)) return false
    if (!fieldMatches(query.contentDescription, listOfNotNull(contentDescription), query.exact)) return false
    if (!fieldMatches(query.testTag, listOfNotNull(tagLikeIdentifier), query.exact)) return false
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
        is AppleNode -> listOfNotNull(accessibilityIdentifier, className)
    }
    val haystack = listOfNotNull(text, editableText, contentDescription, id.toString()) + identifiers
    return haystack.any { it.contains(term, ignoreCase = true) }
}

/** The identifier an author put on the node to find it again: `testTag`, or its landing spot on iOS. */
private val UiNode.tagLikeIdentifier: String?
    get() = when (this) {
        is ComposeNode -> testTag
        is AppleNode -> accessibilityIdentifier
        is ViewNode -> null
    }

private fun fieldMatches(expected: String?, candidates: List<String>, exact: Boolean): Boolean {
    if (expected == null) return true
    return candidates.any { if (exact) it.equals(expected, ignoreCase = true) else it.contains(expected, ignoreCase = true) }
}
