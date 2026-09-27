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
    fun `named only hides unnamed coroutines but keeps an unnamed one that leads to a named one`() {
        val compose = listOf(
            node(
                "root",
                "Compose",
                CoroutineState.Active,
                observed = 0,
                node("ripple", null, CoroutineState.Active, observed = 0),
                node("effect", null, CoroutineState.Active, observed = 0, node("poll", "panel-poll", CoroutineState.Active, observed = 0)),
            ),
        )

        val filtered = filterCoroutineTree(compose, CoroutineFilter.None.copy(namedOnly = true))

        assertEquals(listOf("root", "effect", "poll"), flattenCoroutineTree(filtered, collapsed = emptySet()).map { it.node.id })
        assertEquals(2, countMatchingCoroutines(compose, CoroutineFilter.None.copy(namedOnly = true)))
    }

    @Test
    fun `the match count leaves out the ancestors shown only as the path to a match`() {
        val filter = CoroutineFilter.None.copy(states = setOf(CoroutineState.Cancelling))

        assertEquals(1, countMatchingCoroutines(tree, filter))
        assertEquals(4, countMatchingCoroutines(tree, CoroutineFilter.None))
    }

    @Test
    fun `a collapsed node hides its descendants but keeps its depth`() {
        val rows = flattenCoroutineTree(tree, collapsed = setOf("root/sync"))

        assertEquals(listOf("Application" to 0, "sync" to 1, "poll" to 1), rows.map { it.node.name to it.depth })
    }

    @Test
    fun `a coroutine under two overlapping registered scopes gets a distinct row id each time`() {
        val shared = node("shared", "shared", CoroutineState.Active, observed = 0)
        val roots = listOf(node("parent", "Parent", CoroutineState.Active, 0, node("child", "Child", CoroutineState.Active, 0, shared)), node("child", "Child", CoroutineState.Active, 0, shared))

        val rowIds = flattenCoroutineTree(roots, collapsed = emptySet()).map(CoroutineRow::rowId)

        assertEquals(rowIds.size, rowIds.toSet().size)
    }

    @Test
    fun `a job registered under two names gets a distinct row id for each root`() {
        val job = node("job", "Application", CoroutineState.Active, 0, node("child", "Child", CoroutineState.Active, 0))

        val rowIds = flattenCoroutineTree(listOf(job, job.copy(name = "App")), collapsed = emptySet()).map(CoroutineRow::rowId)

        assertEquals(listOf("job", "job/child", "job#1", "job#1/child"), rowIds)
    }

    @Test
    fun `a coroutine is found with the coroutines from its root down to its parent`() {
        val location = findCoroutine(tree, "download")

        assertEquals(listOf("root", "sync"), location?.ancestors?.map(CoroutineNode::id))
        assertEquals(null, findCoroutine(tree, "gone"))
    }

    @Test
    fun `the coroutines below one are counted by state at every depth`() {
        assertEquals(mapOf(CoroutineState.Active to 2, CoroutineState.Cancelling to 1), tree.single().descendantStates())
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
