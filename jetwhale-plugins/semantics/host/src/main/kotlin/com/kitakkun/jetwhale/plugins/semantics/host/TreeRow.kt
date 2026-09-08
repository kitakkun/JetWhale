package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode

/** One line of the tree view. */
internal sealed interface TreeRow {
    val key: String

    data class RootHeader(val root: ComposeRoot, val nodeCount: Int) : TreeRow {
        override val key: String get() = "root:${root.rootId}"
    }

    data class NodeRow(
        val rootId: String,
        val node: UiNode,
        val depth: Int,
        val expandable: Boolean,
        val expanded: Boolean,
    ) : TreeRow {
        override val key: String get() = "node:$rootId:${node.id}"
    }
}

/**
 * Flattens the roots into the rows the tree view draws.
 *
 * Filtering happens before flattening so a match keeps its ancestors and stays where it belongs in
 * the tree; collapsing happens during it, so a collapsed node's subtree costs nothing to skip. A
 * root with no surviving node still gets its header — "this window has nothing matching" is
 * information, and silently dropping the root would read as the window being gone.
 */
internal fun buildTreeRows(
    roots: List<ComposeRoot>,
    collapsedKeys: Set<NodeKey>,
    predicate: (UiNode) -> Boolean,
): List<TreeRow> = buildList {
    for (root in roots) {
        val filtered = root.node?.filterTree(predicate)
        add(TreeRow.RootHeader(root, nodeCount = filtered?.asSequence()?.count() ?: 0))
        if (filtered != null) appendNodeRows(root.rootId, filtered, depth = 0, collapsedKeys = collapsedKeys)
    }
}

private fun MutableList<TreeRow>.appendNodeRows(
    rootId: String,
    node: UiNode,
    depth: Int,
    collapsedKeys: Set<NodeKey>,
) {
    val expanded = NodeKey(rootId, node.id) !in collapsedKeys
    add(
        TreeRow.NodeRow(
            rootId = rootId,
            node = node,
            depth = depth,
            expandable = node.children.isNotEmpty(),
            expanded = expanded,
        ),
    )
    if (!expanded) return
    node.children.forEach { appendNodeRows(rootId, it, depth + 1, collapsedKeys) }
}
