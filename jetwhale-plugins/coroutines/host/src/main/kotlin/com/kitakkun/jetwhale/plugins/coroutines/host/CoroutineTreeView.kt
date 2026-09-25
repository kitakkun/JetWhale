package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState

/**
 * Which coroutines to show. A condition left null does not filter; [states] empty shows every state.
 *
 * @property minObservedMillis Only coroutines seen at least this long: the ones that may be stuck.
 */
internal data class CoroutineFilter(
    val states: Set<CoroutineState>,
    val nameContains: String?,
    val dispatcherContains: String?,
    val minObservedMillis: Long?,
) {
    fun matches(node: CoroutineNode): Boolean = (states.isEmpty() || node.state in states) &&
        (nameContains.isNullOrBlank() || node.name.orEmpty().contains(nameContains, ignoreCase = true) || node.description.contains(nameContains, ignoreCase = true)) &&
        (dispatcherContains.isNullOrBlank() || node.dispatcher.orEmpty().contains(dispatcherContains, ignoreCase = true)) &&
        (minObservedMillis == null || node.observedMillis >= minObservedMillis)

    companion object {
        val None: CoroutineFilter = CoroutineFilter(states = emptySet(), nameContains = null, dispatcherContains = null, minObservedMillis = null)
    }
}

/**
 * The tree cut down to the coroutines [filter] matches and the ancestors that lead to them, so a
 * match is always shown where it lives. The registered roots are kept even when nothing below them
 * matches, since they are what the app asked to see.
 */
internal fun filterCoroutineTree(roots: List<CoroutineNode>, filter: CoroutineFilter): List<CoroutineNode> {
    if (filter == CoroutineFilter.None) return roots
    fun prune(node: CoroutineNode): CoroutineNode? {
        val children = node.children.mapNotNull(::prune)
        return if (children.isNotEmpty() || filter.matches(node)) node.copy(children = children) else null
    }
    return roots.map { root -> root.copy(children = root.children.mapNotNull(::prune)) }
}

/**
 * One visible line of the coroutine tree.
 *
 * @property rowId The ids from the root down to this node. A coroutine below two overlapping
 *   registered scopes appears twice with the same id; the path tells the two rows apart. A job
 *   registered more than once is a root more than once, so a repeated root id carries its
 *   occurrence (`id#1`).
 */
internal data class CoroutineRow(
    val node: CoroutineNode,
    val rowId: String,
    val depth: Int,
    val expanded: Boolean,
)

/**
 * The rows the tree shows: every root, and below each row whose id is not in [collapsed], its
 * children. Rows start expanded, so a newly appearing coroutine is visible without a click.
 */
internal fun flattenCoroutineTree(roots: List<CoroutineNode>, collapsed: Set<String>): List<CoroutineRow> = buildList {
    fun add(node: CoroutineNode, rowId: String, depth: Int) {
        val expanded = rowId !in collapsed
        add(CoroutineRow(node = node, rowId = rowId, depth = depth, expanded = expanded))
        if (expanded) node.children.forEach { add(it, "$rowId/${it.id}", depth + 1) }
    }
    val rootOccurrences = mutableMapOf<String, Int>()
    roots.forEach { root ->
        val occurrence = rootOccurrences.getOrElse(root.id) { 0 }
        rootOccurrences[root.id] = occurrence + 1
        add(root, rowId = if (occurrence == 0) root.id else "${root.id}#$occurrence", depth = 0)
    }
}

/** "3.2 s", "4 min 10 s": how long a coroutine has been around, at the precision a person reads it. */
internal fun formatObserved(millis: Long): String = when {
    millis < 1_000 -> "$millis ms"
    millis < 60_000 -> "${millis / 100 / 10.0} s"
    millis < 3_600_000 -> "${millis / 60_000} min ${millis / 1_000 % 60} s"
    else -> "${millis / 3_600_000} h ${millis / 60_000 % 60} min"
}
