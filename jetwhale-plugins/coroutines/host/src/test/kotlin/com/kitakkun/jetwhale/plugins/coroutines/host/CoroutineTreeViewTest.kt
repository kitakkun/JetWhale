package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import kotlin.test.Test
import kotlin.test.assertEquals

class CoroutineTreeViewTest {
    private val tree = listOf(
        node(
            "root",
            "Application",
            CoroutineState.Active,
            observed = 120_000,
            node("sync", "sync", CoroutineState.Active, observed = 90_000, node("download", "download", CoroutineState.Cancelling, observed = 2_000)),
            node("poll", "poll", CoroutineState.Active, observed = 500),
        ),
    )

    @Test
    fun `a match deep in the tree is shown with the ancestors that lead to it`() {
        val filtered = filterCoroutineTree(tree, CoroutineFilter.None.copy(states = setOf(CoroutineState.Cancelling)))

        assertEquals(listOf("Application", "sync", "download"), flattenCoroutineTree(filtered, collapsed = emptySet()).map { it.node.name })
    }

    @Test
    fun `a registered root stays even when nothing below it matches`() {
        val filtered = filterCoroutineTree(tree, CoroutineFilter.None.copy(nameContains = "nothing"))

        assertEquals(listOf("Application"), flattenCoroutineTree(filtered, collapsed = emptySet()).map { it.node.name })
    }

    @Test
    fun `the age filter keeps only coroutines seen at least that long`() {
        val filtered = filterCoroutineTree(tree, CoroutineFilter.None.copy(minObservedMillis = 60_000))

        assertEquals(listOf("Application", "sync"), flattenCoroutineTree(filtered, collapsed = emptySet()).map { it.node.name })
    }

    @Test
    fun `a collapsed node hides its descendants but keeps its depth`() {
        val rows = flattenCoroutineTree(tree, collapsed = setOf("sync"))

        assertEquals(listOf("Application" to 0, "sync" to 1, "poll" to 1), rows.map { it.node.name to it.depth })
    }

    @Test
    fun `observed times read at the precision a person needs`() {
        assertEquals("850 ms", formatObserved(850))
        assertEquals("4.2 s", formatObserved(4_250))
        assertEquals("2 min 5 s", formatObserved(125_000))
        assertEquals("1 h 1 min", formatObserved(3_660_000))
    }
}

internal fun node(id: String, name: String?, state: CoroutineState, observed: Long, vararg children: CoroutineNode): CoroutineNode = CoroutineNode(
    id = id,
    name = name,
    state = state,
    observedMillis = observed,
    dispatcher = "Dispatchers.Default",
    description = "StandaloneCoroutine{${state.name}}",
    children = children.toList(),
)
