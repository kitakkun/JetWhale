package com.kitakkun.jetwhale.plugins.nav3.agent

import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

/**
 * Applies [operations] to [stack] in order, as one unit: every operation is validated and every key
 * decoded against a working copy first, so an invalid request leaves the app's back stack untouched
 * instead of half-navigated.
 *
 * The result is written back as the smallest possible edit — the unchanged bottom of the stack is
 * left in place — because Navigation 3 ties each entry's saved state and ViewModels to its presence
 * in the list: rewriting entries that did not change would silently drop the state behind them.
 *
 * @throws IllegalArgumentException when an index is out of range, a key cannot be decoded, or the
 *   operations would leave the stack empty.
 */
internal fun <K> applyNavOperations(
    stack: MutableList<K>,
    operations: List<NavBackStackOperation>,
    decodeKey: (JsonElement) -> K,
) {
    val working = stack.toMutableList()

    operations.forEach { operation ->
        when (operation) {
            is NavBackStackOperation.Push -> {
                val key = decode(operation.key, decodeKey)
                val index = operation.index ?: working.size
                require(index in 0..working.size) { "push index $index is out of range (0..${working.size})" }
                working.add(index, key)
            }

            is NavBackStackOperation.Pop -> {
                require(operation.count > 0) { "pop count must be positive, was ${operation.count}" }
                require(operation.count <= working.size) { "cannot pop ${operation.count} entries off a stack of ${working.size}" }
                repeat(operation.count) { working.removeAt(working.lastIndex) }
            }

            is NavBackStackOperation.PopTo -> {
                require(operation.index in working.indices) { "popTo index ${operation.index} is out of range (${rangeOf(working)})" }
                val remaining = if (operation.inclusive) operation.index else operation.index + 1
                while (working.size > remaining) working.removeAt(working.lastIndex)
            }

            is NavBackStackOperation.RemoveAt -> {
                require(operation.index in working.indices) { "removeAt index ${operation.index} is out of range (${rangeOf(working)})" }
                working.removeAt(operation.index)
            }

            is NavBackStackOperation.MoveToTop -> {
                require(operation.index in working.indices) { "moveToTop index ${operation.index} is out of range (${rangeOf(working)})" }
                working.add(working.removeAt(operation.index))
            }

            is NavBackStackOperation.ReplaceAll -> {
                val keys = operation.keys.map { decode(it, decodeKey) }
                working.clear()
                working.addAll(keys)
            }
        }
    }

    // NavDisplay renders the last entry, so an empty stack crashes the app being debugged. Refusing
    // is the friendlier failure: the caller gets a message, the app keeps running.
    require(working.isNotEmpty()) { "the operations would leave the back stack empty, which Navigation 3 cannot render" }

    writeBack(stack, working)
}

private fun <K> decode(element: JsonElement, decodeKey: (JsonElement) -> K): K = try {
    decodeKey(element)
} catch (e: SerializationException) {
    throw IllegalArgumentException("cannot build a NavKey from $element: ${e.message}", e)
}

private fun rangeOf(stack: List<*>): String = if (stack.isEmpty()) "the stack is empty" else "0..${stack.lastIndex}"

/** Replaces the tail that actually changed, keeping the untouched entries below it identical. */
private fun <K> writeBack(stack: MutableList<K>, working: List<K>) {
    var unchanged = 0
    while (unchanged < stack.size && unchanged < working.size && stack[unchanged] == working[unchanged]) {
        unchanged++
    }
    while (stack.size > unchanged) stack.removeAt(stack.lastIndex)
    for (index in unchanged until working.size) stack.add(working[index])
}
