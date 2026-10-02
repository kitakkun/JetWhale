package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
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
    is AppleNode -> copy(children = children)
}

/** Depth-first walk, this node first. */
internal fun UiNode.asSequence(): Sequence<UiNode> = sequence {
    yield(this@asSequence)
    children.forEach { yieldAll(it.asSequence()) }
}

internal fun ComposeRoot.findNode(nodeId: Int): UiNode? = node?.findDescendant(nodeId)

/** This node, or the node below it with [nodeId]. */
internal fun UiNode.findDescendant(nodeId: Int): UiNode? = if (id == nodeId) this else children.firstNotNullOfOrNull { it.findDescendant(nodeId) }

/** How many nodes this subtree holds, this node included. */
internal fun UiNode.subtreeSize(): Int = 1 + children.sumOf(UiNode::subtreeSize)

/**
 * The root holding [nodeId], for resolving a node the caller named without saying where. Searched
 * newest root first: ids are only unique within a root, and the newest window (a dialog over the
 * screen that opened it) is the one a caller is looking at.
 */
internal fun NodeTreeSnapshot.findRootOf(nodeId: Int): ComposeRoot? = roots.lastOrNull { it.findNode(nodeId) != null }

internal fun NodeTreeSnapshot.nodeCount(): Int = roots.sumOf { it.node?.subtreeSize() ?: 0 }

/**
 * The node whose platform attributes are worth reading, given what the tree has selected: only an
 * Android `View` has any.
 *
 * `null` for a Compose node, for no selection, and for a key this snapshot does not hold — all of
 * which mean the attribute section has nothing to show.
 */
internal fun NodeTreeSnapshot?.viewAttributeNode(selected: NodeKey?): NodeKey? {
    if (selected == null) return null
    val node = this?.roots?.firstOrNull { it.rootId == selected.rootId }?.findNode(selected.nodeId)
    return selected.takeIf { node is ViewNode }
}

/**
 * `true` when the node offers something to do: an action, editable content, or scrolling.
 *
 * This is the filter that answers "what can be operated here" — the question both the tree view's
 * *interactive only* toggle and an AI agent driving the app are actually asking.
 */
internal val UiNode.isInteractive: Boolean
    get() = actions.isNotEmpty() || isClickable || isEditable || isScrollable

/**
 * `true` when the user could operate the node right now: it is [isInteractive], enabled, and a
 * gesture it accepts reaches it. The one answer an agent wants before it acts; the three facts it
 * is made of say why when it is `false`.
 */
internal val UiNode.isOperable: Boolean
    get() = isInteractive && isEnabled && isHittable

/** How a node should read in a list: its own label if it has one, otherwise its role or id. */
internal fun UiNode.displayLabel(): String {
    val label = ownLabel
    return when (this) {
        is AppleNode -> buildString {
            append(className.substringAfterLast('.'))
            accessibilityIdentifier?.let { append(" · $it") }
            label?.let { append(" · $it") }
        }

        is ViewNode -> buildString {
            append(viewClass.substringAfterLast('.'))
            resourceId?.let { append(" · @id/$it") }
            label?.let { append(" · $it") }
        }

        is ComposeNode -> {
            val role = role
            when {
                isLabeledById -> "#$id"
                role != null && label != null -> "$role · $label"
                else -> role ?: label.orEmpty()
            }
        }
    }
}

/** `true` when [displayLabel] falls back to the node's id, the node having no role or label of its own. */
internal val UiNode.isLabeledById: Boolean
    get() = this is ComposeNode && role == null && ownLabel == null

private val UiNode.ownLabel: String?
    get() = listOfNotNull(text, contentDescription, editableText, (this as? ComposeNode)?.testTag).firstOrNull()
